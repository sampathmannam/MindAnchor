package org.mindanchor.sleep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

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

    /**
     * A fixed test day for the phase-logic cases. The exact date
     * does not matter; what matters is that the same `day` is
     * used across all calls in a single test.
     */
    private val day = LocalDate.of(2026, 8, 8)

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

    @Test
    fun `phase is NONE outside the quiet hours when no list has been written`() {
        // No items, no need to surface anything. The home screen
        // is silent most of the time — surfacing an empty card
        // would teach a person to expect a card and stop reading
        // it, which is the failure mode the OpenLoop card and
        // the BedtimeList card were both designed against.
        assertEquals(
            BedtimePhase.NONE,
            BedtimeList.phase(
                quietHours = false,
                items = emptyList(),
                writtenDay = null,
                today = day,
            ),
        )
    }

    @Test
    fun `phase is CAPTURE in the quiet hours when no list has been written`() {
        // The wind-down moment, no list yet, the prompt fires
        // once. This is the place where the Scullin effect is
        // documented (write it down, the Zeigarnik loop closes,
        // sleep onset comes faster).
        assertEquals(
            BedtimePhase.CAPTURE,
            BedtimeList.phase(
                quietHours = true,
                items = emptyList(),
                writtenDay = null,
                today = day,
            ),
        )
    }

    @Test
    fun `phase is NONE in the quiet hours when a list has already been written`() {
        // A list already written does not get re-asked for. The
        // morning is when it gets handed back. Re-prompting at
        // night would be nagging.
        assertEquals(
            BedtimePhase.NONE,
            BedtimeList.phase(
                quietHours = true,
                items = listOf("call Mom at 6"),
                writtenDay = day.toString(),
                today = day,
            ),
        )
    }

    @Test
    fun `phase is RETURN outside the quiet hours for a list from last night`() {
        // The morning after: the list gets handed back, then
        // cleared. This is the "hand it back the morning after"
        // rule from docs/research/07 §4, applied to the bedtime
        // list.
        assertEquals(
            BedtimePhase.RETURN,
            BedtimeList.phase(
                quietHours = false,
                items = listOf("call Mom at 6"),
                writtenDay = day.minusDays(1).toString(),
                today = day,
            ),
        )
    }

    @Test
    fun `phase is RETURN for a list written earlier the same day`() {
        // Someone who took the prompt at 22:00 and is reading
        // the home screen at 08:00 the next morning has a list
        // dated today (well, technically yesterday relative to
        // local time depending on midnight). The frame is
        // "this morning" not "the calendar's today" — both
        // count.
        assertEquals(
            BedtimePhase.RETURN,
            BedtimeList.phase(
                quietHours = false,
                items = listOf("call Mom at 6"),
                writtenDay = day.toString(),
                today = day,
            ),
        )
    }

    @Test
    fun `phase is NONE for a list older than yesterday`() {
        // A list from two days ago is not a bedtime list, it is
        // clutter. Showing it is being reminded of something
        // already let go. The exact rule from OpenLoop.phase
        // applied to the bedtime list.
        assertEquals(
            BedtimePhase.NONE,
            BedtimeList.phase(
                quietHours = false,
                items = listOf("call Mom at 6"),
                writtenDay = day.minusDays(2).toString(),
                today = day,
            ),
        )
    }

    @Test
    fun `phase treats unparseable stored day as NONE, not as a phantom return`() {
        // A corrupted file with a bad date is the same as no
        // file: the home screen stays silent. A silent failure
        // here is the only safe failure — surfacing a list
        // whose date cannot be read would be a
        // "when did I write this?" question no one can answer.
        assertEquals(
            BedtimePhase.NONE,
            BedtimeList.phase(
                quietHours = false,
                items = listOf("call Mom at 6"),
                writtenDay = "not-a-date",
                today = day,
            ),
        )
    }

    @Test
    fun `phase is CAPTURE in quiet hours when a stale list is on file from days ago`() {
        // A list written three nights ago and never returned
        // is clutter. Treating its mere presence as
        // "already on file" would silence the capture prompt
        // forever — the only escape would be the user
        // manually clearing it from the RETURN card, which
        // they have no reason to open because the RETURN
        // card never appears either. The fix is to treat a
        // list older than last night the same as no list at
        // all: a fresh capture prompt fires in the quiet
        // hours, the same as it would for a first-time user.
        assertEquals(
            BedtimePhase.CAPTURE,
            BedtimeList.phase(
                quietHours = true,
                items = listOf("call Mom at 6"),
                writtenDay = day.minusDays(3).toString(),
                today = day,
            ),
        )
    }

    @Test
    fun `phase is CAPTURE in quiet hours when the stored day is unparseable`() {
        // Same trap as the stale-day case: a corrupted file
        // must not silence the capture prompt. The user gets
        // asked again; if the new write succeeds, the bad
        // date is overwritten with a parseable one.
        assertEquals(
            BedtimePhase.CAPTURE,
            BedtimeList.phase(
                quietHours = true,
                items = listOf("call Mom at 6"),
                writtenDay = "not-a-date",
                today = day,
            ),
        )
    }

    @Test
    fun `a line break inside an item cannot split it into two`() {
        // setBedtimeList takes a whole list, so the format guarantee has to
        // live in encode rather than in an add path.
        val stored = BedtimeList.encode(listOf("lay out clothes\nfor the morning"))
        assertEquals(listOf("lay out clothes for the morning"), BedtimeList.decode(stored))
    }

    @Test
    fun `a carriage return splits an item too`() {
        val stored = BedtimeList.encode(listOf("phone\ron the shelf"))
        assertEquals(listOf("phone on the shelf"), BedtimeList.decode(stored))
    }
}
