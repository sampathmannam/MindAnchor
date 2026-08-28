package org.mindanchor.continuity.crypto

import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Encrypts and decrypts a continuity snapshot's plaintext JSON into/out of
 * a [BackupEnvelope], using AES-256-GCM keyed by a portable [RecoveryKey]
 * (not the Android Keystore — see [BackupEnvelope]'s KDoc). This is a fresh
 * implementation; it deliberately does not reuse
 * `org.mindanchor.backup.EncryptedBackupCodec`, which binds encryption to
 * the source phone's Keystore and therefore cannot be decrypted on a
 * replacement phone.
 */
object BackupEnvelopeCodec {

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val AAD_PREFIX = "MindAnchorBackup"
    private const val AES_KEY_ALGORITHM = "AES"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    sealed class DecryptResult {
        data class Success(val plaintextJson: String) : DecryptResult()

        /**
         * The key's own [RecoveryKey.keyId] does not match [BackupEnvelope.keyId]
         * — detected *before* attempting decryption, so this is a
         * definite "wrong key", not a guess. See [decrypt].
         */
        data object WrongKey : DecryptResult()

        /**
         * The key id matched, but the ciphertext, IV, tag, or the
         * recorded [BackupEnvelope.plaintextSha256] does not check out.
         * AES-GCM's authentication failure looks identical whether the
         * *ciphertext* was tampered with or a wrong key was used, but the
         * key id pre-check above already ruled out the latter, so
         * anything that reaches this branch is genuine corruption.
         */
        data object Corrupt : DecryptResult()

        data class UnsupportedVersion(val formatVersion: Int) : DecryptResult()
    }

    /**
     * Encrypts [plaintextJson] (a continuity snapshot's JSON) with [key].
     * A fresh random IV is generated for every call — production uses
     * [SecureRandom]; tests inject a deterministic [random] byte source,
     * mirroring [RecoveryKeyCodec.generate]'s injection pattern.
     */
    fun encrypt(
        plaintextJson: String,
        key: RecoveryKey,
        now: Long,
        random: () -> ByteArray = { ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) } },
    ): BackupEnvelope {
        val iv = random()
        require(iv.size == GCM_IV_LENGTH) { "IV must be $GCM_IV_LENGTH bytes, got ${iv.size}" }

        val plaintextBytes = plaintextJson.encodeToByteArray()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey(key), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        cipher.updateAAD(aad(BackupEnvelope.CURRENT_FORMAT_VERSION, key.keyId))
        val ciphertext = cipher.doFinal(plaintextBytes)

        return BackupEnvelope(
            formatVersion = BackupEnvelope.CURRENT_FORMAT_VERSION,
            keyId = key.keyId,
            createdAt = now,
            ivBase64 = Base64.getEncoder().encodeToString(iv),
            ciphertextBase64 = Base64.getEncoder().encodeToString(ciphertext),
            plaintextSha256 = sha256Hex(plaintextBytes),
        )
    }

    /**
     * Decrypts [envelope] with [key]. Checks, in order: [BackupEnvelope.formatVersion]
     * (→ [DecryptResult.UnsupportedVersion]), then whether [key]'s own id
     * matches [BackupEnvelope.keyId] (→ [DecryptResult.WrongKey], without
     * attempting decryption), then AES-GCM authentication (→
     * [DecryptResult.Corrupt] on any tampering), then
     * [BackupEnvelope.plaintextSha256] (→ [DecryptResult.Corrupt] on a
     * mismatch).
     */
    fun decrypt(envelope: BackupEnvelope, key: RecoveryKey): DecryptResult {
        if (envelope.formatVersion != BackupEnvelope.CURRENT_FORMAT_VERSION) {
            return DecryptResult.UnsupportedVersion(envelope.formatVersion)
        }
        if (envelope.keyId != key.keyId) {
            return DecryptResult.WrongKey
        }

        val iv = runCatching { Base64.getDecoder().decode(envelope.ivBase64) }.getOrNull()
            ?: return DecryptResult.Corrupt
        val ciphertext = runCatching { Base64.getDecoder().decode(envelope.ciphertextBase64) }.getOrNull()
            ?: return DecryptResult.Corrupt

        val plaintextBytes = try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(key), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            cipher.updateAAD(aad(envelope.formatVersion, envelope.keyId))
            cipher.doFinal(ciphertext)
        } catch (e: GeneralSecurityException) {
            return DecryptResult.Corrupt
        }

        if (sha256Hex(plaintextBytes) != envelope.plaintextSha256) {
            return DecryptResult.Corrupt
        }

        return DecryptResult.Success(plaintextBytes.decodeToString())
    }

    /** Serializes [envelope] to JSON — the form a caller would write to a `.mab` file. */
    fun encode(envelope: BackupEnvelope): String = json.encodeToString(envelope)

    private fun secretKey(key: RecoveryKey): SecretKeySpec = SecretKeySpec(key.bytes, AES_KEY_ALGORITHM)

    /**
     * The additional authenticated data: `MindAnchorBackup|<formatVersion>|<keyId>`.
     * Binds the ciphertext to the envelope's own declared format version
     * and key id, so neither can be swapped onto a different envelope
     * without breaking GCM authentication.
     */
    private fun aad(formatVersion: Int, keyId: String): ByteArray =
        "$AAD_PREFIX|$formatVersion|$keyId".encodeToByteArray()

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
