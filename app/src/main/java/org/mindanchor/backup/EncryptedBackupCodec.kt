package org.mindanchor.backup

import android.util.Log
import java.security.Key
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/**
 * The AES-256-GCM wrapper around [BackupCodec] JSON.
 *
 * v0.23.0: the WebDAV upload path sends a `.enc` file,
 * not a plain `.json` file. The wrapper takes the
 * existing JSON [BackupCodec] produces, encrypts it
 * with AES-256-GCM using a key bound to the Android
 * Keystore, and prepends the IV. The result is what
 * travels over the wire. On restore, the file is
 * downloaded, the IV is read, the ciphertext is
 * decrypted and authenticated, and the resulting JSON
 * is fed to [BackupCodec.decode] exactly as it would
 * be for a local restore.
 *
 * ## File format
 *
 * ```
 *  +----------------+----------------+----------------------+
 *  | IV (12 bytes)  | ciphertext (n) | auth tag (16 bytes)  |
 *  +----------------+----------------+----------------------+
 * ```
 *
 * The IV is generated freshly with [SecureRandom] for
 * every wrap. Reusing an IV with the same key under
 * AES-GCM is catastrophic — it leaks the XOR of the two
 * plaintexts. The Keystore-generated AES key requires
 * randomised encryption ([KeystoreAesKey]'s
 * `setRandomizedEncryptionRequired(true)`), so the
 * platform refuses a wrap call without an IV anyway.
 *
 * ## What the cloud sees
 *
 * A 12-byte prefix, followed by what looks like random
 * bytes of `(plaintext.length + 16)` size. The filename
 * extension is `.enc`, not `.json`, so the cloud cannot
 * infer the file's content type from the name. WebDAV
 * servers that log file paths (most do) cannot
 * fingerprint the user as "uses a mental-health
 * journaling tool" from a single 2.5 MB blob.
 *
 * ## Why the wrapper is separate from [BackupCodec]
 *
 * [BackupCodec] is the canonical, versioned, plaintext
 * representation of a backup. The wrapper is a transport
 * concern: it changes nothing about what a backup is,
 * only how it travels. Keeping them separate means:
 *
 *  - The local save path still produces a readable
 *    `.json` file, exactly as it did before v0.23.0.
 *  - The cloud `.enc` file is round-tripped through
 *    [unwrap] and then through [BackupCodec.decode];
 *    a v0.23.0+ restore of a v0.22.x backup still works.
 *  - The wrapper is a single, well-tested transform.
 *    Future transports (S3, local-network, etc.) reuse
 *    it without any change to [BackupCodec].
 */
object EncryptedBackupCodec {
    private const val LOG_TAG = "MindAnchor/BackupEnc"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val BITS_PER_BYTE = 8
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    /**
     * Wraps a [BackupCodec] JSON string with AES-256-GCM.
     * The output is `IV || ciphertext || tag`. The IV is
     * freshly generated with [SecureRandom]; never
     * reuse an IV with the same key.
     *
     * v0.25.9: the ciphertext is bound to the
     * [type.fileName] via GCM AAD
     * ([Cipher.updateAAD]). Without AAD, a blob
     * wrapped for `Notes` could be unwrapped as
     * `Letters` (or vice versa) without the GCM
     * tag noticing — the tag is computed over
     * (key, IV, ciphertext) only, not over
     * (key, IV, ciphertext, file-name). A
     * motivated attacker with `drive.file` scope
     * (the same scope the launcher requests) can
     * read both files and swap them, and the
     * recipient would not see the difference. The
     * AAD makes the cross-file swap a tag failure
     * on unwrap, which the codec surfaces as
     * `null` and the target as `AppendResult.AuthExpired`
     * (or a re-prompt on the user).
     *
     * @return the wrapped bytes, or null on a
     * Keystore or Cipher failure. The caller surfaces
     * a user-visible message in that case; the wrap is
     * an opt-in surface, never silently dropped.
     */
    fun wrap(plaintextJson: String, type: ContentType): ByteArray? = wrapWith(
        plaintextJson = plaintextJson,
        key = KeystoreAesKey.getOrCreate(),
        type = type,
    )

    /**
     * Internal wrap variant that takes a [Key] directly.
     * The unit test suite calls this with a JVM-only
     * [javax.crypto.spec.SecretKeySpec] so the
     * round-trip can be exercised without an Android
     * Keystore. The production call site uses [wrap],
     * which pulls the key from [KeystoreAesKey].
     */
    internal fun wrapWith(plaintextJson: String, key: Key, type: ContentType): ByteArray? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        require(iv.size == GCM_IV_LENGTH) {
            "AES-GCM IV must be 12 bytes, got ${iv.size}"
        }
        // v0.25.9: bind the ciphertext to the file
        // name via AAD. See the [wrap] docstring.
        cipher.updateAAD(type.fileName.toByteArray(Charsets.UTF_8))
        val ciphertext = cipher.doFinal(plaintextJson.encodeToByteArray())
        iv + ciphertext
    }.getOrElse { e ->
        Log.e(LOG_TAG, "wrap failed: $e")
        null
    }

    /**
     * Unwraps a `.enc` blob to the original JSON string.
     * The input is `IV || ciphertext || tag`; the IV is
     * the first 12 bytes, the rest is the GCM
     * ciphertext+tag.
     *
     * v0.25.9: the [type] must match the [type] the
     * blob was [wrap]ped with — the AAD check in
     * [unwrapWith] makes a cross-type unwrap a tag
     * failure (returned as `null`).
     *
     * @return the plaintext JSON, or null on a bad
     * format, an authentication failure (the tag does
     * not verify — the file was tampered with, the
     * wrong [type] was passed, or it was wrapped with
     * a different key), or a Keystore failure. A null
     * return must NOT be treated as "empty backup";
     * the caller surfaces the failure to the user.
     */
    fun unwrap(blob: ByteArray, type: ContentType): String? = unwrapWith(
        blob = blob,
        key = KeystoreAesKey.getOrCreate(),
        type = type,
    )

    /**
     * Internal unwrap variant that takes a [Key]
     * directly. The unit test suite uses this with a
     * JVM-only [javax.crypto.spec.SecretKeySpec] so
     * the round-trip can be exercised without an
     * Android Keystore. The production call site uses
     * [unwrap], which pulls the key from
     * [KeystoreAesKey].
     *
     * @return the plaintext JSON, or null on a bad
     * format, an authentication failure (the tag does
     * not verify, or the [type] does not match the
     * AAD the blob was wrapped with), or a Cipher
     * failure. The error path is deliberately silent
     * at the call boundary; the caller surfaces a
     * user-visible message.
     */
    internal fun unwrapWith(blob: ByteArray, key: Key, type: ContentType): String? = runCatching {
        require(blob.size > GCM_IV_LENGTH + GCM_TAG_LENGTH_BITS / BITS_PER_BYTE) {
            "AES-GCM blob too short: ${blob.size}"
        }
        val iv = blob.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = blob.copyOfRange(GCM_IV_LENGTH, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv),
        )
        // v0.25.9: bind the unwrap to the file name
        // via AAD. A blob wrapped for `Notes` will
        // fail the tag check if unwrapped as
        // `Letters`.
        cipher.updateAAD(type.fileName.toByteArray(Charsets.UTF_8))
        cipher.doFinal(ciphertext).decodeToString()
    }.getOrElse { e ->
        Log.e(LOG_TAG, "unwrap failed: $e")
        null
    }
}
