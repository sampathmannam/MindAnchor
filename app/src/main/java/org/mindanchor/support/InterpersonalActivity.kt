@file:Suppress("MagicNumber")
package org.mindanchor.support

import androidx.compose.runtime.Composable

/**
 * v0.27.0: the Linehan (1993) DBT Module 4 (Interpersonal
 * Effectiveness) menu, opened from SupportActivity. Three skills
 * in one activity: DEAR MAN, GIVE, FAST. The activity hosts the
 * menu and three sub-screens; navigation is via a state var, not
 * separate activities (the three screens share the same
 * visual idiom and the menu is a single tap).
 *
 * v0.29.0: the activity's lifecycle / theme scaffold is in
 * [SupportSurfaceActivity]. This class only declares what the
 * surface renders, not how the activity is wired into the
 * Android lifecycle.
 *
 * ## What this is and what it is not
 *
 * Each skill is a short DBT script with an optional "draft of
 * what you might say" text field. The user is not told what to
 * say; the draft is *theirs*, optional, and stays on the device.
 * The surface is not a treatment plan.
 *
 * ## BPD-safety
 *
 * Validate-then-suggest. The framing is "if you want" and
 * "what you might say" — the user decides. The text field is
 * optional. There is no "you should send this" or "this is the
 * right text" copy anywhere.
 */
class InterpersonalActivity : SupportSurfaceActivity() {
    @Composable
    override fun Surface(onDone: () -> Unit) {
        InterpersonalScreen(onDone = onDone)
    }
}
