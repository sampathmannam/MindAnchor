package org.mindanchor.backup

import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip tests for [EncryptedBackupCodec].
 *
 * The production code path goes through the Android
 * Keystore ([KeystoreAesKey]), which is not available
 * in the JVM unit-test environment. The test calls the
 * `*With` variants of wrap / unwrap with a
 * [SecretKeySpec] of the same shape (AES, 256 bits,
 * 32 bytes) so the cipher operations are exercised
 * end-to-end. The Keystore-bound production path is
 * exercised by the instrumented tests on the emulator.
 */
class EncryptedBackupCodecTest {

    /**
     * A 32-byte AES-256 key. The bytes are deterministic
     * (all-`0x42`) so a failing test produces the same
     * diff every run — no flake.
     */
    private val key = SecretKeySpec(
        ByteArray(32) { 0x42 },
        "AES",
    )

    @Test
    fun `wrap then unwrap returns the original JSON`() {
        val plaintext = """{"version":1,"note":"hello"}"""
        val blob = EncryptedBackupCodec.wrapWith(plaintext, key, ContentType.Notes)
        assertNotNull("wrap must produce a non-null blob", blob)
        val unwrapped = EncryptedBackupCodec.unwrapWith(blob!!, key, ContentType.Notes)
        assertEquals(plaintext, unwrapped)
    }

    @Test
    fun `wrap produces a blob that is 12 bytes longer than the plaintext plus 16-byte GCM tag`() {
        val plaintext = "x".repeat(200)
        val blob = EncryptedBackupCodec.wrapWith(plaintext, key, ContentType.Notes)!!
        // 12 (IV) + 200 (ciphertext) + 16 (GCM tag) = 228
        assertEquals(228, blob.size)
    }

    @Test
    fun `wrap produces different ciphertexts for the same plaintext (random IV)`() {
        val plaintext = "the same text"
        val a = EncryptedBackupCodec.wrapWith(plaintext, key, ContentType.Notes)!!
        val b = EncryptedBackupCodec.wrapWith(plaintext, key, ContentType.Notes)!!
        // The first 12 bytes are the IV; the rest is
        // ciphertext. Even a single-bit IV difference
        // cascades through GCM, so the two blobs are
        // byte-different.
        val ivA = a.copyOfRange(0, 12)
        val ivB = b.copyOfRange(0, 12)
        // Both IVs are 12 bytes (sanity).
        assertEquals("IV A must be 12 bytes", 12, ivA.size)
        assertEquals("IV B must be 12 bytes", 12, ivB.size)
        // The two IVs are not byte-equal.
        assertTrue("IVs must differ across wraps", !ivA.contentEquals(ivB))
        // And the full blobs are not byte-equal.
        assertTrue("blobs must differ across wraps", !a.contentEquals(b))
    }

    @Test
    fun `wrap with one key and unwrap with another returns null (authentication failure)`() {
        val plaintext = "secret data"
        val blob = EncryptedBackupCodec.wrapWith(plaintext, key, ContentType.Notes)!!
        val otherKey = SecretKeySpec(ByteArray(32) { 0x99.toByte() }, "AES")
        val unwrapped = EncryptedBackupCodec.unwrapWith(blob, otherKey, ContentType.Notes)
        // GCM auth tag verification fails, the cipher
        // throws BadPaddingException or similar, and
        // unwrap returns null.
        assertNull("unwrapping with a different key must fail", unwrapped)
    }

    @Test
    fun `unwrap on a too-short blob returns null`() {
        val tiny = ByteArray(10)
        assertNull(EncryptedBackupCodec.unwrapWith(tiny, key, ContentType.Notes))
    }

    @Test
    fun `unwrap on a tampered blob returns null (GCM auth failure)`() {
        val plaintext = "this must round-trip"
        val blob = EncryptedBackupCodec.wrapWith(plaintext, key, ContentType.Notes)!!
        // Flip a bit in the middle of the ciphertext.
        blob[blob.size - 5] = (blob[blob.size - 5].toInt() xor 0x01).toByte()
        val unwrapped = EncryptedBackupCodec.unwrapWith(blob, key, ContentType.Notes)
        assertNull("tampered ciphertext must not decrypt", unwrapped)
    }

    @Test
    fun `wrap then unwrap preserves UTF-8 text round-trip`() {
        val plaintext = "नमस्ते 你好 🌅 — UTF-8 survives AES-GCM"
        val blob = EncryptedBackupCodec.wrapWith(plaintext, key, ContentType.Notes)
        assertNotNull(blob)
        val unwrapped = EncryptedBackupCodec.unwrapWith(blob!!, key, ContentType.Notes)
        assertEquals(plaintext, unwrapped)
    }

    @Test
    fun `wrap with Notes AAD then unwrap as Letters returns null (cross-type AAD failure)`() {
        val plaintext = """{"date":"2026-08-13","body":"a note"}"""
        val blob = EncryptedBackupCodec.wrapWith(plaintext, key, ContentType.Notes)!!
        // Unwrap with the wrong ContentType — the AAD
        // does not match and the GCM tag check fails.
        val unwrapped = EncryptedBackupCodec.unwrapWith(blob, key, ContentType.Letters)
        assertNull(
            "unwrapping a Notes-wrapped blob as Letters must fail (AAD mismatch). " +
                "The cross-type swap is exactly the attack the v0.25.9 AAD fix defends against.",
            unwrapped,
        )
        // Sanity: the same blob unwraps correctly with Notes.
        val ok = EncryptedBackupCodec.unwrapWith(blob, key, ContentType.Notes)
        assertEquals(plaintext, ok)
    }
}
