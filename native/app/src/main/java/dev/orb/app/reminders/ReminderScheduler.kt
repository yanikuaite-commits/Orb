package dev.orb.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.orb.app.data.Reminder

class ReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun scheduleAll(reminders: List<Reminder>) {
        val now = System.currentTimeMillis()
        reminders.filter { it.dueAt > 0 && (it.dueAt * 1000).toLong() > now }.forEach { reminder ->
            val triggerAt = (reminder.dueAt * 1000).toLong()
            val pending = pendingIntent(reminder)
            if (canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        }
    }

    fun cancelAll(reminders: List<Reminder>) {
        reminders.forEach { alarmManager.cancel(pendingIntent(it)) }
    }

    private fun pendingIntent(reminder: Reminder): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "dev.orb.app.REMINDER_${reminder.id}"
            putExtra(AlarmReceiver.EXTRA_ID, reminder.id)
            putExtra(AlarmReceiver.EXTRA_TEXT, reminder.text)
            putExtra(AlarmReceiver.EXTRA_DUE, reminder.dueAt)
        }
        return PendingIntent.getBroadcast(
            context,
            reminder.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
