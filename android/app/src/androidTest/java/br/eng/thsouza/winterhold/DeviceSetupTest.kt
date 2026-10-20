package br.eng.thsouza.winterhold

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/** Explicit opt-in device setup; never included in the shipped application. */
@RunWith(AndroidJUnit4::class)
class DeviceSetupTest {
    @Test fun configureOnlyWhenExplicitlyRequested() {
        val args = InstrumentationRegistry.getArguments()
        if (args.getString("configureDevice") != "true") return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val password = args.getString("adminPassword") ?: error("Provide adminPassword")
        instrumentation.runOnMainSync {
            DemoCentral.ensureStarted(context)
            val users = DemoCentral.webUsers!!
            val admin = users.users.first { it.username == "admin" }
            users.resetPassword(admin.id, password)
            if (args.getString("resetTestState") == "true") {
                DemoCentral.store.restoreJson(DemoStore().savedJson())
                DeliverySettings(context).apply { smtpHost = ""; smtpUser = ""; smtpFrom = ""; smtpPassword = "" }
            }
            DemoCentral.store.liveMode = true; DemoCentral.store.simulationRunning = false
        }
        val settings = DeliverySettings(context)
        val deadline = System.currentTimeMillis() + 90_000
        var pairing = false
        while (System.currentTimeMillis() < deadline && !pairing) {
            runCatching {
                val connection = URL("${settings.bridgeUrl}/status").openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 2000; connection.readTimeout = 5000
                    connection.setRequestProperty("Authorization", "Bearer ${settings.bridgeToken}")
                    assertEquals(200, connection.responseCode)
                    val result = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    val image = result.optString("qr")
                    if (image.startsWith("data:image/png;base64,")) {
                        java.io.File(context.cacheDir, "whatsapp-qr.png").writeBytes(android.util.Base64.decode(image.substringAfter(','), android.util.Base64.DEFAULT))
                        pairing = true
                    } else if (result.optString("connection") == "connected") pairing = true
                    java.io.File(context.cacheDir, "bridge-validation.json").writeText(JSONObject()
                        .put("connection", result.optString("connection"))
                        .put("runtime", EmbeddedBridge.status).put("tlsFingerprint", WebTls.fingerprint).toString())
                } finally { connection.disconnect() }
            }
            if (!pairing) Thread.sleep(2000)
        }
        assertTrue("Baileys must reach QR pairing or connected state", pairing)
    }
}
