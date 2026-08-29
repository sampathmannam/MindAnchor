package org.mindanchor.continuity

import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.mindanchor.backup.BackupCodec

/**
 * Computes the deterministic content hash for a [ContinuityPayload].
 *
 * The hash is over *content* only — [ContinuitySnapshot.snapshotId],
 * [ContinuitySnapshot.createdAt], and [ContinuitySnapshot.sourceDeviceId]
 * are never passed in here, which is why [hash] takes the payload rather
 * than the whole snapshot. The same logical data restored on a new phone
 * (new snapshot id, new creation time, new device id) must produce the
 * same hash, so list order and the legacy backup's own embedded save
 * timestamp are also normalised away before hashing.
 *
 * ## Why the hash is versioned
 *
 * Program 1 appended two lists to [ContinuityPayload]. Serialising with
 * `encodeDefaults = true` writes those fields even when both lists are
 * empty, so a single unversioned hash function would have changed the
 * digest of every payload in existence — and with it, the verification
 * step of every encrypted checkpoint a Program 0 build ever wrote. A
 * replacement phone restoring such a checkpoint would have merged the data
 * correctly and then reported `VerifyMismatch`.
 *
 * So [hash] projects the payload onto the field set of the requested
 * format version before digesting it: version 1 serialises exactly the ten
 * fields Program 0 had, version 2 serialises all twelve. Callers verifying
 * a staged snapshot pass **that snapshot's own** `formatVersion`, not the
 * current one.
 */
object ContinuityContentHasher {

    private val json = Json { encodeDefaults = true }

    /**
     * Sorts every list in [payload] into its stable canonical order.
     * Journal/context/measure/change rows sort lexicographically by their
     * string id; notes sort numerically by id; letters and read dates sort
     * lexicographically by their ISO date string (which sorts correctly
     * for `yyyy-MM-dd`); package lists sort lexicographically; ledger
     * events sort by sequence then id and study phases by ordinal then id,
     * which is both their natural order and a total one. Exposed so
     * [ContinuitySnapshotRepository] can build an already-canonical payload
     * before computing [hash], rather than duplicating the sort keys.
     */
    fun sorted(payload: ContinuityPayload): ContinuityPayload = payload.copy(
        journalEntries = payload.journalEntries.sortedBy { it.id },
        contextRows = payload.contextRows.sortedBy { it.id },
        morningMeasures = payload.morningMeasures.sortedBy { it.id },
        notes = payload.notes.sortedBy { it.id },
        letters = payload.letters.sortedBy { it.date },
        readLetterDates = payload.readLetterDates.sorted(),
        frictionedApps = payload.frictionedApps.sorted(),
        alwaysOpenApps = payload.alwaysOpenApps.sorted(),
        continuityChanges = payload.continuityChanges.sortedBy { it.id },
    )

    /**
     * Lowercase SHA-256 hex of the canonicalized [payload], projected onto
     * [formatVersion]'s field set.
     *
     * @throws IllegalArgumentException if [formatVersion] is not one this
     *   build knows how to project onto — better a loud failure than a
     *   hash silently computed over the wrong shape.
     */
    fun hash(
        payload: ContinuityPayload,
        formatVersion: Int = ContinuityContract.SNAPSHOT_FORMAT_VERSION,
    ): String {
        require(formatVersion in ContinuityContract.SUPPORTED_SNAPSHOT_FORMAT_VERSIONS) {
            "unsupported snapshot format version: $formatVersion"
        }
        val canonical = canonicalize(payload)
        val text = when (formatVersion) {
            ContinuityContract.PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION -> json.encodeToString(projectV1(canonical))
            else -> json.encodeToString(canonical)
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(text.encodeToByteArray())
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    /**
     * Sorts, then strips the two things that are bookkeeping rather than
     * content: which snapshot last acknowledged a change, and the legacy
     * backup's own export timestamp.
     */
    private fun canonicalize(payload: ContinuityPayload): ContinuityPayload =
        sorted(payload).let { sortedPayload ->
            sortedPayload.copy(
                // Excluded from hashed content: which snapshot last
                // acknowledged a change is bookkeeping about the sync
                // process, not about the change itself.
                continuityChanges = sortedPayload.continuityChanges.map {
                    it.copy(acknowledgedSnapshotId = null)
                },
                legacyBackupJson = normalizeLegacyBackup(sortedPayload.legacyBackupJson),
            )
        }

    /**
     * Zeroes the legacy backup's own `savedAt` field so its embedded
     * export timestamp never perturbs the content hash. Falls back to the
     * raw text unchanged if it does not parse as a legacy backup — the
     * hash should still be deterministic for the same raw string, just
     * without the timestamp normalisation.
     */
    private fun normalizeLegacyBackup(raw: String): String {
        if (raw.isBlank()) return raw
        val backup = BackupCodec.decode(raw) ?: return raw
        return BackupCodec.encode(backup.copy(savedAt = 0L))
    }

    private fun projectV1(payload: ContinuityPayload): V1Payload = V1Payload(
        journalEntries = payload.journalEntries,
        contextRows = payload.contextRows,
        morningMeasures = payload.morningMeasures,
        notes = payload.notes,
        letters = payload.letters,
        readLetterDates = payload.readLetterDates,
        frictionedApps = payload.frictionedApps,
        alwaysOpenApps = payload.alwaysOpenApps,
        continuityChanges = payload.continuityChanges,
        legacyBackupJson = payload.legacyBackupJson,
    )

    /**
     * Program 0's payload field set, in Program 0's declaration order.
     *
     * Both properties matter: kotlinx.serialization writes JSON object
     * keys in declaration order, so reordering these lines would change
     * every Program 0 hash as surely as adding a field would. Nothing may
     * be added to, removed from, or reordered within this class — it
     * describes a shape that already shipped.
     */
    @Serializable
    private data class V1Payload(
        val journalEntries: List<JournalEntryDto>,
        val contextRows: List<JournalContextDto>,
        val morningMeasures: List<MorningMeasureDto>,
        val notes: List<NoteDto>,
        val letters: List<LetterDto>,
        val readLetterDates: List<String>,
        val frictionedApps: List<String>,
        val alwaysOpenApps: List<String>,
        val continuityChanges: List<ContinuityChangeDto>,
        val legacyBackupJson: String,
    )
}
