@file:Suppress("MagicNumber")
package org.mindanchor.support

import androidx.compose.runtime.Composable

/**
 * v0.29.0: the ACT values clarification surface
 * (Hayes et al. 1999/2004; Wilson & Murrell 2004). Opened
 * from SupportActivity as the last reflective practice in
 * the "More moments" group (in-the-moment → reflective).
 *
 * The activity's lifecycle / theme scaffold is in
 * [SupportSurfaceActivity]. This class only declares what
 * the surface renders, not how the activity is wired into
 * the Android lifecycle.
 *
 * ## What this is and is not
 *
 * The card is the standard ACT values clarification
 * exercise — the 8-domain taxonomy is what every ACT
 * workbook uses. The values are *chosen life directions*,
 * not goals (a value is not something you finish, it is
 * something you keep doing). The screen is for *the moment
 * when a person has time to think about what they want
 * their life to be*, not a crisis surface. The card is
 * saved to [ValuesPrefs] on a single Save tap; the user
 * can revisit and see their previously-written values.
 *
 * ## BPD-safety
 *
 * No directive language. No "you should...". No judgment
 * on the user's words. The placeholder is the same shape
 * as the values the user writes. All fields are optional;
 * the Save button saves what is there. The Done button
 * dismisses without consequence.
 */
class ValuesActivity : SupportSurfaceActivity() {
    @Composable
    override fun Surface(onDone: () -> Unit) {
        ValuesScreen(onDone = onDone)
    }
}
