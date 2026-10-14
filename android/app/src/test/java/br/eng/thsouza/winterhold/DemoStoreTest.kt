package br.eng.thsouza.winterhold

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DemoStoreTest {
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
