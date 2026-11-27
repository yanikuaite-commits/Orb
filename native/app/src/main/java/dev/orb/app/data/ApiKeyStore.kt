package dev.orb.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class ApiKeyStore(context: Context) {
    private val prefs: SharedPreferences = runCatching {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            "orb_secrets",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse {
        // Keystore can fail on a broken/restored device; the app must still open.
        context.applicationContext.getSharedPreferences("orb_secrets_fallback", Context.MODE_PRIVATE)
    }

    fun gemini(): String = prefs.getString(GEMINI_KEY, "").orEmpty().trim()

    fun setGemini(value: String) {
        prefs.edit().putString(GEMINI_KEY, value.trim()).apply()
    }

    fun clearGemini() {
        prefs.edit().remove(GEMINI_KEY).apply()
    }

    fun hasGemini(): Boolean = gemini().isNotBlank()

    fun groq(): String = prefs.getString(GROQ_KEY, "").orEmpty().trim()

    fun setGroq(value: String) {
        prefs.edit().putString(GROQ_KEY, value.trim()).apply()
    }

    fun clearGroq() {
        prefs.edit().remove(GROQ_KEY).apply()
    }

    fun hasGroq(): Boolean = groq().isNotBlank()

    private companion object {
        const val GEMINI_KEY = "gemini_api_key"
        const val GROQ_KEY = "groq_api_key"
    }
}
