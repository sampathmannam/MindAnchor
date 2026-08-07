package org.mindanchor.pulse

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.mindanchor.R
import org.mindanchor.data.db.AnchorDatabase

/**
 * One gentle reminder 14 days after each completed pulse — the WHO-5's
 * natural cadence. Missing it never nags; the next reminder only arms when
 * the user actually takes the next pulse.
 */
object PulseReminder {

    private const val CHANNEL_ID = "pulse"
    private const val REQUEST_CODE = 71
    private const val INTERVAL_DAYS = 14L
    private const val INTERVAL_INTERVAL_MILLIS = INTERVAL_DAYS * 24 * 3_600_000

    /** Long enough that a reboot never turns into an instant reminder. */
    private const val OVERDUE_GRACE_MILLIS = 3_600_000L

    fun scheduleNext(context: Context) {
        armAt(context, System.currentTimeMillis() + INTERVAL_INTERVAL_MILLIS)
    }

    /**
     * Puts the reminder back after a reboot, which otherwise loses it
     * silently — an alarm does not survive a restart, and nothing was
     * re-arming this one, so a fortnightly check-in simply stopped
     * arriving until the person next opened the screen themselves.
     *
     * Deliberately not [scheduleNext]. That one counts fourteen days from
     * *now*, so calling it on boot would push the reminder out afresh
     * every restart and a frequently-rebooted phone would never reach it.
     * This counts from the last pulse actually taken, which is the date
     * the cadence is supposed to hang off.
     *
     * No pulse ever taken means nothing to re-arm: the first reminder only
     * exists once somebody has answered once, which is the existing
     * design and not something to change here.
     */
    suspend fun ensureScheduled(context: Context) {
        val last = runCatching {
            AnchorDatabase.get(context).pulses().latest()
        }.getOrNull() ?: return
        val due = last.takenAt + INTERVAL_INTERVAL_MILLIS
        // Already overdue, because the phone was off when it should have
        // fired. Restore it shortly rather than firing the instant the
        // device finishes booting — "missing it never nags" is the rule
        // this feature is built on.
        armAt(context, maxOf(due, System.currentTimeMillis() + OVERDUE_GRACE_MILLIS))
    }

    private fun armAt(context: Context, triggerAt: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, PulseReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val canExact =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun postReminder(context: Context) {
        if (ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.pulse_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, PulseActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(context.getString(R.string.pulse_reminder_title))
            .setContentText(context.getString(R.string.pulse_reminder_text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(REQUEST_CODE, notification)
    }
}

class PulseReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        PulseReminder.postReminder(context.applicationContext)
    }
}
