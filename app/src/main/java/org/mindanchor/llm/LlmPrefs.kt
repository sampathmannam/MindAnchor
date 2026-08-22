package org.mindanchor.llm

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.letterLlmDataStore by preferencesDataStore(name = "letter_llm")

/**
 * The result of the last "Test connection" tap in Settings →
 * Reading → Daily letter (LLM). [success] drives the status
 * row's icon and color; [message] is the line shown beneath
 * the row label; [testedAtMillis] is rendered as a relative
 * timestamp ("2 minutes ago") in the row.
 */
data class LlmTestResult(
    val success: Boolean,
    val message: String,
    val testedAtMillis: Long,
) {
    companion object {
        /** Empty result for a fresh install. */
        val NONE = LlmTestResult(success = false, message = "", testedAtMillis = 0L)
    }
}

/**
 * The BYOK API key + provider + model selection, stored
 * in a *separate* DataStore file (letter_llm.preferences_pb)
 * so the key doesn't sit next to the rest of the launcher's
 * settings. Threat model: the device, not the Windows
 * account. [EncryptedSharedPreferences] is the documented
 * upgrade path for a follow-up; not enabled by default
 * because the launcher's other settings live in plain
 * DataStore and the key is the only sensitive value.
 */
class LlmPrefs(private val context: Context) {

    private val providerKey = stringPreferencesKey("provider")
    private val apiKeyKey = stringPreferencesKey("api_key")
    private val modelKey = stringPreferencesKey("model")
    private val lastTestSuccessKey = stringPreferencesKey("last_test_success")
    private val lastTestMessageKey = stringPreferencesKey("last_test_message")
    private val lastTestAtKey = longPreferencesKey("last_test_at")

    val provider: Flow<LlmProvider> = context.letterLlmDataStore.data.map { prefs ->
        when (prefs[providerKey]) {
            LlmProvider.GROQ.name -> LlmProvider.GROQ
            else -> LlmProvider.GROQ
        }
    }

    val apiKey: Flow<String> = context.letterLlmDataStore.data.map { prefs ->
        prefs[apiKeyKey].orEmpty()
    }

    val model: Flow<String> = context.letterLlmDataStore.data.map { prefs ->
        prefs[modelKey] ?: GroqModels.DEFAULT
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
        context.letterLlmDataStore.edit { it[apiKeyKey] = key }
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

    /**
     * Clears every key. Test-only — same pattern as
     * [org.mindanchor.letters.LetterStore.reset] (v0.25.5).
     */
    internal suspend fun reset() {
        context.letterLlmDataStore.edit { it.clear() }
    }
}
