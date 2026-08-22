package org.mindanchor.backup

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.Key
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.UnrecoverableKeyException
import javax.crypto.KeyGenerator

/**
 * The single AES-256 key used by the WebDAV backup
 * wrapper.
 *
 * v0.23.0 introduced an AES-256-GCM layer on top of the
 * existing JSON backup so the cloud sees ciphertext, not
 * the safety plan in plain. The key is generated on first
 * use and stored in the Android Keystore under the alias
 * `org.mindanchor.backup.aes-256-gcm`. The key is
 * non-exportable: the Keystore API does not allow the raw
 * bytes to leave the secure storage, so even a compromised
 * app process cannot exfiltrate the key.
 *
 * ## Key rotation
 *
 * The Keystore's own key rotation policy applies. On most
 * devices the Keystore key is bound to the user's
 * lockscreen credentials; changing the lockscreen does
 * NOT invalidate the key. Wiping the device (factory
 * reset) DOES. So:
 *
 *  - A user enabling WebDAV backup and then factory-
 *    resetting the phone loses the ability to decrypt
 *    old `.enc` files on the remote. The remote copies
 *    remain valid ciphertext, but they become
 *    permanently undecryptable from this device. The
 *    user is told this in the one-time confirmation
 *    screen that arms auto-backup.
 *  - A user restoring a backup onto a fresh device can
 *    re-arm the bridge but cannot decrypt any pre-reset
 *    `.enc` files.
 *
 * The trade-off is acceptable because the alternative —
 * a key derived from a user-supplied passphrase — means
 * the user has to type the passphrase on every restore,
 * which is exactly the friction the opt-in bridge is
 * designed to remove.
 *
 * ## StrongBox / TEE / software fallback
 *
 * On devices that ship with StrongBox KeyMint, the key
 * is StrongBox-backed. On devices without, it falls back
 * to the TEE. On devices without either, the key is
 * software-backed. The same trade-off as
 * [org.mindanchor.friction.KeystoreHmacKey] applies:
 * software-backed is a defense-in-depth improvement over
 * plaintext, not a guarantee against a determined local
 * attacker.
 */
object KeystoreAesKey {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val LOG_TAG = "MindAnchor/BackupAes"
    const val ALIAS = "org.mindanchor.backup.aes-256-gcm"
    private const val KEY_SIZE_BITS = 256

    /**
     * Get the AES-256 key, generating it on first use.
     * The returned [Key] is opaque to callers: the
     * raw bytes never leave the Keystore, and any
     * attempt to call `key.getEncoded()` on a
     * hardware-backed key returns null.
     *
     * If the Keystore subsystem is unavailable, the
     * exception is re-raised; the caller is expected
     * to surface a user-visible message and refuse to
     * wrap/unwrap.
     */
    fun getOrCreate(): Key {
        val ks = openKeystore()
        val existing = retrieveExisting(ks)
        if (existing != null) return existing
        return generateFresh()
    }

    private fun openKeystore(): KeyStore {
        return try {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        } catch (e: KeyStoreException) {
            Log.e(LOG_TAG, "Keystore unavailable: $e")
            throw e
        }
    }

    private fun retrieveExisting(ks: KeyStore): Key? {
        return try {
            ks.getKey(ALIAS, null)
        } catch (e: UnrecoverableKeyException) {
            Log.w(LOG_TAG, "Existing AES key is corrupted; regenerating", e)
            null
        } catch (e: KeyStoreException) {
            Log.e(LOG_TAG, "Keystore retrieval failed", e)
            null
        } catch (e: NoSuchAlgorithmException) {
            Log.e(LOG_TAG, "Unknown algorithm", e)
            null
        }
    }

    private fun generateFresh(): Key {
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(true)
            .build()
        gen.init(spec)
        return gen.generateKey()
    }
}
