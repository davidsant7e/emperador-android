package com.davidsant7e.emperador.datos

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Guarda la clave de Gemini cifrada con una llave del Android Keystore (no sale del teléfono). */
object Boveda {
    private const val ALIAS = "emperador_gemini"
    private const val PREFS = "boveda"
    private const val CLAVE = "gemini"

    fun guardar(ctx: Context, clave: String) {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, llave())
        val cifrado = c.doFinal(clave.toByteArray(Charsets.UTF_8))
        val todo = c.iv + cifrado
        prefs(ctx).edit().putString(CLAVE, Base64.encodeToString(todo, Base64.NO_WRAP)).apply()
    }

    fun leer(ctx: Context): String? {
        val texto = prefs(ctx).getString(CLAVE, null) ?: return null
        return try {
            val todo = Base64.decode(texto, Base64.NO_WRAP)
            val iv = todo.copyOfRange(0, 12)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, llave(), GCMParameterSpec(128, iv))
            String(c.doFinal(todo.copyOfRange(12, todo.size)), Charsets.UTF_8)
        } catch (e: Exception) {
            null // la llave cambió (por ejemplo, tras restaurar el teléfono): hay que volver a pegar la clave
        }
    }

    fun hay(ctx: Context) = prefs(ctx).contains(CLAVE)

    fun borrar(ctx: Context) = prefs(ctx).edit().remove(CLAVE).apply()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun llave(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }
}
