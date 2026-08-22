package org.mindanchor.model

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.mindanchor.data.CheckInPrefs
import org.mindanchor.ui.MindAnchorTheme

/**
 * Hosts the v0.20.1 round 5 follow-up
 * check-in history view.
 *
 * ## Why a separate activity
 *
 * The check-in prompt ([CheckInActivity]) is the
 * *write* side: it appears, takes a response, and
 * dismisses. The history is the *read* side: a
 * list of past check-ins for the user to chart
 * over time, scroll back through, or simply
 * re-encounter.
 *
 * Same pattern as the notes feature: a separate
 * activity for the separate concern. The launcher
 * can route here from a home-screen affordance.
 *
 * ## Why launcher-authored, NOT clinical-review-gated
 *
 * The screen renders the user's own data with no
 * interpretation. There is no "average mood,"
 * "trend line," "concerning pattern" — the user
 * sees their own ratings, in their own time order,
 * with the optional reflections they wrote. The
 * launcher is a *view*, not a *judge*.
 *
 * If we ever add an interpretation layer (averages,
 * trends, "you've been low for N days"), THAT
 * would be clinical-review-gated. The view itself
 * is not.
 */
class CheckInHistoryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // v0.20.1 round 5 follow-up: hook the
        // system back button. The TopAppBar
        // navigationIcon handles the in-app back,
        // but the system back gesture / button
        // needs an explicit handler. Same pattern
        // as NoteActivity.
        onBackPressedDispatcher.addCallback(this) {
            finish()
        }

        val prefs = CheckInPrefs(applicationContext)

        setContent {
            MindAnchorTheme {
                // v0.25.17 BUG-004: lifecycle-aware collect. The
                // check-in history activity can be in the
                // background for long stretches (the user opens
                // a check-in, answers, then comes back); the flow
                // was producing fresh lists on every emission
                // even when the activity was STOPPED, which is
                // the documented backpressure hole.
                val state by prefs.checkIns.collectAsStateWithLifecycle(initialValue = CheckInState())
                CheckInHistoryScreen(
                    checkIns = state,
                    onClose = { finish() },
                )
            }
        }
    }
}
