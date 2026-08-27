package org.mindanchor.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.mindanchor.llm.LetterError
import org.mindanchor.llm.LlmClientFactory
import org.mindanchor.llm.LlmPrefs
import org.mindanchor.llm.LlmProvider
import org.mindanchor.llm.LlmTestResult

class LlmSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val llmPrefs = LlmPrefs(application)

    val provider: StateFlow<LlmProvider> = llmPrefs.provider.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LlmProvider.GOOGLE_AI_STUDIO,
    )

    val apiKey: StateFlow<String> = llmPrefs.apiKey.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = "",
    )

    val model: StateFlow<String> = llmPrefs.model.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LlmProvider.GOOGLE_AI_STUDIO.defaultModel,
    )

    val lastTestResult: StateFlow<LlmTestResult> = llmPrefs.lastTestResult.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LlmTestResult.NONE,
    )

    val signupUrl: StateFlow<String> = provider.map { it.signupUrl }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LlmProvider.GOOGLE_AI_STUDIO.signupUrl,
    )

    fun setProvider(p: LlmProvider) {
        viewModelScope.launch { setProviderNow(p) }
    }

    fun setApiKey(key: String) {
        viewModelScope.launch { setApiKeyNow(key) }
    }

    /**
     * v0.72.x: the privacy off-switch. Wipes the API key
     * from the encrypted blob and resets the cached
     * test-result row so the Connection row is back to
     * "Never tested" rather than the last error from
     * the wiped key.
     */
    fun clearApiKey() {
        viewModelScope.launch { llmPrefs.clearApiKey() }
    }

    fun setModel(model: String) {
        viewModelScope.launch { setModelNow(model) }
    }

    internal suspend fun setProviderNow(p: LlmProvider) {
        val currentModel = llmPrefs.model.first()
        if (currentModel !in p.suggestedModels) {
            llmPrefs.setModel(p.defaultModel)
        }
        llmPrefs.setProvider(p)
    }

    internal suspend fun setApiKeyNow(key: String) {
        llmPrefs.setApiKey(key)
    }

    internal suspend fun setModelNow(model: String) {
        llmPrefs.setModel(model)
    }

    /**
     * v0.72.x: the voice the letter is written in.
     * Exposed as a [StateFlow] so the picker UI can
     * `collectAsState()` and reflect the choice.
     */
    val voice: StateFlow<org.mindanchor.llm.LetterVoice> = llmPrefs.voice.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = org.mindanchor.llm.LetterVoice.DEFAULT,
    )

    fun setVoice(voice: org.mindanchor.llm.LetterVoice) {
        viewModelScope.launch { llmPrefs.setVoice(voice) }
    }

    internal suspend fun setVoiceNow(voice: org.mindanchor.llm.LetterVoice) {
        llmPrefs.setVoice(voice)
    }

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
                        message = "Connected · ${p.displayName} · $m",
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
