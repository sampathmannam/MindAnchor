package org.mindanchor.backup

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Persists the user's WebDAV endpoint URL, username, and
 * app-password in EncryptedSharedPreferences, so the
 * auto-backup worker can PUT a new file without re-prompting.
 *
 * The master key lives in the Android Keystore (AES-256
 * GCM) and never leaves the secure hardware. Even on a
 * rooted device the encrypted blob is useless without
 * the Keystore-backed key.
 *
 * The trade-off the user accepts by opting in: the
 * launcher holds the user's WebDAV app-password in
 * Keystore-encrypted form for as long as the bridge is
 * connected. Disabling the bridge wipes the blob
 * (see [clear]).
 *
 * The credential shape is a tuple of `(url, username,
 * password)`. All three fields are required for a usable
 * credential; any of them missing or blank means the
 * bridge is not configured. The URL is stored verbatim —
 * the launcher does not normalise, percent-encode, or
 * strip trailing slashes. The user's endpoint is the
 * user's endpoint; round-tripping a Nextcloud URL of
 * `https://cloud.example.com/remote.php/dav/files/alice/`
 * is the only sane default.
 *
 * `open` so the test suite can subclass with a
 * Context-free in-memory fake. The test subclass
 * overrides every public method and the [prefs] field is
 * never touched.
 */
open class WebDavCredentialStore(context: Context) {

    /**
     * The encrypted-prefs handle. Open + `by lazy` so the
     * test subclass can ignore it entirely (the lazy
     * field never initialises if no overridden method
     * reads it).
     */
    protected open val prefs: SharedPreferences by lazy { openEncrypted(context) }

    /**
     * @return true if all three of URL, username, and
     * password are present and non-blank. The actual
     * values are read lazily on the calling thread; this
     * method is cheap.
     */
    open fun isConfigured(): Boolean =
        prefs.contains(KEY_URL) &&
            prefs.getString(KEY_URL, "").orEmpty().isNotBlank() &&
            prefs.contains(KEY_USERNAME) &&
            prefs.getString(KEY_USERNAME, "").orEmpty().isNotBlank() &&
            prefs.contains(KEY_PASSWORD) &&
            prefs.getString(KEY_PASSWORD, "").orEmpty().isNotBlank()

    open fun read(): Triple<String, String, String>? {
        val url = prefs.getString(KEY_URL, null).orEmpty()
        val username = prefs.getString(KEY_USERNAME, null).orEmpty()
        val password = prefs.getString(KEY_PASSWORD, null).orEmpty()
        if (url.isBlank() || username.isBlank() || password.isBlank()) return null
        return Triple(url, username, password)
    }

    open fun write(url: String, username: String, password: String) {
        prefs.edit()
            .putString(KEY_URL, url)
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    open fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREF_FILE = "webdav_backup"
        private const val KEY_URL = "url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"

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
