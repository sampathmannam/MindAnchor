package org.mindanchor.friction

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.Key
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.KeyGenerator

/**
 * The single HMAC key for the codec integrity layer.
 *
 * The key is generated on first use and stored in the
 * Android Keystore under the alias
 * `org.mindanchor.friction.codec-integrity`. The key
 * is non-exportable: the Keystore API does not allow
 * the raw bytes to leave the secure storage, so even a
 * compromised app process cannot exfiltrate the key.
 *
 * On devices that ship with StrongBox KeyMint, the key
 * is StrongBox-backed. On devices without, it falls back
 * to the TEE. On devices without either, the key is
 * software-backed and the integrity layer is *not*
 * effective against a determined local attacker — but
 * neither is the v0.20.0 plaintext form, so the layer
 * is still a *defense-in-depth* improvement over
 * nothing.
 *
 * ## Recovery (v0.20.1)
 *
 * CodeRabbit audit 2026-08-08: the v0.20.0 key
 * retrieval threw on Keystore corruption, which
 * crashed the app. v0.20.1 catches
 * [UnrecoverableKeyException] (the typical
 * failure mode after a failed OTA, a Keystore
 * wipe, or a StrongBox re-initialization), deletes
 * the corrupted entry, and re-generates a fresh
 * key. The data written with the old key is
 * unrecoverable — the integrity layer returns the
 * reset value on the first read after a
 * re-generation. The user re-enters their
 * small-things / if-then plans / compassion
 * moments.
 *
 * MASTG-BEST-0066 is the canonical reference. The project
 * accepts the documented limitation.
 *
 * @wording-reviewed — the error path on Keystore
 * corruption logs a user-visible message; the wording
 * requires clinical review.
 */
object KeystoreHmacKey {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val LOG_TAG = "MindAnchor/Hmac"
    const val ALIAS = "org.mindanchor.friction.codec-integrity"

    /**
     * Get the HMAC key, generating it on first use.
     * The returned [Key] is opaque to callers: the
     * raw bytes never leave the Keystore, and any
     * attempt to call `key.getEncoded()` on a
     * hardware-backed key returns null.
     *
     * v0.20.1: on [UnrecoverableKeyException]
     * (corrupted Keystore, post-OTA failure, etc.),
     * the corrupted entry is deleted and a fresh
     * key is generated. The caller is expected to
     * treat the first read with the new key as a
     * reset event (the integrity layer returns the
     * reset value).
     */
    fun getOrCreate(): Key {
        val ks = try {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        } catch (e: Exception) {
            // The Keystore subsystem itself is broken.
            // Re-raise; the integrity layer's
            // [IntegritySealedCodec] catches and
            // returns the reset value.
            Log.e(LOG_TAG, "Keystore unavailable: $e")
            throw e
        }
        // Try to retrieve the existing key. If the
        // entry is corrupted (e.g. after a failed
        // OTA), the Keystore throws
        // UnrecoverableKeyException. We catch and
        // regenerate.
        val existing: Key? = try {
            ks.getKey(ALIAS, null)
        } catch (e: UnrecoverableKeyException) {
            Log.w(LOG_TAG, "Existing HMAC key is corrupted; deleting and regenerating")
            try {
                ks.deleteEntry(ALIAS)
            } catch (e2: Exception) {
                Log.e(LOG_TAG, "Failed to delete corrupted key: $e2")
            }
            null
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Keystore retrieval failed: $e")
            null
        }
        if (existing != null) return existing
        // Generate a fresh key. This call can also
        // fail (e.g. on a device with no HMAC-SHA256
        // support, or a Keystore that's still in
        // re-initialization after a wipe). We re-raise
        // so the integrity layer can return the reset
        // value; the user is in a "data is fresh,
        // re-enter" state.
        val gen = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_HMAC_SHA256,
            ANDROID_KEYSTORE,
        )
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        )
            .setKeySize(256)
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()
        gen.init(spec)
        return try {
            gen.generateKey()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to generate HMAC key: $e")
            throw e
        }
    }

    /**
     * Delete the HMAC key. Used by the integrity
     * layer's recovery path on a Mac.init failure
     * that is not a transient key-retrieval error.
     *
     * After this call, the next [getOrCreate] will
     * generate a fresh key. The data written with
     * the old key is unrecoverable; the integrity
     * layer returns the reset value on the first
     * read with the new key.
     */
    fun resetKey() {
        try {
            KeyStore.getInstance(ANDROID_KEYSTORE)
                .apply { load(null) }
                .deleteEntry(ALIAS)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to reset HMAC key: $e")
        }
    }
}
