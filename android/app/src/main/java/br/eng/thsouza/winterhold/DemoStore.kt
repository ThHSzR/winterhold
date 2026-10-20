package br.eng.thsouza.winterhold

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
    val temperature: Float?,
    val humidity: Float?,
    val voltage: Float?,
    val deviceId: String? = null
) {
    fun value(metric: Metric): Float? = when (metric) {
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
    val simulated: Boolean = true,
    val deviceId: String? = null
)

data class DemoContact(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val role: String,
    val metrics: Set<Metric>,
    val channels: Set<Channel>,
    val active: Boolean = true,
    val phone: String = "",
    val email: String = ""
)

/** Stable ESP id plus the user-facing environment and its alert interval. */
data class EnvironmentProfile(
    val deviceId: String,
    val name: String,
    val icon: String = "snow",
    val temperatureMin: Float = 18f,
    val temperatureMax: Float = 26f,
    val humidityMin: Float = 30f,
    val humidityMax: Float = 70f,
    val voltageMin: Float = 198f,
    val voltageMax: Float = 242f,
    val favorite: Boolean = false
) {
    companion object {
        val icons = listOf("snow", "classroom", "lab", "office", "server")
        val idPattern = Regex("[A-Za-z0-9_-]{1,40}")
    }

    fun validated(): EnvironmentProfile {
        require(deviceId.matches(idPattern)) { "ID do ESP inválido" }
        require(name.trim() == name && name.length in 1..60) { "Nome do ambiente inválido" }
        require(icon in icons) { "Ícone inválido" }
        require(temperatureMin.isFinite() && temperatureMax.isFinite() && temperatureMin in -40f..85f &&
            temperatureMax in -40f..85f && temperatureMin < temperatureMax) { "Faixa de temperatura inválida" }
        require(humidityMin.isFinite() && humidityMax.isFinite() && humidityMin in 0f..100f &&
            humidityMax in 0f..100f && humidityMin < humidityMax) { "Faixa de umidade inválida" }
        require(voltageMin.isFinite() && voltageMax.isFinite() && voltageMin in 0f..300f &&
            voltageMax in 0f..300f && voltageMin < voltageMax) { "Faixa de tensão inválida" }
        return this
    }

    fun bounds(metric: Metric): Pair<Float, Float> = when (metric) {
        Metric.TEMPERATURE -> temperatureMin to temperatureMax
        Metric.HUMIDITY -> humidityMin to humidityMax
        Metric.VOLTAGE -> voltageMin to voltageMax
    }
}

/** Shared central state; demo and ESP readings are kept separate. Mutate on the main thread. */
class DemoStore {
    private var tick = 0
    val readings = mutableStateListOf<Reading>()
    val liveReadings = mutableStateListOf<Reading>()
    val lastSeen = mutableStateMapOf<String, Long>()
    val environments = mutableStateMapOf<String, EnvironmentProfile>()
    val demoEnvironments = mutableStateMapOf<String, EnvironmentProfile>()
    var selectedDemoId by mutableStateOf("demo_sala01")
    companion object {
        val demoPresets = listOf(
            EnvironmentProfile("demo_sala01", "Sala de aula 01", "classroom", 19f, 26f, 35f, 65f, 198f, 242f, true),
            EnvironmentProfile("demo_lab02", "Laboratório 02", "lab", 20f, 24f, 40f, 60f, 205f, 235f, true),
            EnvironmentProfile("demo_servidores", "Sala de servidores", "server", 17f, 22f, 30f, 55f, 208f, 232f),
            EnvironmentProfile("demo_adm", "Administração", "office", 21f, 28f, 35f, 70f, 200f, 240f),
            EnvironmentProfile("demo_auditorio", "Auditório", "snow", 18f, 25f, 30f, 68f, 198f, 242f)
        )
    }
    val events = mutableStateListOf<AlertEvent>()
    val contacts = mutableStateListOf<DemoContact>()

    var temperatureLimit by mutableFloatStateOf(26f)
    var humidityLimit by mutableFloatStateOf(70f)
    var nominalVoltage by mutableFloatStateOf(220f)
    var voltageTolerance by mutableFloatStateOf(10f)
    var simulationRunning by mutableStateOf(false)
    var offlineDemo by mutableStateOf(false)
    var liveMode by mutableStateOf(true)
    var selectedDeviceId by mutableStateOf<String?>(null)
    var now by mutableStateOf(System.currentTimeMillis())
        private set
    private val activeViolations = mutableSetOf<String>()
    private val disconnected = mutableSetOf<String>()
    val enabledChannels = mutableStateListOf(Channel.SMS, Channel.EMAIL, Channel.WHATSAPP)

    init {
        val now = System.currentTimeMillis()
        demoPresets.forEach { profile ->
            demoEnvironments[profile.deviceId] = profile
            for (i in 35 downTo 0) {
                val step = 35 - i
                readings.add(Reading(now - i * 5 * 60_000L,
                    22f + demoPresets.indexOf(profile) * .5f + .9f * sin(step / 5.0).toFloat(),
                    52f + demoPresets.indexOf(profile) * 2f + 4f * sin(step / 7.0).toFloat(),
                    220f + 2.5f * sin(step / 3.0).toFloat(), profile.deviceId))
            }
        }
        readings.sortWith(compareByDescending { it.time })
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

    val visibleReadings: List<Reading> get() = if (liveMode) {
        liveReadings.filter { it.deviceId == selectedDeviceId }
    } else readings.filter { it.deviceId == selectedDemoId }
    val latest: Reading get() = visibleReadings.firstOrNull() ?: Reading(0, null, null, null)
    val deviceOnline: Boolean get() = selectedDeviceId?.let { id ->
        lastSeen[id]?.let { now - it in 0..15_000L }
    } ?: false
    val selectedEnvironment: EnvironmentProfile? get() = if (liveMode) selectedDeviceId?.let(environments::get)
        else demoEnvironments[selectedDemoId]
    val currentDeviceId: String? get() = if (liveMode) selectedDeviceId else selectedDemoId
    val currentEnvironments: Map<String, EnvironmentProfile> get() = if (liveMode) environments else demoEnvironments
    fun selectEnvironment(id: String) {
        require(id in currentEnvironments) { "Ambiente desconhecido" }
        if (liveMode) selectedDeviceId = id else selectedDemoId = id
    }
    fun saveDemoEnvironment(profile: EnvironmentProfile) {
        profile.validated()
        require(profile.deviceId in demoEnvironments) { "Ambiente demo desconhecido" }
        demoEnvironments[profile.deviceId] = profile
    }
    fun setDemoReading(id: String, temperature: Float, humidity: Float, voltage: Float): List<AlertEvent> {
        require(!liveMode && id in demoEnvironments) { "Ative a demonstração e selecione um ambiente" }
        require(temperature.isFinite() && temperature in -40f..85f && humidity.isFinite() && humidity in 0f..100f &&
            voltage.isFinite() && voltage in 0f..300f) { "Leitura fora da faixa física" }
        val reading = Reading(System.currentTimeMillis(), temperature, humidity, voltage, id)
        readings.add(0, reading)
        readings.indices.filter { readings[it].deviceId == id }.drop(60).asReversed().forEach { readings.removeAt(it) }
        now = reading.time
        val before = events.map { it.id }.toSet()
        Metric.entries.forEach { metric ->
            val value = reading.value(metric)
            val (low, high) = demoEnvironments.getValue(id).bounds(metric)
            checkLimit(id, metric, value, low, high, true)
        }
        return events.filter { it.id !in before }
    }

    fun saveEnvironment(profile: EnvironmentProfile) {
        profile.validated()
        require(profile.deviceId in environments || environments.size < 32) { "Limite de ambientes atingido" }
        environments[profile.deviceId] = profile
        if (selectedDeviceId == null) selectedDeviceId = profile.deviceId
    }

    private fun defaultEnvironment(id: String) = EnvironmentProfile(id, id,
        temperatureMax = temperatureLimit, humidityMax = humidityLimit,
        voltageMin = nominalVoltage * (1 - voltageTolerance / 100),
        voltageMax = nominalVoltage * (1 + voltageTolerance / 100))

    fun heartbeat(at: Long = System.currentTimeMillis()) {
        now = at
        lastSeen.forEach { (id, time) ->
            if (at - time > 15_000 && disconnected.add(id)) addEvent(id, null,
                "Dispositivo sem comunicação", "$id · última leitura há mais de 15 segundos", Severity.CRITICAL)
        }
    }

    fun ingest(reading: Reading) {
        val id = requireNotNull(reading.deviceId)
        if (id !in environments) saveEnvironment(defaultEnvironment(id))
        require(id in lastSeen || lastSeen.size < 32) { "Limite de dispositivos atingido" }
        if (selectedDeviceId == null) selectedDeviceId = id
        if (disconnected.remove(id)) addEvent(id, null, "Comunicação restabelecida", "$id · MQTT ativo", Severity.INFO, true)
        lastSeen[id] = reading.time
        now = reading.time
        liveReadings.add(0, reading)
        val positions = liveReadings.indices.filter { liveReadings[it].deviceId == id }
        if (positions.size > 60) liveReadings.removeAt(positions.last())
        val profile = environments.getValue(id)
        Metric.entries.forEach { metric ->
            val (low, high) = profile.bounds(metric)
            checkLimit(id, metric, reading.value(metric), low, high)
        }
    }

    private fun checkLimit(id: String, metric: Metric, value: Float?, low: Float, high: Float, simulated: Boolean = false) {
        if (value == null) return
        val key = "$simulated:$id:${metric.name}"
        val violation = when { value < low -> "min"; value > high -> "max"; else -> null }
        val previous = activeViolations.firstOrNull { it.startsWith("$key:") }
        if (previous != null && previous != "$key:$violation") {
            activeViolations.remove(previous)
            events.indexOfFirst { it.simulated == simulated && !it.resolved && it.metric == metric && it.deviceId == id }
                .takeIf { it >= 0 }?.let { events[it] = events[it].copy(resolved = true) }
            if (violation == null) addEvent(id, metric, "${metric.label} normalizada", "$id · $value ${metric.unit} · faixa $low–$high ${metric.unit}", Severity.INFO, true, simulated)
        }
        if (violation != null && activeViolations.add("$key:$violation"))
            addEvent(id, metric, "${metric.label} ${if (violation == "min") "abaixo do mínimo" else "acima do máximo"}",
                "$id · $value ${metric.unit} · faixa $low–$high ${metric.unit}",
                if (metric == Metric.VOLTAGE) Severity.CRITICAL else Severity.WARNING, simulated = simulated)
    }

    private fun addEvent(deviceId: String, metric: Metric?, title: String, detail: String, severity: Severity,
                         resolved: Boolean = false, simulated: Boolean = false) {
        events.add(0, AlertEvent(time = now, deviceId = deviceId, metric = metric, title = title, detail = detail,
            severity = severity, resolved = resolved, simulated = simulated))
        if (events.size > 500) events.removeAt(events.lastIndex)
    }

    fun advance() {
        if (!simulationRunning || offlineDemo || liveMode) return
        tick++
        demoEnvironments.values.forEach { profile ->
            val previous = readings.firstOrNull { it.deviceId == profile.deviceId }
            readings.add(0, Reading(System.currentTimeMillis(),
                (previous?.temperature ?: 22f) + .08f * sin(tick / 5.0).toFloat(),
                (previous?.humidity ?: 55f) + .15f * sin(tick / 7.0).toFloat(),
                (previous?.voltage ?: 220f) + .1f * sin(tick / 3.0).toFloat(), profile.deviceId))
            readings.indices.filter { readings[it].deviceId == profile.deviceId }.drop(60).asReversed().forEach { readings.removeAt(it) }
            val latest = readings.first { it.deviceId == profile.deviceId }
            now = latest.time
            Metric.entries.forEach { metric ->
                val (low, high) = profile.bounds(metric)
                checkLimit(profile.deviceId, metric, latest.value(metric), low, high, true)
            }
        }
    }

    fun simulateAlert(metric: Metric): AlertEvent {
        require(!liveMode) { "Ative o modo demonstração" }
        val id = selectedDemoId
        val profile = demoEnvironments.getValue(id)
        val (low, high) = profile.bounds(metric)
        return AlertEvent(time = System.currentTimeMillis(), metric = metric,
            title = "Teste de ${metric.label.lowercase()}",
            detail = "${profile.name} · $id · limite $low–$high ${metric.unit}",
            severity = if (metric == Metric.VOLTAGE) Severity.CRITICAL else Severity.WARNING,
            simulated = true, deviceId = id).also { events.add(0, it) }
    }

    fun simulateDisconnection() {
        offlineDemo = !offlineDemo
        events.add(0, AlertEvent(
            time = System.currentTimeMillis(), metric = null,
            title = if (offlineDemo) "Dispositivo sem comunicação" else "Comunicação restabelecida",
            detail = "$selectedDemoId · ESP32 simulado · evento de demonstração",
            deviceId = selectedDemoId,
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
        require(contact.phone.isEmpty() || contact.phone.matches(Regex("\\+[1-9][0-9]{7,14}"))) { "Telefone deve estar em formato internacional: +5511999999999" }
        require(contact.email.isEmpty() || contact.email.matches(Regex("[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9.-]{1,190}\\.[A-Za-z]{2,20}"))) { "E-mail inválido" }
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
        .put("version", 5).put("tick", tick)
        .put("readings", JSONArray(readings.map { JSONObject()
            .put("time", it.time).put("temperature", it.temperature)
            .put("humidity", it.humidity).put("voltage", it.voltage).put("deviceId", it.deviceId) }))
        .put("liveReadings", JSONArray(liveReadings.map { JSONObject()
            .put("time", it.time).put("temperature", it.temperature).put("humidity", it.humidity)
            .put("voltage", it.voltage).put("deviceId", it.deviceId) }))
        .put("lastSeen", JSONObject(lastSeen.toMap()))
        .put("environments", JSONArray(environments.values.sortedBy { it.deviceId }.map { profile -> JSONObject()
            .put("deviceId", profile.deviceId).put("name", profile.name).put("icon", profile.icon)
            .put("temperatureMin", profile.temperatureMin).put("temperatureMax", profile.temperatureMax)
            .put("humidityMin", profile.humidityMin).put("humidityMax", profile.humidityMax)
            .put("voltageMin", profile.voltageMin).put("voltageMax", profile.voltageMax)
            .put("favorite", profile.favorite) }))
        .put("demoEnvironments", JSONArray(demoEnvironments.values.sortedBy { it.deviceId }.map { profile -> JSONObject()
            .put("deviceId", profile.deviceId).put("name", profile.name).put("icon", profile.icon)
            .put("temperatureMin", profile.temperatureMin).put("temperatureMax", profile.temperatureMax)
            .put("humidityMin", profile.humidityMin).put("humidityMax", profile.humidityMax)
            .put("voltageMin", profile.voltageMin).put("voltageMax", profile.voltageMax)
            .put("favorite", profile.favorite) }))
        .put("liveMode", liveMode).put("selectedDeviceId", selectedDeviceId).put("selectedDemoId", selectedDemoId)
        .put("events", JSONArray(events.take(500).map { JSONObject()
            .put("id", it.id).put("time", it.time).put("metric", it.metric?.name ?: JSONObject.NULL)
            .put("title", it.title).put("detail", it.detail).put("severity", it.severity.name)
            .put("resolved", it.resolved).put("simulated", it.simulated)
            .put("deviceId", it.deviceId ?: JSONObject.NULL) }))
        .put("contacts", JSONArray(contacts.map { JSONObject()
            .put("id", it.id).put("name", it.name).put("role", it.role)
            .put("metrics", JSONArray(it.metrics.map { metric -> metric.name }))
            .put("channels", JSONArray(it.channels.map { channel -> channel.name }))
            .put("active", it.active).put("phone", it.phone).put("email", it.email) }))
        .put("temperatureLimit", temperatureLimit.toDouble()).put("humidityLimit", humidityLimit.toDouble())
        .put("nominalVoltage", nominalVoltage.toDouble()).put("voltageTolerance", voltageTolerance.toDouble())
        .put("simulationRunning", simulationRunning).put("offlineDemo", offlineDemo)
        .put("enabledChannels", JSONArray(enabledChannels.map { it.name }))
        .toString()

    /** Decode and validate everything before replacing the live state. */
    internal fun restoreJson(json: String) {
        val root = JSONObject(json)
        val version = root.getInt("version")
        require(version in 1..5) { "Versão de estado desconhecida" }
        val savedTick = root.getInt("tick").also { require(it >= 0) }
        fun optionalFloat(json: JSONObject, key: String): Float? = if (json.isNull(key)) null else
            json.getDouble(key).toFloat().also { require(it.isFinite()) }
        val savedReadings = root.getJSONArray("readings").objects().map {
            Reading(it.getLong("time"), optionalFloat(it, "temperature"),
                optionalFloat(it, "humidity"), optionalFloat(it, "voltage"),
                it.optString("deviceId").takeIf { id -> id in demoPresets.map { preset -> preset.deviceId } } ?: "demo_sala01")
        }.also { require(it.isNotEmpty() && it.size <= 300) }
        val savedLive = if (version >= 2) root.getJSONArray("liveReadings").objects().map {
            val id = it.getString("deviceId").also { id -> require(id.matches(Regex("[A-Za-z0-9_-]{1,40}"))) }
            Reading(it.getLong("time"), optionalFloat(it, "temperature"), optionalFloat(it, "humidity"),
                optionalFloat(it, "voltage"), id)
        }.also { require(it.size <= 1_920) } else emptyList()
        val savedSeen = if (version >= 2) root.getJSONObject("lastSeen").let { seen ->
            seen.keys().asSequence().associateWith { id ->
                require(id.matches(Regex("[A-Za-z0-9_-]{1,40}")))
                seen.getLong(id).also { require(it >= 0) }
            }.also { require(it.size <= 32) }
        } else emptyMap()
        val selected = if (version >= 2 && !root.isNull("selectedDeviceId")) root.getString("selectedDeviceId") else null
        val savedEvents = root.getJSONArray("events").objects().map {
            AlertEvent(id = it.getString("id"), time = it.getLong("time"),
                metric = if (it.isNull("metric")) null else Metric.valueOf(it.getString("metric")),
                title = it.getString("title"), detail = it.getString("detail"),
            severity = Severity.valueOf(it.getString("severity")), resolved = it.getBoolean("resolved"),
            simulated = it.optBoolean("simulated", true),
            deviceId = if (version >= 3 && !it.isNull("deviceId")) it.getString("deviceId") else
                if (it.optBoolean("simulated", true)) null else it.getString("detail").substringBefore(" ·"))
        }.also { require(it.size <= 500) }
        val savedContacts = root.getJSONArray("contacts").objects().map {
            DemoContact(id = it.getString("id"), name = it.getString("name"), role = it.getString("role"),
                metrics = it.getJSONArray("metrics").strings().map(Metric::valueOf).toSet(),
                channels = it.getJSONArray("channels").strings().map(Channel::valueOf).toSet(),
                active = it.getBoolean("active"), phone = it.optString("phone"), email = it.optString("email"))
        }
        val temperature = root.getDouble("temperatureLimit").toFloat().also { require(it in 20f..35f) }
        val humidity = root.getDouble("humidityLimit").toFloat().also { require(it in 40f..90f) }
        val nominal = root.getDouble("nominalVoltage").toFloat().also { require(it in 100f..250f) }
        val tolerance = root.getDouble("voltageTolerance").toFloat().also { require(it in 2f..20f) }
        val savedProfiles = if (version >= 3) root.getJSONArray("environments").objects().map { item ->
            EnvironmentProfile(item.getString("deviceId"), item.getString("name"), item.getString("icon"),
                item.getDouble("temperatureMin").toFloat(), item.getDouble("temperatureMax").toFloat(),
                item.getDouble("humidityMin").toFloat(), item.getDouble("humidityMax").toFloat(),
                item.getDouble("voltageMin").toFloat(), item.getDouble("voltageMax").toFloat(),
                item.optBoolean("favorite", false)).validated()
        } else savedSeen.keys.map { id -> EnvironmentProfile(id, id, temperatureMax = temperature,
            humidityMax = humidity, voltageMin = nominal * (1 - tolerance / 100),
            voltageMax = nominal * (1 + tolerance / 100)).validated() }
        val savedDemoProfiles = if (version >= 5) root.getJSONArray("demoEnvironments").objects().map { item ->
            EnvironmentProfile(item.getString("deviceId"), item.getString("name"), item.getString("icon"),
                item.getDouble("temperatureMin").toFloat(), item.getDouble("temperatureMax").toFloat(),
                item.getDouble("humidityMin").toFloat(), item.getDouble("humidityMax").toFloat(),
                item.getDouble("voltageMin").toFloat(), item.getDouble("voltageMax").toFloat(),
                item.optBoolean("favorite", false)).validated()
        }.also { require(it.map { profile -> profile.deviceId }.toSet() == demoPresets.map { it.deviceId }.toSet()) }
            else demoPresets
        val savedDemoId = root.optString("selectedDemoId", "demo_sala01").takeIf { it in demoPresets.map { preset -> preset.deviceId } }
            ?: "demo_sala01"
        require(savedProfiles.size <= 32 && savedProfiles.map { it.deviceId }.distinct().size == savedProfiles.size)
        require(savedSeen.keys.all { id -> savedProfiles.any { it.deviceId == id } })
        require(selected == null || savedProfiles.any { it.deviceId == selected })
        val channels = root.getJSONArray("enabledChannels").strings().map(Channel::valueOf)
        readings.clear(); readings.addAll(savedReadings)
        demoEnvironments.clear(); savedDemoProfiles.forEach { demoEnvironments[it.deviceId] = it }
        selectedDemoId = savedDemoId
        liveReadings.clear(); liveReadings.addAll(savedLive)
        lastSeen.clear(); lastSeen.putAll(savedSeen)
        environments.clear(); savedProfiles.forEach { environments[it.deviceId] = it }
        liveMode = true
        selectedDeviceId = selected
        activeViolations.clear(); disconnected.clear()
        savedEvents.filter { !it.simulated && !it.resolved && it.metric != null }.forEach { event ->
            event.detail.substringBefore(" ·").takeIf { it in savedSeen }?.let { id ->
                val bound = environments[id]?.bounds(event.metric!!)
                val reading = savedLive.firstOrNull { it.deviceId == id }?.value(event.metric!!)
                val side = if (reading != null && bound != null && reading < bound.first) "min" else "max"
                activeViolations.add("false:$id:${event.metric!!.name}:$side")
            }
        }
        events.clear(); events.addAll(savedEvents)
        contacts.clear(); contacts.addAll(savedContacts)
        enabledChannels.clear(); enabledChannels.addAll(channels.distinct())
        tick = savedTick
        temperatureLimit = temperature; humidityLimit = humidity
        nominalVoltage = nominal; voltageTolerance = tolerance
        simulationRunning = false; offlineDemo = false
    }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
}
