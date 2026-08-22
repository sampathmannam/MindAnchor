package org.mindanchor.letters

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * Round-trip the generation log through DataStore. The
 * 9 fields per entry are pinned: 3 fixed strings (date,
 * provider, model), 4 numerics (promptTokens,
 * completionTokens, durationMs, timestamp), 2 nullable
 * strings (errorClass, errorMessage).
 */
@RunWith(RobolectricTestRunner::class)
class LetterGenerationLogTest {

    private lateinit var log: LetterGenerationLog

    @Before
    fun setUp() {
        log = LetterGenerationLog(ApplicationProvider.getApplicationContext())
        runBlocking { log.reset() }
    }

    @Test
    fun `append a successful entry then read returns the same entry`() = runBlocking {
        val entry = LetterLogEntry(
            date = LocalDate.of(2026, 8, 22),
            provider = "groq",
            model = "llama-3.3-70b-versatile",
            promptTokens = 1240,
            completionTokens = 380,
            durationMs = 1234L,
            errorClass = null,
            errorMessage = null,
            timestampMillis = 1_700_000_000_000L,
        )
        log.append(entry)
        val read = log.entries.first()
        assertEquals(1, read.size)
        assertEquals(entry, read[0])
    }

    @Test
    fun `append an error entry then read returns the same entry with errorClass set`() = runBlocking {
        val entry = LetterLogEntry(
            date = LocalDate.of(2026, 8, 22),
            provider = "groq",
            model = "llama-3.3-70b-versatile",
            promptTokens = null,
            completionTokens = null,
            durationMs = 30_000L,
            errorClass = "Timeout",
            errorMessage = "The request timed out. Try again.",
            timestampMillis = 1_700_000_000_000L,
        )
        log.append(entry)
        val read = log.entries.first()
        assertEquals(1, read.size)
        assertEquals("Timeout", read[0].errorClass)
        assertEquals("The request timed out. Try again.", read[0].errorMessage)
    }

    @Test
    fun `append 3 entries then read returns all 3 in append order`() = runBlocking {
        val e1 = entry("groq", 100L, 1_700_000_000_000L)
        val e2 = entry("groq", 200L, 1_700_000_001_000L)
        val e3 = entry("groq", 300L, 1_700_000_002_000L)
        log.append(e1)
        log.append(e2)
        log.append(e3)
        val read = log.entries.first()
        assertEquals(3, read.size)
        assertEquals(e1, read[0])
        assertEquals(e2, read[1])
        assertEquals(e3, read[2])
    }

    @Test
    fun `reset clears all entries`() = runBlocking {
        log.append(entry("groq", 100L, 1L))
        log.reset()
        assertEquals(0, log.entries.first().size)
    }

    @Test
    fun `success entry has null errorClass and errorMessage`() = runBlocking {
        val entry = entry("groq", 100L, 1L, errorClass = null, errorMessage = null)
        log.append(entry)
        val read = log.entries.first()[0]
        assertNull(read.errorClass)
        assertNull(read.errorMessage)
    }

    private fun entry(
        provider: String,
        durationMs: Long,
        timestamp: Long,
        errorClass: String? = null,
        errorMessage: String? = null,
    ) = LetterLogEntry(
        date = LocalDate.of(2026, 8, 22),
        provider = provider,
        model = "llama-3.3-70b-versatile",
        promptTokens = 1240,
        completionTokens = 380,
        durationMs = durationMs,
        errorClass = errorClass,
        errorMessage = errorMessage,
        timestampMillis = timestamp,
    )
}
