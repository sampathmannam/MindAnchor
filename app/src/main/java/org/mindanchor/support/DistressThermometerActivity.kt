@file:Suppress("MagicNumber", "MaxLineLength", "FunctionNaming", "LongMethod")
package org.mindanchor.support

import android.content.Intent
import androidx.compose.runtime.Composable

/**
 * v0.28.0: the Distress Thermometer (DBT + Gross emotion
 * regulation). The user slides a 0–100 indicator. After the
 * release, the activity shows a single DBT-grounded suggestion
 * matched to the band:
 *   0–30  → mindfulness / sensory grounding (the body settles first)
 *   31–60 → name what is here (Lieberman 2007; Gross 1998 affect
 *           labelling)
 *   61–85 → TIPP (temperature, intense movement, paced breath,
 *           paired muscle relaxation)
 *   86–100 → open the Support group (DBT skills + the people the
 *           user would actually call — no hardcoded numbers,
 *           R1 honored)
 *
 * The matching is deterministic and BPD-safe. The activity does
 * not save, log, or score. The Done button dismisses; the
 * back gesture also dismisses.
 *
 * Research basis:
 *   * Linehan 1993, DBT Distress Tolerance, TIPP skill
 *   * Linehan 1993, DBT Emotion Regulation, PLEASE / opposite action
 *   * Gross 1998, emotion regulation process model
 *   * Lieberman 2007, affect labelling reduces amygdala response
 *
 * v0.28.1: the 86+ band's "Open Support" affordance now actually
 * launches SupportActivity. Previously it called onDone (a silent
 * dismiss), which was a false promise to a user in the high-distress
 * band. R1 stays honored — no hardcoded helpline numbers anywhere.
 *
 * v0.29.0: the activity's lifecycle / theme scaffold is in
 * [SupportSurfaceActivity]. The 86+ band's "open Support" callback
 * is wired here, in the Surface() override, because it is the
 * only place that has both the activity context (for
 * startActivity) and the screen.
 */
class DistressThermometerActivity : SupportSurfaceActivity() {
    @Composable
    override fun Surface(onDone: () -> Unit) {
        DistressThermometerScreen(
            onDone = onDone,
            onOpenSupport = {
                // v0.28.1: the high-distress band reaches the
                // Support group directly. R1 honored — no
                // hardcoded numbers, just DBT skills + the
                // user's own contact list.
                startActivity(
                    Intent(this, SupportActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                finish()
            },
        )
    }
}
