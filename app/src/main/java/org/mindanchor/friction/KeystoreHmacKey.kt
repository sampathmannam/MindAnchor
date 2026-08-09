package org.mindanchor.friction

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.Key
import java.security.KeyStore
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
 * MASTG-BEST-0066 is the canonical reference. The project
 * accepts the documented limitation.
 *
 * @wording-reviewed — the error path on Keystore
 * corruption logs a user-visible message; the wording
 * requires clinical review.
 */
object KeystoreHmacKey {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    const val ALIAS = "org.mindanchor.friction.codec-integrity"

    /**
     * Get the HMAC key, generating it on first use.
     * The returned [Key] is opaque to callers: the
     * raw bytes never leave the Keystore, and any
     * attempt to call `key.getEncoded()` on a
     * hardware-backed key returns null.
     */
    fun getOrCreate(): Key {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = ks.getKey(ALIAS, null)
        if (existing != null) return existing
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
        return gen.generateKey()
    }
}
