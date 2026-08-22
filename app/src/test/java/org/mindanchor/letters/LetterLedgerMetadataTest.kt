package org.mindanchor.letters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * The v0.25.7+ wire format adds 5 optional tab-separated
 * fields after the body. The round-trip + the backward-
 * compat decode are pinned here.
 */
class LetterLedgerMetadataTest {

    @Test
    fun `encode then decode round-trips a letter with all metadata fields`() {
        val letter = Letter(
            date = LocalDate.of(2026, 8, 22),
            body = "It was a quiet Tuesday.",
            provider = "groq",
            model = "llama-3.3-70b-versatile",
            promptTokens = 1240,
            completionTokens = 380,
            durationMs = 1234L,
        )
        val encoded = LetterLedger.encode(listOf(letter))
        val decoded = LetterLedger.decode(encoded)
        assertEquals(1, decoded.size)
        assertEquals(letter, decoded[0])
    }

    @Test
    fun `encode then decode round-trips a letter with partial metadata`() {
        val letter = Letter(
            date = LocalDate.of(2026, 8, 22),
            body = "A letter with provider but no model.",
            provider = "groq",
            model = null,
            promptTokens = null,
            completionTokens = null,
            durationMs = null,
        )
        val encoded = LetterLedger.encode(listOf(letter))
        val decoded = LetterLedger.decode(encoded)
        assertEquals(letter, decoded[0])
    }

    @Test
    fun `decode of a pre-v0_25_7 line returns a letter with null metadata`() {
        // A v0.25.5/v0.25.6 line on disk: just date + body.
        val raw = "2026-08-15\tA canned letter from the local Phi-4 model.\n"
        val decoded = LetterLedger.decode(raw)
        assertEquals(1, decoded.size)
        val letter = decoded[0]
        assertEquals(LocalDate.of(2026, 8, 15), letter.date)
        assertEquals("A canned letter from the local Phi-4 model.", letter.body)
        assertNull(letter.provider)
        assertNull(letter.model)
        assertNull(letter.promptTokens)
        assertNull(letter.completionTokens)
        assertNull(letter.durationMs)
    }

    @Test
    fun `encode of a letter with null provider produces the pre-v0_25_7 line shape`() {
        val letter = Letter(date = LocalDate.of(2026, 8, 15), body = "Canned.")
        val encoded = LetterLedger.encode(listOf(letter))
        // 2 tab-separated fields, no trailing tabs.
        val fields = encoded.trim().split('\t')
        assertEquals(2, fields.size)
    }

    @Test
    fun `decode handles a v0_25_7 line with 5 empty metadata fields as all-null`() {
        // A future contributor who writes an empty metadata
        // payload (e.g. a failed generation that still saved
        // a row) gets a Letter with provider="" but the
        // decoder treats empty strings as null per
        // getOrNull(...).takeIf { it.isNotEmpty() }.
        val raw = "2026-08-22\tA letter with empty metadata.\t\t\t\t\t\n"
        val decoded = LetterLedger.decode(raw)
        assertEquals(1, decoded.size)
        val letter = decoded[0]
        assertNotNull(letter)
        assertNull(letter.provider)
        assertNull(letter.model)
        assertNull(letter.promptTokens)
        assertNull(letter.completionTokens)
        assertNull(letter.durationMs)
    }
}
