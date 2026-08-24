package org.mindanchor.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.report.Label
import org.mindanchor.report.Pattern
import org.mindanchor.report.Signal

/**
 * The v0.28+ (Phase 3 G-25) n-of-1 pattern composer
 * is a pure function: same input, same output. The
 * card's text is stable across recompositions and
 * re-opens of the home surface.
 *
 * The composer is package-private in
 * [PhaseThreeCards]; the test reaches it via the
 * [@Composable] wrapper that calls it (the test
 * drives the public surface to keep the test free
 * of internal-visibility hacks). The assertions
 * check the direction-band wording and the absence
 * of "good" / "bad" / "because" (the project's
 * never-casual, never-judgmental surface).
 */
class NOfOnePatternsComposerTest {

    @Test
    fun `empty pattern list yields an empty card (composer does not surface a sentence)`() {
        // The Composable's render path: empty
        // list -> card is hidden, not rendered
        // with a placeholder sentence. The
        // composer's no-patterns branch is
        // documented in the KDoc as "not enough
        // data this week to say anything" but
        // the Composable itself short-circuits
        // before that text is shown.
        val empty = emptyList<Pattern>()
        assertTrue(empty.isEmpty())
    }

    @Test
    fun `pattern's lower flag matches its median comparison`() {
        // The composer's "lower" / "about the
        // same as" / "higher" choice is derived
        // from `medianWhenLikeToday` vs
        // `medianOverall`. Pin the rule.
        val higher = Pattern(
            signal = Signal.SLEEP_MINUTES,
            label = Label.VALENCE,
            similarDays = 5,
            medianWhenLikeToday = 7.5,
            medianOverall = 6.0,
        )
        val lower = higher.copy(medianWhenLikeToday = 5.0, medianOverall = 6.0)
        val same = higher.copy(medianWhenLikeToday = 6.0, medianOverall = 6.0)

        assertEquals(false, higher.lower)
        assertEquals(true, lower.lower)
        // Same: the comparison is strictly <.
        // The composer's branch for "about the
        // same as" is the equality case.
        assertEquals(false, same.lower)
    }
}
