package org.mindanchor.continuity.crypto

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.Base64

/**
 * The at-rest surface for the local device's copy of the portable recovery
 * key. Follows the exact shape of `org.mindanchor.backup.TokenStore`: the
 * constructor takes a plain [SharedPreferences] so a Robolectric test can
 * inject a regular (non-encrypted) instance — Robolectric's Keystore stub
 * does not back the [MasterKey] the encrypted form needs — while production
 * code goes through [create], which opens a Keystore-backed
 * [EncryptedSharedPreferences] file.
 *
 * This is purely local-device protection of the key's plaintext bytes at
 * rest. It is unrelated to the portable envelope format itself: nothing
 * here binds the *recovery key* to this device's Keystore — only this
 * store's own on-disk copy of it is Keystore-wrapped. The key material
 * that comes out of [current] is exactly as portable as the one that went
 * into [save].
 */
class RecoveryKeyStore(private val prefs: SharedPreferences) {

    /**
     * Persists [key] as the device's current recovery key. Also resets the
     * verified flag to false: verification is a claim about a specific
     * key's material having been re-entered by the user (see [markVerified]),
     * and a newly saved key has not had that happen yet, regardless of
     * whatever key it replaces.
     */
    fun save(key: RecoveryKey) {
        prefs.edit {
            putString(KEY_BYTES, Base64.getEncoder().encodeToString(key.bytes))
            putString(KEY_ID, key.keyId)
            putBoolean(KEY_VERIFIED, false)
        }
    }

    /** @return the stored recovery key, or null if none has been [save]d (or it was [clear]ed). */
    fun current(): RecoveryKey? {
        val encodedBytes = prefs.getString(KEY_BYTES, null) ?: return null
        val keyId = prefs.getString(KEY_ID, null) ?: return null
        val bytes = runCatching { Base64.getDecoder().decode(encodedBytes) }.getOrNull() ?: return null
        return RecoveryKey(bytes, keyId)
    }

    /** @return whether the current key has been [markVerified]. Defaults to false. */
    fun isVerified(): Boolean = prefs.getBoolean(KEY_VERIFIED, false)

    /**
     * Marks the current key as verified.
     *
     * This store cannot itself know *why* it is being called — it just
     * records the boolean. The contract, enforced by the caller (a later
     * UI task), is that this is only called after the user has re-entered
     * the full generated key by hand. Copying the key to the clipboard, or
     * merely viewing it, is not verification: only successfully retyping
     * (or re-scanning) it back proves the user actually captured it.
     */
    fun markVerified() {
        prefs.edit { putBoolean(KEY_VERIFIED, true) }
    }

    /** Clears the stored key, its id, and the verified flag (e.g. "Forget account" / reset). */
    fun clear() {
        prefs.edit { clear() }
    }

    companion object {
        private const val PREF_FILE = "mindanchor_recovery_key"
        private const val KEY_BYTES = "recovery_key_bytes"
        private const val KEY_ID = "recovery_key_id"
        private const val KEY_VERIFIED = "recovery_key_verified"

        /** The production factory. Opens the encrypted-prefs file under [PREF_FILE] with a Keystore-backed [MasterKey]. */
        fun create(context: Context): RecoveryKeyStore = RecoveryKeyStore(openEncrypted(context))

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
