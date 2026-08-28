package org.mindanchor.vitals.coros

import java.security.MessageDigest

/**
 * The COROS Training Hub web API expects the password
 * to be sent as a *hex MD5* of the UTF-8 bytes — not
 * plaintext, not double-hashed, not salted. The
 * reverse-engineered `cygnusb/coros-mcp` reference
 * implementation is the only public source for this:
 *   `hashlib.md5(value.encode()).hexdigest()`
 *
 * The COROS mobile API uses a different scheme
 * (AES-128-CBC + XOR with a per-login app key); the
 * side-channel deliberately does NOT implement the
 * mobile API, because acquiring a mobile token logs
 * the user out of the COROS phone app (the CorosAPIError
 * KDoc on cygnusb/coros-mcp documents this). The
 * web-API path covers HRV and RHR — the two signals
 * the user's COROS Pacer 3 does not export to Health
 * Connect — which is what the side-channel is for.
 */
object CorosPasswordHasher {

    private val HEX_DIGITS = "0123456789abcdef".toCharArray()

    /**
     * Returns the hex-encoded MD5 of the password.
     * The COROS API rejects uppercase hex (tested once
     * during reverse-engineering: 2025-12-04 against
     * a real account, the request returned
     * `result: "1001"` — invalid credentials — when
     * uppercase was sent).
     */
    @Suppress("InsecureCryptoAlgorithm", "WeakHash")
    fun md5Hex(password: CharSequence): String {
        val bytes = password.toString().toByteArray(Charsets.UTF_8)
        // COROS Training Hub web API requires hex MD5 of UTF-8 password bytes (mobile
        // API logs user out). Class KDoc documents the 2025-12-04 contract test.
        val digest = MessageDigest.getInstance("MD5").digest(bytes) // nosemgrep: use-of-md5
        val out = CharArray(digest.size * 2)
        for (i in digest.indices) {
            val b = digest[i].toInt() and BYTE_MASK
            out[i * 2] = HEX_DIGITS[b ushr HEX_HIGH_NIBBLE_SHIFT]
            out[i * 2 + 1] = HEX_DIGITS[b and HEX_LOW_NIBBLE_MASK]
        }
        return String(out)
    }
}

// The MD5 byte-decomposition constants. Top-level so
// the magic-number rule does not fire on the
// binary-decomposition line; the only consumer is
// [CorosPasswordHasher.md5Hex] which is in this file.
private const val BYTE_MASK: Int = 0xff
private const val HEX_HIGH_NIBBLE_SHIFT: Int = 4
private const val HEX_LOW_NIBBLE_MASK: Int = 0x0f
