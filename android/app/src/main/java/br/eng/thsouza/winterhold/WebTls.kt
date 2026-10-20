package br.eng.thsouza.winterhold

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.SSLContext
import javax.net.ssl.X509ExtendedKeyManager
import javax.security.auth.x500.X500Principal

internal object WebTls {
    private const val ALIAS = "winterhold-web-tls-v1"
    var fingerprint = ""
        private set
    fun context(): SSLContext {
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keys.containsAlias(ALIAS)) KeyPairGenerator.getInstance("RSA", "AndroidKeyStore").apply {
            initialize(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(2048).setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .setCertificateSubject(X500Principal("CN=Winterhold local"))
                .setCertificateSerialNumber(BigInteger.ONE).setCertificateNotBefore(Date(0))
                .setCertificateNotAfter(Date(4_102_444_800_000L)).build())
        }.generateKeyPair()
        val chain = keys.getCertificateChain(ALIAS).map { it as X509Certificate }.toTypedArray()
        fingerprint = MessageDigest.getInstance("SHA-256").digest(chain[0].encoded).joinToString(":") { "%02X".format(it) }
        val manager = object : X509ExtendedKeyManager() {
            override fun getPrivateKey(alias: String?) = keys.getKey(ALIAS, null) as PrivateKey
            override fun getCertificateChain(alias: String?) = chain
            override fun getServerAliases(type: String?, issuers: Array<out Principal>?) = arrayOf(ALIAS)
            override fun chooseServerAlias(type: String?, issuers: Array<out Principal>?, socket: Socket?) = if (type == "RSA") ALIAS else null
            override fun chooseEngineServerAlias(type: String?, issuers: Array<out Principal>?, engine: javax.net.ssl.SSLEngine?) =
                if (type == "RSA") ALIAS else null
            override fun getClientAliases(type: String?, issuers: Array<out Principal>?) = null
            override fun chooseClientAlias(types: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = null
        }
        return SSLContext.getInstance("TLS").apply { init(arrayOf(manager), null, null) }
    }
}
