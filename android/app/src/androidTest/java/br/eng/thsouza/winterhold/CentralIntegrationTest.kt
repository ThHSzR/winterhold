package br.eng.thsouza.winterhold

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@RunWith(AndroidJUnit4::class)
class CentralIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun request(path: String, action: String? = null): Pair<Int, String> {
        val connection = URL("http://127.0.0.1:8790$path").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 2_000
            connection.readTimeout = 4_000
            if (action != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(action.toByteArray()) }
            }
            val code = connection.responseCode
            val body = (if (code < 400) connection.inputStream else connection.errorStream)
                .bufferedReader().use { it.readText() }
            return code to body
        } finally { connection.disconnect() }
    }

    private fun awaitCondition(check: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            if (runCatching(check).getOrDefault(false)) return
            Thread.sleep(100)
        }
        fail("Central não atingiu o estado esperado")
    }

    @Test fun servesDirectIpWithoutKeyPersistsAndSurvivesClosingActivity() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.executeShellCommand(
                "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS"
            ).use { descriptor ->
                java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
            }
        }
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        awaitCondition { request("/healthz").first == 200 }
        assertTrue(request("/").second.contains("Winterhold"))
        assertEquals(200, request("/api/v1/state").first)
        assertEquals(200, request("/api/v1/action", """{"type":"running","value":false}""").first)
        assertEquals(200, request("/api/v1/action", """{"type":"limit","key":"temperature","value":29}""").first)
        awaitCondition {
            JSONObject(File(context.filesDir, "demo-state.json").readText()).getInt("temperatureLimit") == 29
        }
        instrumentation.runOnMainSync { activity.finish() }
        instrumentation.waitForIdleSync()
        assertEquals(200, request("/healthz").first)
        // Stop closes the listening socket and disables automatic startup.
        instrumentation.runOnMainSync { CentralService.stop(context) }
        awaitCondition { !CentralService.isEnabled(context) && runCatching { request("/healthz") }.isFailure }
        val reopened = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        awaitCondition { request("/healthz").first == 200 }
        val state = JSONObject(request("/api/v1/state").second)
        assertEquals(29, state.getJSONObject("limits").getInt("temperature"))
        assertFalse(state.getBoolean("running"))
        assertEquals(400, request("/api/v1/action", """{"type":"limit","key":"temperature","value":900}""").first)
        instrumentation.runOnMainSync { reopened.finish() }
        // Leave enabled for the CI boot/process-recovery smoke test.
    }
}
