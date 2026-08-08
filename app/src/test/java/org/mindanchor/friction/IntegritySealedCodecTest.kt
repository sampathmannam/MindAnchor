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
 * ## Security model
 *
 * v0.20.0 (the previous behavior) had a fall-through
 * that decoded a forged or tampered record as plaintext
 * on read. CodeRabbit flagged that as a CRITICAL
 * security issue on 2026-08-08: a power user with root
 * could rewrite the friction-gate ledger by simply
 * appending a tab and a fake base64 MAC, and the decoder
 * would silently accept the result.
 *
 * v0.20.1 (this version) requires an unambiguous
 * `v1\t` envelope marker. Any record without the marker
 * is rejected; any record with a wrong MAC is rejected.
 * The reset value (an empty list, an empty plan) is
 * returned instead. The next write seals the data.
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

    /**
     * The reset value is `RESET_MARKER` so we can
     * distinguish "the inner codec produced this" from
     * "the integrity layer returned the default because
     * of a fail." For the identity inner codec, the
     * inner's `encode("")` form is `""`, so the reset
     * value is empty. We use a non-empty sentinel here
     * for clarity.
     */
    private fun newSealed(reset: String = "") =
        IntegritySealedCodec(identityCodec, key, resetValue = reset)

    @Test
    fun `round-trip encode-decode preserves content`() {
        val sealed = newSealed()
        val original = "hello world"
        val encoded = sealed.encode(original)
        val decoded = sealed.decode(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `encoded form is v1 envelope payload tab base64 mac`() {
        val sealed = newSealed()
        val encoded = sealed.encode("hello")
        // The envelope is "v1\t<payload>\t<base64-mac>".
        val parts = encoded.split('\t')
        // 3 parts: "v1", payload, base64 MAC.
        assertEquals(3, parts.size)
        assertEquals("v1", parts[0])
        // The inner is identity, so payload is "hello".
        assertEquals("hello", parts[1])
        // The MAC is base64 of a 32-byte HMAC-SHA256 tag.
        val mac = android.util.Base64.decode(parts[2], android.util.Base64.NO_WRAP)
        assertEquals(32, mac.size)
    }

    @Test
    fun `byte-flip in payload fails verification and returns the reset value`() {
        val sealed = newSealed(reset = "RESET")
        val encoded = sealed.encode("hello")
        // Flip the first byte of the payload.
        val flipped = encoded.replaceFirst("h", "H")
        val decoded = sealed.decode(flipped)
        // The MAC will fail. v0.20.1 returns the reset
        // value, not the flipped payload. This is the
        // security fix.
        assertEquals("RESET", decoded)
        assertNotEquals("hello", decoded)
    }

    @Test
    fun `byte-flip in the MAC fails verification and returns the reset value`() {
        val sealed = newSealed(reset = "RESET")
        val encoded = sealed.encode("hello")
        // The MAC base64 is 44 chars long (32 bytes).
        // Flipping the *first* character of the MAC
        // changes a high-entropy byte, which is the
        // realistic forgery case.
        val lastTab = encoded.lastIndexOf('\t')
        val payload = encoded.substring(0, lastTab)
        val mac = encoded.substring(lastTab + 1)
        val flippedMac = (if (mac[0] == 'A') 'B' else 'A') + mac.substring(1)
        val forged = "$payload\t$flippedMac"
        val decoded = sealed.decode(forged)
        // v0.20.1 returns the reset value, not the
        // forged record.
        assertEquals("RESET", decoded)
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
    fun `wrong-key verification fails and returns the reset value`() {
        val sealedA = newSealed(reset = "RESET")
        val keyB = SecretKeySpec(
            ByteArray(32) { (it + 1).toByte() },
            "HmacSHA256",
        )
        val sealedB = IntegritySealedCodec(identityCodec, keyB, resetValue = "RESET")
        val encoded = sealedA.encode("hello")
        val decoded = sealedB.decode(encoded)
        // MAC verification with the wrong key fails.
        // v0.20.1 returns the reset value. The forged
        // form is rejected, not decoded.
        assertEquals("RESET", decoded)
        assertNotEquals("hello", decoded)
    }

    @Test
    fun `plaintext input without envelope is rejected and returns the reset value`() {
        // v0.20.0 accepted a plaintext form on read for
        // migration. v0.20.1 rejects it: a fresh install
        // gets the reset value, and the first write
        // produces a sealed record. This is the security
        // hardening; the v0.20.0 fall-through was a
        // vulnerability.
        val sealed = newSealed(reset = "RESET")
        val plain = "hello"
        assertEquals("RESET", sealed.decode(plain))
    }

    @Test
    fun `plaintext input with v0_20_0 tab-mac form (no v1 prefix) is rejected`() {
        // The v0.20.0 form was "<payload>\t<base64-mac>".
        // v0.20.1 requires the "v1\t" prefix. A v0.20.0
        // record is treated as a forge and rejected.
        val sealed = newSealed(reset = "RESET")
        val legacy = "hello\tAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        assertEquals("RESET", sealed.decode(legacy))
    }

    @Test
    fun `re-encoding a reset value produces a sealed record that decodes to the reset`() {
        val sealed = newSealed(reset = "RESET")
        // After a rejected read, the next encode() seals
        // the reset value. A subsequent read returns the
        // reset value (the inner codec decodes "RESET"
        // to "RESET" for the identity inner).
        val encoded = sealed.encode(sealed.decode("not-an-envelope"))
        assertEquals("RESET", sealed.decode(encoded))
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

    @Test
    fun `empty string is a valid reset value (default)`() {
        // The default reset value is the empty string.
        // For the identity inner, "" decodes to "".
        val sealed = IntegritySealedCodec(identityCodec, key)
        assertEquals("", sealed.decode("not-an-envelope"))
        assertEquals("", sealed.decode(""))
    }

    @Test
    fun `MAC tag length is 32 bytes (SHA-256 output)`() {
        // The HMAC tag is the SHA-256 output. The
        // constant is load-bearing for the integration
        // with the KeystoreHmacKey.
        val sealed = newSealed()
        val encoded = sealed.encode("hello")
        val lastTab = encoded.lastIndexOf('\t')
        val macPart = encoded.substring(lastTab + 1)
        val mac = android.util.Base64.decode(macPart, android.util.Base64.NO_WRAP)
        assertEquals(32, mac.size)
    }
}
