package org.mindanchor.friction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

/**
 * The integrity layer for the v0.20.0 plaintext codecs.
 * The pure-function test uses a fixed HMAC key (the
 * production path uses the Android Keystore; both go
 * through the same `Mac.init(Key)` API).
 *
 * @see docs/research/19 for the rationale and the
 * MASTG-BEST-0066 reference.
 */
class IntegritySealedCodecTest {

    /**
     * A trivial inner codec for testing. The integrity
     * layer is codec-agnostic: it operates on the
     * String <-> String transform. A real inner codec
     * is `OpenLoop.encode(List<String>) -> String`
     * (tab/newline) and the inverse; the integrity
     * layer wraps that. For the test, the inner is
     * the identity to keep the assertions focused on
     * the integrity behavior.
     */
    private val identityCodec = object : IntegritySealedCodec.Codec<String> {
        override fun encode(value: String): String = value
        override fun decode(encoded: String): String = encoded
    }

    private val key = SecretKeySpec(
        ByteArray(32) { it.toByte() },
        "HmacSHA256",
    )

    private fun newSealed() = IntegritySealedCodec(identityCodec, key)

    @Test
    fun `round-trip encode-decode preserves content`() {
        val sealed = newSealed()
        val original = "hello world"
        val encoded = sealed.encode(original)
        val decoded = sealed.decode(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `encoded form is the inner payload plus a tab plus a base64 mac`() {
        val sealed = newSealed()
        val encoded = sealed.encode("hello")
        val parts = encoded.split('\t')
        // The inner is identity, so payload is "hello".
        // The MAC is base64 of a 32-byte HMAC-SHA256 tag.
        assertEquals("hello", parts[0])
        assertEquals(2, parts.size)
        // Base64 of 32 bytes is 44 characters (no padding
        // when the input length is not a multiple of 3;
        // here it is a multiple, so 44 with one `=`).
        val macPart = parts[1]
        // Just verify it parses as base64 and is 32 raw bytes.
        val mac = android.util.Base64.decode(macPart, android.util.Base64.NO_WRAP)
        assertEquals(32, mac.size)
    }

    @Test
    fun `byte-flip in payload fails verification and falls back to plaintext or empty`() {
        val sealed = newSealed()
        val encoded = sealed.encode("hello")
        // Flip the first byte of the payload.
        val flipped = encoded.replaceFirst("h", "H")
        val decoded = sealed.decode(flipped)
        // The MAC will fail. The fallback tries the
        // inner codec on the whole string as plaintext;
        // the identity codec returns the flipped string
        // verbatim. This is the "rebuild" behavior — the
        // data is treated as untrusted and the next
        // write replaces it with a fresh MAC.
        assertEquals(flipped, decoded)
        assertNotEquals("hello", decoded)
    }

    @Test
    fun `byte-flip in the MAC fails verification`() {
        val sealed = newSealed()
        val encoded = sealed.encode("hello")
        // Find the MAC part and flip a byte in it.
        // The MAC base64 is 44 chars long (32 bytes).
        // Flipping the *first* character of the MAC
        // changes a high-entropy byte, which is the
        // realistic forgery case. Flipping the last
        // character may not change the decoded MAC
        // because the trailing base64 chars carry
        // padding-only bits.
        val lastTab = encoded.lastIndexOf('\t')
        val payload = encoded.substring(0, lastTab)
        val mac = encoded.substring(lastTab + 1)
        val flippedMac = (if (mac[0] == 'A') 'B' else 'A') + mac.substring(1)
        val forged = "$payload\t$flippedMac"
        val decoded = sealed.decode(forged)
        // MAC verification fails; the fallback decodes
        // the *whole* forged string as plaintext; the
        // identity codec returns it verbatim.
        assertEquals(forged, decoded)
    }

    @Test
    fun `empty-codec round-trip works`() {
        val sealed = newSealed()
        val encoded = sealed.encode("")
        assertEquals("", sealed.decode(encoded))
    }

    @Test
    fun `unicode-codec round-trip works`() {
        val sealed = newSealed()
        val original = "हिंदी · 日本語 · 한국어"
        val encoded = sealed.encode(original)
        assertEquals(original, sealed.decode(encoded))
    }

    @Test
    fun `wrong-key verification fails`() {
        val sealedA = newSealed()
        val keyB = SecretKeySpec(
            ByteArray(32) { (it + 1).toByte() },
            "HmacSHA256",
        )
        val sealedB = IntegritySealedCodec(identityCodec, keyB)
        val encoded = sealedA.encode("hello")
        val decoded = sealedB.decode(encoded)
        // MAC verification with the wrong key fails;
        // the fallback decodes the whole string as
        // plaintext, which the identity codec returns
        // verbatim. The forged form is the *whole*
        // encoded string including the MAC, not the
        // original payload.
        assertEquals(encoded, decoded)
        assertNotEquals("hello", decoded)
    }

    @Test
    fun `migration path - plaintext input (no MAC) decodes via the inner codec`() {
        val sealed = newSealed()
        // A v0.20.0 plaintext form: no tab, no MAC.
        val plain = "hello"
        assertEquals(plain, sealed.decode(plain))
    }

    @Test
    fun `migration path - re-encoding a plaintext input produces a MACed form`() {
        val sealed = newSealed()
        val encoded = sealed.encode("hello")
        // Now decode the plaintext form and re-encode;
        // the result is the MACed form.
        val decoded = sealed.decode("hello")
        val reEncoded = sealed.encode(decoded)
        // Re-encoded should match the original MACed form.
        assertEquals(encoded, reEncoded)
    }

    @Test
    fun `the MAC is the same for the same payload under the same key (deterministic)`() {
        val sealed = newSealed()
        val a = sealed.encode("hello")
        val b = sealed.encode("hello")
        assertEquals(a, b)
    }

    @Test
    fun `different payloads produce different MACs`() {
        val sealed = newSealed()
        val a = sealed.encode("hello")
        val b = sealed.encode("world")
        assertNotEquals(a, b)
    }
}
