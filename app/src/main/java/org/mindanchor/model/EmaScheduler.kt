package org.mindanchor.model

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mindanchor.R
import org.mindanchor.data.SunsetPrefs
import org.mindanchor.notifications.BatchSchedule
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Turns [EmaSchedule]'s pure math into real alarms. Ema.kt decides *when*
 * to ask and how often; everything here is only the Android glue that
 * arms those times and, when one arrives, shows a notification.
 *
 * ## Why the sunset window supplies wake and sleep
 *
 * [SunsetPrefs] already holds the one thing this app knows about when a
 * person is and is not awake: their own quiet hours. Waking hours are
 * everything outside that window, so its END becomes the wake minute and
 * its START becomes the sleep minute. Getting that backwards would put
 * every prompt inside the exact hours this feature is required to stay
 * out of.
 *
 * ## Why one alarm re-arms the rest
 *
 * Today's prompt count depends on today's label total, which only grows
 * as the day goes on, and the taper threshold in [EmaSchedule] means
 * tomorrow can legitimately ask for a different number of prompts than
 * today did. So "tomorrow's schedule" is never computed once and stored —
 * a single daily alarm, timed to the wake minute, recomputes it fresh
 * each morning. Every prompt alarm recomputes it too when it fires, so a
 * missed rearm on one day is self-healing at the next prompt rather than
 * silently ending the whole feature.
 */
object EmaScheduler {

    private const val CHANNEL_ID = "ema"
    private const val ACTION_PROMPT = "org.mindanchor.EMA_PROMPT"
    private const val ACTION_REARM = "org.mindanchor.EMA_REARM"
    private const val REQUEST_CODE_REARM = 90
    private const val REQUEST_CODE_PROMPT_BASE = 91
    private const val NOTIFICATION_ID = 95

    /** Reserved alarm slots — never more than the busiest day ever needs. */
    private const val MAX_SLOTS = EmaSchedule.LEARNING_PROMPTS

    /**
     * Recomputes today's prompts against the current label count and
     * quiet-hours window, arms whichever slots are still ahead of now,
     * clears the rest, and arms one rearm alarm for tomorrow's wake
     * minute. If check-ins are switched off, everything is cleared
     * instead.
     *
     * Idempotent: calling this twice in a row — from the settings switch,
     * from a fired alarm, from both in the same minute — leaves
     * AlarmManager in exactly the state one call would have, because
     * every slot is explicitly set or explicitly cancelled every time,
     * never left holding whatever a previous call happened to leave.
     *
     * Never throws. A denied exact-alarm permission, a missing
     * AlarmManager, an unreadable preference or anything else that can go
     * wrong here is a reason to arm nothing today, not a reason to crash
     * next to the launcher.
     */
    suspend fun ensureScheduled(context: Context) {
        runCatching { arm(context) }
    }

    /**
     * v0.20.1 round 5 follow-up: disable the
     * scheduled-EMA feature entirely. Sets
     * [MomentStore.setEnabled] to false (so
     * [ensureScheduled] will be a no-op the next
     * time it is called) and cancels every
     * pending alarm + notification.
     *
     * The historical [Moment] data is *not*
     * touched. The user can still see their past
     * check-ins in any future read surface, and
     * re-enabling the feature is a single
     * [MomentStore.setEnabled] call away (the
     * settings path that does not exist yet is
     * the v0.20.2 follow-up).
     *
     * The call site is [CheckInActivity.onSave]:
     * the first time the user accepts a check-in
     * through the new phone-unlock flow, the old
     * scheduled-EMA flow is disabled so the user
     * never receives a *second* check-in
     * notification in the same day.
     *
     * Never throws. A denied exact-alarm
     * permission or a missing AlarmManager is a
     * reason to disable silently, not crash.
     */
    suspend fun disable(context: Context) {
        runCatching {
            val appContext = context.applicationContext
            val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return@runCatching
            MomentStore(appContext).setEnabled(false)
            clearAll(appContext, alarmManager)
        }
    }

    private suspend fun arm(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val store = MomentStore(appContext)

        if (!store.enabled.first()) {
            clearAll(appContext, alarmManager)
            return
        }

        // The END of quiet hours is when the person wakes; the START is
        // when they mean to be asleep. See the class doc — reversing
        // these two would prompt exactly inside the hours this schedule
        // exists to avoid.
        val (quietStart, quietEnd) = SunsetPrefs(appContext).window()
        val wakeMinute = quietEnd.hour * 60 + quietEnd.minute
        val sleepMinute = quietStart.hour * 60 + quietStart.minute

        val today = LocalDate.now()
        // v0.25.10 (SOTA v2 bug-hunt B5): ZonedDateTime.now(zone) so the
        // "is this slot still ahead of now" comparison and the per-slot
        // ZonedDateTime.of(...) build both come from the same system
        // instant and the same zone. The pre-fix shape was a bare
        // wall-clock-now read plus a separate atZone(systemDefault()) at
        // the schedule() call site. The same system read supplies the
        // BatchSchedule.nextRelease() call for the wake-minute rearm.
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val count = store.count.first()
        val perDay = EmaSchedule.promptsPerDay(count)
        // The date, not a random source: the same day always recomputes
        // to the same scatter (idempotent), and consecutive days differ
        // (so the schedule cannot be learned and anticipated).
        val seed = today.toEpochDay().toInt()
        val times = EmaSchedule.promptTimes(wakeMinute, sleepMinute, perDay, seed)
        val canExact =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

        for (index in 0 until MAX_SLOTS) {
            val pending = promptPendingIntent(appContext, index)
            val at = times.getOrNull(index)?.let { ZonedDateTime.of(today, it, zone) }
            if (at == null || !at.isAfter(now)) {
                // Nothing for this slot today, or its moment already
                // passed. A slot going unused is normal, not an error —
                // it is cleared rather than left pointing at a stale
                // time or a time that no longer exists in today's plan.
                alarmManager.cancel(pending)
                continue
            }
            schedule(alarmManager, at, pending, canExact)
        }

        // One rearm, at the next occurrence of the wake minute, so a new
        // day's label count and a possibly-edited quiet-hours window are
        // both read fresh rather than carried over from whenever this
        // last ran.
        val rearmAt = BatchSchedule.nextRelease(now, listOf(quietEnd))
        schedule(alarmManager, rearmAt, rearmPendingIntent(appContext), canExact)
    }

    private fun schedule(
        alarmManager: AlarmManager,
        at: ZonedDateTime,
        pending: PendingIntent,
        canExact: Boolean,
    ) {
        val triggerAt = at.toInstant().toEpochMilli()
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    /** Cancels every alarm this scheduler could ever have armed. */
    private fun clearAll(context: Context, alarmManager: AlarmManager) {
        alarmManager.cancel(rearmPendingIntent(context))
        for (index in 0 until MAX_SLOTS) {
            alarmManager.cancel(promptPendingIntent(context, index))
        }
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    private fun promptPendingIntent(context: Context, index: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_PROMPT_BASE + index,
            Intent(context, EmaAlarmReceiver::class.java).setAction(ACTION_PROMPT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun rearmPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_REARM,
            Intent(context, EmaAlarmReceiver::class.java).setAction(ACTION_REARM),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * A prompt time arrived, or it is time to plan tomorrow's. Both cases
     * end the same way: the whole schedule is recomputed from scratch,
     * which is what makes either kind of alarm firing self-healing for
     * the other.
     *
     * The receiver that calls this only wraps it in goAsync's `finally`,
     * not a `catch` — so, like [ensureScheduled], this never throws.
     * This alarm can fire at any moment while the person is looking at
     * their own home screen, which is exactly when an uncaught exception
     * here would be most visible.
     */
    internal suspend fun onAlarm(context: Context, action: String?) {
        runCatching {
            if (action == ACTION_PROMPT && MomentStore(context).enabled.first()) {
                postPrompt(context)
            }
        }
        ensureScheduled(context)
    }

    /**
     * A single quiet notification that opens straight into the first
     * question. Checked against POST_NOTIFICATIONS first and a silent
     * no-op without it — exactly like
     * [org.mindanchor.friction.SessionManager.postExpiryNotification].
     *
     * One line, not a summons: a missed check-in is not a failure, so
     * this never re-posts, never escalates, and never says how many were
     * missed.
     */
    private fun postPrompt(context: Context) {
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
                context.getString(R.string.ema_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val contentIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, EmaActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            // NOTE(ci): drawable id, unverifiable without a build; the
            // same constant already ships in PulseReminder.
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            // The question itself, rather than an invented "check-in
            // ready" label — only strings that already exist may be used
            // here, and the question makes a better one-line notification
            // than a wrapper around it would.
            .setContentTitle(context.getString(R.string.ema_valence))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}

/** Fires at each armed check-in time, and once a day to plan the next. */
class EmaAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                EmaScheduler.onAlarm(appContext, intent.action)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
