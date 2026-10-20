package br.eng.thsouza.winterhold

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DemoStoreTest {
    @Test fun favoritesAndRecipientsPersistWhileDemoResetsOnRestart() {
        val original = DemoStore()
        original.saveEnvironment(EnvironmentProfile("lab-01", "Laboratório", favorite = true))
        val recipient = original.contacts.first().copy(phone = "+5511999999999", email = "alertas@example.com")
        original.updateContact(recipient)
        original.simulationRunning = true
        original.liveMode = false
        val restored = DemoStore().apply { restoreJson(original.savedJson()) }
        assertTrue(restored.environments.getValue("lab-01").favorite)
        assertEquals(recipient, restored.contacts.first())
        assertFalse(restored.simulationRunning)
        assertTrue(restored.liveMode)
        assertThrows(IllegalArgumentException::class.java) {
            restored.updateContact(recipient.copy(phone = "11999999999"))
        }
    }

    @Test fun profilesKeepIndependentMinimumMaximumAlertsAndHistory() {
        val store = DemoStore()
        store.saveEnvironment(EnvironmentProfile("lab-01", "Laboratório", "lab",
            temperatureMin = 20f, temperatureMax = 25f))
        store.saveEnvironment(EnvironmentProfile("sala-02", "Sala de aula", "classroom",
            temperatureMin = 18f, temperatureMax = 30f))
        store.liveMode = true
        store.ingest(Reading(1_000, 27f, 50f, 220f, "lab-01"))
        store.ingest(Reading(2_000, 27f, 50f, 220f, "sala-02"))
        assertEquals(1, store.events.count { !it.simulated && it.title.contains("acima do máximo") })
        assertEquals("lab-01", store.events.first().deviceId)
        store.selectedDeviceId = "sala-02"
        assertEquals(1, store.visibleReadings.size)
        assertEquals(2_000L, store.latest.time)
        store.ingest(Reading(3_000, 16f, 50f, 220f, "sala-02"))
        assertEquals("sala-02", store.events.first().deviceId)
        assertTrue(store.events.first().title.contains("abaixo do mínimo"))
        store.ingest(Reading(4_000, 23f, 50f, 220f, "lab-01"))
        assertTrue(store.events.any { it.deviceId == "lab-01" && it.title.contains("normalizada") })
        assertTrue(store.events.any { it.deviceId == "sala-02" && !it.resolved && it.title.contains("abaixo") })
        val restored = DemoStore().apply { restoreJson(store.savedJson()) }
        assertEquals(store.environments.toMap(), restored.environments.toMap())
        assertEquals(store.liveReadings.toList(), restored.liveReadings.toList())
        assertEquals(store.events.toList(), restored.events.toList())
        assertEquals("sala-02", restored.selectedDeviceId)
    }

    @Test fun profileUpdatesRejectInvalidBoundsWithoutChangingSavedEnvironment() {
        val store = DemoStore()
        val valid = EnvironmentProfile("sala-01", "Sala 01")
        store.saveEnvironment(valid)
        assertThrows(IllegalArgumentException::class.java) {
            store.saveEnvironment(valid.copy(temperatureMin = 28f, temperatureMax = 26f))
        }
        assertEquals(valid, store.environments["sala-01"])
        assertThrows(IllegalArgumentException::class.java) {
            store.saveEnvironment(valid.copy(icon = "<script>"))
        }
    }

    @Test fun versionTwoStateMigratesExistingEspToEnvironment() {
        val original = DemoStore().apply { temperatureLimit = 29f; ingest(Reading(1_000, 24f, 50f, 220f, "antigo")) }
        val old = JSONObject(original.savedJson()).put("version", 2).apply { remove("environments") }.toString()
        val restored = DemoStore().apply { restoreJson(old) }
        assertEquals("antigo", restored.environments["antigo"]?.name)
        assertEquals(29f, restored.environments.getValue("antigo").temperatureMax, .001f)
        assertEquals(original.liveReadings.toList(), restored.liveReadings.toList())
    }
    @Test fun acceptsFirmwarePayloadAndTracksRealEventsSeparately() {
        val payload = """{"device_id":"sala-01","timestamp":null,"temperatura_c":28.2,"umidade_pct":null,"tensao_rms_v":null}"""
        val first = EspTelemetry.decode("winterhold/sala-01/leituras", payload.toByteArray(), 1_000)
        val store = DemoStore().apply { liveMode = true; ingest(first) }
        assertEquals("sala-01", store.selectedDeviceId)
        assertEquals(28.2f, store.latest.temperature!!, 0.001f)
        assertNull(store.latest.voltage)
        assertTrue(store.events.first().title.contains("Temperatura"))
        assertFalse(store.events.first().simulated)
        store.ingest(first.copy(time = 2_000, temperature = 23f))
        assertTrue(store.events.any { it.title.contains("normalizada") })
        store.heartbeat(20_000)
        assertFalse(store.deviceOnline)
        assertTrue(store.events.first().title.contains("sem comunicação"))
        val restored = DemoStore().apply { restoreJson(store.savedJson()) }
        assertEquals(store.liveReadings.toList(), restored.liveReadings.toList())
        assertEquals(store.events.toList(), restored.events.toList())
        assertTrue(restored.liveMode)
    }

    @Test fun rejectsMismatchedOrInvalidFirmwareSamples() {
        val topic = "winterhold/sala-01/leituras"
        fun decode(json: String) = EspTelemetry.decode(topic, json.toByteArray())
        assertThrows(IllegalArgumentException::class.java) {
            decode("""{"device_id":"outro","temperatura_c":20,"umidade_pct":50,"tensao_rms_v":220}""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            decode("""{"device_id":"sala-01","temperatura_c":99,"umidade_pct":50,"tensao_rms_v":220}""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            decode("""{"device_id":"sala-01","temperatura_c":null,"umidade_pct":null,"tensao_rms_v":null}""")
        }
    }
    @Test fun restoresSharedSettingsHistoryContactsAndAlerts() {
        val original = DemoStore().apply {
            temperatureLimit = 30f
            humidityLimit = 80f
            simulationRunning = false
            liveMode = false
            simulateDisconnection()
            simulateAlert(Metric.VOLTAGE)
            resolveAlert(events.first().id)
            addContact("Laboratório", "Pesquisa")
            updateContact(contacts.last().copy(active = false, channels = setOf(Channel.EMAIL)))
            enabledChannels.clear()
            enabledChannels.add(Channel.EMAIL)
        }
        val restored = DemoStore().apply { restoreJson(original.savedJson()) }
        assertEquals(original.readings.toList(), restored.readings.toList())
        assertEquals(original.events.toList(), restored.events.toList())
        assertEquals(original.contacts.toList(), restored.contacts.toList())
        assertFalse(restored.simulationRunning)
        assertFalse(restored.offlineDemo)
        restored.advance()
        assertEquals(original.readings.toList(), restored.readings.toList())
    }

    @Test fun demoResetsOffOnStartupAndKeepsBoundedHistory() {
        val original = DemoStore().apply { simulationRunning = true; liveMode = false }
        repeat(80) { original.advance() }
        val restored = DemoStore().apply { restoreJson(original.savedJson()) }
        assertFalse(restored.simulationRunning)
        assertTrue(restored.liveMode)
        restored.advance()
        assertEquals(original.readings.toList(), restored.readings.toList())
        assertEquals(300, restored.readings.size)
        assertEquals(5, restored.demoEnvironments.size)
        assertEquals(60, restored.readings.count { it.deviceId == "demo_sala01" })
    }

    @Test fun invalidStateDoesNotPartiallyReplaceLiveData() {
        val store = DemoStore()
        val before = store.savedJson()
        val invalid = JSONObject(before).put("temperatureLimit", 900).toString()
        assertThrows(IllegalArgumentException::class.java) { store.restoreJson(invalid) }
        assertEquals(before, store.savedJson())
        assertThrows(IllegalArgumentException::class.java) {
            store.restoreJson(JSONObject(before).put("version", 999).toString())
        }
        assertEquals(before, store.savedJson())
    }

    @Test fun persistedEventsAreBoundedAndRemainDemoEvents() {
        val store = DemoStore().apply { liveMode = false }
        repeat(510) { store.simulateAlert(Metric.TEMPERATURE) }
        val restored = DemoStore().apply { restoreJson(store.savedJson()) }
        assertEquals(500, restored.events.size)
        assertTrue(restored.events.all { it.simulated })
    }
    @Test fun fiveDemoRoomsKeepIndependentReadingsAndAlerts() {
        val store = DemoStore().apply { liveMode = false }
        assertEquals(5, store.demoEnvironments.size)
        store.selectEnvironment("demo_servidores")
        val events = store.setDemoReading("demo_servidores", 30f, 40f, 220f)
        assertEquals(1, events.size)
        assertTrue(events.single().simulated)
        assertEquals("demo_servidores", store.latest.deviceId)
        store.selectEnvironment("demo_sala01")
        assertNotEquals(30f, store.latest.temperature)
        assertThrows(IllegalArgumentException::class.java) { store.saveDemoEnvironment(EnvironmentProfile("new", "New")) }
        val restored = DemoStore().apply { restoreJson(store.savedJson()); liveMode = false }
        assertEquals("demo_sala01", restored.selectedDemoId)
        restored.selectEnvironment("demo_servidores")
        assertEquals(30f, restored.latest.temperature!!, 0.01f)
    }
}
