package org.mindanchor.sleep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `docs/research/15` named the Scullin 2018 bedtime to-do list as the
 * highest-ROI S-effort feature gap. The brief is explicit that
 * **specificity is the active ingredient** (Scullin et al. 2018, J
 * Exp Psychol Gen 147(1):139–146, doi:10.1037/xge0000374): vague
 * items do not show the same effect on sleep-onset latency.
 *
 * [BedtimeList.isSpecific] is the conservative heuristic that
 * separates a Scullin-style specific item from a vague one. These
 * tests pin the *signs* the heuristic is supposed to look for —
 * length, a verb, a time/day token — so a future edit cannot make
 * the heuristic silently more or less strict without the tests
 * noticing.
 */
class BedtimeListTest {

    @Test
    fun `a specific item with verb and time token is recognised`() {
        assertTrue(BedtimeList.isSpecific("call Mom at 6 about Saturday"))
        assertTrue(BedtimeList.isSpecific("email the report by Tuesday morning"))
        assertTrue(BedtimeList.isSpecific("pick up package after 5pm"))
    }

    @Test
    fun `a vague item with no verb is rejected`() {
        // Scullin 2018: a "be better" / "do the thing" type item
        // does not show the effect. The heuristic is supposed to
        // refuse it.
        assertFalse(BedtimeList.isSpecific("be better at work"))
        assertFalse(BedtimeList.isSpecific("the thing I keep forgetting"))
    }

    @Test
    fun `a verb without a time or day anchor is rejected`() {
        // The time anchor is what *drove* the effect in Scullin
        // 2018. A bare "call Mom" without a when is a wish, not a
        // plan.
        assertFalse(BedtimeList.isSpecific("call Mom"))
        assertFalse(BedtimeList.isSpecific("write the report"))
    }

    @Test
    fun `a too-short line is rejected, even with a verb and a time`() {
        // A single word with a number on the end is not a plan.
        // The 12-character floor is the deliberate cap below
        // which a sentence is implausible.
        assertFalse(BedtimeList.isSpecific("call 6"))
        assertFalse(BedtimeList.isSpecific(""))
    }

    @Test
    fun `a verb with a day-of-week counts as anchored`() {
        // "Monday" is the most-common tomorrow-anchor the heuristic
        // will see, and a regression that dropped day names from
        // the time token list would lose the most common case.
        assertTrue(BedtimeList.isSpecific("submit application Monday"))
        assertTrue(BedtimeList.isSpecific("review notes on Sunday"))
    }

    @Test
    fun `a number 1-31 counts as an anchor`() {
        // Day-of-month tokens. A regression that dropped these
        // would lose a meaningful share of the corpus.
        assertTrue(BedtimeList.isSpecific("renew car registration 15"))
        assertTrue(BedtimeList.isSpecific("submit form 31"))
    }

    @Test
    fun `matching is case-insensitive`() {
        assertTrue(BedtimeList.isSpecific("CALL MOM AT 6PM"))
        assertTrue(BedtimeList.isSpecific("Call Mom at 6pm"))
    }

    @Test
    fun `cleanLine trims, replaces newlines, caps at 140 chars, and returns null on blank`() {
        assertEquals("call Mom at 6", BedtimeList.cleanLine("  call Mom at 6  "))
        assertEquals("two line item", BedtimeList.cleanLine("two\nline item"))
        assertEquals("a".repeat(BedtimeList.MAX_LINE_LENGTH), BedtimeList.cleanLine("a".repeat(500)))
        assertNull(BedtimeList.cleanLine(""))
        assertNull(BedtimeList.cleanLine("   "))
    }

    @Test
    fun `encode and decode round-trip`() {
        val original = listOf("call Mom at 6", "email the report by Tuesday", "buy milk")
        assertEquals(original, BedtimeList.decode(BedtimeList.encode(original)))
    }

    @Test
    fun `decode drops blank lines and caps the list at MAX_ITEMS`() {
        // A corrupted or hand-edited file with empty lines and
        // extra items must not produce an overflowing list. The
        // cap is on the *output*, not the input.
        val raw = (1..10).joinToString("\n") { "item $it" } + "\n\n"
        val out = BedtimeList.decode(raw)
        assertEquals(BedtimeList.MAX_ITEMS, out.size)
        assertTrue(out.all { it.isNotBlank() })
    }

    @Test
    fun `decode of an empty file is an empty list, not a phantom item`() {
        // A blank line in storage must cost one line of signal,
        // not an item that wakes somebody up.
        assertTrue(BedtimeList.decode("").isEmpty())
        assertTrue(BedtimeList.decode("\n\n\n").isEmpty())
    }
}
