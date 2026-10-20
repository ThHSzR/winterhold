package br.eng.thsouza.winterhold

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DemoStoreTest {
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
        assertEquals(original.savedJson(), restored.savedJson())
        restored.advance()
        assertEquals(original.readings.toList(), restored.readings.toList())
    }

    @Test fun simulationContinuesFromSavedPhaseAndKeepsBoundedHistory() {
        val original = DemoStore()
        repeat(80) { original.advance() }
        val restored = DemoStore().apply { restoreJson(original.savedJson()) }
        original.advance()
        restored.advance()
        assertEquals(original.latest.temperature, restored.latest.temperature)
        assertEquals(original.latest.voltage, restored.latest.voltage)
        assertEquals(60, restored.readings.size)
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
        val store = DemoStore()
        repeat(510) { store.simulateAlert(Metric.TEMPERATURE) }
        val restored = DemoStore().apply { restoreJson(store.savedJson()) }
        assertEquals(500, restored.events.size)
        assertTrue(restored.events.all { it.simulated })
    }
}
