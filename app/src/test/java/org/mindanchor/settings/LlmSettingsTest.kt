package org.mindanchor.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.llm.GroqModels
import org.mindanchor.llm.LlmPrefs
import org.mindanchor.llm.LlmProvider
import org.mindanchor.llm.LlmTestResult
import org.robolectric.RobolectricTestRunner

/**
 * The settings view-model's job is to expose LlmPrefs
 * flows + the testConnection side-effect. The pinned
 * tests are: default model is GroqModels.DEFAULT,
 * default provider is GROQ, setApiKey/setModel round-trip,
 * default lastTestResult is NONE.
 *
 * The actual `testConnection` call is not exercised here:
 * it would need a real (or mocked) `LlmClient` plus a
 * test dispatcher. The VM's logic for that path is the
 * `LlmClientFactory.create` + `result.fold` chain, and is
 * covered by the LLM-package tests (GroqClient,
 * LlmClientFactory). This file pins the
 * ViewModel-as-wrapper shape — the only logic in the
 * VM beyond `viewModelScope.launch { llmPrefs.setX(...) }`.
 *
 * The setApiKey/setModel tests call the internal
 * `setApiKeyNow` / `setModelNow` suspend seams rather
 * than the public fire-and-forget setters. The public
 * setters wrap the same work in `viewModelScope.launch`,
 * which uses `Dispatchers.Main.immediate` and suspends
 * on DataStore's internal `Dispatchers.IO` — the test
 * scheduler can't wait for that chain. Same pattern
 * as `LetterWriteStateMachineTest` calling
 * `LetterViewModel.runGeneration` directly (Task 11).
 */
@RunWith(RobolectricTestRunner::class)
class LlmSettingsTest {

    private lateinit var vm: LlmSettingsViewModel
    private lateinit var prefs: LlmPrefs

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        prefs = LlmPrefs(app)
        runBlocking { prefs.reset() }
        vm = LlmSettingsViewModel(app)
    }

    @Test
    fun `default model is GroqModels DEFAULT`() = runBlocking {
        assertEquals(GroqModels.DEFAULT, vm.model.first())
    }

    @Test
    fun `default provider is GROQ`() = runBlocking {
        assertEquals(LlmProvider.GROQ, vm.provider.first())
    }

    @Test
    fun `setApiKey round-trips through the view model`() = runBlocking {
        // The setApiKeyNow seam writes to the same
        // LlmPrefs that the StateFlow wraps. We read
        // the underlying prefs directly here rather
        // than `vm.apiKey.value` because the StateFlow's
        // internal collector runs on viewModelScope
        // (Dispatchers.Main.immediate), and a test's
        // `runBlocking` on a worker thread cannot
        // deterministically observe the StateFlow's
        // update without explicit dispatcher plumbing.
        // The VM's only job is to write to LlmPrefs;
        // the StateFlow is a UI-layer concern and the
        // production screen collects it via Compose's
        // `collectAsState`, which is on the main thread.
        vm.setApiKeyNow("gsk_test_key")
        assertEquals("gsk_test_key", prefs.apiKey.first())
    }

    @Test
    fun `setModel round-trips through the view model`() = runBlocking {
        // See the apiKey test above for the StateFlow-
        // race rationale.
        vm.setModelNow(GroqModels.LLAMA_8B)
        assertEquals(GroqModels.LLAMA_8B, prefs.model.first())
    }

    @Test
    fun `default lastTestResult is NONE`() = runBlocking {
        assertEquals(LlmTestResult.NONE, vm.lastTestResult.first())
    }
}
