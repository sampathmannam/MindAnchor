package org.mindanchor.backup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * When the nightly Google Drive backup may run, and what to do when it
 * may not.
 *
 * Same shape as [org.mindanchor.report.ReportSchedule] — [Decision], the
 * charging-and-not-interactive gate, the bounded retry window, and the
 * [Instant] × [ZoneId] handling that re-reads the zone at the moment the
 * next alarm is armed rather than trusting a cached offset (so a DST
 * shift or a timezone change never pushes the alarm to a wall-clock
 * nobody picked) — see that object's KDoc for the full rationale, all of
 * which applies here unchanged. Kept as its own object rather than a
 * shared call into that one so the backup feature carries no dependency
 * on the report package for what is otherwise a coincidence of two
 * features both wanting "once, late at night, while charging and idle".
 */
object DriveSyncSchedule {

    /**
     * Two in the morning — staggered an hour before the nightly report's
     * three, so the two nightly jobs this app runs do not both wake the
     * radio in the same minute.
     */
    const val RUN_HOUR = 2

    /**
     * Same reasoning as [org.mindanchor.report.ReportSchedule.RETRY_HOURS]:
     * past this the person is plausibly awake and using the phone, and a
     * backup that lands at lunchtime is worth less than the battery spent
     * chasing one now instead of waiting for tonight.
     */
    const val RETRY_HOURS = 5

    enum class Decision {
        /** Conditions are met and tonight's backup has not run yet. */
        RUN,

        /** Not yet, but still inside tonight's window — try again in an hour. */
        RETRY,

        /** Nothing more tonight. Arm the next one. */
        WAIT_FOR_TOMORROW,
    }

    /** What to do at this moment. See [org.mindanchor.report.ReportSchedule.decide] — identical logic. */
    fun decide(
        charging: Boolean,
        interactive: Boolean,
        hourOfDay: Int,
        lastRunDay: String?,
        today: String,
    ): Decision = when {
        lastRunDay == today -> Decision.WAIT_FOR_TOMORROW
        charging && !interactive -> Decision.RUN
        hourOfDay in RUN_HOUR until (RUN_HOUR + RETRY_HOURS) -> Decision.RETRY
        else -> Decision.WAIT_FOR_TOMORROW
    }

    /** When to set the next alarm. See [org.mindanchor.report.ReportSchedule.nextRun] — identical logic. */
    fun nextRun(now: Instant, zone: ZoneId, decision: Decision): Instant = when (decision) {
        Decision.RETRY -> now.plusSeconds(3600)
        Decision.RUN, Decision.WAIT_FOR_TOMORROW -> nextNight(now, zone)
    }

    private fun nextNight(now: Instant, zone: ZoneId): Instant {
        val nowLocal = now.atZone(zone)
        val tonightLocal = nowLocal.toLocalDate().atTime(RUN_HOUR, 0).atZone(zone)
        val tonightInstant = tonightLocal.toInstant()
        return if (tonightInstant.isAfter(now)) tonightInstant
        else nowLocal.toLocalDate().plusDays(1).atTime(RUN_HOUR, 0).atZone(zone).toInstant()
    }
}

/**
 * Arms the nightly Google Drive backup, and runs it when the alarm fires.
 *
 * Same division of labour as [org.mindanchor.report.ReportScheduler]:
 * [DriveSyncSchedule] decides whether to run and when to try next, and
 * this object is the Android glue plus the [BackupScheduler.backupAll]
 * call itself.
 *
 * Nothing here throws: this can fire at two in the morning with nobody
 * watching. A missing Drive scope, a signed-out account, a network
 * hiccup — none of it is worth a crash, and the next alarm is armed in a
 * `finally` so a failure here can never leave the schedule silently dead.
 */
object DriveNightlySync {

    private const val ACTION_SYNC = "org.mindanchor.DRIVE_SYNC_RUN"
    private const val REQUEST_CODE = 97

    /**
     * Arms, or re-arms, the nightly alarm. Safe to call every time the
     * toggle is switched on, on boot ([org.mindanchor.Alarms.ensureAll]),
     * and on every app start — it always sets the one alarm under the one
     * request code, so repeated calls replace it rather than accumulate.
     * Cancels the alarm instead if the toggle is off.
     */
    suspend fun ensureScheduled(context: Context) {
        runCatching {
            val appContext = context.applicationContext
            val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
            if (!BackupPrefs(appContext).driveNightlySyncEnabled.first()) {
                alarmManager.cancel(pendingIntent(appContext))
                return
            }
            armNext(appContext, alarmManager, DriveSyncSchedule.Decision.WAIT_FOR_TOMORROW)
        }
    }

    /** Switched off from settings: no nightly backup until asked for again. */
    fun cancel(context: Context) {
        runCatching {
            val appContext = context.applicationContext
            appContext.getSystemService(AlarmManager::class.java)
                ?.cancel(pendingIntent(appContext))
        }
    }

    /**
     * Backs up right now, on demand, whatever the hour — the Settings
     * "Back up now" button's path. Deliberately skips
     * [DriveSyncSchedule.decide]: a person pressing a button IS the
     * charging-and-idle question, answered.
     */
    suspend fun runNow(context: Context): BackupScheduler.BackupAllResult = backupNow(context.applicationContext)

    /** The alarm fired. Decide, maybe back up, and always arm the next one. */
    internal suspend fun onAlarm(context: Context) {
        val appContext = context.applicationContext
        var decision = DriveSyncSchedule.Decision.WAIT_FOR_TOMORROW
        try {
            val prefs = BackupPrefs(appContext)
            if (!prefs.driveNightlySyncEnabled.first()) return
            val now = LocalDateTime.now()
            decision = DriveSyncSchedule.decide(
                charging = isCharging(appContext),
                interactive = isInteractive(appContext),
                hourOfDay = now.hour,
                lastRunDay = runCatching { prefs.lastSyncDay.first() }.getOrNull(),
                today = LocalDate.now().toString(),
            )
            if (decision == DriveSyncSchedule.Decision.RUN) {
                runCatching { backupNow(appContext) }
                runCatching { prefs.setLastSyncDay(LocalDate.now().toString()) }
            }
        } catch (_: Throwable) {
            // Deliberately swallowed. See the class KDoc: nothing this does
            // overnight is worth a crash, and the finally below is what
            // keeps tomorrow's alarm armed regardless.
        } finally {
            runCatching {
                val alarmManager = appContext.getSystemService(AlarmManager::class.java)
                if (alarmManager != null) armNext(appContext, alarmManager, decision)
            }
        }
    }

    /**
     * Builds the four Drive targets and runs [BackupScheduler.backupAll].
     * Skips the attempt entirely, returning an all-zero result, when
     * nobody is signed in — the nightly alarm can fire for years after a
     * "Forget this account" tap, and every one of those nights would
     * otherwise spend the retry window on appends that only fail once
     * they reach the network.
     */
    private suspend fun backupNow(context: Context): BackupScheduler.BackupAllResult {
        val auth = GoogleDriveAuth(context)
        if (auth.signedInEmailFlow.first() == null) return EMPTY_RESULT
        val client = OkHttpClient()
        val scheduler = BackupScheduler(
            context = context,
            targets = BackupTargets(
                notes = GoogleDriveBackupTarget(client = client, auth = auth, type = ContentType.Notes),
                letters = GoogleDriveBackupTarget(client = client, auth = auth, type = ContentType.Letters),
                checkIns = GoogleDriveBackupTarget(client = client, auth = auth, type = ContentType.CheckIns),
                wellness = GoogleDriveBackupTarget(
                    client = client,
                    auth = auth,
                    type = ContentType.WellnessReadings,
                ),
            ),
        )
        return scheduler.backupAll()
    }

    private val EMPTY_RESULT = BackupScheduler.BackupAllResult(0, 0, 0, 0, 0, 0, 0, 0)

    private fun armNext(context: Context, alarmManager: AlarmManager, decision: DriveSyncSchedule.Decision) {
        val triggerAt = DriveSyncSchedule
            .nextRun(Instant.now(), ZoneId.systemDefault(), decision)
            .toEpochMilli()
        val pending = pendingIntent(context)
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, DriveSyncAlarmReceiver::class.java).setAction(ACTION_SYNC),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** See [org.mindanchor.report.ReportScheduler.isCharging] — identical reasoning. */
    private fun isCharging(context: Context): Boolean = runCatching {
        val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = status?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        plugged != 0
    }.getOrDefault(false)

    /** See [org.mindanchor.report.ReportScheduler.isInteractive] — identical reasoning. */
    private fun isInteractive(context: Context): Boolean = runCatching {
        context.getSystemService(PowerManager::class.java)?.isInteractive ?: true
    }.getOrDefault(true)
}

/** Fires nightly, and hourly through the retry window when conditions are not met. */
class DriveSyncAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                DriveNightlySync.onAlarm(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
