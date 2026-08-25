package org.mindanchor.llm

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * v0.30+ (security audit 2026-08-25) — the LLM API
 * key was previously stored in plain DataStore via
 * [LlmPrefs]. The audit's recommendation is the same
 * pattern the COROS bridge uses for the user's web
 * token: a dedicated [EncryptedSharedPreferences]
 * file keyed by the existing
 * [org.mindanchor.backup.KeystoreAesKey] master
 * pattern. The key lives only in the device
 * Keystore; the encrypted blob lives in
 * `/data/data/org.mindanchor/files/letter_llm_tokens`.
 *
 * ## Why a separate file from `letter_llm_dataStore`
 *
 * The DataStore is the canonical store for
 * non-secret settings: the provider enum, the
 * model name, the last-test result. Mixing the API
 * key into the same blob means a forensic copy of
 * the DataStore file exposes both the secret and the
 * settings. Keeping the secret in its own
 * Keystore-backed file means the secret is only
 * decryptable by a process holding the Keystore
 * key (which a forensic image of `/data/data/`
 * alone cannot do — the Keystore is in a separate
 * hardware-backed location).
 *
 * ## What "encrypted" means in practice
 *
 * - The master key is generated on first use, in
 *   `AES256_GCM` mode, and stored in the Android
 *   Keystore under `androidx.security.crypto`.
 * - The values are AES-256-GCM under
 *   [EncryptedSharedPreferences], with the keyset
 *   bound to the master key.
 * - The values can only be decrypted on the same
 *   device, by a process the Keystore grants access
 *   to. A forensic image alone cannot decrypt.
 *
 * ## Robolectric testability
 *
 * The [SharedPreferences] the class wraps is
 * dependency-injected: the public constructor takes
 * one, the companion [create] factory builds the
 * production Keystore-backed one. Robolectric tests
 * pass a plain [SharedPreferences] (the same shape
 * as the existing [org.mindanchor.backup.TokenStore]
 * test in
 * `GoogleDriveAuthTokenStoreRoundTripFindingTest`).
 * The production call site in [LlmPrefs] uses
 * [create], which the unit test does not.
 *
 * ## What if the Keystore is unavailable
 *
 * [create] returns a [LlmTokenStore] with null
 * preferences when [MasterKey] cannot bind the master
 * key. The read returns null, the write is a no-op,
 * the user sees "configure your LLM provider" in
 * the Settings flow. The user is not stranded; the
 * DataStore path that holds
 * `provider + model + lastTestResult` is independent.
 */
class LlmTokenStore(private val preferences: SharedPreferences?) {

    /**
     * The encrypted API key, or null if:
     *  - no key has ever been set
     *  - the Keystore is unavailable (the [preferences]
     *    dependency was null at construction time)
     *  - the encrypted blob is corrupted
     */
    val apiKey: String?
        get() = preferences?.getString(KEY_API, null)

    fun setApiKey(value: String) {
        // v0.30+ (security audit 2026-08-25) — the
        // same trim + cap + control-char filter as
        // [LlmPrefs.setApiKey] is applied before the
        // value reaches the encrypted blob. A 10 MB
        // payload is truncated to [LlmPrefs.MAX_KEY_LEN]
        // before encryption so a single malformed
        // value cannot exhaust the encrypted blob.
        val cleaned = value
            .trim()
            .take(LlmPrefs.MAX_KEY_LEN)
            .filter { !it.isISOControl() }
        if (cleaned.isEmpty()) return
        preferences?.edit()?.putString(KEY_API, cleaned)?.apply()
    }

    /** Clear the API key. Used on sign-out / provider change. */
    fun clear() {
        preferences?.edit()?.remove(KEY_API)?.apply()
    }

    companion object {
        private const val LOG_TAG = "MindAnchor/LlmToken"
        private const val FILE_NAME = "letter_llm_tokens"
        private const val KEY_API = "api_key"

        /**
         * Production factory: build the
         * Keystore-backed [EncryptedSharedPreferences].
         * Returns a [LlmTokenStore] with null preferences
         * on Keystore failure (rooted device, Keystore
         * corrupted, etc.) — the caller (the LLM prefs)
         * treats null as "no key configured" and the user
         * can re-enter the key.
         */
        fun create(context: Context): LlmTokenStore = runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            LlmTokenStore(prefs)
        }.getOrElse { e ->
            Log.e(LOG_TAG, "EncryptedSharedPreferences init failed: $e")
            LlmTokenStore(null)
        }
    }
}
