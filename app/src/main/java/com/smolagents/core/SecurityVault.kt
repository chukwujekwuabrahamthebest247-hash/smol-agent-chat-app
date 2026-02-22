

package com.agent.smolchat.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.GCMParameterSpec
import java.security.SecureRandom

object SecurityVault {
    private const val PREF_NAME = "AgentSecureVault"
    private const val KEY_ALIAS = "AgentVaultKey"
    private const val AES_MODE = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12
    private const val TAG_SIZE = 128

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private fun generateKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        return keyGen.generateKey()
    }

    private fun getOrCreateKey(context: Context): SecretKey {
        val prefs = getPrefs(context)
        val storedKey = prefs.getString(KEY_ALIAS, null)
        return if (storedKey != null) {
            val decoded = Base64.decode(storedKey, Base64.DEFAULT)
            SecretKeySpec(decoded, "AES")
        } else {
            val newKey = generateKey()
            val encoded = Base64.encodeToString(newKey.encoded, Base64.DEFAULT)
            prefs.edit().putString(KEY_ALIAS, encoded).apply()
            newKey
        }
    }

    private fun encrypt(context: Context, plainText: String): String {
        val key = getOrCreateKey(context)
        val cipher = Cipher.getInstance(AES_MODE)
        val iv = ByteArray(IV_SIZE)
        SecureRandom().nextBytes(iv)
        val spec = GCMParameterSpec(TAG_SIZE, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        val encrypted = cipher.doFinal(plainText.toByteArray())
        val combined = iv + encrypted
        return Base64.encodeToString(combined, Base64.DEFAULT)
    }

    private fun decrypt(context: Context, encryptedText: String): String {
        val key = getOrCreateKey(context)
        val decoded = Base64.decode(encryptedText, Base64.DEFAULT)
        val iv = decoded.copyOfRange(0, IV_SIZE)
        val encrypted = decoded.copyOfRange(IV_SIZE, decoded.size)
        val cipher = Cipher.getInstance(AES_MODE)
        val spec = GCMParameterSpec(TAG_SIZE, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        val decrypted = cipher.doFinal(encrypted)
        return String(decrypted)
    }

    // Updated for Hugging Face
    fun storeHuggingFaceEndpoint(context: Context, url: String) {
        val encrypted = encrypt(context, url)
        getPrefs(context).edit().putString("HF_ENDPOINT", encrypted).apply()
    }

    fun getHuggingFaceEndpoint(context: Context): String? {
        val encrypted = getPrefs(context).getString("HF_ENDPOINT", null)
        return encrypted?.let { decrypt(context, it) }
    }
}
