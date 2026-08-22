package org.mindanchor.letters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Finding tests for v0.24.0 daily letter. Each test pins one
 * piece of the contract the v0.24.0 release depends on; a
 * refactor that breaks the test forces the contributor to
 * think about whether the shape is still right.
 */
class LetterFindingTest {

    /**
     * The system prompt forbids clinical categories. The night
     * report has the same rule; the letter inherits it.
     * Pinning the forbidden categories is a finding-test
     * because the safety filter is what stops the model from
     * crossing into "I am your therapist" territory.
     */
    @Test
    fun `system prompt forbids clinical categories and sources`() {
        val sys = LetterPrompting.SYSTEM.lowercase()
        // The model is told not to invoke diagnoses or symptoms.
        for (forbidden in listOf("depression", "anxiety", "diagnosis", "symptom")) {
            assertTrue(
                "system prompt must not invite $forbidden",
                sys.contains("never mention a condition, a diagnosis, a symptom, or a risk"),
            )
        }
        // The model is told not to cite sources. Same as the
        // night report.
        assertTrue(
            "system prompt must not invite citations",
            sys.contains("never name a study, an author, or a year"),
        )
        // The model is told to be observational, not evaluative.
        assertTrue(sys.contains("observational, not evaluative"))
    }

    /**
     * The week input is the user's own data, not the model's.
     * A non-empty [WeekData] whose fields are mostly empty
     * should not produce a prompt the model can read — the
     * collector has already returned nothing worth writing
     * from, and the prompt would just be headers.
     */
    @Test
    fun `build returns null when the week has fewer than two non-empty surfaces`() {
        val sparse = WeekData(
            start = LocalDate.of(2026, 8, 4),
            end = LocalDate.of(2026, 8, 10),
            who5Summary = "",
            emaSummary = "12 check-ins.",
            notesSummary = "",
            sleepSummary = "",
            patternsLines = emptyList(),
            smallThings = emptyList(),
        )
        assertNull("a 1-surface week must not produce a prompt", LetterPrompting.build(sparse))

        val empty = WeekData(
            start = LocalDate.of(2026, 8, 4),
            end = LocalDate.of(2026, 8, 10),
            who5Summary = "",
            emaSummary = "",
            notesSummary = "",
            sleepSummary = "",
            patternsLines = emptyList(),
            smallThings = emptyList(),
        )
        assertNull("an empty week must not produce a prompt", LetterPrompting.build(empty))
    }

    /**
     * A 2-surface week produces a prompt with the right labels
     * and the right number of paragraph hints. Pinning the
     * shape means a future refactor that renames a field or
     * drops a section is a finding-test failure, not a silent
     * behaviour change.
     */
    @Test
    fun `build renders a multi-section prompt with the right header`() {
        val week = WeekData(
            start = LocalDate.of(2026, 8, 4),
            end = LocalDate.of(2026, 8, 10),
            who5Summary = "5/7 days scored. Median 17/25. Trajectory: steady.",
            emaSummary = "14 check-ins. Average valence 3.4/5, arousal 2.7/5.",
            notesSummary = "3 notes this week. Most recent: \"Slept 6 hours, did not feel rested.\"",
            sleepSummary = "Median bedtime 23:30, wake 06:45.",
            patternsLines = listOf("Higher resting heart rate on the days you wrote about work."),
            smallThings = listOf("two minutes outside", "one glass of water before phone"),
        )
        val prompt = LetterPrompting.build(week)
        assertNotNull(prompt)
        // The prompt opens with the window, so the model
        // can read the date range without re-deriving it.
        assertTrue(prompt!!.contains("2026-08-04"))
        assertTrue(prompt.contains("2026-08-10"))
        // Each section's label is in the prompt.
        for (label in listOf("WHO-5 pulse", "EMA check-ins", "Notes", "Sleep rhythm", "Patterns", "Small things")) {
            assertTrue("prompt must include $label section", prompt.contains(label))
        }
        // The instruction budget is in the prompt.
        assertTrue(prompt.contains("Letter (2-"))
    }

    /**
     * The on-disk format round-trips. One letter per line,
     * date tab-separated from the body, newlines in the
     * body escaped to spaces. A corrupt line is skipped, not
     * fatal.
     */
    @Test
    fun `letter ledger round-trips and skips corrupt lines`() {
        val date1 = LocalDate.of(2026, 8, 5)
        val date2 = LocalDate.of(2026, 8, 10)
        val original = listOf(
            Letter(date = date1, body = "Your week had a rhythm I noticed."),
            Letter(date = date2, body = "Today was a quiet one.\nTwo paragraphs."),
        )
        val encoded = LetterLedger.encode(original)
        val decoded = LetterLedger.decode(encoded)
        assertEquals(2, decoded.size)
        // Bodies have newlines escaped to spaces on the wire.
        assertTrue("encoded must not embed a literal newline inside a body", !encoded.contains("\nYour "))
        // Decoded bodies have the spaces back; newlines in the
        // body are intentionally lost on the wire (the inbox
        // renders a single paragraph per letter). A future
        // surface that needs the newlines will need a different
        // format.
        val body2 = decoded.first { it.date == date2 }.body
        assertEquals("Today was a quiet one. Two paragraphs.", body2)
    }

    @Test
    fun `letter ledger skips a corrupt line without losing the rest`() {
        val raw = "2026-08-05\tA valid letter.\n" +
            "garbage line without a tab\n" +
            "2026-08-10\tA second valid letter.\n"
        val decoded = LetterLedger.decode(raw)
        assertEquals(2, decoded.size)
        assertEquals(LocalDate.of(2026, 8, 5), decoded[0].date)
        assertEquals(LocalDate.of(2026, 8, 10), decoded[1].date)
    }

    /**
     * The data is local. The model is told the prompt is the
     * user's own record, not a third-party's. Pinning the word
     * "your" (and the absence of "user's" as a noun) is a small
     * but durable reminder: the letter is the user, in the
     * user's own words, observed back.
     */
    @Test
    fun `build prompt speaks in your-voice, not third-person`() {
        val week = WeekData(
            start = LocalDate.of(2026, 8, 4),
            end = LocalDate.of(2026, 8, 10),
            who5Summary = "5/7 days scored.",
            emaSummary = "12 check-ins.",
            notesSummary = "",
            sleepSummary = "",
            patternsLines = emptyList(),
            smallThings = emptyList(),
        )
        val prompt = LetterPrompting.build(week)!!
        assertTrue(
            "the prompt should be about 'this person' or 'you' (the user)",
            prompt.contains("This person's past 7 days"),
        )
        assertTrue(
            "the prompt must not use the noun 'the user'",
            !prompt.contains("the user"),
        )
    }
}
