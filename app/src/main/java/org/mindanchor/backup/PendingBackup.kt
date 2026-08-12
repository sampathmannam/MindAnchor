package org.mindanchor.backup

import java.time.Instant

/**
 * One pending backup entry the on-write trigger failed to send.
 *
 * v0.25.5 WP-H: WorkManager offline retry for the v0.25.4 Drive
 * backup. The on-write trigger's best-effort [BackupTarget.append]
 * returns [AppendResult.NetworkError] when the device is offline;
 * the entry is then enqueued here and re-attempted on the next
 * [BackupRetryWorker] run, which carries a
 * [androidx.work.NetworkType.CONNECTED] constraint.
 *
 * The class carries everything the worker needs to re-attempt the
 * append: the [ContentType] (which file), the encrypted payload
 * (the bytes that should have been appended), and the wall-clock
 * instant the entry was first attempted (for the on-disk ordering
 * the wire format requires — Drive has no native append).
 */
data class PendingBackup(
    val type: ContentType,
    val payload: ByteArray,
    val queuedAt: Instant,
) {
    // data class equals/hashCode for ByteArray are reference-based;
    // override to make queue dedup work in tests and in the
    // shared-prefs encode/decode round-trip.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PendingBackup) return false
        return type === other.type &&
            payload.contentEquals(other.payload) &&
            queuedAt == other.queuedAt
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + queuedAt.hashCode()
        return result
    }
}

/**
 * The newline + tab-separated codec for [PendingBackup]s. Same
 * plain-text discipline as the rest of the app's on-device stores
 * — one corrupt line costs one entry, never the file.
 *
 * Wire format: one entry per line, four tab-separated fields:
 *   `typeFileName<TAB>queuedAtIso<TAB>payloadBase64<TAB>payloadLengthBytes`
 *
 * The type is keyed by [ContentType.fileName] (not by enum
 * name) because [ContentType] is a sealed interface, not an
 * enum; a future v0.25.6 + value that adds a third
 * [ContentType] does not require touching this codec.
 * The base64 form is the one a `String`-backed DataStore can
 * hold without further escape rules; the trailing length is a
 * redundant cross-check that the base64 decode produced the
 * expected number of bytes.
 */
object PendingBackupLog {

    private val allTypes: List<ContentType> = listOf(ContentType.Notes, ContentType.Letters)

    fun encode(entries: List<PendingBackup>): String =
        entries.joinToString("\n") { entry ->
            val b64 = java.util.Base64.getEncoder().withoutPadding().encodeToString(entry.payload)
            "${entry.type.fileName}\t${entry.queuedAt}\t$b64\t${entry.payload.size}"
        }

    /** Bad lines are dropped, never thrown on. */
    fun decode(raw: String): List<PendingBackup> =
        raw.lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 4) return@mapNotNull null
            val type = allTypes.firstOrNull { it.fileName == parts[0] } ?: return@mapNotNull null
            val queuedAt = runCatching { Instant.parse(parts[1]) }.getOrNull()
                ?: return@mapNotNull null
            val bytes = runCatching {
                java.util.Base64.getDecoder().decode(parts[2])
            }.getOrNull() ?: return@mapNotNull null
            val expectedLen = parts[3].toIntOrNull() ?: return@mapNotNull null
            if (bytes.size != expectedLen) return@mapNotNull null
            PendingBackup(type = type, payload = bytes, queuedAt = queuedAt)
        }.toList()
}
