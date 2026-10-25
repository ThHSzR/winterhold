package br.eng.thsouza.winterhold

import android.util.Log
import java.io.DataInputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.Semaphore
import kotlin.concurrent.thread

/** MQTT 3.1.1 QoS 0 publisher endpoint for the ESP firmware, not a general-purpose broker. */
internal class MqttIngress(
    private val secret: String,
    private val onReading: (Reading) -> Unit,
    private val port: Int = 1883
) {
    private val slots = Semaphore(8)
    private val clients = Collections.synchronizedSet(mutableSetOf<Socket>())
    private lateinit var listener: ServerSocket

    fun start() {
        listener = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), port), 8)
        }
        thread(name = "winterhold-mqtt", isDaemon = true) {
            while (!listener.isClosed) {
                try {
                    val socket = listener.accept()
                    if (!slots.tryAcquire()) { socket.close(); continue }
                    clients.add(socket)
                    thread(name = "winterhold-mqtt-client", isDaemon = true) {
                        try { socket.use(::serve) }
                        catch (error: Exception) { Log.d("WinterholdMQTT", "Cliente encerrado", error) }
                        finally { clients.remove(socket); slots.release() }
                    }
                } catch (_: Exception) {
                    if (listener.isClosed) return@thread
                }
            }
        }
    }

    fun stop() {
        if (::listener.isInitialized) listener.close()
        synchronized(clients) { clients.toList().forEach { runCatching { it.close() } } }
    }

    private fun serve(socket: Socket) {
        socket.soTimeout = 45_000
        val input = DataInputStream(socket.getInputStream())
        val output = socket.getOutputStream()
        val connect = readPacket(input) ?: return
        if (connect.first != 0x10) return
        val connection = Cursor(connect.second)
        val code = try {
            val protocol = connection.string()
            val level = connection.byte()
            val flags = connection.byte()
            connection.short() // keep alive
            val clientId = connection.string()
            require(protocol == "MQTT" && level == 4 && (flags and 0xC4) == 0xC0)
            require(clientId.matches(Regex("[A-Za-z0-9_-]{1,40}")))
            val username = connection.string()
            val password = connection.string()
            require(connection.remaining() == 0)
            if (username == "winterhold" && MessageDigest.isEqual(
                    password.toByteArray(Charsets.UTF_8), secret.toByteArray(Charsets.UTF_8))) 0 else 4
        } catch (_: Exception) { 2 }
        output.write(byteArrayOf(0x20, 0x02, 0x00, code.toByte()))
        output.flush()
        if (code != 0) return
        while (true) {
            val packet = readPacket(input) ?: return
            when {
                packet.first == 0xC0 && packet.second.isEmpty() -> {
                    output.write(byteArrayOf(0xD0.toByte(), 0x00)); output.flush()
                }
                packet.first == 0xE0 && packet.second.isEmpty() -> return
                packet.first == 0x30 -> {
                    val message = Cursor(packet.second)
                    val topic = message.string()
                    val payload = message.rest()
                    try { onReading(EspTelemetry.decode(topic, payload)) }
                    catch (error: Exception) { Log.w("WinterholdMQTT", "Leitura rejeitada: ${error.message}") }
                }
                else -> return // QoS > 0, subscriptions and unsupported MQTT commands are rejected.
            }
        }
    }

    private fun readPacket(input: DataInputStream): Pair<Int, ByteArray>? {
        val header = input.read()
        if (header < 0) return null
        var length = 0
        var multiplier = 1
        repeat(4) {
            val digit = input.readUnsignedByte()
            length += (digit and 127) * multiplier
            if (digit and 128 == 0) {
                require(length <= 2048) { "Pacote MQTT muito grande" }
                return header to ByteArray(length).also { input.readFully(it) }
            }
            multiplier *= 128
        }
        throw IllegalArgumentException("Tamanho MQTT inválido")
    }

    private class Cursor(private val bytes: ByteArray) {
        private var index = 0
        fun remaining() = bytes.size - index
        fun byte(): Int { require(remaining() >= 1); return bytes[index++].toInt() and 255 }
        fun short() = (byte() shl 8) or byte()
        fun string(): String {
            val length = short()
            require(length <= remaining())
            return String(bytes, index, length, Charsets.UTF_8).also { index += length }
        }
        fun rest() = bytes.copyOfRange(index, bytes.size)
    }
}
