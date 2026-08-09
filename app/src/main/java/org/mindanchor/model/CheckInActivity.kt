package org.mindanchor.model

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.mindanchor.data.CheckInPrefs
import org.mindanchor.ui.MindAnchorTheme
import java.time.ZoneId

/**
 * Hosts the v0.20.1 check-in itself, opened from
 * [CheckInTrigger] when the user unlocks their
 * phone.
 *
 * ## Why this is *not* invoked from a notification
 *
 * The brief is explicit: the user wants the check-in
 * to be a full-screen moment that *pushes* them to
 * fill it, not a notification they can swipe away.
 * The trigger fires on `ACTION_USER_PRESENT`
 * (phone unlock), not on a scheduled alarm. The
 * surface wakes the screen and shows on top of the
 * lock screen.
 *
 * ## Why the reject path is the back button
 *
 * The brief is also explicit: "no differing of
 * check-in, just a simple back button to reject."
 * The launcher does *not* show a "Not now" button.
 * The user can back out at any moment by pressing
 * the system back button; that is the entire
 * reject affordance. The activity does not record
 * a rejection (no engagement analytics, no
 * deferral picker, no reschedule, no log of
 * rejection — see brief §B3, B6).
 *
 * ## Why setShowWhenLocked / setTurnScreenOn
 *
 * The check-in is meant to be the *first* thing the
 * user sees when they wake their phone, not a
 * notification in the shade they have to reach for.
 * These two API calls (Android M and Lollipop
 * respectively) let the activity present on top of
 * the lock screen and turn the screen on if it was
 * off. The check-in does *not* dismiss the keyguard
 * — the user still has to unlock to use the phone
 * afterwards; we just present our UI in front of
 * the lock so the prompt is visible.
 *
 * The activity does *not* use
 * `SYSTEM_ALERT_WINDOW`. The check-in is a normal
 * Activity, started by a BroadcastReceiver, with
 * the standard `setShowWhenLocked` /
 * `setTurnScreenOn` API. No overlay permission is
 * needed.
 *
 * @wording-reviewed — the visible strings (the
 * "How did today sit?" prompt, the rating anchors,
 * the reflection placeholder) are clinical-review-
 * gated. See docs/CLINICAL_REVIEW.md.
 */
class CheckInActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Wake the screen and present on top of the
        // lock screen. The user still has to unlock
        // to use the phone afterwards; we just
        // present our UI in front of the lock so
        // the prompt is visible.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }

        val prefs = CheckInPrefs(applicationContext)

        // Use the process-scoped rate-limit holder
        // (CheckInRateLimitHolder). The trigger and
        // the activity share the same in-memory
        // state, so a consecutive-rejection counter
        // carries across phone unlocks within the
        // same process. On process death the holder
        // is fresh (transient by design, brief §B6).
        var rateLimitValue = CheckInRateLimitHolder.state

        // Capture the day boundary at the moment
        // the activity is created, in the device's
        // local time zone. The engine uses this for
        // its rate-limit state. The first call to
        // rolloverIfNeeded with an UNINITIALISED
        // day sets the boundary and clears the daily
        // counters; the holder might already have a
        // boundary from earlier today.
        val zone = ZoneId.systemDefault()
        val nowMillis = System.currentTimeMillis()
        val dayStart = java.time.Instant.ofEpochMilli(nowMillis)
            .atZone(zone)
            .toLocalDate()
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        if (rateLimitValue.dayStartMillis == CheckInRateLimit.UNINITIALISED_DAY) {
            rateLimitValue = rateLimitValue.copy(dayStartMillis = dayStart)
            // v0.20.1 round 5 follow-up: use the
            // monitor-based update so a concurrent
            // trigger callback cannot lose this
            // write. The previous direct-assignment
            // pattern (`state = X`) was racy.
            CheckInRateLimitHolder.update { rateLimitValue }
        }

        // Reject-tracking. A "rejection" is a back-
        // press that does not result in a save. We
        // track it here (in-memory only) so the
        // engine's rate-limit can auto-pause after
        // 3 consecutive rejections. The back-press
        // itself is the standard system back button
        // — we do not draw a "Not now" button.
        var saved = false

        // Hook the back button. The system back
        // gesture / button calls this handler. The
        // back button is the *only* reject path
        // (per brief: "no differing of check in,
        // just a simple back button to reject").
        onBackPressedDispatcher.addCallback(this) {
            if (!saved) {
                // v0.20.1 round 5 follow-up: use the
                // monitor-based update. The
                // back-press rejection must be
                // serialized with the trigger's
                // shouldFire read-modify-write
                // and the activity's recordAcceptance.
                CheckInRateLimitHolder.update { rl ->
                    CheckInEngine.recordRejection(
                        rateLimit = rl,
                        nowMillis = System.currentTimeMillis(),
                    )
                }
            }
            finish()
        }

        setContent {
            MindAnchorTheme {
                CheckInScreen(
                    saving = saved,
                    onSave = { rating, reflection ->
                        // Guard against double-tap.
                        // The Save button is enabled
                        // only when a rating is
                        // picked, but a fast double-
                        // tap can fire onSave twice
                        // before the activity finishes.
                        // Without this guard the user
                        // would end up with two
                        // check-ins for one prompt.
                        if (saved) return@CheckInScreen
                        saved = true
                        val now = System.currentTimeMillis()
                        val checkIn = CheckIn(
                            rating = rating,
                            reflection = reflection,
                            atMillis = now,
                        )
                        // Bump the rate-limit, save to
                        // disk. The state passed to
                        // recordAcceptance is the
                        // *current* accepted check-ins;
                        // the engine only uses it for
                        // the day-window filter, and
                        // the rate-limit already tracks
                        // acceptedToday separately.
                        // We pass CheckInState() (empty)
                        // because the new check-in is
                        // appended to disk; the in-memory
                        // state here is just for the
                        // engine's day-window count.
                        val (newRl, _) = CheckInEngine.recordAcceptance(
                            rateLimit = CheckInRateLimitHolder.state,
                            state = CheckInState(),
                            checkIn = checkIn,
                            nowMillis = now,
                        )
                        // v0.20.1 round 5 follow-up: use
                        // the monitor-based update. The
                        // previous direct-assignment
                        // pattern could lose the
                        // acceptedToday increment to a
                        // concurrent trigger or
                        // back-press callback.
                        CheckInRateLimitHolder.update { newRl }
                        // Both prefs.add and
                        // EmaScheduler.disable run
                        // in an application-scoped
                        // coroutine. lifecycleScope
                        // would be cancelled by
                        // finish() before either
                        // reaches DataStore +
                        // AlarmManager. The user
                        // already saw "thanks" via
                        // the rate-limit bump; the
                        // persistence is the part
                        // that must complete.
                        val appScope = kotlinx.coroutines.CoroutineScope(
                            kotlinx.coroutines.SupervisorJob() +
                                kotlinx.coroutines.Dispatchers.IO,
                        )
                        appScope.launch {
                            runCatching { prefs.add(checkIn) }
                            // v0.20.1 round 5 follow-up:
                            // the first time the user
                            // accepts a check-in
                            // through the new flow,
                            // disable the old
                            // scheduled-EMA feature
                            // so the user never
                            // receives a second
                            // check-in notification
                            // in the same day. The
                            // historical data is
                            // preserved on disk.
                            runCatching {
                                EmaScheduler.disable(applicationContext)
                            }
                        }
                        finish()
                    },
                )
            }
        }
    }
}
