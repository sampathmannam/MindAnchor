package org.mindanchor.continuity.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Generates, formats, and parses the portable recovery key.
 *
 * ## Wire shape
 *
 * The key's payload is 32 random bytes plus a 4-byte checksum — the first
 * 4 bytes of SHA-256(the 32 random bytes) — for 36 payload bytes total.
 * Base64url-without-padding-encoding 36 bytes (288 bits) always produces
 * exactly `288 / 6 = 48` characters with no padding needed (288 is a
 * multiple of 6). Grouped into 6-character chunks, that is exactly 8
 * groups, and with the `MA1-` prefix the full human form is exactly:
 *
 * ```text
 * MA1-xxxxxx-xxxxxx-xxxxxx-xxxxxx-xxxxxx-xxxxxx-xxxxxx-xxxxxx
 * ```
 *
 * The `MA1` prefix and the trailing checksum are format/integrity
 * controls — they exist to catch a mistyped or mis-copied key at decode
 * time, not to make any claim about the key's strength. 32 random bytes is
 * already very strong entropy on its own.
 *
 * ## Normalization rules (applied by [decode])
 *
 * A recovery key is a value the user copies, retypes, or reads aloud — so
 * [decode] tolerates the incidental noise that introduces, without ever
 * softening what the checksum is for (catching a *real* transcription
 * error):
 *
 *  - All whitespace (spaces, tabs, newlines) is stripped, wherever it
 *    appears in the string.
 *  - The `MA1` prefix is matched case-insensitively (`ma1`, `Ma1`, ... all
 *    accepted) — it is a fixed literal, not payload data.
 *  - The grouping hyphens are optional: both the fully-grouped canonical
 *    form (`MA1-xxxxxx-...-xxxxxx`, 59 characters after whitespace
 *    removal) and the fully-concatenated form with no separators at all
 *    (`MA1` + the 48-character base64url payload, 51 characters) are
 *    accepted. Any other length is rejected.
 *  - The base64url payload itself is **not** case-folded. Base64url is
 *    case-sensitive by construction (upper- and lower-case letters encode
 *    different values), so normalizing case there would silently corrupt
 *    real key material rather than tolerate a typo.
 *  - When the grouped form is used, hyphens are only ever treated as
 *    separators at their fixed positions (immediately after the prefix,
 *    then every 6 payload characters). This means a base64url character
 *    that happens to be a literal `-` inside a group is never mistaken
 *    for a separator.
 */
object RecoveryKeyCodec {

    private const val PREFIX = "MA1"
    private const val GROUP_COUNT = 8
    private const val GROUP_SIZE = 6
    private const val PAYLOAD_B64_LENGTH = GROUP_COUNT * GROUP_SIZE // 48
    private const val KEY_BYTES_LENGTH = 32
    private const val CHECKSUM_BYTES_LENGTH = 4
    private const val PAYLOAD_BYTES_LENGTH = KEY_BYTES_LENGTH + CHECKSUM_BYTES_LENGTH // 36
    private const val KEY_ID_SOURCE_BYTES = 8

    // Length of everything after the "MA1" prefix, grouped form: one
    // separator before each of the 8 groups, plus the 48 payload chars.
    private const val REST_LENGTH_GROUPED = GROUP_COUNT * (GROUP_SIZE + 1) // 56
    private const val REST_LENGTH_UNGROUPED = PAYLOAD_B64_LENGTH // 48

    /**
     * Generates a new [RecoveryKey]. Production callers use the default
     * [SecureRandom]-backed byte source; tests inject a deterministic
     * [random] function instead.
     */
    fun generate(random: () -> ByteArray = { ByteArray(KEY_BYTES_LENGTH).also { SecureRandom().nextBytes(it) } }): RecoveryKey {
        val keyBytes = random()
        require(keyBytes.size == KEY_BYTES_LENGTH) {
            "recovery key byte source must return $KEY_BYTES_LENGTH bytes, got ${keyBytes.size}"
        }
        return RecoveryKey(keyBytes, deriveKeyId(keyBytes))
    }

    /** Formats [key] as the human-readable `MA1-xxxxxx-...` form. */
    fun format(key: RecoveryKey): String {
        val checksum = checksumOf(key.bytes)
        val payload = key.bytes + checksum
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(payload)
        check(encoded.length == PAYLOAD_B64_LENGTH) {
            "unexpected base64url payload length: ${encoded.length}"
        }
        val groups = (0 until GROUP_COUNT).map { g ->
            encoded.substring(g * GROUP_SIZE, (g + 1) * GROUP_SIZE)
        }
        return "$PREFIX-" + groups.joinToString("-")
    }

    /**
     * Parses [human] back into a [RecoveryKey], applying the normalization
     * rules documented on this object. Returns null when the prefix, the
     * base64url alphabet, the decoded byte count, or the checksum does not
     * match — i.e. any transcription error.
     */
    fun decode(human: String): RecoveryKey? {
        val cleaned = human.filterNot { it.isWhitespace() }
        if (cleaned.length < PREFIX.length) return null
        if (!cleaned.substring(0, PREFIX.length).equals(PREFIX, ignoreCase = true)) return null
        val rest = cleaned.substring(PREFIX.length)

        val base64Payload = when (rest.length) {
            REST_LENGTH_GROUPED -> extractGroupedPayload(rest) ?: return null
            REST_LENGTH_UNGROUPED -> rest
            else -> return null
        }

        val decoded = runCatching { Base64.getUrlDecoder().decode(base64Payload) }.getOrNull() ?: return null
        if (decoded.size != PAYLOAD_BYTES_LENGTH) return null

        val keyBytes = decoded.copyOfRange(0, KEY_BYTES_LENGTH)
        val checksum = decoded.copyOfRange(KEY_BYTES_LENGTH, PAYLOAD_BYTES_LENGTH)
        if (!checksum.contentEquals(checksumOf(keyBytes))) return null

        return RecoveryKey(keyBytes, deriveKeyId(keyBytes))
    }

    /**
     * Extracts the 48-character base64url payload from [rest] (the part of
     * a cleaned human string after the `MA1` prefix), treating a hyphen as
     * a separator only at its fixed position — never inside a group, even
     * if a group's own base64url characters happen to include a literal
     * `-`. Returns null if a separator is missing where one is expected.
     */
    private fun extractGroupedPayload(rest: String): String? {
        val sb = StringBuilder(PAYLOAD_B64_LENGTH)
        for (g in 0 until GROUP_COUNT) {
            val sepIndex = g * (GROUP_SIZE + 1)
            if (rest[sepIndex] != '-') return null
            sb.append(rest, sepIndex + 1, sepIndex + 1 + GROUP_SIZE)
        }
        return sb.toString()
    }

    /** The first 4 bytes of SHA-256(keyBytes). */
    private fun checksumOf(keyBytes: ByteArray): ByteArray =
        sha256(keyBytes).copyOfRange(0, CHECKSUM_BYTES_LENGTH)

    /**
     * `keyId`: SHA-256 of the 32-byte key, first 8 bytes, lowercase hex
     * (16 hex characters). Identifies the key without revealing it — see
     * [BackupEnvelopeCodec]'s cheap wrong-key pre-check.
     */
    private fun deriveKeyId(keyBytes: ByteArray): String =
        sha256(keyBytes).copyOfRange(0, KEY_ID_SOURCE_BYTES).joinToString("") { "%02x".format(it) }

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
}
