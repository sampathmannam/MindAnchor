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
import java.time.ZonedDateTime
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
        // v0.25.10 (SOTA v2 bug-hunt B5): ZonedDateTime.now(zone) so the
        // "what's the next release" comparison and the alarm Instant come
        // from the same system instant. Pre-fix shape was a bare
        // wall-clock-now read plus a separate atZone(systemDefault()) —
        // a second system call that could cross a midnight or DST
        // boundary between the comparison and the conversion.
        val zone = ZoneId.systemDefault()
        val next = BatchSchedule.nextRelease(ZonedDateTime.now(zone), times)
        val triggerAt = next.toInstant().toEpochMilli()
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

/**
 * Re-arms every alarm after a reboot.
 *
 * The list used to live here and had drifted out of date — check-in
 * prompts were missing from it, so they stopped after a restart. It
 * now lives in [org.mindanchor.Alarms], which the
 * exact-alarm permission receiver shares, so there is one list to keep
 * right instead of two to keep in step.
 */
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
                org.mindanchor.Alarms.ensureAll(appContext)
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Re-arms everything the moment exact alarms become available.
 *
 * Granting the permission does nothing to alarms already scheduled — they
 * keep whatever inexact window they were armed with, in some cases for
 * the next fourteen days. Without this the person grants permission,
 * sees no change, and reasonably concludes the setting is decorative.
 *
 * Android also delivers this when the permission is *revoked*, which is
 * equally worth acting on: the alarms are then re-armed inexactly and
 * on purpose rather than lingering as exact ones the system will refuse.
 */
class ExactAlarmPermissionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) {
            return
        }
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                org.mindanchor.Alarms.ensureAll(appContext)
            } finally {
                pending.finish()
            }
        }
    }
}
