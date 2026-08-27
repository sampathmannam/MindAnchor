// @wording-reviewed — the host composes [CheckInFloatingPrompt] into
// the home surface. The wording on the pill itself (R.string.check_in_*)
// and the auto-pause hint (R.string.check_in_paused_hint) are
// clinical-review-gated. This file owns the show/dismiss lifecycle,
// the auto-pause hint, the rate-limit integration, and the
// EXTRA_RATING contract that pre-fills the expand-to-reflection screen.

package org.mindanchor.model

import android.app.Application
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mindanchor.R
import org.mindanchor.data.CheckInPrefs

/**
 * v0.72+ — the in-app host for the floating check-in pill.
 *
 * Mounts [CheckInFloatingPrompt] on the home surface and wires it to
 * the existing [CheckInEngine] + [CheckInRateLimitHolder] +
 * [CheckInPrefs] pipeline. The pill is a controlled surface:
 *
 * - On first composition the host calls [CheckInEngine.shouldFire];
 *   if it returns true the pill shows. If the engine reports the user
 *   is in the auto-pause state (3 consecutive rejections in a day), a
 *   quiet "see you tomorrow" hint takes the pill's slot so the user
 *   knows the launcher noticed and is not just silent.
 * - On accept the host calls [CheckInEngine.recordAcceptance],
 *   persists via [CheckInPrefs.add], disables the scheduled EMA
 *   (matching the existing [CheckInActivity] behaviour), and
 *   dismisses the pill.
 * - On reject the host calls [CheckInEngine.recordRejection] and
 *   dismisses the pill.
 * - On expand the host opens [CheckInActivity] with [EXTRA_RATING]
 *   set to whatever the user has already picked on the pill (or 0 if
 *   they tapped the question text without a rating). The activity
 *   pre-fills the rating row from that extra.
 *
 * The pill does **not** re-show on the same host instance. The
 * next show is gated by the rate-limit and the daily cap
 * (the engine's `shouldFire` will return false until the interval
 * has elapsed and the cap hasn't been hit).
 *
 * See `docs/CHECKIN_FLOATING_PROMPT.md` for the design rationale.
 */
@Composable
fun CheckInPromptHost(
    /**
     * The application context. Used for [CheckInPrefs] and to start
     * the expand activity.
     */
    application: Application,
) {
    val context = LocalContext.current

    // Local visible state. The host is the only thing that sets this.
    var visible by remember { mutableStateOf(false) }

    // The user is in the auto-pause window — the engine will not
    // fire today, but the user should see *something* so they know
    // the launcher is paying attention, not broken. The hint
    // replaces the pill in the same bottom slot.
    var pausedHint by remember { mutableStateOf(false) }

    // Decide whether to show on first composition. We use
    // `LaunchedEffect(Unit)` so this runs once when the host enters
    // the composition; recomposition does not re-trigger.
    LaunchedEffect(Unit) {
        val prefs = CheckInPrefs(application)
        // prefs.checkIns is a Flow<CheckInState>; .first() reads once.
        // Wrap in a try/catch so a corrupt / missing store can't crash
        // the host — failure defaults to an empty state.
        val state: CheckInState = try {
            prefs.checkIns.first()
        } catch (t: Throwable) {
            CheckInState()
        }
        val rateLimit = CheckInRateLimitHolder.state
        val shouldFire = CheckInEngine.shouldFire(
            rateLimit = rateLimit,
            state = state,
            nowMillis = System.currentTimeMillis(),
        )
        when {
            shouldFire -> visible = true
            // v0.72+ — auto-pause hint. Show a quiet "see you tomorrow"
            // line in place of the pill so the user knows the launcher
            // is intentionally quiet, not broken. The wording is
            // clinical-review-gated (see R.string.check_in_paused_hint).
            rateLimit.autoPaused -> pausedHint = true
            // else: out of active hours, daily cap hit, or
            // interval not yet elapsed — silent. The user will see
            // the pill again when the engine says they should.
        }
    }

    CheckInFloatingPrompt(
        visible = visible,
        onAccept = { rating ->
            // Save the rating, then dismiss.
            val now = System.currentTimeMillis()
            val checkIn = CheckIn(
                rating = rating,
                reflection = "",
                atMillis = now,
            )
            // Bump the rate-limit in-memory; the host also persists
            // the check-in to disk. The engine recordAcceptance reads
            // the *current* state from the holder (single-threaded
            // update via the monitor-based write).
            val (newRl, _) = CheckInEngine.recordAcceptance(
                rateLimit = CheckInRateLimitHolder.state,
                state = CheckInState(),
                checkIn = checkIn,
                nowMillis = now,
            )
            CheckInRateLimitHolder.update { newRl }
            // Persist to disk + disable the scheduled EMA, in an
            // application-scoped coroutine so a fast dismiss doesn't
            // cancel the work. Mirrors CheckInActivity.onSave. The
            // runCatching is defensive — a corrupt DataStore or
            // a transient AlarmManager failure must never propagate
            // to the UI; the user already saw the dismiss animation.
            val appScope = kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() +
                    kotlinx.coroutines.Dispatchers.IO,
            )
            appScope.launch {
                runCatching { CheckInPrefs(application).add(checkIn) }
                runCatching { EmaScheduler.disable(application) }
            }
            visible = false
        },
        onReject = {
            // Record a rejection in the rate-limit. Pure function.
            val now = System.currentTimeMillis()
            val newRl = CheckInEngine.recordRejection(
                rateLimit = CheckInRateLimitHolder.state,
                nowMillis = now,
            )
            CheckInRateLimitHolder.update { newRl }
            visible = false
        },
        onExpand = { rating ->
            // The pill taps the expand path through `onExpand`. The
            // surface-wide click on the pill bubbles here. Open the
            // existing [CheckInActivity] with [EXTRA_RATING] set so
            // the activity can pre-fill the rating row in its UI.
            // 0 means "no rating yet"; the activity treats that as
            // "start with nothing selected."
            val intent = Intent(context, CheckInActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
                putExtra(EXTRA_RATING, rating)
            }
            runCatching { context.startActivity(intent) }
            visible = false
        },
    )

    // v0.72+ — auto-pause hint. Renders below the pill area when
    // the engine reports the user is paused. The wording lives in
    // R.string.check_in_paused_hint and is clinical-review-gated.
    if (pausedHint) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.check_in_paused_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Intent extra key for the pre-filled rating passed from the floating
 * pill to the full-screen [CheckInActivity]. 0 means "no rating
 * selected yet"; 1-5 means the user already picked on the pill.
 *
 * Public so [CheckInActivity] can read it; the host also writes it
 * on the expand path.
 */
const val EXTRA_RATING = "org.mindanchor.model.CHECK_IN_EXTRA_RATING"
