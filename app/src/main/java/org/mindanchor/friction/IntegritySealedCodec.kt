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
 *
 *     v1\t<payload>\t<base64-mac>
 *
 * The `v1` envelope marker is unambiguous: a sealed
 * record always starts with the literal `v1\t`. A v0.20.0
 * plaintext form (no envelope marker) is *not* accepted on
 * read; the first read after a fresh install returns the
 * codec's default (an empty list, an empty plan, etc.)
 * and the first write seals it. This is the v0.20.1
 * behavior — the migration fall-through that the v0.20.0
 * implementation had was a vulnerability (CodeRabbit
 * audit, 2026-08-08: a forged or corrupted record was
 * decoded as plaintext, defeating the integrity check).
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
    /**
     * The reset value returned when a sealed record is
     * missing, malformed, or fails MAC verification. The
     * inner codec knows the type (empty list, empty
     * plan); this string is the inner codec's
     * `encode("")` form — the encoder's "fresh install"
     * value.
     */
    private val resetValue: String = "",
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
        return "$ENVELOPE\t$payload\t${Base64.encodeToString(mac, Base64.NO_WRAP)}"
    }

    override fun decode(encoded: String): String {
        // The envelope marker is required. Anything else
        // is a v0.20.0 plaintext form, a forge, or a
        // corruption. The first read on a fresh install
        // and any tampered record both land here. The
        // result is the reset value.
        if (!encoded.startsWith(ENVELOPE_PREFIX)) {
            return resetValue
        }
        // Strip the "v1\t" prefix and find the last
        // tab separating the payload from the MAC.
        val body = encoded.substring(ENVELOPE_PREFIX.length)
        val lastTab = body.lastIndexOf('\t')
        if (lastTab < 0) {
            return resetValue
        }
        val payload = body.substring(0, lastTab)
        val macPart = body.substring(lastTab + 1)
        // The MAC part is base64. If decoding fails or
        // the MAC doesn't verify, return the reset value.
        // The whole point of the integrity layer is to
        // detect forgery; a fall-through to plaintext
        // would defeat the protection.
        val providedMac = try {
            Base64.decode(macPart, Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            return resetValue
        }
        if (!constantTimeEquals(hmac(payload), providedMac)) {
            return resetValue
        }
        return inner.decode(payload)
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

    private companion object {
        /** The envelope marker. Anything else is rejected. */
        const val ENVELOPE = "v1"
        const val ENVELOPE_PREFIX = "v1\t"
    }
}
