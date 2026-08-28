package org.mindanchor.model

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mindanchor.data.CheckInPrefs

/**
 * The phone-unlock trigger for the v0.20.1
 * check-in. Receives `ACTION_USER_PRESENT` (which
 * fires when the user unlocks the phone) and
 * launches [CheckInActivity] if the engine says
 * one should fire.
 *
 * ## Registration: runtime, not manifest
 *
 * v0.70+ fix: this receiver was declared in the
 * manifest with a USER_PRESENT intent filter, but
 * ACTION_USER_PRESENT is an implicit broadcast and
 * manifest-declared receivers targeting API 26+ are
 * never delivered it (it is not in the exemption
 * list) — the trigger has been silently dead on
 * every device this app supports. It is now
 * registered at runtime by
 * [org.mindanchor.friction.AppWatchService], whose
 * accessibility-service lifetime is the window in
 * which launching a full-screen moment is possible
 * anyway.
 *
 * ## Why ACTION_USER_PRESENT
 *
 * The brief is explicit: the user wants the
 * check-in to be a full-screen moment that
 * *pushes* them to fill it, triggered on phone
 * unlock. `ACTION_USER_PRESENT` is the standard
 * system broadcast for "the user has just
 * authenticated and is now using the phone." It
 * is *not* fired when the screen simply turns on
 * (that's `ACTION_SCREEN_ON`); only on the
 * actual user-present event.
 *
 * ## Why a separate rate-limit
 *
 * The engine ([CheckInEngine]) is the gate: it
 * enforces the 90-min minimum inter-prompt
 * interval, the 4-prompt/day soft cap, and the
 * 3-consecutive-rejection auto-pause. The
 * trigger's only job is to *ask* the engine
 * "should I fire right now?" — the engine
 * decides.
 *
 * ## Why a process-scoped rate-limit holder
 *
 * v0.20.1 round 5 follow-up: the rate-limit is
 * held in [CheckInRateLimitHolder] (an in-memory
 * Kotlin `object`) so the consecutive-rejection
 * counter and the daily cap persist across
 * `ACTION_USER_PRESENT` events within the same
 * process. Without the holder, every phone
 * unlock would create a fresh `CheckInRateLimit`
 * and the 3-rejection auto-pause could never
 * trigger. App restart resets the holder; the
 * brief accepted this trade-off ("the launcher
 * prefers a missed check-in over a permanent
 * record").
 *
 * ## Why we don't post a notification
 *
 * The brief is explicit: the user wants the
 * check-in to be a full-screen moment, not a
 * notification. We launch the activity directly
 * with the `FLAG_ACTIVITY_NEW_TASK` flag and the
 * `setShowWhenLocked` / `setTurnScreenOn` flags
 * the activity sets on its own window.
 */
class CheckInTrigger : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_PRESENT) return
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val prefs = CheckInPrefs(context.applicationContext)
                val state = prefs.checkIns.first()

                val now = System.currentTimeMillis()
                // Use the process-scoped rate-limit
                // holder. The trigger and the
                // activity share the same in-memory
                // state, so a consecutive-rejection
                // counter carries across phone
                // unlocks within the same process.
                val rateLimit = CheckInRateLimitHolder.state
                val shouldFire = CheckInEngine.shouldFire(
                    rateLimit = rateLimit,
                    state = state,
                    nowMillis = now,
                )

                if (shouldFire) {
                    launchCheckIn(context.applicationContext)
                }
            } catch (e: Exception) {
                // Fire-and-forget trigger; a failure
                // here costs this one check-in, never
                // the launcher sitting behind it.
                Log.w(TAG, "check-in trigger failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun launchCheckIn(context: Context) {
        val activityIntent = Intent(context, CheckInActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NO_ANIMATION
        }
        context.startActivity(activityIntent)
    }

    companion object {
        private const val TAG = "CheckInTrigger"
    }
}
