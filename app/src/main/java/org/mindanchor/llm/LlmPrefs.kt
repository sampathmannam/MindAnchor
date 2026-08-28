package org.mindanchor.llm

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

/**
 * v0.30+ (security audit 2026-08-25) — the API
 * key is the only secret in the LLM surface. It
 * previously lived in the plain DataStore at
 * `letter_llm.preferences_pb`, which is a typed
 * key-value file on disk; any process with read
 * access to the app sandbox (root, forensic
 * acquisition, a sibling app under a shared UID)
 * could read it.
 *
 * The fix: the API key moves to an
 * [EncryptedSharedPreferences] blob, keyed by
 * the Keystore-backed master key (the same
 * [MasterKey] scheme the v0.25.4 Google Drive
 * token store uses). The other fields (provider,
 * model, last test result) stay in the plain
 * DataStore because they are not secrets.
 *
 * The encrypted blob is a normal file under
 * `/data/data/org.mindanchor/shared_prefs/`. The
 * raw bytes are useless without the Keystore key,
 * which never leaves the secure hardware.
 */
class LlmPrefs(private val context: Context) {

    private val providerKey = stringPreferencesKey("provider")
    private val modelKey = stringPreferencesKey("model")
    private val lastTestSuccessKey = stringPreferencesKey("last_test_success")
    private val lastTestMessageKey = stringPreferencesKey("last_test_message")
    private val lastTestAtKey = longPreferencesKey("last_test_at")

    /**
     * The encrypted-prefs handle for the API key.
     * `by lazy` because [MasterKey] is built on
     * first access, and we do not want to spin
     * the Keystore on a simple [provider] read.
     */
    private val keyStore: LlmKeyStore by lazy { LlmKeyStore.create(context) }

    val provider: Flow<LlmProvider> = context.letterLlmDataStore.data.map { prefs ->
        when (prefs[providerKey]) {
            LlmProvider.GOOGLE_AI_STUDIO.name -> LlmProvider.GOOGLE_AI_STUDIO
            LlmProvider.OPENROUTER.name -> LlmProvider.OPENROUTER
            LlmProvider.GROQ.name -> LlmProvider.GROQ
            else -> LlmProvider.GOOGLE_AI_STUDIO
        }
    }

    /**
     * v0.70+ (bug fix, part 2): the key is scoped
     * per [LlmProvider]. It used to be one shared
     * slot regardless of which provider was
     * selected — switching the provider chip left
     * whatever key was typed for the *previous*
     * provider sitting in the field, silently
     * tested against the *new* provider's API.
     * Google AI Studio, OpenRouter and Groq are
     * three separate services with incompatible
     * keys; a Groq key (`gsk_...`) sent to
     * OpenRouter's endpoint will 401 every time,
     * no matter how many times it is retyped —
     * confirmed live via a temporary debug log
     * that showed the exact same `gsk_` key being
     * sent to OpenRouter's and Google's endpoints
     * after switching provider chips. Each
     * provider now gets its own encrypted slot and
     * its own cached flow, so switching providers
     * shows (and tests) that provider's own key,
     * and a key entered for one provider survives
     * switching away and back.
     *
     * The cache lives on the companion object, not
     * the instance: both [org.mindanchor.settings
     * .LlmSettingsViewModel] and
     * [org.mindanchor.launcher.LauncherViewModel]
     * construct their own [LlmPrefs] against the
     * same encrypted file, and both need to see a
     * key saved through the other one — an
     * instance-level cache would leave the letter
     * writer holding a stale key after the user
     * updates it in Settings, which is the same
     * kind of bug in a different shape. Seeded
     * lazily per provider from the encrypted blob
     * by whichever [LlmPrefs] instance asks for
     * that provider's key first, so nothing spins
     * up the Keystore until something actually
     * needs it.
     */
    private fun sharedApiKey(provider: LlmProvider): MutableStateFlow<String> =
        sharedApiKeyState.computeIfAbsent(provider) { MutableStateFlow(keyStore.read(it)) }

    fun apiKeyFor(provider: LlmProvider): Flow<String> = sharedApiKey(provider)

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

    /**
     * v0.30+ (security audit) — the API key is
     * sanitised before storage:
     *  - trimmed of leading / trailing whitespace
     *    (a key pasted with a trailing newline
     *    would otherwise 401);
     *  - capped at 256 characters (a sane upper
     *    bound for any of the three providers'
     *    keys; longer strings are user error or
     *    attack);
     *  - control characters are stripped (CRLF
     *    injection is structurally impossible in
     *    OkHttp headers, but a defence-in-depth
     *    step costs nothing here).
     *
     * A blank result is still written — the user
     * clearing the field is an explicit action
     * and the empty string is the right value.
     */
    suspend fun setApiKey(provider: LlmProvider, key: String) {
        val cleaned = key.trim().take(MAX_KEY_LEN).filterNot { it.isISOControl() }
        keyStore.write(provider, cleaned)
        sharedApiKey(provider).value = cleaned
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
        keyStore.clear()
        for (p in LlmProvider.values()) {
            sharedApiKey(p).value = ""
        }
        context.letterLlmDataStore.edit { it.clear() }
    }

    companion object {
        /**
         * Sane upper bound for an LLM provider API
         * key. Google's keys are 39 chars, OpenRouter
         * keys are ~73 chars, Groq keys are 56 chars.
         * 256 leaves headroom for the three combined
         * plus any future provider.
         */
        const val MAX_KEY_LEN = 256

        /** One cached flow per provider, shared across every [LlmPrefs] instance. */
        private val sharedApiKeyState =
            java.util.concurrent.ConcurrentHashMap<LlmProvider, MutableStateFlow<String>>()
    }
}

/**
 * The encrypted-prefs blob for the LLM API keys —
 * one slot per [LlmProvider], since Google AI
 * Studio, OpenRouter and Groq are separate services
 * with separate, incompatible keys.
 *
 * Mirrors [org.mindanchor.backup.TokenStore] and
 * [org.mindanchor.vitals.coros.CorosCredentialStore]:
 * one file, one [MasterKey], AES-256-SIV for the
 * key-encryption key, AES-256-GCM for the value.
 *
 * The file is wiped by [clear] (called from
 * [LlmPrefs.reset], the test's `@Before reset`).
 */
internal class LlmKeyStore(private val prefs: SharedPreferences) {

    fun read(provider: LlmProvider): String =
        prefs.getString(keyFor(provider), null)?.takeIf { it.isNotBlank() } ?: ""

    fun write(provider: LlmProvider, key: String) {
        if (key.isBlank()) {
            prefs.edit { remove(keyFor(provider)) }
        } else {
            prefs.edit { putString(keyFor(provider), key) }
        }
    }

    fun clear() {
        prefs.edit { clear() }
    }

    private fun keyFor(provider: LlmProvider) = "api_key_${provider.name}"

    companion object {
        private const val PREF_FILE = "letter_llm_keys"

        fun create(context: Context): LlmKeyStore = LlmKeyStore(openEncrypted(context))

        private fun openEncrypted(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                PREF_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}
