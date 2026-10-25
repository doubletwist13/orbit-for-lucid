package io.github.doubletwist13.orbit

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Refresh token encrypted with a non-exportable Android Keystore AES-GCM key; ciphertext lives in app prefs. */
class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply { init(spec) }.generateKey()
    }

    fun save(token: String) {
        val c = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val blob = c.iv + c.doFinal(token.toByteArray())
        prefs.edit().putString("rt", Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
    }

    /** Null when absent or undecryptable (e.g. key lost after a lock-screen reset): user just logs in again. */
    fun load(): String? = runCatching {
        val blob = Base64.decode(prefs.getString("rt", null) ?: return null, Base64.NO_WRAP)
        val c = Cipher.getInstance(TRANSFORM).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob, 0, IV_LEN))
        }
        String(c.doFinal(blob, IV_LEN, blob.size - IV_LEN))
    }.getOrNull()

    fun clear() = prefs.edit().remove("rt").apply()

    /** Random per-install id sent as the gateway's device id; not a secret. */
    fun deviceId(): String = prefs.getString("did", null)
        ?: UUID.randomUUID().toString().replace("-", "").take(12).also { prefs.edit().putString("did", it).apply() }

    private companion object {
        const val ALIAS = "orbit_refresh_token"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_LEN = 12
    }
}
