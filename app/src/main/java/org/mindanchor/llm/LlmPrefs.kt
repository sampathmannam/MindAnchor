package org.mindanchor.llm

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.letterLlmDataStore by preferencesDataStore(name = "letter_llm")

data class LlmTestResult(
    val success: Boolean,
    val message: String,
    val testedAtMillis: Long,
) {
    companion object {
        val NONE = LlmTestResult(success = false, message = "", testedAtMillis = 0L)
    }
}

class LlmPrefs(private val context: Context) {

    private val providerKey = stringPreferencesKey("provider")
    private val apiKeyKey = stringPreferencesKey("api_key")
    private val modelKey = stringPreferencesKey("model")
    private val lastTestSuccessKey = stringPreferencesKey("last_test_success")
    private val lastTestMessageKey = stringPreferencesKey("last_test_message")
    private val lastTestAtKey = longPreferencesKey("last_test_at")

    val provider: Flow<LlmProvider> = context.letterLlmDataStore.data.map { prefs ->
        when (prefs[providerKey]) {
            LlmProvider.GOOGLE_AI_STUDIO.name -> LlmProvider.GOOGLE_AI_STUDIO
            LlmProvider.OPENROUTER.name -> LlmProvider.OPENROUTER
            LlmProvider.GROQ.name -> LlmProvider.GROQ
            else -> LlmProvider.GOOGLE_AI_STUDIO
        }
    }

    val apiKey: Flow<String> = context.letterLlmDataStore.data.map { prefs ->
        prefs[apiKeyKey].orEmpty()
    }

    val model: Flow<String> = context.letterLlmDataStore.data.map { prefs ->
        prefs[modelKey] ?: LlmProvider.GOOGLE_AI_STUDIO.defaultModel
    }

    val lastTestResult: Flow<LlmTestResult> = context.letterLlmDataStore.data.map { prefs ->
        val success = prefs[lastTestSuccessKey]?.toBooleanStrictOrNull() ?: false
        val message = prefs[lastTestMessageKey].orEmpty()
        val at = prefs[lastTestAtKey] ?: 0L
        if (at == 0L) LlmTestResult.NONE else LlmTestResult(success, message, at)
    }

    suspend fun setProvider(provider: LlmProvider) {
        context.letterLlmDataStore.edit { it[providerKey] = provider.name }
    }

    suspend fun setApiKey(key: String) {
        // v0.30+ (security audit 2026-08-24) — the
        // previous version accepted any string
        // unbounded. The audit's adversarial payload
        // was a 10 MB ASCII string that, stored in
        // DataStore, would be re-sent on every LLM
        // call as a 10 MB Authorization header. The
        // fix mirrors the spec's "validate the
        // LLM-bridge setApiKey" remediation: trim,
        // cap, reject control chars, reject empty
        // post-trim.
        val cleaned = key
            .trim()
            .take(MAX_KEY_LEN)
            .filter { !it.isISOControl() }
        if (cleaned.isEmpty()) return
        context.letterLlmDataStore.edit { it[apiKeyKey] = cleaned }
    }

    suspend fun setModel(model: String) {
        context.letterLlmDataStore.edit { it[modelKey] = model }
    }

    suspend fun setLastTestResult(result: LlmTestResult) {
        context.letterLlmDataStore.edit { prefs ->
            prefs[lastTestSuccessKey] = result.success.toString()
            prefs[lastTestMessageKey] = result.message
            prefs[lastTestAtKey] = result.testedAtMillis
        }
    }

    internal suspend fun reset() {
        context.letterLlmDataStore.edit { it.clear() }
    }

    companion object {
        // v0.30+ (security audit 2026-08-24) — the
        // LLM provider keys are at most 256 chars in
        // practice (Google AI Studio: 39; OpenRouter: 64;
        // Groq: 56). 256 is a comfortable ceiling that
        // also stops a 10 MB adversarial payload from
        // landing in DataStore.
        const val MAX_KEY_LEN = 256
    }
}
