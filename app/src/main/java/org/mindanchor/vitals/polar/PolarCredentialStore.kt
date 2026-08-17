package org.mindanchor.vitals.polar

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Persists the user's Polar Flow email + password in
 * EncryptedSharedPreferences, so the OAuth2 token can be
 * refreshed without re-prompting.
 *
 * The shape mirrors [org.mindanchor.vitals.coros.CorosCredentialStore]:
 * Keystore-backed master key, AES-256 GCM value encryption,
 * AES-256 SIV key encryption. The launcher never holds the
 * user's Polar password in plain text; the encrypted blob is
 * useless without the Keystore-backed key.
 *
 * Trade-off the user accepts by opting in: the launcher
 * holds the user's Polar password in Keystore-encrypted
 * form for as long as the bridge is connected. Disconnect
 * wipes the blob (see [clear]).
 *
 * `open` so the test suite can subclass with a
 * Context-free in-memory fake. The test subclass overrides
 * every public method and the [prefs] field is never
 * touched.
 *
 * @wording-reviewed — clinical-review-required. The
 * user-facing "Connected" / "Disconnected" surface in
 * [org.mindanchor.settings.PolarSection] is the
 * clinical-review surface for this class. Wording changes
 * must be re-reviewed per docs/CLINICAL_REVIEW.md.
 */
open class PolarCredentialStore(context: Context) {

    /**
     * The encrypted-prefs handle. Open + `by lazy` so the
     * test subclass can ignore it entirely (the lazy
     * field never initialises if no overridden method
     * reads it).
     */
    protected open val prefs: SharedPreferences by lazy { openEncrypted(context) }

    open fun isConnected(): Boolean =
        prefs.contains(KEY_EMAIL) && prefs.getString(KEY_EMAIL, "").orEmpty().isNotBlank()

    open fun read(): Pair<String, String>? {
        val email = prefs.getString(KEY_EMAIL, null) ?: return null
        val password = prefs.getString(KEY_PASSWORD, null) ?: return null
        return email to password
    }

    open fun write(email: String, password: String) {
        prefs.edit()
            .putString(KEY_EMAIL, email)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    /**
     * Persist the OAuth2 access token. Polar tokens are
     * 3-day TTL, so this method is called by [PolarAuth]
     * after every successful token exchange.
     */
    open fun writeAccessToken(token: String, expiresAtEpochMs: Long) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, token)
            .putLong(KEY_TOKEN_EXPIRES_AT, expiresAtEpochMs)
            .apply()
    }

    open fun readAccessToken(): String? =
        prefs.getString(KEY_ACCESS_TOKEN, null)

    open fun readAccessTokenExpiryEpochMs(): Long =
        prefs.getLong(KEY_TOKEN_EXPIRES_AT, 0L)

    open fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREF_FILE = "polar_bridge"
        private const val KEY_EMAIL = "email"
        private const val KEY_PASSWORD = "password"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_TOKEN_EXPIRES_AT = "token_expires_at"

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
