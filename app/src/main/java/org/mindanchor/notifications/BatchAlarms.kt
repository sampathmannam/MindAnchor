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
import java.time.ZoneId

/**
 * Release-time scheduling: exact alarm when the user has granted it,
 * inexact otherwise (a Doze-delayed batch is acceptable — docs/research/05
 * §5). Each firing releases the batch and schedules the next.
 */
object BatchAlarms {

    private const val REQUEST_CODE = 41

    fun ensureScheduled(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val next = BatchSchedule.nextRelease(LocalDateTime.now())
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
        BatchAlarms.ensureScheduled(appContext)
        // The sunset window is stored rather than hardcoded, so re-arming
        // it has to read preferences. goAsync keeps the receiver alive for
        // that read — without it the process can be torn down first and
        // the quiet hours simply never come back after a reboot.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
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
