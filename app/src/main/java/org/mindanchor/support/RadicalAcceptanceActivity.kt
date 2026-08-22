@file:Suppress("MagicNumber")
package org.mindanchor.support

import androidx.compose.runtime.Composable

/**
 * v0.27.0: the Linehan (1993) radical acceptance exercise, opened
 * from SupportActivity. Four sentences at 10 seconds each (40
 * seconds total). The activity exists so the activity-launch
 * smoke tests can verify the surface; the whole Composable is
 * in [RadicalAcceptanceScreen].
 *
 * v0.29.0: the activity's lifecycle / theme scaffold is in
 * [SupportSurfaceActivity]. This class only declares what the
 * surface renders, not how the activity is wired into the
 * Android lifecycle.
 *
 * ## What this is and what it is not
 *
 * Radical acceptance is a DBT distress-tolerance skill for
 * situations that cannot be changed. The four sentences are
 * Linehan's framing: reality is what it is, the pain is part of
 * the pain, accepting reduces suffering, refusing acceptance
 * increases suffering. The activity does not interpret, score,
 * or record.
 *
 * ## BPD-safety
 *
 * The framing is descriptive, not directive ("it is what it is",
 * not "you must accept"). The Done button is always present.
 * The back gesture dismisses without consequence.
 */
class RadicalAcceptanceActivity : SupportSurfaceActivity() {
    @Composable
    override fun Surface(onDone: () -> Unit) {
        RadicalAcceptanceScreen(onDone = onDone)
    }
}
