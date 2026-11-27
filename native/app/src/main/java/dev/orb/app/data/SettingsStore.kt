package dev.orb.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.orbDataStore by preferencesDataStore(name = "orb_settings")

data class OrbSettings(
    val serverUrl: String = "",
    val token: String = "",
    val ttsEnabled: Boolean = true,
    val assistantName: String = "Orb",
    val language: String = "pt-BR",
    val voice: String = "Charon",
    val ttsEngine: String = "device",
)

class SettingsStore(private val context: Context, scope: CoroutineScope) {
    private object Keys {
        val serverUrl = stringPreferencesKey("server_url")
        val token = stringPreferencesKey("token")
        val ttsEnabled = booleanPreferencesKey("tts_enabled")
        val assistantName = stringPreferencesKey("assistant_name")
        val language = stringPreferencesKey("language")
        val voice = stringPreferencesKey("voice")
        val ttsEngine = stringPreferencesKey("tts_engine")
    }

    @Volatile
    var current: OrbSettings = OrbSettings()

    val flow: Flow<OrbSettings> = context.orbDataStore.data.map { prefs ->
        OrbSettings(
            serverUrl = prefs[Keys.serverUrl].orEmpty(),
            token = prefs[Keys.token].orEmpty(),
            ttsEnabled = prefs[Keys.ttsEnabled] ?: true,
            assistantName = prefs[Keys.assistantName] ?: "Orb",
            language = prefs[Keys.language] ?: "pt-BR",
            voice = prefs[Keys.voice] ?: "Charon",
            ttsEngine = prefs[Keys.ttsEngine] ?: "device",
        )
    }

    init {
        scope.launch { flow.collect { current = it } }
    }

    suspend fun save(settings: OrbSettings) {
        current = settings
        context.orbDataStore.edit { prefs ->
            prefs[Keys.serverUrl] = settings.serverUrl
            prefs[Keys.token] = settings.token
            prefs[Keys.ttsEnabled] = settings.ttsEnabled
            prefs[Keys.assistantName] = settings.assistantName
            prefs[Keys.language] = settings.language
            prefs[Keys.voice] = settings.voice
            prefs[Keys.ttsEngine] = settings.ttsEngine
        }
    }
}
