@file:Suppress("MagicNumber")
package org.mindanchor.support

import androidx.compose.runtime.Composable

/**
 * v0.28.0: the Linehan (1993, ch. 8) Opposite Action skill, opened
 * from SupportActivity. Four steps, each with a label and an
 * optional free-text field. The whole Composable is in
 * [OppositeActionScreen].
 *
 * v0.29.0: the activity's lifecycle / theme scaffold is in
 * [SupportSurfaceActivity]. This class only declares what the
 * surface renders, not how the activity is wired into the
 * Android lifecycle.
 *
 * ## What this is and what it is not
 *
 * Opposite action is a DBT Distress Tolerance skill for the moment
 * when an emotion does not fit the facts and the action urge is
 * the wrong one. For BPD the all-or-nothing pattern follows the
 * emotion; the skill is to do the opposite of the urge, not to
 * argue with the emotion. The activity is not a treatment plan
 * and not a substitute for one. There is no log, no save, no score.
 *
 * ## BPD-safety
 *
 * Validate-then-suggest. The four steps are framed as "what is
 * here" / "what is the evidence" / "what does the urge want" /
 * "the opposite is yours to choose". The free-text fields are
 * optional. The Done button dismisses without consequence.
 */
class OppositeActionActivity : SupportSurfaceActivity() {
    @Composable
    override fun Surface(onDone: () -> Unit) {
        OppositeActionScreen(onDone = onDone)
    }
}
