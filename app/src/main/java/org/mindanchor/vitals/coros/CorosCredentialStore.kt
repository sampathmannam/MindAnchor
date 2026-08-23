package org.mindanchor.vitals.coros

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Persists the user's COROS email + password in
 * EncryptedSharedPreferences, so the 24h web token
 * can be refreshed without re-prompting.
 *
 * The master key lives in the Android Keystore (AES-256
 * GCM) and never leaves the secure hardware. Even on a
 * rooted device the encrypted blob is useless without
 * the Keystore-backed key.
 *
 * The trade-off the user accepts by opting in: the
 * launcher holds the user's COROS password in
 * Keystore-encrypted form for as long as the bridge is
 * connected. Disconnect wipes the blob (see [clear]).
 *
 * `open` so the test suite can subclass with a
 * Context-free in-memory fake. The test subclass
 * overrides every public method and the [prefs] field is
 * never touched.
 */
open class CorosCredentialStore(context: Context) {

    /**
     * The encrypted-prefs handle. Open + `by lazy` so the
     * test subclass can ignore it entirely (the lazy
     * field never initialises if no overridden method
     * reads it).
     */
    protected open val prefs: SharedPreferences by lazy { openEncrypted(context) }

    /**
     * @return true if a stored credential is present and
     * the email field is non-blank. The password is read
     * lazily on the calling thread; this method is cheap.
     */
    open fun isConnected(): Boolean =
        prefs.contains(KEY_EMAIL) && prefs.getString(KEY_EMAIL, "").orEmpty().isNotBlank()

    open fun read(): Pair<String, String>? {
        val email = prefs.getString(KEY_EMAIL, null) ?: return null
        val password = prefs.getString(KEY_PASSWORD, null) ?: return null
        return email to password
    }

    open fun write(email: String, password: String, region: String) {
        prefs.edit {
            putString(KEY_EMAIL, email)
            putString(KEY_PASSWORD, password)
            putString(KEY_REGION, region)
        }
    }

    open fun region(): String =
        prefs.getString(KEY_REGION, null) ?: DEFAULT_REGION

    open fun clear() {
        prefs.edit { clear() }
    }

    companion object {
        private const val PREF_FILE = "coros_bridge"
        private const val KEY_EMAIL = "email"
        private const val KEY_PASSWORD = "password"
        private const val KEY_REGION = "region"
        private const val DEFAULT_REGION = "eu"

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
