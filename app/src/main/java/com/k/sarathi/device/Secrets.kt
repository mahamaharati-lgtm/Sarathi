package com.k.sarathi.device

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.k.sarathi.ui.KeyVault
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the Claude API key encrypted with an AES-256-GCM key that lives in
 * the Android Keystore (it never leaves secure hardware). The app's data is
 * excluded from cloud backup, and the key is never written to exports.
 */
class KeystoreVault(ctx: Context) : KeyVault {
    private val prefs = ctx.getSharedPreferences("vault", Context.MODE_PRIVATE)
    private val alias = "sarathi_api_key"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    @Volatile private var cache: String? = null
    @Volatile private var loaded = false

    override fun get(): String? {
        if (loaded) return cache
        val blob = prefs.getString("k", null)
        cache = blob?.let { runCatching {
            val raw = Base64.decode(it, Base64.NO_WRAP)
            val iv = raw.copyOfRange(0, 12)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(c.doFinal(raw, 12, raw.size - 12))
        }.getOrNull() }
        loaded = true
        return cache
    }

    override fun set(value: String?) {
        if (value.isNullOrBlank()) {
            prefs.edit().remove("k").apply()
            cache = null; loaded = true
            return
        }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val out = c.iv + c.doFinal(value.toByteArray())
        prefs.edit().putString("k", Base64.encodeToString(out, Base64.NO_WRAP)).apply()
        cache = value; loaded = true
    }
}
