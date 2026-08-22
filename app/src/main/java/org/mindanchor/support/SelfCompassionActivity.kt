@file:Suppress("MagicNumber")
package org.mindanchor.support

import androidx.compose.runtime.Composable

/**
 * v0.27.0: the Neff (2003) self-compassion break, opened
 * from SupportActivity. Three sentences at 15 seconds each (45 seconds
 * total). The activity exists so the activity-launch
 * smoke tests can verify the surface; the whole Composable is
 * in [SelfCompassionScreen].
 *
 * v0.29.0: the activity's lifecycle / theme scaffold is in
 * [SupportSurfaceActivity]. This class only declares what the
 * surface renders, not how the activity is wired into the
 * Android lifecycle.
 *
 * ## What this is and what it is not
 *
 * The break is the Neff et al. 2003 self-compassion intervention
 * for the moment of acute distress: validate the suffering,
 * normalize the suffering as part of being human, and offer
 * self-kindness. It is not a treatment plan and not a substitute
 * for one. The activity stays on screen for ~45 seconds and does
 * not log, score, or record the interaction — the user is in a
 * state that does not benefit from being asked questions.
 *
 * ## BPD-safety
 *
 * No directive language ("you should..."). No all-or-nothing
 * framing. No good-vs-bad day. The user can dismiss at any time
 * via the Done button.
 */
class SelfCompassionActivity : SupportSurfaceActivity() {
    @Composable
    override fun Surface(onDone: () -> Unit) {
        SelfCompassionScreen(onDone = onDone)
    }
}
