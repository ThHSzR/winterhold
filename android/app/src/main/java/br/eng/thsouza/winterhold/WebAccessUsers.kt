package br.eng.thsouza.winterhold

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

internal data class WebUser(val id: String, val name: String, val username: String,
    val salt: String, val hash: String, val active: Boolean = true, val legacyUntil: Long = 0)

/** Account mutations exist only in the APK. Passwords are never retained. */
internal class WebAccessUsers(context: Context) {
    private val prefs = context.getSharedPreferences("web_access", Context.MODE_PRIVATE)
    val users = mutableStateListOf<WebUser>()
    private data class Session(val userId: String, val expires: Long)
    private val sessions = mutableMapOf<String, Session>()
    private val attempts = mutableMapOf<String, Pair<Int, Long>>()
    var initialPassword: String? = null
        private set
    init {
        val saved = prefs.getString("accounts_v2", null)
        if (saved != null) {
            val array = JSONArray(saved)
            repeat(array.length()) {
                val item = array.getJSONObject(it)
                users.add(WebUser(item.getString("id"), item.getString("name"), item.getString("username"),
                    item.getString("salt"), item.getString("hash"), item.getBoolean("active"), item.optLong("legacyUntil")))
            }
        } else {
            val legacy = JSONArray(prefs.getString("users", "[]"))
            repeat(legacy.length()) {
                val item = legacy.getJSONObject(it)
                val salt = random()
                users.add(WebUser(item.getString("id"), item.getString("name"), "usuario${it + 1}", salt,
                    derive(item.getString("key"), salt), item.getBoolean("active"), System.currentTimeMillis() + 7 * 86_400_000L))
            }
            if (users.isEmpty()) {
                val password = prefs.getString("secret", null) ?: random().take(20)
                initialPassword = password
                val salt = random()
                users.add(WebUser(UUID.randomUUID().toString(), "Administrador", "admin", salt, derive(password, salt)))
            }
            persist()
        }
    }
    private fun persist() {
        check(prefs.edit().putString("accounts_v2", JSONArray(users.map { JSONObject()
            .put("id", it.id).put("name", it.name).put("username", it.username).put("salt", it.salt)
            .put("hash", it.hash).put("active", it.active).put("legacyUntil", it.legacyUntil) }).toString())
            .remove("users").remove("secret").commit())
    }
    private fun username(value: String): String = value.trim().lowercase(java.util.Locale.ROOT).also {
        require(it.matches(Regex("[a-z0-9_.-]{3,40}"))) { "Login: 3–40 letras, números, ponto, _ ou -" }
    }
    @Synchronized fun add(name: String, login: String, password: String): WebUser {
        val normalized = username(login)
        require(name.trim().length in 1..60 && users.size < 16 && users.none { it.username == normalized }) { "Nome/login inválido, duplicado ou limite atingido" }
        require(password.length in 12..256) { "Use senha de 12 a 256 caracteres" }
        val salt = random()
        return WebUser(UUID.randomUUID().toString(), name.trim(), normalized, salt, derive(password, salt))
            .also { users.add(it); persist() }
    }
    @Synchronized fun edit(id: String, name: String, login: String) {
        val index = users.indexOfFirst { it.id == id }; require(index >= 0)
        val normalized = username(login)
        require(name.trim().length in 1..60 && users.none { it.id != id && it.username == normalized }) { "Nome/login inválido ou duplicado" }
        users[index] = users[index].copy(name = name.trim(), username = normalized)
        revoke(id); persist()
    }
    @Synchronized fun setActive(id: String, active: Boolean) {
        val index = users.indexOfFirst { it.id == id }; require(index >= 0)
        require(active || !users[index].active || users.count { it.active } > 1) { "Mantenha ao menos um usuário ativo" }
        users[index] = users[index].copy(active = active); revoke(id); persist()
    }
    @Synchronized fun delete(id: String) {
        val user = users.first { it.id == id }
        require(!user.active || users.count { it.active } > 1) { "Mantenha ao menos um usuário ativo" }
        users.remove(user); revoke(id); persist()
    }
    @Synchronized fun resetPassword(id: String, password: String) {
        require(password.length in 12..256) { "Use senha de 12 a 256 caracteres" }
        val index = users.indexOfFirst { it.id == id }; require(index >= 0)
        val salt = random()
        users[index] = users[index].copy(salt = salt, hash = derive(password, salt), legacyUntil = 0)
        initialPassword = null; revoke(id); persist()
    }
    @Synchronized fun revoke(id: String) { sessions.entries.removeAll { it.value.userId == id } }
    @Synchronized fun login(login: String, password: String, peer: String): Pair<Int, String?> {
        val now = System.currentTimeMillis()
        attempts.entries.removeAll { now - it.value.second >= 60_000 }
        if ((attempts[peer]?.first ?: 0) >= 5 || (attempts["*"]?.first ?: 0) >= 30) return 429 to null
        for (key in listOf(peer, "*")) {
            val old = attempts[key]
            attempts[key] = ((old?.first ?: 0) + 1) to (old?.second ?: now)
        }
        if (password.length !in 1..256 || login.length > 40) return 401 to null
        val user = users.firstOrNull { it.username == login.trim().lowercase(java.util.Locale.ROOT) }
        val derived = derive(password, user?.salt ?: "0".repeat(48))
        if (user == null || !user.active || user.legacyUntil != 0L && now > user.legacyUntil ||
            !MessageDigest.isEqual(derived.toByteArray(), user.hash.toByteArray())) return 401 to null
        sessions.entries.removeAll { it.value.expires <= now }
        if (sessions.size >= 128) sessions.remove(sessions.keys.first())
        val token = random()
        sessions[token] = Session(user.id, now + 8 * 60 * 60_000)
        return 200 to token
    }
    @Synchronized fun acceptsSession(token: String): Boolean {
        val session = sessions[token] ?: return false
        return session.expires > System.currentTimeMillis() && users.any { it.id == session.userId && it.active }
    }
    @Synchronized fun logout(token: String) { sessions.remove(token) }
    companion object {
        private fun random() = ByteArray(24).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
        private fun derive(password: String, salt: String): String {
            val spec = PBEKeySpec(password.toCharArray(), salt.toByteArray(), 210_000, 256)
            return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
                .joinToString("") { "%02x".format(it) } } finally { spec.clearPassword() }
        }
    }
}
