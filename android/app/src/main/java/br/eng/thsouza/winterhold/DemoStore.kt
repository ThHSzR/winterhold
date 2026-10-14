package br.eng.thsouza.winterhold

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.sin

enum class Metric(val label: String, val unit: String) {
    TEMPERATURE("Temperatura", "°C"),
    HUMIDITY("Umidade", "%"),
    VOLTAGE("Tensão", "V")
}

enum class Channel(val label: String) {
    SMS("SMS"), EMAIL("E-mail"), WHATSAPP("WhatsApp")
}

enum class Severity { CRITICAL, WARNING, INFO }

data class Reading(
    val time: Long,
    val temperature: Float,
    val humidity: Float,
    val voltage: Float
) {
    fun value(metric: Metric): Float = when (metric) {
        Metric.TEMPERATURE -> temperature
        Metric.HUMIDITY -> humidity
        Metric.VOLTAGE -> voltage
    }
}

data class AlertEvent(
    val id: String = UUID.randomUUID().toString(),
    val time: Long,
    val metric: Metric?,
    val title: String,
    val detail: String,
    val severity: Severity,
    val resolved: Boolean = false,
    val simulated: Boolean = true
)

data class DemoContact(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val role: String,
    val metrics: Set<Metric>,
    val channels: Set<Channel>,
    val active: Boolean = true
)

/** Estado demo compartilhado. A central persiste uma cópia local; sem sensores ou mensagens reais. */
class DemoStore {
    private var tick = 0
    val readings = mutableStateListOf<Reading>()
    val events = mutableStateListOf<AlertEvent>()
    val contacts = mutableStateListOf<DemoContact>()

    var temperatureLimit by mutableFloatStateOf(26f)
    var humidityLimit by mutableFloatStateOf(70f)
    var nominalVoltage by mutableFloatStateOf(220f)
    var voltageTolerance by mutableFloatStateOf(10f)
    var simulationRunning by mutableStateOf(true)
    var offlineDemo by mutableStateOf(false)
    val enabledChannels = mutableStateListOf(Channel.SMS, Channel.EMAIL, Channel.WHATSAPP)

    init {
        val now = System.currentTimeMillis()
        for (i in 35 downTo 0) {
            val step = 35 - i
            readings.add(0, Reading(
                time = now - i * 5 * 60_000L,
                temperature = 23.4f + 0.9f * sin(step / 5.0).toFloat(),
                humidity = 58f + 4f * sin(step / 7.0).toFloat(),
                voltage = 220f + 2.5f * sin(step / 3.0).toFloat()
            ))
        }
        tick = 36
        events.add(AlertEvent(
            time = now - 32 * 60_000L,
            metric = Metric.TEMPERATURE,
            title = "Temperatura acima do limite",
            detail = "Sala 01 · 27,1 °C por 5 min · limite 26 °C",
            severity = Severity.WARNING,
            resolved = true
        ))
        events.add(AlertEvent(
            time = now - 95 * 60_000L,
            metric = Metric.VOLTAGE,
            title = "Oscilação de tensão",
            detail = "Sala 01 · 194 V · faixa configurada 198–242 V",
            severity = Severity.CRITICAL,
            resolved = true
        ))
        events.add(AlertEvent(
            time = now - 4 * 60 * 60_000L,
            metric = null,
            title = "Monitoramento iniciado",
            detail = "Central de demonstração · dispositivo ESP32 simulado",
            severity = Severity.INFO,
            resolved = true
        ))
        contacts.add(DemoContact(
            name = "Equipe de manutenção", role = "Manutenção",
            metrics = Metric.entries.toSet(),
            channels = setOf(Channel.SMS, Channel.WHATSAPP)
        ))
        contacts.add(DemoContact(
            name = "Técnico elétrico", role = "Elétrica",
            metrics = setOf(Metric.VOLTAGE),
            channels = setOf(Channel.SMS)
        ))
        contacts.add(DemoContact(
            name = "Gestão de instalações", role = "Gestão",
            metrics = setOf(Metric.TEMPERATURE, Metric.HUMIDITY),
            channels = setOf(Channel.EMAIL, Channel.WHATSAPP)
        ))
    }

    val latest: Reading get() = readings.first()

    fun advance() {
        if (!simulationRunning || offlineDemo) return
        tick++
        readings.add(0, Reading(
            time = System.currentTimeMillis(),
            temperature = 23.4f + 0.9f * sin(tick / 5.0).toFloat(),
            humidity = 58f + 4f * sin(tick / 7.0).toFloat(),
            voltage = 220f + 2.5f * sin(tick / 3.0).toFloat()
        ))
        if (readings.size > 60) readings.removeAt(readings.lastIndex)
    }

    fun simulateAlert(metric: Metric) {
        val (title, detail, severity) = when (metric) {
            Metric.TEMPERATURE -> Triple("Temperatura acima do limite", "Sala 01 · 28,2 °C · limite ${temperatureLimit.toInt()} °C", Severity.WARNING)
            Metric.HUMIDITY -> Triple("Umidade acima do limite", "Sala 01 · 76% · limite ${humidityLimit.toInt()}%", Severity.WARNING)
            Metric.VOLTAGE -> Triple("Oscilação de tensão", "Sala 01 · 190 V · nominal ${nominalVoltage.toInt()} V", Severity.CRITICAL)
        }
        events.add(0, AlertEvent(time = System.currentTimeMillis(), metric = metric,
            title = title, detail = detail, severity = severity))
    }

    fun simulateDisconnection() {
        offlineDemo = !offlineDemo
        events.add(0, AlertEvent(
            time = System.currentTimeMillis(), metric = null,
            title = if (offlineDemo) "Dispositivo sem comunicação" else "Comunicação restabelecida",
            detail = "Sala 01 · ESP32 simulado · evento de demonstração",
            severity = if (offlineDemo) Severity.CRITICAL else Severity.INFO,
            resolved = !offlineDemo
        ))
    }

    fun resolveAlert(id: String) {
        val index = events.indexOfFirst { it.id == id }
        if (index >= 0) events[index] = events[index].copy(resolved = true)
    }

    fun addContact(name: String, role: String) {
        if (name.isBlank()) return
        contacts.add(DemoContact(name = name.trim(), role = role.trim().ifBlank { "Equipe" },
            metrics = Metric.entries.toSet(), channels = setOf(Channel.SMS)))
    }

    fun updateContact(contact: DemoContact) {
        val index = contacts.indexOfFirst { it.id == contact.id }
        if (index >= 0) contacts[index] = contact
    }

    fun routesFor(event: AlertEvent): List<Pair<DemoContact, Set<Channel>>> = contacts
        .filter { it.active && (event.metric == null || event.metric in it.metrics) }
        .mapNotNull { contact ->
            val channels = contact.channels.intersect(enabledChannels.toSet())
            if (channels.isEmpty()) null else contact to channels
        }

    internal fun savedJson(): String = JSONObject()
        .put("version", 1).put("tick", tick)
        .put("readings", JSONArray(readings.map { JSONObject()
            .put("time", it.time).put("temperature", it.temperature.toDouble())
            .put("humidity", it.humidity.toDouble()).put("voltage", it.voltage.toDouble()) }))
        .put("events", JSONArray(events.take(500).map { JSONObject()
            .put("id", it.id).put("time", it.time).put("metric", it.metric?.name ?: JSONObject.NULL)
            .put("title", it.title).put("detail", it.detail).put("severity", it.severity.name)
            .put("resolved", it.resolved) }))
        .put("contacts", JSONArray(contacts.map { JSONObject()
            .put("id", it.id).put("name", it.name).put("role", it.role)
            .put("metrics", JSONArray(it.metrics.map { metric -> metric.name }))
            .put("channels", JSONArray(it.channels.map { channel -> channel.name }))
            .put("active", it.active) }))
        .put("temperatureLimit", temperatureLimit.toDouble()).put("humidityLimit", humidityLimit.toDouble())
        .put("nominalVoltage", nominalVoltage.toDouble()).put("voltageTolerance", voltageTolerance.toDouble())
        .put("simulationRunning", simulationRunning).put("offlineDemo", offlineDemo)
        .put("enabledChannels", JSONArray(enabledChannels.map { it.name }))
        .toString()

    /** Decode and validate everything before replacing the live state. */
    internal fun restoreJson(json: String) {
        val root = JSONObject(json)
        require(root.getInt("version") == 1) { "Versão de estado desconhecida" }
        val savedTick = root.getInt("tick").also { require(it >= 0) }
        val savedReadings = root.getJSONArray("readings").objects().map {
            Reading(it.getLong("time"), it.getDouble("temperature").toFloat(),
                it.getDouble("humidity").toFloat(), it.getDouble("voltage").toFloat())
        }.also { require(it.isNotEmpty() && it.size <= 60) }
        val savedEvents = root.getJSONArray("events").objects().map {
            AlertEvent(id = it.getString("id"), time = it.getLong("time"),
                metric = if (it.isNull("metric")) null else Metric.valueOf(it.getString("metric")),
                title = it.getString("title"), detail = it.getString("detail"),
                severity = Severity.valueOf(it.getString("severity")), resolved = it.getBoolean("resolved"))
        }.also { require(it.size <= 500) }
        val savedContacts = root.getJSONArray("contacts").objects().map {
            DemoContact(id = it.getString("id"), name = it.getString("name"), role = it.getString("role"),
                metrics = it.getJSONArray("metrics").strings().map(Metric::valueOf).toSet(),
                channels = it.getJSONArray("channels").strings().map(Channel::valueOf).toSet(),
                active = it.getBoolean("active"))
        }
        val temperature = root.getDouble("temperatureLimit").toFloat().also { require(it in 20f..35f) }
        val humidity = root.getDouble("humidityLimit").toFloat().also { require(it in 40f..90f) }
        val nominal = root.getDouble("nominalVoltage").toFloat().also { require(it in 100f..250f) }
        val tolerance = root.getDouble("voltageTolerance").toFloat().also { require(it in 2f..20f) }
        val running = root.getBoolean("simulationRunning")
        val offline = root.getBoolean("offlineDemo")
        val channels = root.getJSONArray("enabledChannels").strings().map(Channel::valueOf)
        readings.clear(); readings.addAll(savedReadings)
        events.clear(); events.addAll(savedEvents)
        contacts.clear(); contacts.addAll(savedContacts)
        enabledChannels.clear(); enabledChannels.addAll(channels.distinct())
        tick = savedTick
        temperatureLimit = temperature; humidityLimit = humidity
        nominalVoltage = nominal; voltageTolerance = tolerance
        simulationRunning = running; offlineDemo = offline
    }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
}
