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
import kotlinx.coroutines.flow.first
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

    /**
     * Overnight-aware window test.
     *
     * Delegates to [SunsetPrefs.isInWindow], which is the single definition
     * of what "inside the window" means. Kept here so existing callers and
     * tests need not move.
     */
    fun isInWindow(now: LocalTime, start: LocalTime, end: LocalTime): Boolean =
        SunsetPrefs.isInWindow(now, start, end)

    suspend fun onToggled(context: Context, enabled: Boolean) {
        if (enabled) {
            ensureScheduled(context)
            if (SunsetPrefs(context).isQuietHour()) {
                applyFilter(context, priorityOnly = true)
            }
        } else {
            applyFilter(context, priorityOnly = false)
        }
    }

    /**
     * Re-arms both alarms at the person's own window.
     *
     * Suspending because the window is stored now rather than hardcoded.
     * Call it again after changing the times: the alarms already sitting
     * with AlarmManager point at the old ones, and nothing else will move
     * them.
     */
    suspend fun ensureScheduled(context: Context) {
        val (start, end) = SunsetPrefs(context).window()
        schedule(context, ACTION_START, start, requestCode = 61)
        schedule(context, ACTION_END, end, requestCode = 62)
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
        // v0.25.9 (lint sweep): SDK_INT < S is always false. Simplify.
        val canExact = alarmManager.canScheduleExactAlarms()
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

                // Gated on quietHours specifically, not on the pair above.
                // Someone who switched on only the grey screen asked for a
                // colourless phone, not one that makes their apps refuse to
                // open — and finding out at 22:00 that a colour setting had
                // started suspending things would be the worst possible way
                // to learn what this app does.
                //
                // Where it does apply, quiet hours are enforced rather than
                // suggested: nothing is suspended that SuspensionGuard has
                // not cleared, and everything is lifted at the end of the
                // window. Clearing passes the whole chosen set rather than
                // the filtered one, because lifting a suspension that was
                // never applied does nothing, and missing one would leave
                // an app shut until somebody noticed.
                if (quietHours && org.mindanchor.admin.DeviceOwner.isDeviceOwner(appContext)) {
                    val friction = org.mindanchor.data.FrictionPrefs(appContext)
                    val chosen = friction.flaggedApps.first()
                    val alwaysOpen = friction.alwaysOpen.first()
                    if (starting) {
                        org.mindanchor.admin.DeviceOwner.apply(appContext, chosen, alwaysOpen)
                    } else {
                        org.mindanchor.admin.DeviceOwner.clear(appContext, chosen)
                    }
                }
                ensureScheduled(appContext)
            }

            // v0.70 (master plan T-1.2): OS Mode adds the person's own
            // feed-app list to what closes through the window. Sync runs
            // on every alarm — START applies it, END lifts it, and either
            // action heals any state that drifted out of step with the
            // window. Deliberately outside the quietHours/greyNights block:
            // turning a switch off must also be able to lift what an
            // earlier window closed. Never throws.
            runCatching { org.mindanchor.admin.OsMode.sync(appContext) }
        }
    }
}

class SunsetReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        SunsetController.handleAlarm(context, intent.action)
    }
}
