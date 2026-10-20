package br.eng.thsouza.winterhold

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

@Composable
internal fun BridgePairingPanel() {
    val app = LocalContext.current.applicationContext
    var state by remember { mutableStateOf("Consultando WhatsAppâ€¦") }
    var qr by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val settings = DeliverySettings(app)
                    val connection = URL("${settings.bridgeUrl}/status").openConnection() as HttpURLConnection
                    try {
                        connection.connectTimeout = 2000; connection.readTimeout = 3000
                        connection.instanceFollowRedirects = false
                        connection.setRequestProperty("Authorization", "Bearer ${settings.bridgeToken}")
                        require(connection.responseCode == 200)
                        val bytes = connection.inputStream.use { input ->
                            val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(4096)
                            while (true) { val count = input.read(buffer); if (count < 0) break; require(out.size() + count <= 100_000); out.write(buffer, 0, count) }; out.toByteArray()
                        }
                        require(bytes.size <= 100_000)
                        JSONObject(String(bytes, Charsets.UTF_8))
                    } finally { connection.disconnect() }
                }
            }
            result.onSuccess {
                state = "WhatsApp: ${it.optString("connection")}"
                qr = it.optString("qr").takeIf { image -> image.startsWith("data:image/png;base64,") }?.let { image ->
                    val bytes = Base64.decode(image.substringAfter(','), Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            }.onFailure { state = "WhatsApp aguardando ponte / rede"; qr = null }
            delay(5_000)
        }
    }
    Column {
        Text(state)
        qr?.let { Image(it.asImageBitmap(), "QR para vincular WhatsApp", Modifier.size(240.dp)) }
        Text("No WhatsApp do telefone da conta, abra Aparelhos conectados e escaneie o QR. O pareamento tambÃ©m aparece na web.")
    }
}
