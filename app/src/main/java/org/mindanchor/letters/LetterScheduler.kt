package org.mindanchor.letters

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mindanchor.R
import org.mindanchor.narrate.ModelStore
import org.mindanchor.narrate.ModelSlot
import org.mindanchor.report.PatternFinder

/**
 * Arms the daily letter, and writes one when the alarm fires.
 *
 * Same shape as [org.mindanchor.model.EmaScheduler]: the time
 * is decided here (the user picked it; the worker does not
 * second-guess), the work happens in a [BroadcastReceiver] so
 * the AlarmManager can hand the launcher's process off to the
 * OS scheduler, and every firing re-arms the next one — nothing
 * here ever leaves AlarmManager holding nothing, which is the
 * failure that ends a feature silently instead of loudly.
 *
 * ## Why one alarm
 *
 * The letter is one a day. A single daily alarm at the
 * user-chosen time is the simplest thing that can possibly work
 * and has the fewest moving parts: no day-of-week filter, no
 * skip-if-already-done (the [LetterStore] deduplicates by date
 * on save), no retry loop. The rearm is unconditional, so even
 * a rearm that fires while the user is reading yesterday's
 * letter does the right thing — runs today, schedules tomorrow.
 *
 * ## Why nothing here throws
 *
 * This fires at 8 AM (the default) with nobody watching. A
 * missing model, a corrupt store, a DataStore write that did not
 * land — none of them is worth a crash, and a day with no
 * letter is the same honest outcome as a day with one. Every
 * path is wrapped, and the next alarm is armed in a `finally`
 * so even an unexpected failure cannot end the feature.
 */
object LetterScheduler {

    private const val CHANNEL_ID = "letters"
    private const val ACTION_FIRE = "org.mindanchor.LETTER_FIRE"
    /**
     * Action used by the letter notification's contentIntent. Read by
     * HomeActivity to route to the letter reader.
     */
    const val ACTION_OPEN_LETTER = "org.mindanchor.letters.OPEN_LETTER"
    private const val REQUEST_CODE = 80
    private const val NOTIFICATION_ID = 85

    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Re-arms the daily alarm at the user's chosen time. Idempotent:
     * calling twice in a row leaves AlarmManager in exactly the
     * same state. Cancels any previous alarm first.
     *
     * If the user has not enabled the feature, or the model is
     * not on file, or the model is too large to run on this phone,
     * the alarm is cleared and the feature sits silent — same
     * gating as the existing night report.
     *
     * Never throws.
     */
    suspend fun ensureScheduled(context: Context) {
        runCatching {
            val appContext = context.applicationContext
            val store = LetterStore(appContext)
            val enabled = store.enabled.first()
            val hasModel = ModelStore.hasModel(appContext)
            val fit = ModelStore.fit(appContext)
            val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return@runCatching
            if (!enabled || !hasModel || !ModelSlot.runnable(fit)) {
                clear(appContext, alarmManager)
                return@runCatching
            }
            val (hour, minute) = store.time.first()
            val now = LocalDateTime.now()
            var target = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
            if (!target.isAfter(now)) target = target.plusDays(1)
            schedule(appContext, alarmManager, target)
        }
    }

    private fun schedule(context: Context, alarmManager: AlarmManager, at: LocalDateTime) {
        val triggerAt = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pending = pendingIntent(context)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val canExact = alarmManager.canScheduleExactAlarms()
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    private fun clear(context: Context, alarmManager: AlarmManager) {
        alarmManager.cancel(pendingIntent(context))
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, LetterAlarmReceiver::class.java).setAction(ACTION_FIRE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * The alarm fired. Generate a letter for today (or for the
     * day the alarm was scheduled against, when the user changed
     * the time), save it, post a notification, and rearm tomorrow.
     *
     * Never throws. A missing model, a sparse week, a generation
     * that [NarrationGuard] rejects — all of those are a quiet
     * day, not a failure.
     */
    internal suspend fun onFire(context: Context) {
        val appContext = context.applicationContext
        runCatching {
            val store = LetterStore(appContext)
            val enabled = store.enabled.first()
            if (!enabled) {
                ensureScheduled(appContext)
                return@runCatching
            }
            val week = WeekDataCollector(appContext).collectLastWeek()
            val writer = LetterWriter(appContext)
            val body = writer.write(week)
            if (body != null) {
                val date = LocalDate.now()
                store.save(Letter(date = date, body = body))
                postNotification(appContext, date, body)
            }
        }
        ensureScheduled(appContext)
    }

    private fun postNotification(context: Context, date: LocalDate, body: String) {
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
                context.getString(R.string.letters_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        val openIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, org.mindanchor.HomeActivity::class.java)
                .setAction(ACTION_OPEN_LETTER)
                .putExtra("letter_date", date.toString()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // First 1-2 lines, the spec's preview shape. The launcher
        // shows the full letter in the inbox; the notification
        // preview is enough to decide whether to open.
        val preview = body.lineSequence().take(2).joinToString(separator = "\n")
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(context.getString(R.string.letters_notification_title))
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}

/**
 * The receiver that AlarmManager hands the alarm to. Lives at
 * the package scope because it must be addressable by an
 * explicit [Intent] component (AlarmManager's PendingIntent
 * pattern); it does not appear in [AndroidManifest.xml]
 * because the only call site is the alarm.
 */
class LetterAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        LetterScheduler.scope.launch {
            try {
                LetterScheduler.onFire(context)
            } finally {
                pending.finish()
            }
        }
    }
}
