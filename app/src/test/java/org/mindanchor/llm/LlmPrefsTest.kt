package org.mindanchor.llm

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Round-trip the BYOK API key + provider + model through
 * the real DataStore. The test uses Robolectric so the
 * DataStore extension delegate can resolve a real
 * Android Context (the unit-test JVM doesn't have one).
 */
@RunWith(RobolectricTestRunner::class)
class LlmPrefsTest {

    private lateinit var prefs: LlmPrefs

    @Before
    fun setUp() {
        prefs = LlmPrefs(ApplicationProvider.getApplicationContext())
        runBlocking { prefs.reset() }
    }

    @Test
    fun `default provider is GROQ`() = runBlocking {
        assertEquals(LlmProvider.GROQ, prefs.provider.first())
    }

    @Test
    fun `default api key is empty`() = runBlocking {
        assertEquals("", prefs.apiKey.first())
    }

    @Test
    fun `default model is GroqModels DEFAULT`() = runBlocking {
        assertEquals(GroqModels.DEFAULT, prefs.model.first())
    }

    @Test
    fun `setApiKey then read returns the same key`() = runBlocking {
        prefs.setApiKey("gsk_test_abc123")
        assertEquals("gsk_test_abc123", prefs.apiKey.first())
    }

    @Test
    fun `setModel then read returns the same model`() = runBlocking {
        prefs.setModel(GroqModels.LLAMA_8B)
        assertEquals(GroqModels.LLAMA_8B, prefs.model.first())
    }

    @Test
    fun `setLastTestResult then read returns the same result`() = runBlocking {
        val result = LlmTestResult(
            success = true,
            message = "Connected · Groq · llama-3.3-70b",
            testedAtMillis = 1_700_000_000_000L,
        )
        prefs.setLastTestResult(result)
        val read = prefs.lastTestResult.first()
        assertEquals(result, read)
    }

    @Test
    fun `clear wipes the api key and the test result`() = runBlocking {
        prefs.setApiKey("gsk_to_be_cleared")
        prefs.setLastTestResult(LlmTestResult(true, "ok", 1L))
        prefs.reset()
        assertEquals("", prefs.apiKey.first())
        assertEquals(LlmTestResult.NONE, prefs.lastTestResult.first())
    }
}
