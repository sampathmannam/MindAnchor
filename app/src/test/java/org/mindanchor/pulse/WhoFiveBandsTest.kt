package org.mindanchor.pulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure-function split from [WhoFive.band], [WhoFive.screenPositive]
 * and [WhoFive.change] is the part of the score-presentation change
 * that lives on the *data* side. The wording lives in [PulseScreen] and
 * is the part the clinical reviewer reads.
 *
 * `docs/CLINICAL_REVIEW.md` R3 makes "never interpreting a WHO-5 score"
 * an invariant. These tests are the audit log for the data side: every
 * boundary the brief calls out by name is pinned here, so a future edit
 * cannot silently change which readings are screen-positive without
 * the tests noticing.
 */
class WhoFiveBandsTest {

    @Test
    fun `a null score is incomplete, not okay`() {
        assertEquals(WhoFive.Band.INCOMPLETE, WhoFive.band(null))
    }

    @Test
    fun `a score of 51 is the boundary above the screen-positive cut-off`() {
        // The screen-positive cut-off is ≤ 50. 51 is the first score
        // outside the cut-off. This is the line Topp 2015 and WHO 1998
        // draw; missing it by one would either over-trigger or
        // under-trigger the support flow on every borderline reading.
        assertEquals(WhoFive.Band.OKAY, WhoFive.band(51))
        assertFalse(WhoFive.screenPositive(listOf(3, 3, 3, 3, 3), score = 51))
    }

    @Test
    fun `a score of 50 is screen-positive per the WHO cut-off`() {
        assertEquals(WhoFive.Band.LOW, WhoFive.band(50))
        assertTrue(WhoFive.screenPositive(listOf(3, 3, 3, 3, 3), score = 50))
    }

    @Test
    fun `a score of 29 is in the LOW band, not VERY_LOW`() {
        // 28 is the boundary. 29 is the lowest reading still in the
        // LOW band. A regression that flipped 28 and 29 would
        // re-band everyone just below the very-low threshold.
        assertEquals(WhoFive.Band.LOW, WhoFive.band(29))
        assertEquals(WhoFive.Band.VERY_LOW, WhoFive.band(28))
    }

    @Test
    fun `screen-positive fires for any single item answered 0 or 1, even at a high score`() {
        // This is the WHO 1998 per-item criterion the field often
        // forgets. A score of 76 with one 0 still flags for further
        // assessment, because the WHO document says it should.
        val answers = listOf(0, 5, 5, 5, 4) // raw 19 * 4 = 76
        assertEquals(76, WhoFive.score(answers))
        assertTrue(WhoFive.screenPositive(answers, score = 76))
    }

    @Test
    fun `screen-positive fires for a single 1 as well as a single 0`() {
        val answers = listOf(1, 5, 5, 5, 5) // raw 21 * 4 = 84
        assertEquals(84, WhoFive.score(answers))
        assertTrue(WhoFive.screenPositive(answers, score = 84))
    }

    @Test
    fun `screen-positive does not fire on a high score with no per-item floor`() {
        val answers = listOf(4, 5, 5, 5, 5) // raw 24 * 4 = 96
        assertEquals(96, WhoFive.score(answers))
        assertFalse(WhoFive.screenPositive(answers, score = 96))
    }

    @Test
    fun `change is null on the first reading`() {
        assertNull(WhoFive.change(current = 64, previous = null))
    }

    @Test
    fun `change ignores a sub-threshold shift, by design`() {
        // 8 points is below the 10-point meaningful-change threshold
        // (WHO 1998 citing John Ware 1995; Topp 2015). Showing
        // sub-threshold shifts as a "trend" is a documented harm
        // (DISCOVER RCT, Lancet Digital Health 2024) — silence is
        // the right behaviour.
        assertNull(WhoFive.change(current = 64, previous = 56))
        assertNull(WhoFive.change(current = 50, previous = 42))
    }

    @Test
    fun `change is meaningful up at the 10-point boundary`() {
        assertEquals(WhoFive.Change.MEANINGFUL_UP, WhoFive.change(current = 70, previous = 60))
    }

    @Test
    fun `change is meaningful down at the 10-point boundary`() {
        assertEquals(WhoFive.Change.MEANINGFUL_DOWN, WhoFive.change(current = 50, previous = 60))
    }

    @Test
    fun `a 9 point change is below threshold, a 10 point change is at threshold`() {
        // The brief allows a band of 8–12 points centred on 10. The
        // code uses 10 as the centre. 9 is below; 10 is at the line.
        assertNull(WhoFive.change(current = 65, previous = 56))
        assertEquals(WhoFive.Change.MEANINGFUL_UP, WhoFive.change(current = 66, previous = 56))
    }

    @Test
    fun `a very large shift is still meaningful, not error`() {
        // A first reading of 30 followed by 80 is a 50-point jump.
        // The function returns a direction, never "too large to
        // interpret" — the human eye is the right place for that
        // judgement, not a guard inside a pure function.
        assertEquals(WhoFive.Change.MEANINGFUL_UP, WhoFive.change(current = 80, previous = 30))
    }
}
