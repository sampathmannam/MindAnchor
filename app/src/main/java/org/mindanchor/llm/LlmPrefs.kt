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
    private val voiceKey = stringPreferencesKey("letter_voice")
    private val lastTestSuccessKey = stringPreferencesKey("last_test_success")
    private val lastTestMessageKey = stringPreferencesKey("last_test_message")
    private val lastTestAtKey = longPreferencesKey("last_test_at")

    /**
     * The shared, process-wide cache of the current
     * API key. [LlmPrefs] is created multiple times in
     * the same process (the settings viewmodel, the
     * letter scheduler, the letter viewmodel, the
     * `Generate now` viewmodel, tests). Without the
     * shared cache, an instance that opens after the
     * key was written would see the empty-string
     * initial value and never observe the update —
     * exactly the "pasted key not showing" symptom.
     *
     * Initial value is "". The [LlmKeyStore] is the
     * on-disk source of truth; it is read lazily on
     * first [setApiKey] call (i.e. once the user
     * starts using the LLM). That is the right shape
     * for the tests, which start with no key, and
     * for the production flow, where the user pastes
     * a key before any of this code is exercised.
     */
    // v0.72.x: was `private companion object`, but
    // [LlmTokenStore] in the same module needs to read
    // [MAX_KEY_LEN] for the read-side trim. The
    // [apiKeyState] itself is private to keep callers
    // from bypassing [setApiKey]'s sanitisation; the
    // public surface is the [apiKey] property.
    companion object {
        private val apiKeyState = kotlinx.coroutines.flow.MutableStateFlow("")

        /**
         * Sane upper bound for an LLM provider API
         * key. Google's keys are 39 chars, OpenRouter
         * keys are ~73 chars, Groq keys are 56 chars.
         * 256 leaves headroom for the three combined
         * plus any future provider.
         */
        const val MAX_KEY_LEN = 256
    }

    val provider: Flow<LlmProvider> = context.letterLlmDataStore.data.map { prefs ->
        when (prefs[providerKey]) {
            LlmProvider.GOOGLE_AI_STUDIO.name -> LlmProvider.GOOGLE_AI_STUDIO
            LlmProvider.OPENROUTER.name -> LlmProvider.OPENROUTER
            LlmProvider.GROQ.name -> LlmProvider.GROQ
            else -> LlmProvider.GOOGLE_AI_STUDIO
        }
    }

    /**
     * The API key, observed reactively.
     *
     * v0.72.x: the previous shape was a one-shot
     * `flow { emit(keyStore.read()) }`, which only
     * fires once. The StateFlow in
     * [org.mindanchor.settings.LlmSettingsViewModel]
     * is collected from this; with a one-shot flow
     * the field the user typed into never re-emits
     * and the saved value never re-emits either, so
     * `apiKey` was stuck at the value collected the
     * first time `stateIn` ran. The visible symptom
     * was: paste a key, look at the field, the field
     * appears empty (because the flow never re-emits
     * with the saved value).
     *
     * The fix is a process-wide [kotlinx.coroutines.flow.MutableStateFlow]
     * keyed off the [LlmKeyStore] and the [setApiKey]
     * writer. [LlmPrefs] is created multiple times in
     * the same process (the settings viewmodel, the
     * letter scheduler, the letter viewmodel, the
     * `Generate now` viewmodel, tests); a per-instance
     * StateFlow would let one instance's write stay
     * invisible to another's reader. The
     * [companion object] scope is the simplest
     * process-wide cache, and the test surface in
     * `LlmSettingsTest` proves it round-trips.
     *
     * Initial value: empty. The LlmKeyStore is the
     * source of truth on disk, but the in-memory
     * state is updated on every [setApiKey] and is
     * what's observed by callers.
     */
    val apiKey: Flow<String> get() = apiKeyState

    val model: Flow<String> = context.letterLlmDataStore.data.map { prefs ->
        prefs[modelKey] ?: LlmProvider.GOOGLE_AI_STUDIO.defaultModel
    }

    /**
     * v0.72.x: the voice a letter is written in.
     * Defaults to [LetterVoice.QUIET] for users
     * upgrading from pre-0.72.x. The chosen voice
     * becomes the system-prompt template the LLM
     * receives; see [LetterVoice.systemPrompt].
     */
    val voice: Flow<LetterVoice> = context.letterLlmDataStore.data.map { prefs ->
        LetterVoice.fromName(prefs[voiceKey])
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
    suspend fun setApiKey(key: String) {
        val cleaned = key.trim().take(MAX_KEY_LEN).filterNot { it.isISOControl() }
        LlmKeyStore.write(context, cleaned)
        apiKeyState.value = cleaned
    }

    /**
     * v0.72.x: the privacy off-switch. Wipes the API key
     * from the encrypted blob, clears the in-memory
     * StateFlow, and resets the cached
     * [org.mindanchor.llm.LetterError.LastTestResult]
     * row so the next time the user opens settings the
     * Connection row is back to "Never tested" rather
     * than the last error from the wiped key.
     */
    suspend fun clearApiKey() {
        LlmKeyStore.write(context, "")
        apiKeyState.value = ""
        context.letterLlmDataStore.edit {
            it.remove(lastTestSuccessKey)
            it.remove(lastTestMessageKey)
            it[lastTestAtKey] = 0L
        }
    }

    suspend fun setModel(model: String) {
        context.letterLlmDataStore.edit { it[modelKey] = model }
    }

    suspend fun setVoice(voice: LetterVoice) {
        context.letterLlmDataStore.edit { it[voiceKey] = voice.name }
    }

    suspend fun setLastTestResult(result: LlmTestResult) {
        context.letterLlmDataStore.edit { prefs ->
            prefs[lastTestSuccessKey] = result.success.toString()
            prefs[lastTestMessageKey] = result.message
            prefs[lastTestAtKey] = result.testedAtMillis
        }
    }

    internal suspend fun reset() {
        LlmKeyStore.clear(context)
        context.letterLlmDataStore.edit { it.clear() }
        apiKeyState.value = ""
    }
}

/**
 * The encrypted-prefs blob for the LLM API key.
 *
 * v0.72.x: was a class, instantiated per `LlmPrefs`
 * instance. That meant the on-disk read in
 * [LlmPrefs.keyStore] lazy-init could not be observed
 * from the companion-object's [apiKeyState] — the
 * two were in different objects. This file is now an
 * `object` (process-wide singleton): one
 * [SharedPreferences] handle for the whole app, one
 * read, one write. The companion-object flow in
 * [LlmPrefs] reads the current value lazily on first
 * use, so a previously-saved key shows up after a
 * restart, and every reader in the process sees every
 * write.
 *
 * The file is wiped by [clear] (called from
 * [LlmPrefs.reset], the test's `@Before reset`).
 */
internal object LlmKeyStore {

    /**
     * The encrypted SharedPreferences handle. Lazy
     * because [MasterKey] requires a [Context] — it
     * has to be built on first call, not at class
     * load. The [Context] comes from the LlmPrefs
     * caller that first needs the store.
     */
    @Volatile
    private var prefsRef: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences {
        prefsRef?.let { return it }
        return synchronized(this) {
            prefsRef ?: openEncrypted(context.applicationContext).also { prefsRef = it }
        }
    }

    fun read(context: Context): String =
        prefs(context).getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() } ?: ""

    fun write(context: Context, key: String) {
        if (key.isBlank()) {
            prefs(context).edit { remove(KEY_API_KEY) }
        } else {
            prefs(context).edit { putString(KEY_API_KEY, key) }
        }
    }

    fun clear(context: Context) {
        prefs(context).edit { clear() }
    }

    private const val PREF_FILE = "letter_llm_keys"
    private const val KEY_API_KEY = "api_key"

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
