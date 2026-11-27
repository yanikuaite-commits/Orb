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

    fun get(): String = prefs.getString(KEY, "").orEmpty().trim()

    fun set(value: String) {
        prefs.edit().putString(KEY, value.trim()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    fun hasKey(): Boolean = get().isNotBlank()

    private companion object {
        const val KEY = "gemini_api_key"
    }
}
