@file:Suppress("MagicNumber")
package org.mindanchor.support

import androidx.compose.runtime.Composable

/**
 * v0.38.0: 4-7-8 rhythmic breathing surface.
 *
 * Research basis: Zaccaro et al. 2018 *Frontiers in Human
 * Neuroscience* 12:353 — slow-paced breathing (~6 breaths/min)
 * is among the strongest single-intervention anxiety reductions
 * in the meta-analysis. The 4-7-8 protocol (Weil 2011, adapted
 * from pranayama) is 19 seconds per cycle: inhale 4s, hold 7s,
 * exhale 8s.
 *
 * v0.38.0 design choice: NO count, NO streak, NO timer readout,
 * NO progress bar. The single visual is a circle that grows
 * (inhale), holds, and shrinks (exhale). The only text is the
 * phase label. A user in distress does not need to *see* they
 * are halfway through a cycle — they need to *feel* the breath
 * with the device.
 *
 * v0.38.0 red-dot treatment:
 *   - Warm cream paper background (#FAF6EE), not the app's
 *     default dark navy. The breathing surface is a single-screen
 *     exception, not a default theme.
 *   - Soft teal-to-deep-blue gradient on the breath circle.
 *   - Subtle haptic on each phase transition.
 *   - One citation footer: "Zaccaro et al. 2018".
 */
class BreathingActivity : SupportSurfaceActivity() {
    @Composable
    override fun Surface(onDone: () -> Unit) {
        BreathingScreen(onDone = onDone)
    }
}
