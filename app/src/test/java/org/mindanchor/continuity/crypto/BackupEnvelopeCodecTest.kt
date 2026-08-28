package org.mindanchor.continuity.crypto

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.continuity.ContinuityContentHasher
import org.mindanchor.continuity.ContinuityPayload
import org.mindanchor.continuity.ContinuitySnapshot
import org.mindanchor.continuity.ContinuitySnapshotCodec
import org.mindanchor.continuity.JournalEntryDto

/**
 * Task 8 — pins [BackupEnvelopeCodec]: an authenticated round trip, a cheap
 * `keyId`-based [BackupEnvelopeCodec.DecryptResult.WrongKey] pre-check that
 * never touches the cipher, [BackupEnvelopeCodec.DecryptResult.Corrupt] for
 * any tampering of the IV, ciphertext, tag, or the recorded plaintext hash,
 * a typed [BackupEnvelopeCodec.DecryptResult.UnsupportedVersion], and proof
 * that the ciphertext is genuinely opaque (no Journal plaintext leaks into
 * the envelope's own JSON).
 */
class BackupEnvelopeCodecTest {

    private val journalBody = "A very specific secret about what happened at the bridge on Tuesday."

    /** A real [ContinuitySnapshot], JSON-encoded, containing [journalBody] in a Journal entry. */
    private fun sampleSnapshotJson(): String {
        val payload = ContinuityPayload(
            journalEntries = listOf(
                JournalEntryDto(
                    id = "entry-1",
                    createdAt = 1_000L,
                    updatedAt = 1_000L,
                    localDate = "2026-08-27",
                    title = "Tuesday",
                    body = journalBody,
                    kind = "DAILY",
                    sourceDeviceId = "device-a",
                    deletedAt = null,
                ),
            ),
        )
        val snapshot = ContinuitySnapshot(
            formatVersion = ContinuitySnapshot.CURRENT_FORMAT_VERSION,
            snapshotId = "snapshot-1",
            createdAt = 2_000L,
            appVersionCode = 94,
            appVersionName = "0.70.0",
            sourceDeviceId = "device-a",
            payload = payload,
            contentSha256 = ContinuityContentHasher.hash(payload),
        )
        return ContinuitySnapshotCodec.encode(snapshot)
    }

    private fun fixedIv(seed: Int): () -> ByteArray = { ByteArray(12) { i -> ((seed + i) and 0xFF).toByte() } }

    private fun fixedRecoveryKey(seed: Int): RecoveryKey =
        RecoveryKeyCodec.generate { ByteArray(32) { i -> ((seed + i) and 0xFF).toByte() } }

    /** Flips one byte (XOR 0x01) at [index] of a base64-decoded field and re-encodes. */
    private fun flipByte(base64: String, index: Int): String {
        val bytes = Base64.getDecoder().decode(base64)
        bytes[index] = (bytes[index].toInt() xor 0x01).toByte()
        return Base64.getEncoder().encodeToString(bytes)
    }

    @Test
    fun `encrypt then decrypt with the same key round-trips the plaintext exactly`() {
        val plaintext = sampleSnapshotJson()
        val key = fixedRecoveryKey(1)

        val envelope = BackupEnvelopeCodec.encrypt(plaintext, key, now = 5_000L, random = fixedIv(1))
        val result = BackupEnvelopeCodec.decrypt(envelope, key)

        assertTrue(result is BackupEnvelopeCodec.DecryptResult.Success)
        assertEquals(plaintext, (result as BackupEnvelopeCodec.DecryptResult.Success).plaintextJson)
    }

    @Test
    fun `decrypting with the wrong recovery key returns WrongKey via the cheap keyId pre-check`() {
        val plaintext = sampleSnapshotJson()
        val keyA = fixedRecoveryKey(1)
        val keyB = fixedRecoveryKey(2)
        assertNotEquals("test setup: the two keys must actually differ", keyA.keyId, keyB.keyId)

        val envelope = BackupEnvelopeCodec.encrypt(plaintext, keyA, now = 5_000L, random = fixedIv(1))
        val result = BackupEnvelopeCodec.decrypt(envelope, keyB)

        assertEquals(BackupEnvelopeCodec.DecryptResult.WrongKey, result)
    }

    @Test
    fun `a one-byte IV modification returns Corrupt, not WrongKey`() {
        val plaintext = sampleSnapshotJson()
        val key = fixedRecoveryKey(1)
        val envelope = BackupEnvelopeCodec.encrypt(plaintext, key, now = 5_000L, random = fixedIv(1))

        val tampered = envelope.copy(ivBase64 = flipByte(envelope.ivBase64, 0))
        val result = BackupEnvelopeCodec.decrypt(tampered, key)

        assertEquals(BackupEnvelopeCodec.DecryptResult.Corrupt, result)
    }

    @Test
    fun `a one-byte ciphertext modification returns Corrupt, not WrongKey`() {
        val plaintext = sampleSnapshotJson()
        val key = fixedRecoveryKey(1)
        val envelope = BackupEnvelopeCodec.encrypt(plaintext, key, now = 5_000L, random = fixedIv(1))

        // Byte 0 of the ciphertext output is real ciphertext (the GCM tag
        // is appended at the end by javax.crypto's Cipher.doFinal).
        val tampered = envelope.copy(ciphertextBase64 = flipByte(envelope.ciphertextBase64, 0))
        val result = BackupEnvelopeCodec.decrypt(tampered, key)

        assertEquals(BackupEnvelopeCodec.DecryptResult.Corrupt, result)
    }

    @Test
    fun `a one-byte tag modification returns Corrupt, not WrongKey`() {
        val plaintext = sampleSnapshotJson()
        val key = fixedRecoveryKey(1)
        val envelope = BackupEnvelopeCodec.encrypt(plaintext, key, now = 5_000L, random = fixedIv(1))

        // The 16-byte GCM auth tag is the LAST 16 bytes of the ciphertext
        // output (javax.crypto's Cipher.doFinal convention).
        val ciphertextBytes = Base64.getDecoder().decode(envelope.ciphertextBase64)
        val lastByteIndex = ciphertextBytes.size - 1
        val tampered = envelope.copy(ciphertextBase64 = flipByte(envelope.ciphertextBase64, lastByteIndex))
        val result = BackupEnvelopeCodec.decrypt(tampered, key)

        assertEquals(BackupEnvelopeCodec.DecryptResult.Corrupt, result)
    }

    @Test
    fun `a plaintextSha256 field mismatch after successful GCM auth returns Corrupt`() {
        val plaintext = sampleSnapshotJson()
        val key = fixedRecoveryKey(1)
        val envelope = BackupEnvelopeCodec.encrypt(plaintext, key, now = 5_000L, random = fixedIv(1))

        // GCM authentication will still succeed (ciphertext/IV/tag are all
        // untouched); only the recorded hash field is wrong.
        val tampered = envelope.copy(plaintextSha256 = "0".repeat(64))
        val result = BackupEnvelopeCodec.decrypt(tampered, key)

        assertEquals(BackupEnvelopeCodec.DecryptResult.Corrupt, result)
    }

    @Test
    fun `an unsupported format version is rejected before any decryption is attempted`() {
        val plaintext = sampleSnapshotJson()
        val key = fixedRecoveryKey(1)
        val envelope = BackupEnvelopeCodec.encrypt(plaintext, key, now = 5_000L, random = fixedIv(1))

        val tampered = envelope.copy(formatVersion = 999)
        val result = BackupEnvelopeCodec.decrypt(tampered, key)

        assertEquals(BackupEnvelopeCodec.DecryptResult.UnsupportedVersion(999), result)
    }

    @Test
    fun `no Journal plaintext appears anywhere in the encrypted envelope's JSON`() {
        val plaintext = sampleSnapshotJson()
        assertTrue("test setup: the plaintext must actually contain the Journal body", plaintext.contains(journalBody))
        val key = fixedRecoveryKey(1)

        val envelope = BackupEnvelopeCodec.encrypt(plaintext, key, now = 5_000L, random = fixedIv(1))
        val envelopeJson = BackupEnvelopeCodec.encode(envelope)

        assertFalse(envelopeJson.contains(journalBody))
        assertFalse(envelopeJson.contains("bridge"))
    }
}
