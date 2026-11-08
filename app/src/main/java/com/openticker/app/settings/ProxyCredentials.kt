package com.openticker.app.settings

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import okhttp3.HttpUrl.Companion.toHttpUrl

fun validProxyToken(value: String) = Regex("^[a-f0-9]{64}$").matches(value)
fun proxyCredentialScope(value: String): String {
    require(validStockProxy(value))
    return value.toHttpUrl().toString().trimEnd('/')
}
/** In-memory editor only. Never serialized or interpolated into diagnostics. */
class ProxyTokenEdit(val value: String) {
    override fun toString() = "[redacted]"
}
interface ProxyTokenCipher {
    fun encrypt(token: String, scope: String): String
    fun decrypt(encrypted: String, scope: String): String
}
/** Random IV per write; the URL is authenticated data, preventing credential re-binding. */
class AndroidProxyTokenCipher : ProxyTokenCipher {
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    override fun encrypt(token: String, scope: String): String {
        require(validProxyToken(token))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key()); cipher.updateAAD(scope.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8)))
    }
    override fun decrypt(encrypted: String, scope: String): String {
        val bytes = Base64.getDecoder().decode(encrypted)
        require(bytes.size == 12 + 64 + 16)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD(scope.toByteArray(Charsets.UTF_8))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8).also { require(validProxyToken(it)) }
    }
    companion object { private const val ALIAS = "openticker-proxy-access-v1" }
}
