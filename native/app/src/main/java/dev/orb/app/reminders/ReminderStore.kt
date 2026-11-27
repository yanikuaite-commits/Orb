package dev.orb.app.reminders

import android.content.Context
import dev.orb.app.data.Reminder
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ReminderStore(context: Context) {
    private val prefs = context.getSharedPreferences("orb_reminders", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun all(): List<Reminder> = runCatching {
        json.decodeFromString<List<Reminder>>(prefs.getString("items", "[]").orEmpty())
    }.getOrDefault(emptyList())

    fun save(items: List<Reminder>) {
        prefs.edit().putString("items", json.encodeToString(items)).apply()
    }

    fun remove(id: String) {
        save(all().filterNot { it.id == id })
    }
}
