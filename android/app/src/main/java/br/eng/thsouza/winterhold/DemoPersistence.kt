package br.eng.thsouza.winterhold

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.util.concurrent.Executors

/** Atomic local persistence for bounded demo and ESP readings. */
internal class DemoPersistence(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "demo-state.json"))
    private val writer = Executors.newSingleThreadExecutor()

    fun restore(store: DemoStore): String = try {
        if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) {
            store.restoreJson(String(file.readFully(), Charsets.UTF_8))
        }
        "Dados locais salvos no celular"
    } catch (error: Exception) {
        Log.e("Winterhold", "Estado local inválido; usando valores iniciais", error)
        "Estado salvo inválido; dados locais reiniciados"
    }

    fun save(json: String, onError: (Exception) -> Unit) = writer.submit {
        var stream: java.io.FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(json.toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            Log.e("Winterhold", "Falha ao salvar estado demo", error)
            onError(error)
        }
    }
}
