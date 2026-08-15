@file:Suppress("MagicNumber")
package org.mindanchor.support

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.mindanchor.ui.CalmBackground

/**
 * v0.28.0: the DBT diary card (Linehan 1993 ch. 11;
 * Dimeff et al. 2011).
 *
 * A single daily prompt: urge / emotion / intensity / skill /
 * outcome. The card is the gold standard for BPD mood tracking
 * (Rizvi et al. 2017). v0.28.0 ships this as the new primary
 * check-in surface, replacing the v0.27 EMA + CheckIn shape
 * (which is research-weak per docs/research/14-v0.26.6-audit.md
 * §2.5).
 *
 * The card is in [DiaryCardPrefs] (DataStore). The
 * "this week" view shows the last 7 days as a list — never a
 * chart (per the v0.26.6 audit §2.3 BPD-safety rule that a
 * chart implies an interpretation the project is not allowed
 * to make).
 *
 * ## BPD-safety
 *
 * No directive language. No comparison across days (the list
 * is per-day, not a "trend"). No score, no streak, no
 * achievement badge. The intensity slider is 0–10, not 0–100,
 * to match DBT diary card convention. The skill field is
 * optional and the prompt names the skills by name — not "you
 * should have used...".
 */
class DiaryCardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CalmBackground { _ ->
                DiaryCardScreen(onDone = { finish() })
            }
        }
    }
}
