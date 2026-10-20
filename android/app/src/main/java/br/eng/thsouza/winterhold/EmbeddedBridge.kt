package br.eng.thsouza.winterhold

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

/** Child process runs the APK's signed ELF, not code from writable app storage. */
internal object EmbeddedBridge {
    private var context: Context? = null
    private val worker = Executors.newSingleThreadExecutor()
    private var process: Process? = null
    private var wanted = false
    private var starting = false
    private var lastAttempt = 0L
    var status by mutableStateOf("Preparando WhatsApp integrado")
        private set
    fun prepare(value: Context) {
        context = value.applicationContext
        val settings = DeliverySettings(value)
        // Preserve explicitly configured remote bridges during migration.
        if (settings.bridgeUrl.isBlank()) {
            settings.bridgeUrl = "http://127.0.0.1:8791"
            settings.bridgeToken = ByteArray(32).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
        }
    }
    @Synchronized fun ensureStarted() {
        val app = context ?: return
        wanted = true
        if (process?.isAlive == true || starting || System.currentTimeMillis() - lastAttempt < 10_000) return
        val executable = File(app.applicationInfo.nativeLibraryDir, "libwinterhold_node.so")
        if (!executable.exists()) { status = "Runtime integrado indisponível nesta ABI; use ponte HTTPS"; return }
        starting = true; lastAttempt = System.currentTimeMillis()
        worker.execute {
            try {
                val directory = File(app.filesDir, "bridge-runtime")
                val info = app.packageManager.getPackageInfo(app.packageName, 0)
                @Suppress("DEPRECATION")
                val version = (if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()).toString()
                val marker = File(directory, ".version")
                if (marker.readTextOrNull() != version) {
                    directory.deleteRecursively(); directory.mkdirs()
                    ZipInputStream(app.assets.open("bridge/bridge.zip")).use { zip ->
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            val target = File(directory, entry.name)
                            require(target.canonicalPath.startsWith(directory.canonicalPath + File.separator))
                            if (entry.isDirectory) target.mkdirs() else {
                                target.parentFile?.mkdirs(); target.outputStream().use { zip.copyTo(it) }
                            }
                        }
                    }
                    marker.writeText(version)
                }
                val settings = DeliverySettings(app)
                val builder = ProcessBuilder(executable.absolutePath, "--use-bundled-ca", File(directory, "server.js").absolutePath)
                    .directory(directory).redirectErrorStream(true)
                builder.environment().apply {
                    remove("LD_PRELOAD"); remove("NODE_OPTIONS"); remove("NODE_PATH")
                    put("LD_LIBRARY_PATH", app.applicationInfo.nativeLibraryDir)
                    put("HOME", app.filesDir.absolutePath); put("TMPDIR", app.cacheDir.absolutePath)
                    put("OPENSSL_CONF", "/dev/null"); put("NODE_EXTRA_CA_CERTS", File(directory, "ca.pem").absolutePath)
                    put("WINTERHOLD_BRIDGE_TOKEN", settings.bridgeToken)
                    put("WINTERHOLD_BRIDGE_DATA", File(app.filesDir, "whatsapp-session").absolutePath)
                    put("WINTERHOLD_BRIDGE_HOST", "127.0.0.1"); put("WINTERHOLD_BRIDGE_PORT", "8791")
                    put("WINTERHOLD_ANDROID_PARENT", android.os.Process.myPid().toString())
                }
                synchronized(this) {
                    if (!wanted) { starting = false; return@execute }
                    process = builder.start()
                }
                status = "Baileys integrado iniciado · consulte o QR"
                val child = process!!
                Thread({
                    // Drain output without persisting QR, WhatsApp identifiers or message bodies.
                    child.inputStream.bufferedReader().use { input -> while (input.readLine() != null) { } }
                    val code = child.waitFor()
                    synchronized(this) {
                        if (process === child) { process = null; status = "WhatsApp interrompido ($code); retomada automática" }
                    }
                }, "winterhold-baileys").apply { isDaemon = true; start() }
            } catch (error: Exception) { status = "Falha no runtime: ${error.message?.take(120)}" }
            finally { synchronized(this) { starting = false } }
        }
    }
    @Synchronized fun stop() { wanted = false; process?.destroy(); process = null; status = "WhatsApp pausado com a central" }
    private fun File.readTextOrNull() = if (isFile) readText() else null
}
