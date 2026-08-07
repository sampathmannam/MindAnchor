package org.mindanchor.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import org.mindanchor.data.NotificationPrefs
import java.time.ZoneId

/**
 * Release-time scheduling: exact alarm when the user has granted it,
 * inexact otherwise (a Doze-delayed batch is acceptable — docs/research/05
 * §5). Each firing releases the batch and schedules the next.
 */
object BatchAlarms {

    private const val REQUEST_CODE = 41

    /**
     * Arms the next release.
     *
     * Suspend because the release times are now the person's own rather
     * than three constants, and reading them means reading DataStore. The
     * cost is that every caller has to be in a coroutine already — see
     * [BootReceiver], where the call had to move inside the `goAsync`
     * block it was sitting next to, since a receiver whose process is torn
     * down mid-read simply never re-arms.
     */
    suspend fun ensureScheduled(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val times = runCatching { NotificationPrefs(context).currentReleaseTimes() }
            .getOrDefault(BatchSchedule.DEFAULT_TIMES)
        val next = BatchSchedule.nextRelease(LocalDateTime.now(), times)
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, BatchReleaseReceiver::class.java),
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
}

/** Fires at each batch release time. */
class BatchReleaseReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                BatchReleaser.releaseNow(appContext)
                BatchAlarms.ensureScheduled(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/** Re-arms release and sunset alarms after reboot. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val appContext = context.applicationContext
        // Every one of these reads stored preferences now — the release
        // times as much as the sunset window — so all of them have to run
        // inside goAsync. Without it the process can be torn down first
        // and none of the alarms ever come back after a reboot.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                BatchAlarms.ensureScheduled(appContext)
                org.mindanchor.sunset.SunsetController.ensureScheduled(appContext)
                // The nightly report's alarm does not survive a reboot
                // either, and unlike a batch release nobody would notice
                // it missing — it would simply never run again.
                org.mindanchor.report.ReportScheduler.ensureScheduled(appContext)
            } finally {
                pending.finish()
            }
        }
    }
}
