package br.eng.thsouza.winterhold

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** Stores credentials under Android Keystore. Never includes them in the LAN API or saved state. */
internal class DeliverySettings(private val context: Context) {
    private val prefs = context.getSharedPreferences("delivery_settings", Context.MODE_PRIVATE)
    private val alias = "winterhold-delivery-v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(android.security.keystore.KeyGenParameterSpec.Builder(alias,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes("GCM").setEncryptionPaddings("NoPadding").build())
        }.generateKey()
    }
    private fun secret(name: String): String = runCatching {
        val bytes = Base64.decode(prefs.getString(name, "") ?: "", Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
        String(cipher.doFinal(bytes, 12, bytes.size - 12), StandardCharsets.UTF_8)
    }.getOrDefault("")
    private fun secret(name: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val result = cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        check(prefs.edit().putString(name, Base64.encodeToString(result, Base64.NO_WRAP)).commit())
    }
    var smtpHost: String
        get() = prefs.getString("smtpHost", "") ?: ""
        set(value) { require(value.matches(Regex("[A-Za-z0-9.-]{0,253}"))); prefs.edit().putString("smtpHost", value).apply() }
    var smtpUser: String
        get() = prefs.getString("smtpUser", "") ?: ""
        set(value) { require(!value.contains('\r') && !value.contains('\n')); prefs.edit().putString("smtpUser", value).apply() }
    var smtpFrom: String
        get() = prefs.getString("smtpFrom", "") ?: ""
        set(value) { require(value.isEmpty() || validEmail(value)); prefs.edit().putString("smtpFrom", value).apply() }
    var smtpPassword: String
        get() = secret("smtpPassword")
        set(value) = secret("smtpPassword", value)
    var bridgeUrl: String
        get() = prefs.getString("bridgeUrl", "") ?: ""
        set(value) {
            require(value.isEmpty() || runCatching {
                val url = URL(value)
                (url.protocol == "https" || url.protocol == "http" && url.host in setOf("127.0.0.1", "localhost")) &&
                    url.toURI().userInfo == null && url.query == null && url.ref == null && url.path.trimEnd('/') in setOf("", "/")
            }.getOrDefault(false)) { "Use HTTPS ou HTTP local (127.0.0.1)" }
            prefs.edit().putString("bridgeUrl", value.trimEnd('/')).apply()
        }
    var bridgeToken: String
        get() = secret("bridgeToken")
        set(value) = secret("bridgeToken", value)
    companion object {
        fun validEmail(value: String) = value.matches(Regex("[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9.-]{1,190}\\.[A-Za-z]{2,20}"))
    }
}

/** One reservation per event / contact / channel, committed before any external side effect. */
internal class AlertDelivery(private val context: Context) {
    private val settings = DeliverySettings(context)
    private val ledger = context.getSharedPreferences("alert_delivery", Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val knownEvents = mutableSetOf<String>()
    init {
        notifications.createNotificationChannel(NotificationChannel("winterhold-alerts", "Alertas de ambientes", NotificationManager.IMPORTANCE_HIGH))
    }
    private fun record(id: String, status: String) {
        check(ledger.edit().putString(id, status).commit()) { "Falha ao registrar tentativa de entrega" }
    }
    fun status(eventId: String, contactId: String, channel: Channel): String? =
        ledger.getString("$eventId:$contactId:${channel.name}", null)
    fun prime(store: DemoStore) { knownEvents.addAll(store.events.map { it.id }) }
    fun dispatch(store: DemoStore, events: List<AlertEvent>) {
        events.filter { knownEvents.add(it.id) && !it.simulated && !it.resolved && it.severity != Severity.INFO }.forEach { event ->
            deliver(store, event, false)
        }
    }
    fun dispatchTest(store: DemoStore, event: AlertEvent) {
        require(event.simulated && !store.liveMode && event.id in store.events.map { it.id })
        knownEvents.add(event.id)
        deliver(store, event, true)
    }
    private fun deliver(store: DemoStore, event: AlertEvent, test: Boolean) {
            val noticeId = "notice:${event.id}"
            if (ledger.getString(noticeId, null) == null) {
                runCatching {
                    record(noticeId, "reserved")
                    if (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                        val intent = PendingIntent.getActivity(context, event.id.hashCode(), Intent(context, MainActivity::class.java),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                        notifications.notify(event.id, 0, Notification.Builder(context, "winterhold-alerts")
                            .setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle(if (test) "[TESTE] ${event.title}" else event.title)
                            .setContentText(event.detail).setStyle(Notification.BigTextStyle().bigText(event.detail))
                            .setContentIntent(intent).setAutoCancel(true).build())
                    }
                    record(noticeId, "shown")
                }.onFailure { Log.e("Winterhold", "Notificação local falhou", it) }
            }
            store.routesFor(event).forEach { (contact, channels) -> channels.forEach { channel ->
                val destination = if (channel == Channel.EMAIL) contact.email else contact.phone
                if (destination.isBlank()) return@forEach
                if (channel == Channel.EMAIL && (settings.smtpHost.isBlank() || settings.smtpUser.isBlank() ||
                    settings.smtpFrom.isBlank() || settings.smtpPassword.isBlank())) return@forEach
                if (channel == Channel.SMS && context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return@forEach
                if (channel == Channel.WHATSAPP && (settings.bridgeUrl.isBlank() || settings.bridgeToken.isBlank())) return@forEach
                val id = "${event.id}:${contact.id}:${channel.name}"
                if (ledger.getString(id, null) != null) return@forEach
                runCatching { record(id, "reserved") }.onFailure { Log.e("Winterhold", "Registro de entrega falhou", it) }
                    .onSuccess {
                        val message = "Winterhold | ${if (test) "[TESTE] " else ""}${event.title}: ${event.detail}"
                        executor.execute {
                            runCatching {
                                when (channel) {
                                    Channel.SMS -> sms(destination, message, id)
                                    Channel.EMAIL -> { smtp(destination, if (test) "[TESTE] ${event.title}" else event.title, message); record(id, "submitted") }
                                    Channel.WHATSAPP -> { whatsapp(destination, message, id); record(id, "submitted") }
                                }
                            }.onFailure { error ->
                                Log.e("Winterhold", "Entrega ${channel.name} falhou para ${contact.id}", error)
                                runCatching { record(id, if (channel != Channel.SMS) "uncertain" else
                                    "failed: ${error.message.orEmpty().take(100)}") }
                            }
                        }
                    }
            } }
    }
    private fun sms(to: String, body: String, id: String) {
        check(context.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) { "Permissão de SMS ausente" }
        check(context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING)) { "Sem suporte a SMS" }
        @Suppress("DEPRECATION")
        val manager = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java)
            ?: SmsManager.getDefault() else SmsManager.getDefault()
        val parts = manager.divideMessage(body)
        val intents = ArrayList<PendingIntent>(parts.size)
        parts.indices.forEach { index ->
            intents.add(PendingIntent.getBroadcast(context, (id + index).hashCode(),
                Intent(context, SmsStatusReceiver::class.java).putExtra("id", id).putExtra("part", index)
                    .putExtra("count", parts.size), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
        record(id, "queued")
        manager.sendMultipartTextMessage(to, null, parts, intents, null)
    }
    private fun whatsapp(to: String, message: String, id: String) {
        val connection = URL("${settings.bridgeUrl}/send").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"; connection.connectTimeout = 5000; connection.readTimeout = 10000
            connection.instanceFollowRedirects = false; connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer ${settings.bridgeToken}")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(JSONObject().put("id", id).put("to", to).put("message", message)
                .toString().toByteArray(StandardCharsets.UTF_8)) }
            check(connection.responseCode == 202) { "Ponte WhatsApp: HTTP ${connection.responseCode}" }
        } finally { connection.disconnect() }
    }
    private fun smtp(to: String, subject: String, message: String) {
        val host = settings.smtpHost; val user = settings.smtpUser; val from = settings.smtpFrom
        val password = settings.smtpPassword
        check(host.isNotEmpty() && user.isNotEmpty() && from.isNotEmpty() && password.isNotEmpty()) { "Configure SMTP no APK" }
        val socket = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(host, 465) as SSLSocket
        socket.soTimeout = 12000
        socket.sslParameters = socket.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
        socket.use { ssl ->
            ssl.startHandshake()
            val input = BufferedReader(InputStreamReader(ssl.inputStream, StandardCharsets.UTF_8))
            val output = BufferedWriter(OutputStreamWriter(ssl.outputStream, StandardCharsets.UTF_8))
            fun reply(expected: Int) {
                var line: String
                do {
                    line = input.readLine() ?: error("SMTP desconectado")
                    check(line.length >= 4 && line.take(3).toIntOrNull() == expected) { "SMTP respondeu ${line.take(3)}" }
                } while (line[3] == '-')
            }
            fun send(value: String, expected: Int) { output.write("$value\r\n"); output.flush(); reply(expected) }
            fun b64(value: String) = Base64.encodeToString(value.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
            reply(220); send("EHLO winterhold.local", 250); send("AUTH LOGIN", 334)
            send(b64(user), 334); send(b64(password), 235)
            send("MAIL FROM:<$from>", 250); send("RCPT TO:<$to>", 250); send("DATA", 354)
            val safeSubject = b64(subject)
            val safeBody = message.replace("\r", "").split('\n').joinToString("\r\n") { if (it.startsWith('.')) ".$it" else it }
            output.write("From: <$from>\r\nTo: <$to>\r\nSubject: =?UTF-8?B?$safeSubject?=\r\n")
            output.write("MIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n")
            output.write(b64(safeBody) + "\r\n.\r\n"); output.flush(); reply(250)
            send("QUIT", 221)
        }
    }
}

class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val part = intent.getIntExtra("part", -1)
        val count = intent.getIntExtra("count", 0)
        if (part !in 0 until count || count > 20) return
        val prefs = context.getSharedPreferences("alert_delivery", Context.MODE_PRIVATE)
        if (resultCode != android.app.Activity.RESULT_OK) {
            prefs.edit().putString(id, "failed: SMS code $resultCode").commit()
            return
        }
        val seen = prefs.getStringSet("$id:parts", emptySet()).orEmpty() + part.toString()
        prefs.edit().putStringSet("$id:parts", seen).putString(id,
            if (seen.size >= count) "submitted" else "queued").commit()
    }
}
