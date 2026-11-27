package dev.orb.app

import android.app.Application
import android.content.Context
import dev.orb.app.audio.TtsPlayer
import dev.orb.app.audio.WavPlayer
import dev.orb.app.data.OrbApi
import dev.orb.app.data.SettingsStore
import dev.orb.app.reminders.Notifications
import dev.orb.app.reminders.ReminderScheduler
import dev.orb.app.reminders.ReminderStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settings = SettingsStore(appContext, scope)
    val api = OrbApi(settings)
    val reminderStore = ReminderStore(appContext)
    val scheduler = ReminderScheduler(appContext)
    val tts = TtsPlayer(appContext)
    val wavPlayer = WavPlayer(appContext)
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
