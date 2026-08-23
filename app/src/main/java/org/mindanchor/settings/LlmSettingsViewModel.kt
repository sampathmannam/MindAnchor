package org.mindanchor.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.mindanchor.llm.GroqModels
import org.mindanchor.llm.LetterError
import org.mindanchor.llm.LlmClientFactory
import org.mindanchor.llm.LlmPrefs
import org.mindanchor.llm.LlmProvider
import org.mindanchor.llm.LlmTestResult

/**
 * Backs the Settings → Reading → Daily letter (LLM)
 * section. Thin wrapper over [LlmPrefs]: the
 * [provider] / [apiKey] / [model] / [lastTestResult] are
 * direct flows; [testConnection] is the one piece of
 * business logic.
 *
 * Uses [AndroidViewModel] (not the plain `ViewModel`) so
 * [LlmPrefs] can be constructed with an [Application]
 * context — the DataStore extension delegate it owns only
 * resolves on a real [android.content.Context], not the
 * one [ViewModel] would otherwise provide.
 */
class LlmSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val llmPrefs = LlmPrefs(application)

    val provider: StateFlow<LlmProvider> = llmPrefs.provider.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LlmProvider.GROQ,
    )

    val apiKey: StateFlow<String> = llmPrefs.apiKey.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = "",
    )

    val model: StateFlow<String> = llmPrefs.model.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GroqModels.DEFAULT,
    )

    val lastTestResult: StateFlow<LlmTestResult> = llmPrefs.lastTestResult.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LlmTestResult.NONE,
    )

    fun setApiKey(key: String) {
        viewModelScope.launch { setApiKeyNow(key) }
    }

    fun setModel(model: String) {
        viewModelScope.launch { setModelNow(model) }
    }

    /**
     * Suspend seam for the test layer — see [LlmSettingsTest].
     * The public [setApiKey] wraps this in `viewModelScope.launch`,
     * which uses `Dispatchers.Main.immediate` and suspends on
     * DataStore's internal `Dispatchers.IO`. A test's `runBlocking`
     * block can't wait for that chain, so the test calls this
     * internal function directly to observe the write synchronously.
     * Same pattern as [org.mindanchor.letters.LetterViewModel.runGeneration]
     * (Task 11).
     */
    internal suspend fun setApiKeyNow(key: String) {
        llmPrefs.setApiKey(key)
    }

    internal suspend fun setModelNow(model: String) {
        llmPrefs.setModel(model)
    }

    /**
     * Fires one "OK" completion against the currently
     * selected provider / key / model and writes the
     * result back to [lastTestResult]. The section's
     * status row reads [lastTestResult] — it does not
     * observe the call as it runs. The success message
     * is `Connected · PROVIDER · MODEL`; the failure
     * message is `Failed: <userMessage>` so a wrong key
     * or a rate-limit shows the same line the Letter
     * surface shows on the same error.
     */
    fun testConnection() {
        viewModelScope.launch {
            val key = apiKey.value
            val m = model.value
            val p = provider.value
            val client = LlmClientFactory.create(p, key, m)
            val result = client.testConnection()
            val testResult = result.fold(
                onSuccess = {
                    LlmTestResult(
                        success = true,
                        message = "Connected · ${p.name} · $m",
                        testedAtMillis = System.currentTimeMillis(),
                    )
                },
                onFailure = { e ->
                    val userMessage = (e as? LetterError)?.userMessage
                        ?: e.message
                        ?: "Unknown error"
                    LlmTestResult(
                        success = false,
                        message = "Failed: $userMessage",
                        testedAtMillis = System.currentTimeMillis(),
                    )
                },
            )
            llmPrefs.setLastTestResult(testResult)
        }
    }
}
