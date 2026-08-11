package org.mindanchor.letters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The wire format for the letter inbox.
 *
 * A letter is one line on disk: `date\tbody`, with the body
 * having any embedded newlines escaped to spaces (a letter is
 * rendered as one paragraph in the inbox; a future surface
 * that needs the original newlines will need a different
 * format). The format is deliberately plain text, not JSON,
 * because a corrupt line must cost one day's letter, never
 * the whole inbox.
 */
class LetterLedgerTest {

    @Test
    fun `encode then decode returns the same letters in the same order`() {
        val letters = listOf(
            Letter(date = LocalDate.of(2026, 8, 5), body = "First letter."),
            Letter(date = LocalDate.of(2026, 8, 10), body = "Second letter."),
        )
        val encoded = LetterLedger.encode(letters)
        val decoded = LetterLedger.decode(encoded)
        assertEquals(letters, decoded)
    }

    @Test
    fun `encode strips embedded newlines from a body`() {
        val letter = Letter(date = LocalDate.of(2026, 8, 5), body = "line one\nline two")
        val encoded = LetterLedger.encode(listOf(letter))
        // The on-disk form is exactly one line for this letter.
        assertEquals(1, encoded.count { it == '\n' })
    }

    @Test
    fun `decode sorts the result by date`() {
        // If the on-disk order is corrupted, the inbox is still
        // sorted oldest-first. The launcher's display relies
        // on this; a future caller that needs a different
        // order sorts on top.
        val raw = "2026-08-10\tSecond.\n2026-08-05\tFirst.\n"
        val decoded = LetterLedger.decode(raw)
        assertEquals(2, decoded.size)
        assertEquals(LocalDate.of(2026, 8, 5), decoded[0].date)
        assertEquals(LocalDate.of(2026, 8, 10), decoded[1].date)
    }

    @Test
    fun `decode rejects an empty body`() {
        // An empty body is a placeholder, not a real letter.
        // The line is skipped on read rather than shown as a
        // blank entry in the inbox.
        val raw = "2026-08-05\t\n2026-08-10\tReal letter.\n"
        val decoded = LetterLedger.decode(raw)
        assertEquals(1, decoded.size)
        assertEquals(LocalDate.of(2026, 8, 10), decoded[0].date)
    }

    @Test
    fun `decode rejects an unparseable date`() {
        val raw = "not-a-date\tA letter.\n2026-08-10\tReal.\n"
        val decoded = LetterLedger.decode(raw)
        assertEquals(1, decoded.size)
        assertEquals(LocalDate.of(2026, 8, 10), decoded[0].date)
    }

    @Test
    fun `encode then decode round-trips a non-ASCII body`() {
        val letter = Letter(
            date = LocalDate.of(2026, 8, 5),
            body = "Your week was quiet — gentle, really.",
        )
        val encoded = LetterLedger.encode(listOf(letter))
        assertTrue("em-dash must survive the wire format", encoded.contains("—"))
        val decoded = LetterLedger.decode(encoded)
        assertEquals(letter, decoded.single())
    }
}
