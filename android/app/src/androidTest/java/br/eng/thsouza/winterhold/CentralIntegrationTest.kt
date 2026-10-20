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
import java.net.Socket
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class CentralIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private var session = ""
    private val testPassword = "Test-only-long-password-2026"
    private var testUserId: String? = null
    private var originalState: String? = null
    private var originalSettings: List<String>? = null
    @org.junit.Before fun authenticate() {
        instrumentation.runOnMainSync {
            DemoCentral.ensureStarted(context)
            originalState = DemoCentral.store.savedJson()
            val settings = DeliverySettings(context)
            originalSettings = listOf(settings.smtpHost, settings.smtpUser, settings.smtpFrom, settings.bridgeUrl)
            DemoCentral.store.contacts.clear() // MQTT fixtures must never notify real recipients.
            DemoCentral.webUsers!!.users.firstOrNull { it.username == "instrumentation" }?.let { DemoCentral.webUsers!!.delete(it.id) }
            testUserId = DemoCentral.webUsers!!.add("Instrumentation", "instrumentation", testPassword).id
        }
        val connection = URL("http://127.0.0.1:8790/api/v1/login").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"; connection.doOutput = true
        connection.setRequestProperty("X-Winterhold-Request", "1")
        connection.outputStream.use { it.write(JSONObject().put("username", "instrumentation").put("password", testPassword).toString().toByteArray()) }
        assertEquals(200, connection.responseCode)
        session = connection.getHeaderField("Set-Cookie").substringBefore(';')
        connection.disconnect()
    }
    @org.junit.After fun cleanupAccount() {
        instrumentation.runOnMainSync {
            testUserId?.let { DemoCentral.webUsers?.delete(it) }
            originalState?.let { DemoCentral.store.restoreJson(it); DemoCentral.save() }
            originalSettings?.let { values -> DeliverySettings(context).apply {
                smtpHost = values[0]; smtpUser = values[1]; smtpFrom = values[2]; bridgeUrl = values[3]
            } }
        }
    }
    private fun request(path: String, action: String? = null): Pair<Int, String> {
        val connection = URL("http://127.0.0.1:8790$path").openConnection() as HttpURLConnection
        try {
            connection.setRequestProperty("Cookie", session)
            connection.setRequestProperty("X-Winterhold-Request", "1")
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
        var lastError: Throwable? = null
        while (System.currentTimeMillis() < deadline) {
            if (runCatching(check).onFailure { lastError = it }.getOrDefault(false)) return
            Thread.sleep(100)
        }
        throw AssertionError("Central não atingiu o estado esperado: ${DemoCentral.status}", lastError)
    }

    private fun publishMqttSample() {
        fun field(value: String) = byteArrayOf((value.length shr 8).toByte(), value.length.toByte()) + value.toByteArray()
        val connectBody = field("MQTT") + byteArrayOf(4, 0xC2.toByte(), 0, 30) +
            field("ci-esp") + field("winterhold") + field(DemoCentral.mqttSecret)
        require(connectBody.size < 128)
        val payload = """{"device_id":"ci-esp","timestamp":null,"temperatura_c":31.5,"umidade_pct":55,"tensao_rms_v":null}""".toByteArray()
        val publishBody = field("winterhold/ci-esp/leituras") + payload
        val length = ByteArrayOutputStream().apply {
            var remaining = publishBody.size
            do {
                var digit = remaining % 128
                remaining /= 128
                if (remaining > 0) digit = digit or 128
                write(digit)
            } while (remaining > 0)
        }.toByteArray()
        Socket("127.0.0.1", 1883).use { socket ->
            socket.soTimeout = 2_000
            socket.getOutputStream().write(byteArrayOf(0x10, connectBody.size.toByte()) + connectBody)
            val reply = ByteArray(4)
            java.io.DataInputStream(socket.getInputStream()).readFully(reply)
            assertArrayEquals(byteArrayOf(0x20, 2, 0, 0), reply)
            socket.getOutputStream().write(byteArrayOf(0x30) + length + publishBody)
            socket.getOutputStream().flush()
        }
    }

    @Test fun servesWithPasswordSessionPersistsAndSurvivesClosingActivity() {
        // Match the one-time preparation offered by the app, without changing other apps.
        instrumentation.uiAutomation.executeShellCommand(
            "dumpsys deviceidle whitelist +${context.packageName}"
        ).use { descriptor ->
            java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        }
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
        val initialState = JSONObject(request("/api/v1/state").second)
        assertEquals("esp", initialState.getString("mode"))
        assertFalse(initialState.has("webUsers"))
        assertTrue(initialState.getJSONObject("settings").getString("mqttSecret").isNotBlank())
        val environment = """{"type":"saveEnvironment","environment":{"id":"ci-esp","name":"Laboratório","icon":"lab","limits":{"temperatureMin":18,"temperatureMax":25,"humidityMin":30,"humidityMax":70,"voltageMin":198,"voltageMax":242}}}"""
        assertEquals(200, request("/api/v1/action", environment).first)
        assertEquals(400, request("/api/v1/action", environment.replace("\"temperatureMin\":18", "\"temperatureMin\":30")).first)
        publishMqttSample()
        awaitCondition {
            JSONObject(request("/api/v1/state").second).getJSONArray("devices").length() > 0
        }
        val liveState = JSONObject(request("/api/v1/action", """{"type":"mode","value":"esp"}""").second)
        assertEquals("ci-esp", liveState.getString("selectedDeviceId"))
        assertEquals("Laboratório", liveState.getJSONArray("devices").getJSONObject(0).getString("name"))
        assertEquals("lab", liveState.getJSONArray("devices").getJSONObject(0).getString("icon"))
        assertEquals(31.5, liveState.getJSONArray("samples").getJSONObject(0).getDouble("temperature"), 0.01)
        assertTrue(liveState.getJSONArray("events").getJSONObject(0).getString("title").contains("Temperatura"))
        assertEquals("ci-esp", liveState.getJSONArray("events").getJSONObject(0).getString("deviceId"))
        val demo = JSONObject(request("/api/v1/action", """{"type":"mode","value":"demo","acknowledgeRealDelivery":true}""").second)
        assertEquals(5, demo.getJSONArray("devices").length())
        assertEquals(200, request("/api/v1/action", """{"type":"running","value":false}""").first)
        val reading = """{"type":"setDemoReading","id":"demo_servidores","temperature":30,"humidity":40,"voltage":220}"""
        assertEquals(200, request("/api/v1/action", reading).first)
        val serverRoom = JSONObject(request("/api/v1/action", """{"type":"selectedDevice","value":"demo_servidores"}""").second)
        assertEquals(30.0, serverRoom.getJSONArray("samples").getJSONObject(0).getDouble("temperature"), 0.01)
        assertTrue(serverRoom.getJSONArray("events").getJSONObject(0).getBoolean("simulated"))
        assertEquals(200, request("/api/v1/action", """{"type":"deliverySettings","smtpHost":"smtp.example.com","smtpUser":"demo","smtpFrom":"sender@example.com","bridgeUrl":""}""").first)
        val config = JSONObject(request("/api/v1/state").second).getJSONObject("settings")
        assertEquals("smtp.example.com", config.getString("smtpHost"))
        assertFalse(config.has("smtpPassword"))
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
        instrumentation.runOnMainSync { DemoCentral.store.nominalVoltage = 230f }
        awaitCondition {
            JSONObject(File(context.filesDir, "demo-state.json").readText()).getInt("nominalVoltage") == 230
        }
        val reopened = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        awaitCondition { request("/healthz").first == 200 }
        val state = JSONObject(request("/api/v1/action", """{"type":"mode","value":"esp"}""").second)
        assertEquals(29, state.getJSONObject("limits").getInt("temperature"))
        assertEquals(230, state.getJSONObject("limits").getInt("nominalVoltage"))
        assertEquals("Laboratório", state.getJSONArray("devices").getJSONObject(0).getString("name"))
        assertFalse(state.getBoolean("running"))
        assertEquals(400, request("/api/v1/action", """{"type":"limit","key":"temperature","value":900}""").first)
        instrumentation.runOnMainSync { reopened.finish() }
        // Leave enabled for the CI boot/process-recovery smoke test.
    }
}
