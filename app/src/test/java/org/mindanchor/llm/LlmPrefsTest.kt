package org.mindanchor.llm

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
        prefs.setApiKey("key-to-clear")
        prefs.setLastTestResult(LlmTestResult(true, "ok", 1L))
        prefs.reset()
        assertEquals("", prefs.apiKey.first())
        assertEquals(LlmTestResult.NONE, prefs.lastTestResult.first())
    }
}
