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
 * The fix is an HMAC-SHA256 tag over the encoded payload
 * plus a stable [codecId], keyed with a non-exportable
 * key in the Android Keystore. Any byte flip fails the
 * read. The format is
 *
 *     v1\t<codecId>\t<payload>\t<base64-mac>
 *
 * The `v1` envelope marker is unambiguous: a sealed
 * record always starts with the literal `v1\t`. A v0.20.0
 * plaintext form (no envelope marker) is *not* accepted
 * on read; the first read after a fresh install returns
 * the codec's default (an empty list, an empty plan,
 * etc.) and the first write seals it. This is the
 * v0.20.1 behavior — the migration fall-through that
 * the v0.20.0 implementation had was a vulnerability
 * (CodeRabbit audit, 2026-08-08: a forged or corrupted
 * record was decoded as plaintext, defeating the
 * integrity check).
 *
 * The `codecId` field binds the MAC to the storage
 * record. CodeRabbit audit #5 (2026-08-08): without
 * the codecId, an attacker who can modify DataStore
 * can copy a valid sealed value from one preference
 * into another. The MAC would verify (the payload
 * hasn't changed), but the target codec would then
 * consume the substituted payload. The codecId
 * makes the MAC context-dependent; a sealed value
 * from codec A is invalid for codec B.
 *
 * ## Key provider (v0.20.1 round 2)
 *
 * CodeRabbit audit #4 (2026-08-08): the v0.20.1 round 1
 * `hmac()` retained the invalid key after a recovery,
 * and every later operation re-ran the recovery path
 * (deleting the freshly generated key on each call).
 * v0.20.1 round 2 replaces the captured [Key] with a
 * [KeyProvider] — a function the codec calls each
 * time it needs a key. The provider can recover
 * (delete and re-create the key) without the codec
 * having to know the recovery is happening; the
 * codec just calls `provider()` and gets a fresh
 * [Key]. The provider is the *only* place that
 * knows about the Keystore.
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

class IntegritySealedCodec(
    /**
     * The underlying plaintext codec. Same encode/decode
     * signature as the unsealed codec; the integrity
     * layer wraps both calls.
     */
    private val inner: Codec<String>,
    /**
     * A stable identifier for the storage record. The
     * MAC is computed over `codecId + ":" + payload`,
     * so a sealed value from one codec cannot be
     * replayed against another. Convention: the Friction
     * DataStore key (e.g. "small_things",
     * "if_then_plans"). The codecId is part of the
     * public envelope format, so it is not a secret.
     */
    private val codecId: String,
    /**
     * The key provider. v0.20.1 round 2: a function
     * the codec calls each time it needs a [Key]. The
     * provider owns the Keystore and the recovery path;
     * the codec just asks for a key, computes the MAC,
     * and proceeds. If the MAC verification fails, the
     * codec does *not* call the provider to recover —
     * a failed MAC means the payload was tampered, not
     * that the key is bad.
     */
    private val keyProvider: () -> Key,
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

    override fun encode(value: String): String {

        val payload = inner.encode(value)
        val macInput = "$codecId:$payload"
        val mac = try {
            hmac(macInput)
        } catch (e: Exception) {
            // The keyProvider failed (e.g. Keystore
            // unavailable). The integrity layer cannot
            // seal the data; we throw and the caller
            // (FrictionPrefs) catches and either logs
            // and drops the write, or surfaces the error
            // to the UI. The data is not persisted in
            // an unencrypted form; the user's data is
            // lost for this round, and the next write
            // (with a recovered key) will succeed.
            //
            // v0.20.1 round 2: throwing is the only
            // fail-closed option. The v0.20.1 round 1
            // silently returned the inner.encode("") form
            // (an empty string for the empty-list reset
            // value), which would have been written to
            // DataStore as unencrypted text. The user's
            // data was lost AND the integrity promise
            // was broken. Throwing is better.
            throw e
        }
        return "$ENVELOPE\t$codecId\t$payload\t${Base64.encodeToString(mac, Base64.NO_WRAP)}"
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
        // The envelope is "v1\t<codecId>\t<payload>\t<mac>".
        // Strip the prefix and split into the three
        // remaining fields. The codecId field must match
        // this codec's codecId; otherwise the record is
        // a replay from a different storage key.
        val body = encoded.substring(ENVELOPE_PREFIX.length)
        val firstTab = body.indexOf('\t')
        if (firstTab < 0) {
            return resetValue
        }
        val envelopeCodecId = body.substring(0, firstTab)
        if (envelopeCodecId != codecId) {
            // Cross-codec replay attack: the MAC was
            // computed for a different storage record.
            // Return the reset value.
            return resetValue
        }
        val rest = body.substring(firstTab + 1)
        val lastTab = rest.lastIndexOf('\t')
        if (lastTab < 0) {
            return resetValue
        }
        val payload = rest.substring(0, lastTab)
        val macPart = rest.substring(lastTab + 1)
        // The MAC part is base64. If decoding fails or
        // the MAC doesn't verify, return the reset value.
        val providedMac = try {
            Base64.decode(macPart, Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            return resetValue
        }
        // The MAC covers the codecId + the payload, so
        // a tampered payload or a tampered codecId both
        // fail the verification.
        val provided = try {
            constantTimeEquals(hmac("$codecId:$payload"), providedMac)
        } catch (e: Exception) {
            // The keyProvider failed during
            // verification. Fail closed.
            return resetValue
        }
        if (!provided) {
            return resetValue
        }
        return inner.decode(payload)
    }

    /**
     * HMAC-SHA256 over the input, keyed with the
     * current key from the [keyProvider].
     *
     * v0.20.1 round 2: the provider is called on each
     * MAC operation. The provider owns the recovery
     * path; the codec does not retain the key. A
     * provider that returns null (Keystore
     * unavailable) is treated as a fail-closed: the
     * caller catches and returns the reset value.
     */
    private fun hmac(input: String): ByteArray {
        val key = keyProvider() ?: throw java.security.InvalidKeyException("keyProvider returned null")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)
        return mac.doFinal(input.toByteArray(Charsets.UTF_8))
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
