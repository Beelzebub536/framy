package app.framy

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Token / passwords / admin key, AES-GCM encrypted with a key that never leaves AndroidKeyStore. */
object Secrets {
    private const val ALIAS = "framy"
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) { sp = ctx.getSharedPreferences("secrets", Context.MODE_PRIVATE) }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as SecretKey?)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    /** "canvasToken" | "mailAddress" | "mailPassword" | "adminKey". Null if unset or undecryptable. */
    fun get(name: String): String? {
        val blob = sp.getString(name, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob, 0, 12))
            String(c.doFinal(blob, 12, blob.size - 12))
        } catch (e: Exception) { null }
    }

    fun put(name: String, value: String?) {
        if (value.isNullOrEmpty()) { sp.edit().remove(name).apply(); return }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val out = c.iv + c.doFinal(value.toByteArray())
        sp.edit().putString(name, Base64.encodeToString(out, Base64.NO_WRAP)).apply()
    }
}
