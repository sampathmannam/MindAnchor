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
import kotlinx.coroutines.flow.first
import org.mindanchor.R
import org.mindanchor.data.db.AnchorDatabase

/**
 * One gentle reminder, scheduled at the cadence the user's own
 * response pattern has earned.
 *
 * `docs/research/11` reviewed the primary evidence (Lally 2010; WHO 1998
 * DepCare; Topp 2015; Williams 2021 JMIR mEMA; Fogg 2019; Wood 2019;
 * Gardner/Lally 2023) and concluded the prior single constant
 * `INTERVAL_DAYS = 14L` is wrong on the ceiling: a fixed fortnightly
 * schedule cannot *form* the check-in habit (median 66 days per Lally)
 * and Wood's "double law of habit" predicts the reminder itself
 * becomes wallpaper. The replacement is a 7 → 10 → 14-day taper,
 * conditioned on the user's own response streak, with a bounce-back
 * to 7 days after two consecutive misses.
 *
 * The cadence is computed by [PulseCadence.cadenceDays] from a pure
 * function of (completedCount, recentOutcomes, consecutiveMisses).
 * What this class does is the *plumbing* — alarm arming, boot
 * recovery, post-notification — the pure function holds the design
 * decision.
 */
object PulseReminder {

    private const val CHANNEL_ID = "pulse"
    private const val REQUEST_CODE = 71

    /** Long enough that a reboot never turns into an instant reminder. */
    private const val OVERDUE_GRACE_MILLIS = 3_600_000L

    /**
     * Arms the next reminder at the cadence [PulseCadence] picks for
     * this user's own response pattern. Called whenever a pulse is
     * saved; reads the full history each time, so the reminder always
     * reflects the user's most-recent state, not a stale count from
     * the last boot.
     */
    suspend fun scheduleNext(context: Context) {
        val triggerAt = nextTriggerAt(context, anchor = System.currentTimeMillis())
        armAt(context, triggerAt)
    }

    /**
     * Puts the reminder back after a reboot, which otherwise loses it
     * silently — an alarm does not survive a restart, and nothing was
     * re-arming this one, so a check-in simply stopped arriving until
     * the person next opened the screen themselves.
     *
     * Deliberately not [scheduleNext]. That one counts from *now*,
     * so calling it on boot would push the reminder out afresh every
     * restart and a frequently-rebooted phone would never reach it.
     * This counts from the last pulse actually taken, which is the
     * anchor the cadence is supposed to hang off.
     *
     * No pulse ever taken means nothing to re-arm: the first reminder
     * only exists once somebody has answered once, which is the
     * existing design and not something to change here.
     */
    suspend fun ensureScheduled(context: Context) {
        val last = runCatching {
            AnchorDatabase.get(context).pulses().latest()
        }.getOrNull() ?: return
        val due = nextTriggerAt(context, anchor = last.takenAt)
        // Already overdue, because the phone was off when it should
        // have fired. Restore it shortly rather than firing the
        // instant the device finishes booting — "missing it never
        // nags" is the rule this feature is built on.
        armAt(context, maxOf(due, System.currentTimeMillis() + OVERDUE_GRACE_MILLIS))
    }

    /**
     * When the next reminder should fire, given the anchor time (either
     * the last pulse taken, or now, depending on caller).
     *
     * Pulled out so tests can call it without a Context.
     */
    private suspend fun nextTriggerAt(context: Context, anchor: Long): Long {
        val dao = runCatching { AnchorDatabase.get(context).pulses() }.getOrNull()
            ?: return anchor + PulseCadence.EARLY_DAYS * 24 * 3_600_000L
        val all = runCatching { dao.history().first() }.getOrNull() ?: return anchor + PulseCadence.EARLY_DAYS * 24 * 3_600_000L
        // Map the actual pulse history to a boolean list (true =
        // present), oldest first. The DAO returns newest first; we
        // reverse it here so [PulseCadence.recentOutcomes] sees the
        // correct order.
        val outcomes = all.asReversed().map { true }
        val completedCount = all.size
        val consecutiveMisses = PulseCadence.consecutiveMissesAtEnd(outcomes)
        val days = PulseCadence.cadenceDays(completedCount, outcomes, consecutiveMisses)
        return anchor + days * 24 * 3_600_000L
    }

    private fun armAt(context: Context, triggerAt: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, PulseReminderReceiver::class.java),
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
