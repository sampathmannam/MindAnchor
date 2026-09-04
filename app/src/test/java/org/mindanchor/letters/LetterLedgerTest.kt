package org.mindanchor.letters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `encode strips embedded tabs from a body`() {
        // A tab is the field separator. Left in the body it does not
        // just mangle the text: the decoder counts fields, so the tail
        // after the tab is read back as the `provider` column and the
        // body is silently truncated at the tab.
        val letter = Letter(date = LocalDate.of(2026, 8, 5), body = "ran 5k\tfelt good")
        val decoded = LetterLedger.decode(LetterLedger.encode(listOf(letter)))
        assertEquals(1, decoded.size)
        assertEquals("ran 5k felt good", decoded[0].body)
        assertNull(decoded[0].provider)
    }

    @Test
    fun `encode strips a carriage return from a body`() {
        // lineSequence() treats a lone \r as a row terminator too, so a
        // body carrying one loses everything after it: the tail becomes
        // a line with no parseable date and is dropped on read.
        val letter = Letter(date = LocalDate.of(2026, 8, 5), body = "first half\rsecond half")
        val decoded = LetterLedger.decode(LetterLedger.encode(listOf(letter)))
        assertEquals(1, decoded.size)
        assertEquals("first half second half", decoded[0].body)
    }

    @Test
    fun `a tab in a body cannot forge letter metadata`() {
        // The injection shape: everything after a tab lands in the
        // metadata columns, so an unsanitized body could invent a
        // provider, a model and a token count it never had.
        val letter = Letter(date = LocalDate.of(2026, 8, 5), body = "note\tgroq\tllama-3.3\t99\t99\t99")
        val decoded = LetterLedger.decode(LetterLedger.encode(listOf(letter)))
        assertEquals(1, decoded.size)
        assertNull(decoded[0].provider)
        assertNull(decoded[0].model)
        assertNull(decoded[0].promptTokens)
    }

    @Test
    fun `a delimiter in a body survives the metadata shape too`() {
        // The same body, this time on a letter that legitimately has
        // metadata: the real columns must still line up.
        val letter = Letter(
            date = LocalDate.of(2026, 8, 5),
            body = "ran 5k\tfelt good",
            provider = "groq",
            model = "llama-3.3-70b-versatile",
            promptTokens = 12,
            completionTokens = 34,
            durationMs = 567L,
        )
        val decoded = LetterLedger.decode(LetterLedger.encode(listOf(letter)))
        assertEquals(listOf(letter.copy(body = "ran 5k felt good")), decoded)
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
