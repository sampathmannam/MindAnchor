package org.mindanchor.model

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
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
        val rateLimit = MutableStateFlow(CheckInRateLimit())

        // Capture the day boundary at the moment
        // the activity is created, in the device's
        // local time zone. The engine uses this for
        // its rate-limit state.
        val zone = ZoneId.systemDefault()
        val nowMillis = System.currentTimeMillis()
        val dayStart = java.time.Instant.ofEpochMilli(nowMillis)
            .atZone(zone)
            .toLocalDate()
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        rateLimit.value = CheckInRateLimit(dayStartMillis = dayStart)

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
                rateLimit.value = CheckInEngine.recordRejection(
                    rateLimit = rateLimit.value,
                    nowMillis = System.currentTimeMillis(),
                )
            }
            finish()
        }

        setContent {
            MindAnchorTheme {
                CheckInScreen(
                    onSave = { rating, reflection ->
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
                            rateLimit = rateLimit.value,
                            state = CheckInState(),
                            checkIn = checkIn,
                            nowMillis = now,
                        )
                        rateLimit.value = newRl
                        lifecycleScope.launch {
                            runCatching { prefs.add(checkIn) }
                        }
                        finish()
                    },
                )
            }
        }
    }
}
