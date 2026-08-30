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
 * ## Why the hash takes a format version
 *
 * Program 1 appended two lists to [ContinuityPayload]. Serialising with
 * `encodeDefaults = true` writes those fields even when both lists are
 * empty, so a single unversioned hash function would change the digest of
 * every payload in existence — and with it, the verification step of every
 * encrypted checkpoint a Program 0 build ever wrote. A replacement phone
 * restoring such a checkpoint would merge the data correctly and then
 * report `VerifyMismatch`.
 *
 * So [hash] projects the payload onto the field set of the requested
 * format version before digesting it. [ContinuityPayloadV1] is that
 * projection for Program 0's ten fields, and it is frozen: the golden test
 * in `ContinuityHashVersionTest` pins both its field order and the digest
 * of a fully populated fixture.
 *
 * A caller verifying a staged snapshot passes **that snapshot's own**
 * `formatVersion`, not the current one — [RestoreCoordinator] reads it
 * from the decrypted file, falling back to the version persisted when the
 * restore was staged. Verifying a Program 0 checkpoint against today's
 * twelve-field digest would fail every backup written before Program 1,
 * after the data had already been merged.
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
        researchLedgerEvents = payload.researchLedgerEvents.sortedWith(compareBy({ it.sequence }, { it.id })),
        studyPhases = payload.studyPhases.sortedWith(compareBy({ it.ordinal }, { it.id })),
        passiveRawProvenance = payload.passiveRawProvenance.sortedWith(compareBy({ it.eventStart }, { it.id })),
        passiveSourceReads =
            payload.passiveSourceReads.sortedWith(compareBy({ it.attemptedAt }, { it.sourceFamily }, { it.id })),
        passiveSourceLags =
            payload.passiveSourceLags.sortedWith(compareBy({ it.observedAt }, { it.sourceFamily }, { it.id })),
        passiveBaselineSegments = payload.passiveBaselineSegments.sortedWith(compareBy({ it.openedAt }, { it.id })),
        passivePipelineRuns = payload.passivePipelineRuns.sortedWith(compareBy({ it.completedAt }, { it.id })),
        passiveWindowRevisions =
            payload.passiveWindowRevisions.sortedWith(compareBy({ it.windowStart }, { it.asOfTime }, { it.id })),
        passiveDailyRevisions =
            payload.passiveDailyRevisions.sortedWith(compareBy({ it.localDate }, { it.asOfTime }, { it.id })),
        passiveObservationDecisions = payload.passiveObservationDecisions.sortedWith(
            compareBy({ it.localDate }, { it.asOfTime }, { it.id }),
        ),
    )

    /**
     * Lowercase SHA-256 hex of the canonicalized [payload], projected onto
     * [formatVersion]'s field set.
     *
     * @throws IllegalArgumentException if [formatVersion] is not one this
     *   build supports, and [IllegalStateException] if it is supported but
     *   has no projection. Both are loud on purpose: a hash silently
     *   computed over the wrong shape is undetectable later.
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
            ContinuityContract.PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION ->
                json.encodeToString(projectV1(canonical))
            ContinuityContract.PROGRAM_ONE_SNAPSHOT_FORMAT_VERSION ->
                json.encodeToString(projectV2(canonical))
            ContinuityContract.SNAPSHOT_FORMAT_VERSION -> json.encodeToString(canonical)
            else -> error("no canonical projection for snapshot format version $formatVersion")
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
     *
     * CRITICAL: this re-encodes through today's [BackupCodec.Backup], so
     * **appending a field to that class changes the content hash of every
     * continuity snapshot ever written**, whatever
     * [ContinuityContract.SNAPSHOT_FORMAT_VERSION] says. `Backup` has
     * grown fields before (`checkIns`, `readings`, `corpusAdditions`,
     * `inferred`). If it must grow again, treat that as a continuity
     * format change: `ContinuityHashVersionTest`'s golden will go red, and
     * the fix is a new snapshot format version with its own projection,
     * not a re-pinned constant.
     */
    private fun normalizeLegacyBackup(raw: String): String {
        if (raw.isBlank()) return raw
        val backup = BackupCodec.decode(raw) ?: return raw
        return BackupCodec.encode(backup.copy(savedAt = 0L))
    }

    private fun projectV1(payload: ContinuityPayload): ContinuityPayloadV1 = ContinuityPayloadV1(
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

    private fun projectV2(payload: ContinuityPayload): ContinuityPayloadV2 = ContinuityPayloadV2(
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
        researchLedgerEvents = payload.researchLedgerEvents,
        studyPhases = payload.studyPhases,
    )
}

/**
 * Program 0's payload field set, in Program 0's declaration order.
 *
 * Both properties matter: kotlinx.serialization writes JSON object keys in
 * declaration order, so reordering these lines would change every Program 0
 * hash as surely as adding a field would. Nothing may be added to, removed
 * from, or reordered within this class — it describes a shape that already
 * shipped, and `ContinuityHashVersionTest` asserts its element names
 * against a literal list for exactly that reason.
 *
 * `internal` rather than private so that test can read the shape directly
 * instead of inferring it from a digest it also generated.
 */
@Serializable
internal data class ContinuityPayloadV1(
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

/** Program 1's payload field set, frozen in its original declaration order. */
@Serializable
internal data class ContinuityPayloadV2(
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
    val researchLedgerEvents: List<ResearchLedgerEventDto>,
    val studyPhases: List<StudyPhaseDto>,
)
