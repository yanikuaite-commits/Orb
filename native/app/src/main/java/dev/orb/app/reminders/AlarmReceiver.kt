package dev.orb.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.orb.app.data.Reminder

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val text = intent.getStringExtra(EXTRA_TEXT) ?: return
        val due = intent.getDoubleExtra(EXTRA_DUE, 0.0)
        Notifications.showReminder(context, Reminder(id = id, text = text, dueAt = due))
        ReminderStore(context).remove(id)
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_TEXT = "text"
        const val EXTRA_DUE = "due"
    }
}
