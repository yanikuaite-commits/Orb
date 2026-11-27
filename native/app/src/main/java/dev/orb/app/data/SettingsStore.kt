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
    val ttsEnabled: Boolean = true,
    val assistantName: String = "Orb",
    val userName: String = "",
    val language: String = "pt-BR",
    val persona: String = "",
    val voice: String = "Charon",
    val ttsEngine: String = "device",
    val textProvider: String = "gemini",
    val textModel: String = DEFAULT_GEMINI_TEXT_MODEL,
)

class SettingsStore(private val context: Context, scope: CoroutineScope) {
    private object Keys {
        val ttsEnabled = booleanPreferencesKey("tts_enabled")
        val assistantName = stringPreferencesKey("assistant_name")
        val userName = stringPreferencesKey("user_name")
        val language = stringPreferencesKey("language")
        val persona = stringPreferencesKey("persona")
        val voice = stringPreferencesKey("voice")
        val ttsEngine = stringPreferencesKey("tts_engine")
        val textProvider = stringPreferencesKey("text_provider")
        val textModel = stringPreferencesKey("text_model")
    }

    @Volatile
    var current: OrbSettings = OrbSettings()

    val flow: Flow<OrbSettings> = context.orbDataStore.data.map { prefs ->
        OrbSettings(
            ttsEnabled = prefs[Keys.ttsEnabled] ?: true,
            assistantName = prefs[Keys.assistantName] ?: "Orb",
            userName = prefs[Keys.userName].orEmpty(),
            language = prefs[Keys.language] ?: "pt-BR",
            persona = prefs[Keys.persona].orEmpty(),
            voice = prefs[Keys.voice] ?: "Charon",
            ttsEngine = prefs[Keys.ttsEngine] ?: "device",
            textProvider = prefs[Keys.textProvider] ?: "gemini",
            textModel = prefs[Keys.textModel] ?: DEFAULT_GEMINI_TEXT_MODEL,
        )
    }

    init {
        scope.launch { flow.collect { current = it } }
    }

    suspend fun save(settings: OrbSettings) {
        current = settings
        context.orbDataStore.edit { prefs ->
            prefs[Keys.ttsEnabled] = settings.ttsEnabled
            prefs[Keys.assistantName] = settings.assistantName
            prefs[Keys.userName] = settings.userName
            prefs[Keys.language] = settings.language
            prefs[Keys.persona] = settings.persona
            prefs[Keys.voice] = settings.voice
            prefs[Keys.ttsEngine] = settings.ttsEngine
            prefs[Keys.textProvider] = settings.textProvider
            prefs[Keys.textModel] = settings.textModel
        }
    }
}
