package org.mindanchor.continuity.crypto

/**
 * A portable recovery key: 32 random bytes, plus a [keyId] derived from
 * those bytes (see [RecoveryKeyCodec]'s `keyId` derivation) that identifies
 * the key without revealing it.
 *
 * This is *not* a `data class`. [bytes] is a `ByteArray`, and the
 * auto-generated `equals`/`hashCode` a `data class` would produce for a
 * `ByteArray` property compares reference identity, not content — a classic
 * pitfall. [equals]/[hashCode] below compare content explicitly instead.
 *
 * [toString] never prints [bytes] or its human-readable form: the key
 * material must never end up in a log line, a crash report, or a debugger
 * "toString" tooltip by accident.
 */
class RecoveryKey(val bytes: ByteArray, val keyId: String) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RecoveryKey) return false
        return keyId == other.keyId && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * keyId.hashCode() + bytes.contentHashCode()

    /** Deliberately omits [bytes] and the human-readable form — see class KDoc. */
    override fun toString(): String = "RecoveryKey(keyId=$keyId)"
}
