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
 * is rejected; any record with a wrong MAC is rejected;
 * any record with the wrong codecId is rejected (the
 * v0.20.1 round 2 fix for the cross-codec replay attack).
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
    private val identityCodec = object : Codec<String> {
        override fun encode(value: String): String = value
        override fun decode(encoded: String): String = encoded
    }

    /**
     * A second identity codec for the cross-codec replay
     * test. Both codecs share the inner logic; the
     * difference is the codecId. A sealed value from
     * "alpha" must be rejected by the "beta" codec.
     */
    private val identityCodecBeta = object : Codec<String> {
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
    private fun newSealed(
        codecId: String = "alpha",
        reset: String = "",
        key: javax.crypto.spec.SecretKeySpec = this.key,
    ) = IntegritySealedCodec(
        inner = identityCodec,
        codecId = codecId,
        keyProvider = { key },
        resetValue = reset,
        // Use the JVM Base64 in tests so the test
        // runner does not have to mock the Android
        // Base64 static. The envelope format is
        // the same in both (no-wrap, no-padding).
        base64 = JvmBase64,
    )

    private fun newSealedBeta(
        reset: String = "",
    ) = IntegritySealedCodec(
        inner = identityCodecBeta,
        codecId = "beta",
        keyProvider = { key },
        resetValue = reset,
        base64 = JvmBase64,
    )

    @Test
    fun `round-trip encode-decode preserves content`() {
        val sealed = newSealed()
        val original = "hello world"
        val encoded = sealed.encode(original)
        val decoded = sealed.decode(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `encoded form is v1 envelope codecId payload base64 mac`() {
        val sealed = newSealed(codecId = "alpha")
        val encoded = sealed.encode("hello")
        // The envelope is "v1\t<codecId>\t<payload>\t<base64-mac>".
        val parts = encoded.split('\t')
        // 4 parts: "v1", codecId, payload, base64 MAC.
        assertEquals(4, parts.size)
        assertEquals("v1", parts[0])
        assertEquals("alpha", parts[1])
        // The inner is identity, so payload is "hello".
        assertEquals("hello", parts[2])
        // The MAC is base64 of a 32-byte HMAC-SHA256 tag.
        val mac = JvmBase64.decode(parts[3])!!
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
    fun `cross-codec replay attack is rejected`() {
        // CodeRabbit audit #5: without the codecId
        // binding, an attacker who can modify DataStore
        // can copy a valid sealed value from one
        // preference into another. v0.20.1 round 2
        // includes the codecId in the MAC input, so a
        // sealed value from "alpha" is invalid for
        // "beta".
        val sealedAlpha = newSealed(codecId = "alpha", reset = "RESET")
        val sealedBeta = newSealedBeta(reset = "RESET")
        val encodedAlpha = sealedAlpha.encode("hello")
        // Attempt to replay the alpha-encoded record
        // against the beta codec. The MAC was computed
        // for "alpha:hello", not "beta:hello".
        val decodedBeta = sealedBeta.decode(encodedAlpha)
        assertEquals("RESET", decodedBeta)
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
        val sealedB = IntegritySealedCodec(
            inner = identityCodec,
            codecId = "alpha",
            keyProvider = { keyB },
            resetValue = "RESET",
            base64 = JvmBase64,
        )
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
        val sealed = IntegritySealedCodec(
            inner = identityCodec,
            codecId = "alpha",
            keyProvider = { key },
            base64 = JvmBase64,
        )
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
        val mac = JvmBase64.decode(macPart)!!
        assertEquals(32, mac.size)
    }

    @Test
    fun `keyProvider is called on each operation`() {
        // CodeRabbit audit #4: the v0.20.1 round 1
        // retained an invalid key; the v0.20.1 round 2
        // uses a keyProvider. Each MAC operation calls
        // the provider fresh; the codec never holds the
        // key.
        var callCount = 0
        val key = SecretKeySpec(
            ByteArray(32) { it.toByte() },
            "HmacSHA256",
        )
        val sealed = IntegritySealedCodec(
            inner = identityCodec,
            codecId = "alpha",
            keyProvider = {
                callCount++
                key
            },
            base64 = JvmBase64,
        )
        val encoded = sealed.encode("hello")
        sealed.decode(encoded)
        // One call per MAC operation. encode
        // computes one MAC (for the envelope), decode
        // computes one MAC (to verify the envelope).
        // The provider is called once each — total 2.
        // (The v0.20.1 round-1 test expected 4; that
        // was a stale count from an earlier
        // draft where encode re-verified its own
        // output. The current implementation does
        // not re-verify on encode.)
        assertEquals(2, callCount)
    }

    @Test
    fun `keyProvider returning null propagates as a fail-closed throw on encode`() {
        // If the Keystore is unavailable, the provider
        // returns null. The codec's MAC operation
        // throws; encode() does not catch. The caller
        // (FrictionPrefs / SealedCodecs helper) catches
        // and either logs the error and drops the write,
        // or surfaces it to the UI. The data is not
        // persisted in an unencrypted form; the user's
        // data is lost for this round.
        val sealed = IntegritySealedCodec(
            inner = identityCodec,
            codecId = "alpha",
            keyProvider = { null },
            base64 = JvmBase64,
        )
        // encode() must throw — fail-closed.
        try {
            sealed.encode("hello")
            assert(false) { "encode() should have thrown on a null keyProvider" }
        } catch (e: Exception) {
            // Expected: InvalidKeyException or any
            // Exception is fine. The integrity layer
            // does not silently fall through.
        }
    }

    @Test
    fun `keyProvider returning null on decode returns the reset value`() {
        // The decode path wraps the hmac() call in
        // try/catch. A null keyProvider during decode
        // returns the reset value (fail-closed).
        val sealed = IntegritySealedCodec(
            inner = identityCodec,
            codecId = "alpha",
            keyProvider = { null },
            resetValue = "RESET",
            base64 = JvmBase64,
        )
        // First seal a record with a working key, then
        // try to decode with a null key.
        val goodKey = SecretKeySpec(
            ByteArray(32) { it.toByte() },
            "HmacSHA256",
        )
        val good = IntegritySealedCodec(
            inner = identityCodec,
            codecId = "alpha",
            keyProvider = { goodKey },
            base64 = JvmBase64,
        )
        val encoded = good.encode("hello")
        // Now switch the codec to a null-provider and
        // try to decode. The MAC verification will fail
        // (or the keyProvider will return null and the
        // verify path will catch); the result is the
        // reset value.
        val decoded = sealed.decode(encoded)
        assertEquals("RESET", decoded)
    }
}
