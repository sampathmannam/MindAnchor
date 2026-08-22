@file:Suppress("MagicNumber")
package org.mindanchor.support

import androidx.compose.runtime.Composable

/**
 * v0.28.0: ACCEPTS self-soothing menu (DBT Linehan 1993
 * Distress Tolerance Module 2). Seven buttons, each a 2-minute
 * grounding practice. The user picks one, reads the one-line
 * body, and is dismissed back. There is no log, no score.
 *
 * v0.29.0: the activity's lifecycle / theme scaffold is in
 * [SupportSurfaceActivity]. This class only declares what the
 * surface renders, not how the activity is wired into the
 * Android lifecycle.
 *
 * ACCEPTS is a mnemonic in DBT distress tolerance:
 *   A — Activities
 *   C — Contributing
 *   C — Comparisons
 *   E — Emotions (other emotions, e.g. watch a clip that makes
 *       you feel something softer)
 *   P — Push away
 *   T — Thoughts (counting, naming)
 *   S — Sensations
 *
 * Each is a single sensory action, no interpretation. The
 * skill is for the moment of acute distress when the user
 * needs *one* thing to do, not a menu of seven things to
 * remember.
 */
class AcceptsActivity : SupportSurfaceActivity() {
    @Composable
    override fun Surface(onDone: () -> Unit) {
        AcceptsScreen(onDone = onDone)
    }
}
