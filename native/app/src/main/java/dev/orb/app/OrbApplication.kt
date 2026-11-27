package dev.orb.app

import android.app.Application
import android.content.Context
import dev.orb.app.agent.GeminiTextProvider
import dev.orb.app.agent.GroqTextProvider
import dev.orb.app.agent.OrbAgent
import dev.orb.app.agent.OrbTools
import dev.orb.app.audio.TtsPlayer
import dev.orb.app.audio.WavPlayer
import dev.orb.app.data.ApiKeyStore
import dev.orb.app.data.GeminiClient
import dev.orb.app.data.GroqClient
import dev.orb.app.data.HistoryStore
import dev.orb.app.data.LocalMemoryStore
import dev.orb.app.data.SettingsStore
import dev.orb.app.reminders.Notifications
import dev.orb.app.reminders.ReminderScheduler
import dev.orb.app.reminders.ReminderStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import java.io.File

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    val settings = SettingsStore(appContext, scope)
    val apiKeyStore = ApiKeyStore(appContext)
    val memory = LocalMemoryStore(File(appContext.filesDir, "orb/memory.json"), json)
    val history = HistoryStore(File(appContext.filesDir, "orb/history.json"), json)
    val reminderStore = ReminderStore(appContext)
    val scheduler = ReminderScheduler(appContext)
    val tts = TtsPlayer(appContext)
    val wavPlayer = WavPlayer(appContext)

    private val http = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 20_000
            socketTimeoutMillis = 120_000
        }
    }

    val gemini = GeminiClient(http, apiKeyStore)
    val groq = GroqClient(http, apiKeyStore)
    val geminiText = GeminiTextProvider(gemini)
    val groqText = GroqTextProvider(groq, json)
    val tools = OrbTools(http, gemini, memory, reminderStore, scheduler, json)
    val agent = OrbAgent(memory, history, tools, settings)
}

class OrbApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannel(this)
    }
}
