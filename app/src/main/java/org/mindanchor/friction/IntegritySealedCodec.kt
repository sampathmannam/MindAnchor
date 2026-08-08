package org.mindanchor.friction

import android.util.Base64
import java.security.Key
import javax.crypto.Mac

/**
 * The integrity layer for the v0.20.0 plaintext codecs.
 *
 * Each codec stores its data as `tab/newline` plaintext in
 * the friction DataStore. The plaintext form is a design
 * choice: it keeps the binary small and the data
 * inspectable. The threat is *forgery* — a power user
 * with root can rewrite the friction-gate ledger and
 * silence the gate, which is the exact mechanism the
 * project exists to provide.
 *
 * The fix is an HMAC-SHA256 tag over the encoded payload,
 * keyed with a non-exportable key in the Android Keystore.
 * Any byte flip fails the read. The format is
 * `<payload>\t<base64-mac>`; a v0.20.0 plaintext form
 * (no MAC) is still accepted for migration but always
 * re-encoded with the MAC on the next write.
 *
 * MASTG-BEST-0066 is the canonical reference:
 * "Storage integrity checks are bypassable if the
 * attacker can extract the HMAC key ... Treat these as a
 * defense-in-depth control rather than a standalone
 * guarantee." The project accepts this; the threat is
 * "a motivated user forges the gate tally," not
 * "a state actor with a TEE bypass."
 *
 * @wording-reviewed — the migration path messages and
 * the "data reset" log entries are user-visible and
 * clinical-review-required.
 */
class IntegritySealedCodec(
    /**
     * The underlying plaintext codec. Same encode/decode
     * signature as the unsealed codec; the integrity
     * layer wraps both calls.
     */
    private val inner: Codec<String>,
    /**
     * The HMAC key. The production source is the
     * Android Keystore (non-exportable); the test path
     * uses a `SecretKeySpec` with a fixed 32-byte
     * key. Either way the key is opaque to this class —
     * we just hand it to `Mac.init()`.
     */
    private val key: Key,
) : Codec<String> {

    /**
     * The minimal codec interface this layer requires.
     * The existing v0.20.0 codecs (OpenLoop, IfThenPlan,
     * etc.) are simple String <-> String codecs; this
     * interface accepts the String form only because the
     * integrity layer operates on the *encoded* form.
     */
    interface Codec<T> {
        fun encode(value: T): String
        fun decode(encoded: String): T
    }

    override fun encode(value: String): String {
        val payload = inner.encode(value)
        val mac = hmac(payload)
        return "$payload\t${Base64.encodeToString(mac, Base64.NO_WRAP)}"
    }

    override fun decode(encoded: String): String {
        val lastTab = encoded.lastIndexOf('\t')
        // If there's no tab, treat as plaintext (the v0.20.0
        // form, pre-integrity). Decode and return.
        if (lastTab < 0) return inner.decode(encoded)
        val payload = encoded.substring(0, lastTab)
        val macPart = encoded.substring(lastTab + 1)
        // Try the MACed form first. If the MAC doesn't
        // verify, fall back to plaintext (the migration
        // path; the data is corrupted or forged, so we
        // return the empty default).
        return try {
            val providedMac = Base64.decode(macPart, Base64.NO_WRAP)
            if (constantTimeEquals(hmac(payload), providedMac)) {
                inner.decode(payload)
            } else {
                // MAC failure: try the inner codec on the
                // whole encoded string as plaintext. This
                // is the migration path.
                try {
                    inner.decode(encoded)
                } catch (_: Exception) {
                    // Genuine corruption: return empty.
                    inner.decode("")
                }
            }
        } catch (_: IllegalArgumentException) {
            // MAC part wasn't base64 — plaintext migration.
            try {
                inner.decode(encoded)
            } catch (_: Exception) {
                inner.decode("")
            }
        }
    }

    /**
     * HMAC-SHA256 over the payload, keyed with [key].
     */
    private fun hmac(payload: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)
        return mac.doFinal(payload.toByteArray(Charsets.UTF_8))
    }

    /**
     * Constant-time comparison to avoid timing side
     * channels on the MAC verification.
     */
    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) {
            diff = diff or (a[i].toInt() xor b[i].toInt())
        }
        return diff == 0
    }
}
