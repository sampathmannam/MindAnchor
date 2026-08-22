package org.mindanchor.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 9 [LetterError] variants are disjoint, and each has
 * a user message that fits on one line in a CalmBackground
 * (≤ 60 chars). The retryable flag drives whether the
 * `Try again` button is shown; the user message is the
 * only thing the user sees.
 */
class LetterErrorMappingTest {

    @Test
    fun `each LetterError variant has a non-empty user message`() {
        val variants: List<LetterError> = listOf(
            LetterError.NoApiKey(),
            LetterError.InvalidApiKey(),
            LetterError.AccountUnauthorized(),
            LetterError.ModelNotFound(),
            LetterError.RateLimited(),
            LetterError.ServerError(),
            LetterError.NetworkUnreachable(),
            LetterError.Timeout(),
            LetterError.Unknown(),
        )
        for (v in variants) {
            assertTrue("userMessage blank for ${v::class.simpleName}", v.userMessage.isNotBlank())
        }
    }

    @Test
    fun `each user message fits on one line`() {
        // CalmBackground wraps a 60+ char message inelegantly
        // and the line ends mid-word. The threshold is
        // generous (60 chars; the longest message is 51 chars
        // today) but pins the discipline: a new variant with
        // a 90-char message fails the build, which is the
        // point.
        val variants: List<LetterError> = listOf(
            LetterError.NoApiKey(),
            LetterError.InvalidApiKey(),
            LetterError.AccountUnauthorized(),
            LetterError.ModelNotFound(),
            LetterError.RateLimited(),
            LetterError.ServerError(),
            LetterError.NetworkUnreachable(),
            LetterError.Timeout(),
            LetterError.Unknown(),
        )
        for (v in variants) {
            assertTrue(
                "userMessage for ${v::class.simpleName} is ${v.userMessage.length} chars (>60): '${v.userMessage}'",
                v.userMessage.length <= 60,
            )
        }
    }

    @Test
    fun `NoApiKey InvalidApiKey AccountUnauthorized ModelNotFound are not retryable`() {
        // The user has to fix a setting; tapping `Try again`
        // would just re-fail. The flag drives the button
        // visibility.
        assertFalse(LetterError.NoApiKey().isRetryable)
        assertFalse(LetterError.InvalidApiKey().isRetryable)
        assertFalse(LetterError.AccountUnauthorized().isRetryable)
        assertFalse(LetterError.ModelNotFound().isRetryable)
    }

    @Test
    fun `RateLimited ServerError NetworkUnreachable Timeout Unknown are retryable`() {
        assertTrue(LetterError.RateLimited().isRetryable)
        assertTrue(LetterError.ServerError().isRetryable)
        assertTrue(LetterError.NetworkUnreachable().isRetryable)
        assertTrue(LetterError.Timeout().isRetryable)
        assertTrue(LetterError.Unknown().isRetryable)
    }

    @Test
    fun `each variant simpleName is the expected class name`() {
        // The variant's simple name is what gets stored in
        // the letter_generation_log's `errorClass` column.
        // Renaming a class without updating the log writer
        // would silently corrupt the audit data; the test
        // pins the names.
        assertEquals("NoApiKey", LetterError.NoApiKey()::class.simpleName)
        assertEquals("InvalidApiKey", LetterError.InvalidApiKey()::class.simpleName)
        assertEquals("AccountUnauthorized", LetterError.AccountUnauthorized()::class.simpleName)
        assertEquals("ModelNotFound", LetterError.ModelNotFound()::class.simpleName)
        assertEquals("RateLimited", LetterError.RateLimited()::class.simpleName)
        assertEquals("ServerError", LetterError.ServerError()::class.simpleName)
        assertEquals("NetworkUnreachable", LetterError.NetworkUnreachable()::class.simpleName)
        assertEquals("Timeout", LetterError.Timeout()::class.simpleName)
        assertEquals("Unknown", LetterError.Unknown()::class.simpleName)
    }
}
