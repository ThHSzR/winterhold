package br.eng.thsouza.winterhold

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

internal data class WebUser(val id: String, val name: String, val key: String, val active: Boolean = true)

/** Access accounts are only readable and editable from the Android UI. */
internal class WebAccessUsers(context: Context) {
    private val prefs = context.getSharedPreferences("web_access", Context.MODE_PRIVATE)
    val users = mutableStateListOf<WebUser>()
    init {
        val saved = prefs.getString("users", null)
        if (saved != null) runCatching {
            val array = JSONArray(saved)
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                users.add(WebUser(item.getString("id"), item.getString("name"), item.getString("key"), item.getBoolean("active")))
            }
        }
        if (users.isEmpty()) {
            users.add(WebUser(UUID.randomUUID().toString(), "Administrador", prefs.getString("secret", null) ?: newKey()))
            persist()
        }
    }
    private fun newKey() = ByteArray(24).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
    private fun persist() {
        val json = JSONArray(users.map { JSONObject().put("id", it.id).put("name", it.name)
            .put("key", it.key).put("active", it.active) }).toString()
        check(prefs.edit().putString("users", json).remove("secret").commit())
    }
    fun accepts(value: String): Boolean {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        return users.any { it.active && MessageDigest.isEqual(it.key.toByteArray(StandardCharsets.UTF_8), bytes) }
    }
    fun add(name: String): WebUser {
        require(name.trim().length in 1..60 && users.size < 16) { "Nome inválido ou limite de usuários atingido" }
        return WebUser(UUID.randomUUID().toString(), name.trim(), newKey()).also { users.add(it); persist() }
    }
    fun setActive(id: String, active: Boolean) {
        val index = users.indexOfFirst { it.id == id }
        require(index >= 0) { "Usuário desconhecido" }
        require(active || users.count { it.active } > 1) { "Mantenha ao menos um usuário ativo" }
        users[index] = users[index].copy(active = active); persist()
    }
    fun rotate(id: String) {
        val index = users.indexOfFirst { it.id == id }
        require(index >= 0) { "Usuário desconhecido" }
        users[index] = users[index].copy(key = newKey()); persist()
    }
}
