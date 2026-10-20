package br.eng.thsouza.winterhold

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.ObserverHandle
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Collections
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

// O estado compartilhado alimenta Compose e a interface web embutida.
object DemoCentral {
    const val PORT = 8790
    val store = DemoStore()
    private val main = Handler(Looper.getMainLooper())
    private var server: DemoHttpServer? = null
    private var mqtt: MqttIngress? = null
    var mqttStatus by mutableStateOf("MQTT aguardando inicialização")
        private set
    var mqttSecret by mutableStateOf("")
        private set
    val webSecret: String get() = webUsers?.users?.firstOrNull()?.key.orEmpty()
    internal var webUsers: WebAccessUsers? = null
        private set
    private var persistence: DemoPersistence? = null
    private var delivery: AlertDelivery? = null
    fun deliveryStatus(eventId: String, contactId: String, channel: Channel): String? =
        delivery?.status(eventId, contactId, channel)
    private var observer: ObserverHandle? = null
    private var lastSaved: String? = null
    var persistenceStatus by mutableStateOf("Carregando dados locais")
        private set
    var active by mutableStateOf(false)
        private set
    var status by mutableStateOf("Aguardando inicialização")
        private set
    var urls by mutableStateOf(emptyList<String>())
        private set

    private val saveChanges = Runnable {
        Snapshot.sendApplyNotifications()
        save()
    }
    private val advance = object : Runnable {
        override fun run() {
            store.advance()
            store.heartbeat()
            delivery?.dispatch(store, store.events)
            refreshUrls()
            save()
            main.postDelayed(this, 5_000)
        }
    }

    fun prepare(context: Context) {
        if (persistence != null) return
        persistence = DemoPersistence(context.applicationContext).also {
            persistenceStatus = it.restore(store)
        }
        delivery = AlertDelivery(context.applicationContext).also { it.prime(store) }
        val preferences = context.applicationContext.getSharedPreferences("mqtt", Context.MODE_PRIVATE)
        mqttSecret = preferences.getString("secret", null) ?: ByteArray(18).also {
            SecureRandom().nextBytes(it)
        }.joinToString("") { "%02x".format(it) }.also {
            preferences.edit().putString("secret", it).commit()
        }
        webUsers = WebAccessUsers(context.applicationContext)
        // Persistence belongs to the app state, even when the user stops the web service.
        observer = Snapshot.registerGlobalWriteObserver {
            main.removeCallbacks(saveChanges)
            main.post(saveChanges)
        }
        save()
    }

    fun save() {
        val json = store.savedJson()
        if (json == lastSaved) return
        lastSaved = json
        persistence?.save(json) {
            main.post {
                lastSaved = null
                persistenceStatus = "Não foi possível salvar os dados locais"
            }
        }
    }
    fun sendTest(event: AlertEvent) { delivery?.dispatchTest(store, event) }

    fun ensureStarted(context: Context) {
        prepare(context)
        if (server != null) {
            if (mqtt == null) startMqtt()
            return
        }
        try {
            server = DemoHttpServer(context.applicationContext, store).also { it.start() }
            startMqtt()
            active = true
            status = "Central ativa · web na porta $PORT"
            refreshUrls()
            save()
            main.postDelayed(advance, 5_000)
        } catch (error: Exception) {
            status = "Não foi possível iniciar a web: ${error.message}"
        }
    }

    private fun startMqtt() {
        try {
            mqtt = MqttIngress(mqttSecret, { reading ->
                main.post { runCatching { store.ingest(reading); delivery?.dispatch(store, store.events); save() } }
            }).also { it.start() }
            mqttStatus = "Recebendo MQTT na porta 1883"
        } catch (error: Exception) {
            mqttStatus = "MQTT indisponível: ${error.message}"
        }
    }

    fun stop() {
        main.removeCallbacks(advance)
        save()
        server?.stop()
        server = null
        mqtt?.stop()
        mqtt = null
        mqttStatus = "MQTT parado"
        active = false
        urls = emptyList()
        status = "Central parada; retome para acessar pela web"
    }

    fun reportStartFailure(error: Exception) {
        status = "Não foi possível iniciar a central: ${error.message}"
    }

    fun refreshUrls() {
        if (server == null) { urls = emptyList(); return }
        val addresses = try {
            Collections.list(NetworkInterface.getNetworkInterfaces()).flatMap { network ->
                Collections.list(network.inetAddresses).filterIsInstance<Inet4Address>()
                    .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                    .map { it.hostAddress.orEmpty() }
            }.distinct().sortedBy { !InetAddress.getByName(it).isSiteLocalAddress }
        } catch (_: Exception) { emptyList() }
        urls = addresses.map { "http://$it:$PORT/" }
        if (urls.isEmpty()) urls = listOf("http://127.0.0.1:$PORT/")
    }
}

internal class DemoHttpServer(
    private val context: Context,
    private val store: DemoStore
) {
    private val main = Handler(Looper.getMainLooper())
    private val workers = Executors.newCachedThreadPool()
    private lateinit var listener: ServerSocket
    private val assets = mapOf(
        "/" to ("index.html" to "text/html; charset=utf-8"),
        "/index.html" to ("index.html" to "text/html; charset=utf-8"),
        "/app.js" to ("app.js" to "text/javascript; charset=utf-8"),
        "/demo.js" to ("demo.js" to "text/javascript; charset=utf-8"),
        "/styles.css" to ("styles.css" to "text/css; charset=utf-8"),
        "/favicon.svg" to ("favicon.svg" to "image/svg+xml")
    )

    fun start() {
        listener = ServerSocket().apply {
            reuseAddress = true
            bind(java.net.InetSocketAddress(InetAddress.getByName("0.0.0.0"), DemoCentral.PORT), 50)
        }
        Thread({
            while (!listener.isClosed) {
                try {
                    val socket = listener.accept()
                    workers.execute { socket.use { serve(it) } }
                } catch (_: Exception) {
                    if (listener.isClosed) return@Thread
                }
            }
        }, "winterhold-web").apply { isDaemon = true; start() }
    }

    fun stop() {
        listener.close()
        workers.shutdownNow()
    }

    private fun serve(socket: Socket) {
        try {
            socket.soTimeout = 5_000
            val input = BufferedInputStream(socket.getInputStream())
            val first = readLine(input) ?: return
            val parts = first.split(' ')
            if (parts.size != 3) return respond(socket, 400, "text/plain", ByteArray(0))
            val method = parts[0]
            val route = parts[1].substringBefore('?')
            val headers = mutableMapOf<String, String>()
            for (i in 0 until 32) {
                val line = readLine(input) ?: return
                if (line.isEmpty()) break
                val delimiter = line.indexOf(':')
                if (delimiter > 0) headers[line.substring(0, delimiter).lowercase(Locale.ROOT)] =
                    line.substring(delimiter + 1).trim()
            }
            if (route == "/healthz" && method == "GET") {
                return respond(socket, 200, "application/json", """{"status":"ok","mode":"android-central","service":"winterhold-apk"}""".toByteArray())
            }
            if (route == "/api/v1/state" || route == "/api/v1/action" || route == "/api/v1/bridge") {
                if (DemoCentral.webUsers?.accepts(headers["x-winterhold-key"].orEmpty()) != true)
                    return respond(socket, 401, "application/json", """{"error":"Chave web inválida"}""".toByteArray())
                try {
                    if (route == "/api/v1/bridge" && method == "GET") {
                        val settings = DeliverySettings(context)
                        require(settings.bridgeUrl.isNotBlank() && settings.bridgeToken.isNotBlank()) { "Configure a ponte" }
                        val connection = java.net.URL("${settings.bridgeUrl}/status").openConnection() as java.net.HttpURLConnection
                        try {
                            connection.connectTimeout = 3000; connection.readTimeout = 5000
                            connection.instanceFollowRedirects = false
                            connection.setRequestProperty("Authorization", "Bearer ${settings.bridgeToken}")
                            require(connection.responseCode == 200) { "Ponte indisponível: HTTP ${connection.responseCode}" }
                            val body = java.io.ByteArrayOutputStream()
                            connection.inputStream.use { stream ->
                                val chunk = ByteArray(4096)
                                while (true) {
                                    val count = stream.read(chunk)
                                    if (count < 0) break
                                    require(body.size() + count <= 100_000) { "Resposta da ponte muito longa" }
                                    body.write(chunk, 0, count)
                                }
                            }
                            return respond(socket, 200, "application/json", body.toByteArray())
                        } finally { connection.disconnect() }
                    }
                    val json = when {
                        route == "/api/v1/state" && method == "GET" -> onMain { snapshot() }
                        route == "/api/v1/action" && method == "POST" -> {
                            val length = headers["content-length"]?.toIntOrNull() ?: -1
                            require(length in 1..8192) { "Corpo inválido" }
                            val bytes = ByteArray(length)
                            var total = 0
                            while (total < length) {
                                val n = input.read(bytes, total, length - total)
                                require(n > 0) { "Corpo incompleto" }
                                total += n
                            }
                            val action = JSONObject(String(bytes, StandardCharsets.UTF_8))
                            onMain { applyAction(action); DemoCentral.save(); snapshot() }
                        }
                        else -> return respond(socket, 405, "application/json", ByteArray(0))
                    }
                    return respond(socket, 200, "application/json", json.toString().toByteArray(StandardCharsets.UTF_8))
                } catch (error: IllegalArgumentException) {
                    return respond(socket, 400, "application/json",
                        JSONObject().put("error", error.message ?: "Ação inválida").toString().toByteArray())
                }
            }
            if (method != "GET" && method != "HEAD") return respond(socket, 405, "text/plain", ByteArray(0))
            val asset = assets[route] ?: return respond(socket, 404, "text/plain", ByteArray(0))
            val body = context.assets.open(asset.first).use { it.readBytes() }
            respond(socket, 200, asset.second, body, method == "HEAD")
        } catch (_: Exception) {
            try { respond(socket, 500, "text/plain", ByteArray(0)) } catch (_: Exception) { }
        }
    }

    private fun readLine(input: BufferedInputStream): String? {
        val out = java.io.ByteArrayOutputStream()
        while (out.size() < 8192) {
            val byte = input.read()
            if (byte < 0) return null
            if (byte == 10) return out.toString("UTF-8").trimEnd('\r')
            out.write(byte)
        }
        throw IllegalArgumentException("Cabeçalho muito longo")
    }

    private fun respond(socket: Socket, code: Int, type: String, body: ByteArray, head: Boolean = false) {
        val reason = when (code) { 200 -> "OK"; 400 -> "Bad Request"; 401 -> "Unauthorized";
            404 -> "Not Found"; 405 -> "Method Not Allowed"; else -> "Internal Server Error" }
        val header = "HTTP/1.1 $code $reason\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n" +
            "Connection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\n" +
            "Referrer-Policy: no-referrer\r\nContent-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self' data:; object-src 'none'; base-uri 'none'\r\n\r\n"
        socket.getOutputStream().apply {
            write(header.toByteArray(StandardCharsets.UTF_8))
            if (!head) write(body)
            flush()
        }
    }

    private fun onMain(block: () -> JSONObject): JSONObject {
        val done = CountDownLatch(1)
        val result = AtomicReference<JSONObject>()
        val failure = AtomicReference<Throwable>()
        main.post {
            try { result.set(block()) } catch (error: Throwable) { failure.set(error) }
            finally { done.countDown() }
        }
        require(done.await(3, TimeUnit.SECONDS)) { "Central indisponível" }
        failure.get()?.let { throw it }
        return result.get()
    }

    private fun snapshot(): JSONObject {
        val battery = (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
        val memory = ActivityManager.MemoryInfo().also {
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)
        }
        val storage = StatFs(Environment.getDataDirectory().path)
        val uptimeMinutes = SystemClock.elapsedRealtime() / 60_000
        val health = JSONObject()
            .put("battery", battery ?: JSONObject.NULL)
            .put("memory", if (memory.totalMem > 0) ((memory.totalMem - memory.availMem) * 100 / memory.totalMem).toInt() else JSONObject.NULL)
            .put("storage", if (storage.totalBytes > 0) ((storage.totalBytes - storage.availableBytes) * 100 / storage.totalBytes).toInt() else JSONObject.NULL)
            .put("uptime", "${uptimeMinutes / 60}h ${uptimeMinutes % 60}min")
        return JSONObject()
            .put("samples", JSONArray(store.visibleReadings.map { JSONObject()
                .put("time", it.time).put("temperature", it.temperature ?: JSONObject.NULL)
                .put("humidity", it.humidity ?: JSONObject.NULL).put("voltage", it.voltage ?: JSONObject.NULL)
                .put("deviceId", it.deviceId ?: JSONObject.NULL) }))
            .put("events", JSONArray(store.events.map { JSONObject()
                .put("id", it.id).put("time", it.time)
                .put("metric", it.metric?.name?.lowercase(Locale.ROOT) ?: JSONObject.NULL)
                .put("title", it.title).put("detail", it.detail)
                .put("severity", it.severity.name.lowercase(Locale.ROOT))
                .put("resolved", it.resolved).put("simulated", it.simulated)
                .put("deviceId", it.deviceId ?: JSONObject.NULL) }))
            .put("contacts", JSONArray(store.contacts.map { JSONObject()
                .put("id", it.id).put("name", it.name).put("role", it.role)
                .put("phone", it.phone).put("email", it.email)
                .put("metrics", JSONArray(it.metrics.map { m -> m.name.lowercase(Locale.ROOT) }))
                .put("channels", JSONArray(it.channels.map { c -> c.name.lowercase(Locale.ROOT) }))
                .put("active", it.active) }))
            .put("limits", JSONObject().put("temperature", store.temperatureLimit.toInt())
                .put("humidity", store.humidityLimit.toInt()).put("nominalVoltage", store.nominalVoltage.toInt())
                .put("tolerance", store.voltageTolerance.toInt()))
            .put("enabledChannels", JSONArray(store.enabledChannels.map { it.name.lowercase(Locale.ROOT) }))
            .put("running", store.simulationRunning).put("offline", store.offlineDemo)
            .put("mode", if (store.liveMode) "esp" else "demo")
            .put("selectedDeviceId", store.currentDeviceId ?: JSONObject.NULL)
            .put("deliveries", JSONArray(store.events.flatMap { event -> store.contacts.flatMap { contact ->
                Channel.entries.mapNotNull { channel -> deliveryStatus(event, contact, channel) }
            } }.take(100)))
            .put("devices", JSONArray(store.currentEnvironments.values.sortedBy { it.name }.map { profile -> JSONObject()
                .put("id", profile.deviceId).put("name", profile.name).put("icon", profile.icon).put("favorite", profile.favorite)
                .put("lastSeen", store.lastSeen[profile.deviceId] ?: JSONObject.NULL)
                .put("online", if (store.liveMode) store.lastSeen[profile.deviceId]?.let { store.now - it in 0..15_000L } ?: false else !store.offlineDemo)
                .put("limits", JSONObject()
                    .put("temperatureMin", profile.temperatureMin).put("temperatureMax", profile.temperatureMax)
                    .put("humidityMin", profile.humidityMin).put("humidityMax", profile.humidityMax)
                    .put("voltageMin", profile.voltageMin).put("voltageMax", profile.voltageMax)) }))
            .put("mqttAvailable", DemoCentral.mqttStatus.startsWith("Recebendo"))
            .put("settings", DeliverySettings(context).let { settings -> JSONObject()
                .put("smtpHost", settings.smtpHost).put("smtpUser", settings.smtpUser).put("smtpFrom", settings.smtpFrom)
                .put("smtpConfigured", settings.smtpPassword.isNotEmpty())
                .put("bridgeUrl", settings.bridgeUrl).put("bridgeConfigured", settings.bridgeToken.isNotEmpty())
                .put("mqttSecret", DemoCentral.mqttSecret).put("mqttStatus", DemoCentral.mqttStatus)
                .put("webStatus", DemoCentral.status).put("webUrls", JSONArray(DemoCentral.urls))
                .put("smsGranted", context.checkSelfPermission(android.Manifest.permission.SEND_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
                .put("notificationsGranted", android.os.Build.VERSION.SDK_INT < 33 ||
                    context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
                .put("batteryExempt", CentralService.batteryExempt(context)) })
            .put("deviceHealth", health)
    }

    private fun applyAction(action: JSONObject) {
        when (action.optString("type")) {
            "mode" -> { store.liveMode = when (action.getString("value")) {
                "esp" -> true; "demo" -> false
                else -> throw IllegalArgumentException("Modo inválido")
            }; if (!store.liveMode) store.simulationRunning = true }
            "selectedDevice" -> {
                val id = action.getString("value")
                store.selectEnvironment(id)
            }
            "saveEnvironment" -> {
                val profile = action.getJSONObject("environment")
                val limits = profile.getJSONObject("limits")
                fun number(key: String) = limits.optDouble(key, Double.NaN).toFloat()
                val environment = EnvironmentProfile(
                    deviceId = profile.optString("id"), name = profile.optString("name").trim(),
                    icon = profile.optString("icon"),
                    temperatureMin = number("temperatureMin"), temperatureMax = number("temperatureMax"),
                    humidityMin = number("humidityMin"), humidityMax = number("humidityMax"),
                    voltageMin = number("voltageMin"), voltageMax = number("voltageMax"),
                    favorite = profile.optBoolean("favorite", store.currentEnvironments[profile.optString("id")]?.favorite ?: false))
                if (store.liveMode) store.saveEnvironment(environment) else store.saveDemoEnvironment(environment)
            }
            "favoriteEnvironment" -> {
                val id = action.getString("id")
                val old = store.currentEnvironments[id] ?: throw IllegalArgumentException("Ambiente desconhecido")
                if (store.liveMode) store.saveEnvironment(old.copy(favorite = action.getBoolean("value")))
                else store.saveDemoEnvironment(old.copy(favorite = action.getBoolean("value")))
            }
            "simulateAlert" -> store.simulateAlert(metric(action.getString("metric")))
            "testAlert" -> DemoCentral.sendTest(store.simulateAlert(metric(action.getString("metric"))))
            "setDemoReading" -> store.setDemoReading(action.getString("id"), action.getDouble("temperature").toFloat(),
                action.getDouble("humidity").toFloat(), action.getDouble("voltage").toFloat())
            "deliverySettings" -> {
                val settings = DeliverySettings(context)
                val host = action.optString("smtpHost").trim(); val user = action.optString("smtpUser").trim()
                val from = action.optString("smtpFrom").trim(); val bridge = action.optString("bridgeUrl").trim()
                require(host.matches(Regex("[A-Za-z0-9.-]{0,253}")) && !user.contains('\n') &&
                    (from.isEmpty() || DeliverySettings.validEmail(from))) { "SMTP inválido" }
                settings.bridgeUrl = bridge
                settings.smtpHost = host; settings.smtpUser = user; settings.smtpFrom = from
                action.optString("smtpPassword").takeIf { it.isNotBlank() }?.let { settings.smtpPassword = it }
                action.optString("bridgeToken").takeIf { it.isNotBlank() }?.let { settings.bridgeToken = it }
            }
            "resolveAlert" -> store.resolveAlert(action.getString("id"))
            "addContact" -> {
                val name = action.getString("name").trim()
                require(name.isNotEmpty() && name.length <= 60) { "Nome inválido" }
                store.addContact(name, action.optString("role").take(60))
            }
            "contactActive" -> {
                val old = contact(action.getString("id"))
                store.updateContact(old.copy(active = action.getBoolean("value")))
            }
            "contactDestination" -> {
                val old = contact(action.getString("id"))
                store.updateContact(old.copy(phone = action.optString("phone").trim(),
                    email = action.optString("email").trim()))
            }
            "contactItem" -> {
                val old = contact(action.getString("id"))
                val value = action.getString("value")
                when (action.getString("group")) {
                    "metrics" -> {
                        val metric = metric(value)
                        store.updateContact(old.copy(metrics = if (metric in old.metrics) old.metrics - metric else old.metrics + metric))
                    }
                    "channels" -> {
                        val channel = channel(value)
                        store.updateContact(old.copy(channels = if (channel in old.channels) old.channels - channel else old.channels + channel))
                    }
                    else -> throw IllegalArgumentException("Filtro inválido")
                }
            }
            "running" -> { require(!store.liveMode); store.simulationRunning = action.getBoolean("value") }
            "offline" -> { require(!store.liveMode); if (store.offlineDemo != action.getBoolean("value")) store.simulateDisconnection() }
            "channel" -> {
                val channel = channel(action.getString("channel"))
                val enabled = action.getBoolean("value")
                if (enabled && channel !in store.enabledChannels) store.enabledChannels.add(channel)
                if (!enabled) store.enabledChannels.remove(channel)
            }
            "limit" -> {
                require(!store.liveMode)
                val value = action.getDouble("value").toFloat()
                when (action.getString("key")) {
                    "temperature" -> { require(value in 20f..35f); store.temperatureLimit = value }
                    "humidity" -> { require(value in 40f..90f); store.humidityLimit = value }
                    "nominalVoltage" -> { require(value in 100f..250f); store.nominalVoltage = value }
                    "tolerance" -> { require(value in 2f..20f); store.voltageTolerance = value }
                    else -> throw IllegalArgumentException("Limite inválido")
                }
            }
            else -> throw IllegalArgumentException("Ação desconhecida")
        }
    }

    private fun metric(value: String) = Metric.entries.firstOrNull { it.name.equals(value, true) }
        ?: throw IllegalArgumentException("Variável inválida")
    private fun channel(value: String) = Channel.entries.firstOrNull { it.name.equals(value, true) }
        ?: throw IllegalArgumentException("Canal inválido")
    private fun contact(id: String) = store.contacts.firstOrNull { it.id == id }
        ?: throw IllegalArgumentException("Perfil não encontrado")
    private fun deliveryStatus(event: AlertEvent, contact: DemoContact, channel: Channel): JSONObject? =
        DemoCentral.deliveryStatus(event.id, contact.id, channel)?.let { status -> JSONObject()
            .put("eventId", event.id).put("contactId", contact.id)
            .put("channel", channel.name.lowercase(Locale.ROOT)).put("status", status) }
}
