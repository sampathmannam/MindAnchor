package org.mindanchor.llm

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
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
        assertEquals("", prefs.apiKeyFor(LlmProvider.GOOGLE_AI_STUDIO).first())
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
        prefs.setApiKey(LlmProvider.GOOGLE_AI_STUDIO, "test-key-abc")
        assertEquals("test-key-abc", prefs.apiKeyFor(LlmProvider.GOOGLE_AI_STUDIO).first())
    }

    @Test
    fun `setModel then read returns the same model`() = runBlocking {
        val newModel = LlmProvider.GOOGLE_AI_STUDIO.suggestedModels[1]
        prefs.setModel(newModel)
        assertEquals(newModel, prefs.model.first())
    }

    @Test
    fun `setProvider round-trips for all three values`() = runBlocking {
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
        prefs.setApiKey(LlmProvider.GOOGLE_AI_STUDIO, "key-to-clear")
        prefs.setLastTestResult(LlmTestResult(true, "ok", 1L))
        prefs.reset()
        assertEquals("", prefs.apiKeyFor(LlmProvider.GOOGLE_AI_STUDIO).first())
        assertEquals(LlmTestResult.NONE, prefs.lastTestResult.first())
    }

    // v0.70+ (bug fix, part 2) — each provider is a
    // separate service with an incompatible key
    // format; a shared slot meant switching providers
    // silently tested the wrong service's key. This is
    // the regression test for that: setting a key for
    // one provider must never be visible under another.

    @Test
    fun `each provider keeps its own api key`() = runBlocking {
        prefs.setApiKey(LlmProvider.GOOGLE_AI_STUDIO, "google-key")
        prefs.setApiKey(LlmProvider.OPENROUTER, "openrouter-key")
        prefs.setApiKey(LlmProvider.GROQ, "groq-key")

        assertEquals("google-key", prefs.apiKeyFor(LlmProvider.GOOGLE_AI_STUDIO).first())
        assertEquals("openrouter-key", prefs.apiKeyFor(LlmProvider.OPENROUTER).first())
        assertEquals("groq-key", prefs.apiKeyFor(LlmProvider.GROQ).first())
    }

    @Test
    fun `clear wipes the api key for every provider`() = runBlocking {
        for (p in LlmProvider.values()) {
            prefs.setApiKey(p, "key-for-${p.name}")
        }
        prefs.reset()
        for (p in LlmProvider.values()) {
            assertEquals("", prefs.apiKeyFor(p).first())
        }
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
        prefs.setApiKey(LlmProvider.GOOGLE_AI_STUDIO, payload)
        val stored = prefs.apiKeyFor(LlmProvider.GOOGLE_AI_STUDIO).first()
        assertTrue(stored.length <= LlmPrefs.MAX_KEY_LEN)
        // The stored value is the 256-char truncation.
        assertEquals(LlmPrefs.MAX_KEY_LEN, stored.length)
    }

    @Test
    fun `setApiKey strips CRLF from header-injection payload`() = runBlocking {
        prefs.setApiKey(LlmProvider.GOOGLE_AI_STUDIO, "abc\r\nX-Evil-Header: pwned")
        val stored = prefs.apiKeyFor(LlmProvider.GOOGLE_AI_STUDIO).first()
        assertTrue(!stored.contains("\r"))
        assertTrue(!stored.contains("\n"))
        assertEquals("abcX-Evil-Header: pwned", stored)
    }

    @Test
    fun `setApiKey empty or whitespace is a no-op`() = runBlocking {
        prefs.setApiKey(LlmProvider.GOOGLE_AI_STUDIO, "   ")
        assertEquals("", prefs.apiKeyFor(LlmProvider.GOOGLE_AI_STUDIO).first())
        prefs.setApiKey(LlmProvider.GOOGLE_AI_STUDIO, "")
        assertEquals("", prefs.apiKeyFor(LlmProvider.GOOGLE_AI_STUDIO).first())
    }

    @Test
    fun `setApiKey trims leading and trailing whitespace`() = runBlocking {
        prefs.setApiKey(LlmProvider.GOOGLE_AI_STUDIO, "  sk-abc-12345  ")
        assertEquals("sk-abc-12345", prefs.apiKeyFor(LlmProvider.GOOGLE_AI_STUDIO).first())
    }

    // v0.70+ (bug fix) — the Settings screen and its
    // ViewModel never call apiKey.first() on every
    // read; they collect it once into a StateFlow via
    // stateIn() and read .value from then on, exactly
    // like LlmSettingsViewModel does. The tests above
    // all call .first() fresh after every write, which
    // masked the real bug: the old `flow { emit(...) }`
    // implementation only ever read the encrypted store
    // once per collection and then completed, so a
    // collector that started *before* a write never saw
    // it — the API key field looked like it kept
    // reverting to blank, and Test Connection kept using
    // a stale value.
    @Test
    fun `a collector started before the write still sees the new key`() = runBlocking {
        val scope = CoroutineScope(Job())
        val state = prefs.apiKeyFor(LlmProvider.GOOGLE_AI_STUDIO).stateIn(scope, SharingStarted.Eagerly, "")
        prefs.setApiKey(LlmProvider.GOOGLE_AI_STUDIO, "fresh-key")
        delay(50)
        assertEquals("fresh-key", state.value)
        scope.cancel()
    }
}
