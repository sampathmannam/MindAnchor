package org.mindanchor.llm

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LlmPrefsTest {

    private lateinit var prefs: LlmPrefs

    @Before
    fun setUp() {
        prefs = LlmPrefs(ApplicationProvider.getApplicationContext())
        runBlocking { prefs.reset() }
    }

    @Test
    fun `default provider is GOOGLE_AI_STUDIO`() = runBlocking {
        assertEquals(LlmProvider.GOOGLE_AI_STUDIO, prefs.provider.first())
    }

    @Test
    fun `default api key is empty`() = runBlocking {
        assertEquals("", prefs.apiKey.first())
    }

    @Test
    fun `default model is the recommended provider's default`() = runBlocking {
        assertEquals(
            LlmProvider.GOOGLE_AI_STUDIO.defaultModel,
            prefs.model.first(),
        )
    }

    @Test
    fun `setApiKey then read returns the same key`() = runBlocking {
        prefs.setApiKey("test-key-abc")
        assertEquals("test-key-abc", prefs.apiKey.first())
    }

    @Test
    fun `setModel then read returns the same model`() = runBlocking {
        val newModel = LlmProvider.GOOGLE_AI_STUDIO.suggestedModels[1]
        prefs.setModel(newModel)
        assertEquals(newModel, prefs.model.first())
    }

    @Test
    fun `setProvider round-trips for all provider values`() = runBlocking {
        for (p in LlmProvider.values()) {
            prefs.setProvider(p)
            assertEquals(p, prefs.provider.first())
        }
    }

    @Test
    fun `setLastTestResult then read returns the same result`() = runBlocking {
        val result = LlmTestResult(
            success = true,
            message = "Connected · Google AI Studio · gemini-2.0-flash",
            testedAtMillis = 1_700_000_000_000L,
        )
        prefs.setLastTestResult(result)
        val read = prefs.lastTestResult.first()
        assertEquals(result, read)
    }

    @Test
    fun `clear wipes the api key and the test result`() = runBlocking {
        prefs.setApiKey("key-to-clear")
        prefs.setLastTestResult(LlmTestResult(true, "ok", 1L))
        prefs.reset()
        assertEquals("", prefs.apiKey.first())
        assertEquals(LlmTestResult.NONE, prefs.lastTestResult.first())
    }

    // v0.30+ (security audit 2026-08-24) — the
    // following three cases pin the [setApiKey]
    // adversarial-payload defences:
    //  - the 10 MB ASCII payload (memory / network
    //    pressure)
    //  - the CRLF-injection payload (header injection
    //    on a permissive provider)
    //  - the empty / whitespace-only payload
    //  - the over-cap (256 char) payload is truncated
    //    to [MAX_KEY_LEN]
    // The "happy path" above already covers the
    // round-trip; the four cases below are the
    // security cases.

    @Test
    fun `setApiKey rejects 10MB ASCII payload`() = runBlocking {
        val payload = "A".repeat(10 * 1024 * 1024)
        prefs.setApiKey(payload)
        val stored = prefs.apiKey.first()
        assertTrue(stored.length <= LlmPrefs.MAX_KEY_LEN)
        // The stored value is the 256-char truncation.
        assertEquals(LlmPrefs.MAX_KEY_LEN, stored.length)
    }

    @Test
    fun `setApiKey strips CRLF from header-injection payload`() = runBlocking {
        prefs.setApiKey("abc\r\nX-Evil-Header: pwned")
        val stored = prefs.apiKey.first()
        assertTrue(!stored.contains("\r"))
        assertTrue(!stored.contains("\n"))
        assertEquals("abcX-Evil-Header: pwned", stored)
    }

    @Test
    fun `setApiKey empty or whitespace is a no-op`() = runBlocking {
        prefs.setApiKey("   ")
        assertEquals("", prefs.apiKey.first())
        prefs.setApiKey("")
        assertEquals("", prefs.apiKey.first())
    }

    @Test
    fun `setApiKey trims leading and trailing whitespace`() = runBlocking {
        prefs.setApiKey("  sk-abc-12345  ")
        assertEquals("sk-abc-12345", prefs.apiKey.first())
    }
}
