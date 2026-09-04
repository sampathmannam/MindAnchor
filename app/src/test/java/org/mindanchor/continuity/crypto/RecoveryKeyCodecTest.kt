package org.mindanchor.continuity.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 8 — pins [RecoveryKeyCodec]: generation, the exact human-readable
 * format, normalization tolerance, and checksum rejection of any
 * single-character corruption.
 */
class RecoveryKeyCodecTest {

    /** A fixed, deterministic 32-byte source — no [java.security.SecureRandom] in tests. */
    private fun fixedBytes(seed: Int): () -> ByteArray = {
        ByteArray(32) { i -> ((seed + i) and 0xFF).toByte() }
    }

    private val humanFormRegex = Regex("^MA1(-[0-9A-Za-z_-]{6}){8}$")

    @Test
    fun `generate produces a valid 32-byte key with a 16-hex-char key id`() {
        val key = RecoveryKeyCodec.generate()

        assertEquals(32, key.bytes.size)
        assertTrue("keyId must be 16 lowercase hex chars, was ${key.keyId}", key.keyId.matches(Regex("^[0-9a-f]{16}$")))
    }

    @Test
    fun `generate injects a deterministic byte source in tests`() {
        val key = RecoveryKeyCodec.generate(fixedBytes(1))

        assertTrue(fixedBytes(1)().contentEquals(key.bytes))
    }

    @Test
    fun `format produces the exact MA1 grouped human form`() {
        val key = RecoveryKeyCodec.generate(fixedBytes(7))
        val human = RecoveryKeyCodec.format(key)

        // MA1 + 8 groups of 6, joined by hyphens: 3 + 1 + 8*6 + 7 = 59 chars.
        assertEquals(59, human.length)
        assertTrue("'$human' must match $humanFormRegex", human.matches(humanFormRegex))
        assertTrue(human.startsWith("MA1-"))
        assertEquals(8, human.removePrefix("MA1-").split("-").size)
        assertTrue(human.removePrefix("MA1-").split("-").all { it.length == 6 })
    }

    @Test
    fun `format then decode round-trips to an equal key`() {
        val key = RecoveryKeyCodec.generate(fixedBytes(42))
        val human = RecoveryKeyCodec.format(key)

        val decoded = RecoveryKeyCodec.decode(human)

        assertNotNull(decoded)
        assertTrue(key.bytes.contentEquals(decoded!!.bytes))
        assertEquals(key.keyId, decoded.keyId)
    }

    @Test
    fun `decode normalizes hyphens, case of the prefix, and surrounding whitespace`() {
        val key = RecoveryKeyCodec.generate(fixedBytes(99))
        val human = RecoveryKeyCodec.format(key)

        // Built by extracting each fixed-position 6-char group directly,
        // NOT by blindly stripping every '-' from `human` — a base64url
        // character can legitimately be '-', so naively removing all
        // hyphens can corrupt real payload data instead of only removing
        // separators.
        val withoutHyphens = "MA1" + (0 until 8).joinToString("") { g ->
            val start = 4 + g * 7
            human.substring(start, start + 6)
        }
        val lowercasePrefix = "ma1" + human.removePrefix("MA1")
        val withWhitespace = "  " + human.replace("-", "-\n\t ") + "  "

        for (variant in listOf(human, withoutHyphens, lowercasePrefix, withWhitespace)) {
            val decoded = RecoveryKeyCodec.decode(variant)
            assertNotNull("variant '$variant' must decode", decoded)
            assertTrue(key.bytes.contentEquals(decoded!!.bytes))
        }
    }

    @Test
    fun `decode rejects a tampered checksum`() {
        val key = RecoveryKeyCodec.generate(fixedBytes(5))
        val human = RecoveryKeyCodec.format(key)

        // The checksum lives in the final group (the last 4 of the 36
        // payload bytes are entirely within the last 6-char group).
        val lastChar = human.last()
        val replacement = if (lastChar == 'A') 'B' else 'A'
        val tampered = human.dropLast(1) + replacement

        assertNull(RecoveryKeyCodec.decode(tampered))
    }

    @Test
    fun `flipping any single character in the human form fails to decode`() {
        val key = RecoveryKeyCodec.generate(fixedBytes(123))
        val human = RecoveryKeyCodec.format(key)

        for (i in human.indices) {
            if (human[i] == '-') continue // separators carry no data
            val replacement = if (human[i] == 'A') 'B' else 'A'
            val corrupted = human.substring(0, i) + replacement + human.substring(i + 1)
            assertNull("corrupting index $i ('${human[i]}' -> '$replacement') must fail to decode", RecoveryKeyCodec.decode(corrupted))
        }
    }

    @Test
    fun `decode rejects the wrong prefix`() {
        val key = RecoveryKeyCodec.generate(fixedBytes(2))
        val human = RecoveryKeyCodec.format(key)
        val wrongPrefix = "MB1" + human.removePrefix("MA1")

        assertNull(RecoveryKeyCodec.decode(wrongPrefix))
    }

    @Test
    fun `decode rejects an invalid base64url alphabet character`() {
        val key = RecoveryKeyCodec.generate(fixedBytes(3))
        val human = RecoveryKeyCodec.format(key)
        // '!' is never a valid base64url character.
        val invalidAlphabet = human.substring(0, human.length - 1) + "!"

        assertNull(RecoveryKeyCodec.decode(invalidAlphabet))
    }

    @Test
    fun `decode rejects the wrong decoded byte count`() {
        // One group short: 44 payload chars instead of 48 decodes to
        // fewer than 36 bytes.
        val tooShort = "MA1-" + (1..7).joinToString("-") { "AAAAAA" }

        assertNull(RecoveryKeyCodec.decode(tooShort))
    }

    @Test
    fun `stable key id - same key always derives the same id, different keys differ`() {
        val keyA1 = RecoveryKeyCodec.generate(fixedBytes(10))
        val keyA2 = RecoveryKeyCodec.generate(fixedBytes(10))
        val keyB = RecoveryKeyCodec.generate(fixedBytes(20))

        assertEquals(keyA1.keyId, keyA2.keyId)
        assertNotEquals(keyA1.keyId, keyB.keyId)
    }
}
