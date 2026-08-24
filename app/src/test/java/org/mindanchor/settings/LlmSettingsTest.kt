package org.mindanchor.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.llm.LlmProvider
import org.mindanchor.llm.LlmTestResult
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LlmSettingsTest {

    private lateinit var vm: LlmSettingsViewModel
    private lateinit var prefs: org.mindanchor.llm.LlmPrefs

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        prefs = org.mindanchor.llm.LlmPrefs(app)
        runBlocking { prefs.reset() }
        vm = LlmSettingsViewModel(app)
    }

    @Test
    fun `default model is the recommended provider's default`() = runBlocking {
        assertEquals(LlmProvider.GOOGLE_AI_STUDIO.defaultModel, vm.model.first())
    }

    @Test
    fun `default provider is GOOGLE_AI_STUDIO`() = runBlocking {
        assertEquals(LlmProvider.GOOGLE_AI_STUDIO, vm.provider.first())
    }

    @Test
    fun `default signupUrl is the recommended provider's signupUrl`() = runBlocking {
        assertEquals(
            LlmProvider.GOOGLE_AI_STUDIO.signupUrl,
            vm.signupUrl.value,
        )
    }

    @Test
    fun `every LlmProvider has a non-empty https signupUrl`() = runBlocking {
        for (p in LlmProvider.values()) {
            assertTrue(
                "Provider $p must have a non-empty https:// signupUrl: got '${p.signupUrl}'",
                p.signupUrl.startsWith("https://") && p.signupUrl.length > 10,
            )
        }
    }

    @Test
    fun `setApiKey round-trips through the view model`() = runBlocking {
        vm.setApiKeyNow("test-key")
        assertEquals("test-key", prefs.apiKey.first())
    }

    @Test
    fun `setModel round-trips through the view model`() = runBlocking {
        val newModel = LlmProvider.GOOGLE_AI_STUDIO.suggestedModels[1]
        vm.setModelNow(newModel)
        assertEquals(newModel, prefs.model.first())
    }

    @Test
    fun `setProvider round-trips through the view model`() = runBlocking {
        vm.setProviderNow(LlmProvider.OPENROUTER)
        assertEquals(LlmProvider.OPENROUTER, prefs.provider.first())
    }

    @Test
    fun `setProvider resets the saved model when not in new provider's suggestedModels`() = runBlocking {
        val crossProviderModel = LlmProvider.GROQ.suggestedModels[0]
        vm.setModelNow(crossProviderModel)
        vm.setProviderNow(LlmProvider.GOOGLE_AI_STUDIO)
        assertEquals(LlmProvider.GOOGLE_AI_STUDIO, prefs.provider.first())
        assertEquals(
            LlmProvider.GOOGLE_AI_STUDIO.defaultModel,
            prefs.model.first(),
        )
    }

    @Test
    fun `default lastTestResult is NONE`() = runBlocking {
        assertEquals(LlmTestResult.NONE, vm.lastTestResult.first())
    }
}
