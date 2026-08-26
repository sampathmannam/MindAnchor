package org.mindanchor.friction

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mindanchor.data.FrictionPrefs

/**
 * Phase 1 T-1.5 — first-unlock-of-the-day detector.
 *
 * The system fires `ACTION_USER_PRESENT` once per successful
 * unlock (i.e. once the keyguard is dismissed, not every time
 * the screen turns on). The receiver records the timestamp of
 * the first such event of the *local* day; subsequent events
 * do nothing.
 *
 * ## Why a separate receiver (not folded into Alarms.kt)
 *
 * Alarms.kt re-arms time-based side effects. The first-unlock
 * timestamp is an *event* the system fires, not a scheduled
 * alarm. Different lifecycle, different cadence: the receiver
 * can fire many times in a day, only one of which the data
 * layer cares about. Putting it on Alarms.kt would make the
 * re-arm list misleadingly long.
 *
 * ## Why `goAsync()`
 *
 * Reading and writing DataStore is a suspending operation.
 * The receiver's process can be torn down after `onReceive`
 * returns; without `goAsync`, the write can be lost. The
 * 10-second budget is plenty for a single DataStore edit.
 *
 * ## Registration: runtime, not manifest
 *
 * ACTION_USER_PRESENT is an implicit broadcast, and since the Android 8.0
 * background execution limits a manifest-declared receiver targeting API 26+
 * is never delivered implicit broadcasts — it is not in the exemption list
 * (developer.android.com/develop/background-work/background-tasks/broadcasts/
 * broadcast-exceptions). A manifest entry would look wired and be dead.
 * The receiver is therefore registered at runtime by
 * [AppWatchService.onServiceConnected] and unregistered in
 * [AppWatchService.onDestroy]. That lifecycle is also exactly the window in
 * which the friction gate this timestamp feeds can act: with the watcher
 * off, morning protection has no enforcer anyway.
 *
 * Because the registration is dynamic, no export flag is needed for spoof
 * protection beyond ContextCompat.RECEIVER_NOT_EXPORTED: USER_PRESENT is a
 * protected system broadcast, so third-party apps cannot send it at us,
 * and NOT_EXPORTED keeps it that way while the system delivery is unaffected.
 */
class FirstUnlockReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_PRESENT) return
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        scope.launch {
            try {
                recordIfNewDay(appContext, System.currentTimeMillis())
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Records [now] as the first-unlock timestamp of the day
     * if the existing timestamp is from a different local
     * date (or unset).
     *
     * The zone is supplied so a test can pin the local clock.
     * The default is the device's system zone, which is
     * correct for the live receiver.
     *
     * Exposed for direct test access: the system instantiates
     * the receiver for the broadcast, but tests construct the
     * class and call this method directly to avoid the
     * permission dance.
     */
    suspend fun recordIfNewDay(
        context: Context,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        val prefs = FrictionPrefs(context)
        val previous = prefs.morningProtectionLastFirstUnlockEpochMillis.first()
        val today = LocalDate.now(zone)
        val previousDate = if (previous == 0L) {
            null
        } else {
            Instant.ofEpochMilli(previous).atZone(zone).toLocalDate()
        }
        if (previousDate == today) {
            // Already recorded today's first unlock.
            return
        }
        prefs.setMorningProtectionLastFirstUnlock(now)
    }
}
