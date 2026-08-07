package org.mindanchor.sunset

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.mindanchor.data.SunsetPrefs
import org.mindanchor.notifications.BatchSchedule
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Sunset mode: during the wind-down window the interruption filter goes to
 * priority-only — starred contacts and repeat callers still ring, which is
 * exactly the "designated humans" tier (docs/research/05 §6). On Android
 * 15+ the OS converts this into an implicit per-app mode automatically.
 */
object SunsetController {

    private const val ACTION_START = "org.mindanchor.SUNSET_START"
    private const val ACTION_END = "org.mindanchor.SUNSET_END"

    /** Overnight-aware window test. */
    fun isInWindow(now: LocalTime, start: LocalTime, end: LocalTime): Boolean =
        if (start <= end) {
            now >= start && now < end
        } else {
            now >= start || now < end
        }

    suspend fun onToggled(context: Context, enabled: Boolean) {
        if (enabled) {
            ensureScheduled(context)
            if (isInWindow(LocalTime.now(), SunsetPrefs.START, SunsetPrefs.END)) {
                applyFilter(context, priorityOnly = true)
            }
        } else {
            applyFilter(context, priorityOnly = false)
        }
    }

    fun ensureScheduled(context: Context) {
        schedule(context, ACTION_START, SunsetPrefs.START, requestCode = 61)
        schedule(context, ACTION_END, SunsetPrefs.END, requestCode = 62)
    }

    private fun schedule(context: Context, action: String, time: LocalTime, requestCode: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val next = BatchSchedule.nextRelease(LocalDateTime.now(), listOf(time))
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, SunsetReceiver::class.java).setAction(action),
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

    fun applyFilter(context: Context, priorityOnly: Boolean) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (!manager.isNotificationPolicyAccessGranted) return
        if (priorityOnly) {
            // Say explicitly who may still get through, rather than
            // inheriting whatever the phone's existing Do Not Disturb
            // happens to allow. The small hours are exactly when someone in
            // distress needs a person to be able to reach them, so starred
            // contacts, anyone who calls twice, and alarms always ring.
            runCatching {
                manager.notificationPolicy = NotificationManager.Policy(
                    NotificationManager.Policy.PRIORITY_CATEGORY_CALLS or
                        NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS or
                        NotificationManager.Policy.PRIORITY_CATEGORY_MESSAGES or
                        NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS,
                    NotificationManager.Policy.PRIORITY_SENDERS_STARRED,
                    NotificationManager.Policy.PRIORITY_SENDERS_STARRED,
                )
            }
        }
        runCatching {
            manager.setInterruptionFilter(
                if (priorityOnly) {
                    NotificationManager.INTERRUPTION_FILTER_PRIORITY
                } else {
                    NotificationManager.INTERRUPTION_FILTER_ALL
                },
            )
        }
    }

    internal fun handleAlarm(context: Context, action: String?) {
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val prefs = SunsetPrefs(appContext)
            // Quiet hours and colourless hours are independent switches, so
            // each is checked on its own. Someone may want a phone that
            // stops interrupting without one that stops being colourful.
            val quietHours = prefs.isEnabled()
            val greyNights = prefs.isGrayscaleAtNight()
            if (quietHours || greyNights) {
                val starting = action == ACTION_START
                if (quietHours) applyFilter(appContext, priorityOnly = starting)
                if (greyNights) org.mindanchor.grayscale.Grayscale.set(appContext, starting)
                ensureScheduled(appContext)
            }
        }
    }
}

class SunsetReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        SunsetController.handleAlarm(context, intent.action)
    }
}
