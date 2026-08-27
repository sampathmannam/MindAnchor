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
            // Sunset switched off inside the window: the same clock that
            // justified a suspension no longer says "night", so OS Mode
            // must lift whatever it applied rather than wait for an alarm
            // that is no longer scheduled.
            org.mindanchor.osmode.OsModeController.rederiveSuspend(context)
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

                // v0.70 (Phase 1 T-1.2): all package-suspension decisions
                // are delegated to OS Mode's re-derivation, called on every
                // firing regardless of which switches brought us here —
                // when nothing is opted in it is a few cheap reads ending
                // in a no-op, but skipping it on any path risks a stale
                // suspension nobody will lift. The window stays the single
                // source of truth: rederive reads the grant, the explicit
                // opt-in (default OFF — provisioning a phone never implies
                // handing over its nights), and the clock, then suspends
                // or lifts accordingly. A process that died mid-window
                // converges on the same answer the next time anything
                // fires.
                //
                // The old gating note still holds one level down: someone
                // who switched on only the grey screen asked for a
                // colourless phone, and rederive honours that by requiring
                // both the quiet hours *and* an explicit opt-in before it
                // ever suspends anything.
                org.mindanchor.osmode.OsModeController.rederiveSuspend(appContext)
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
