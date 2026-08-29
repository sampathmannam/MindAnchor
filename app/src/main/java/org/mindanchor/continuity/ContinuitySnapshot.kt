package org.mindanchor.continuity

import kotlinx.serialization.Serializable
import org.mindanchor.data.db.ContinuityChangeEntity
import org.mindanchor.data.db.JournalContextEntity
import org.mindanchor.data.db.JournalEntryEntity
import org.mindanchor.data.db.MorningMeasureEntity
import org.mindanchor.data.db.ResearchLedgerEventEntity
import org.mindanchor.data.db.StudyPhaseEntity
import org.mindanchor.letters.Letter
import org.mindanchor.model.Note

/**
 * The one canonical continuity snapshot: everything Program 0 protects,
 * gathered into a single, deterministically-hashable document. This is the
 * shape a phone captures on this device and a replacement phone restores
 * from — every store in `docs/backup/program-0-data-inventory.md`'s
 * "Protected in Program 0" table has a destination somewhere in [payload].
 *
 * [snapshotId], [createdAt], and [sourceDeviceId] are *about* the capture,
 * not part of the captured content, so they are deliberately excluded from
 * [ContinuityContentHasher.hash] — see that class for why.
 */
@Serializable
data class ContinuitySnapshot(
    val formatVersion: Int,
    val snapshotId: String,
    val createdAt: Long,
    val appVersionCode: Int,
    val appVersionName: String,
    val sourceDeviceId: String,
    val payload: ContinuityPayload,
    val contentSha256: String,
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = ContinuityContract.SNAPSHOT_FORMAT_VERSION
    }
}

/**
 * The captured content itself. Every list here is a flat DTO mirror of an
 * existing domain/entity type (field-for-field), except [legacyBackupJson]
 * — the existing [org.mindanchor.backup.BackupRepository.export] output,
 * carried verbatim rather than re-modelled, since Task 7's job is to add
 * the stores that file did not already cover, not to duplicate the ones it
 * does.
 */
@Serializable
data class ContinuityPayload(
    val journalEntries: List<JournalEntryDto> = emptyList(),
    val contextRows: List<JournalContextDto> = emptyList(),
    val morningMeasures: List<MorningMeasureDto> = emptyList(),
    val notes: List<NoteDto> = emptyList(),
    val letters: List<LetterDto> = emptyList(),
    val readLetterDates: List<String> = emptyList(),
    val frictionedApps: List<String> = emptyList(),
    val alwaysOpenApps: List<String> = emptyList(),
    val continuityChanges: List<ContinuityChangeDto> = emptyList(),
    val legacyBackupJson: String = "",
    // Program 1, appended never inserted: the version-1 projection in
    // ContinuityContentHasher depends on Program 0's ten fields staying
    // the first ten, and a test asserts exactly that.
    val researchLedgerEvents: List<ResearchLedgerEventDto> = emptyList(),
    val studyPhases: List<StudyPhaseDto> = emptyList(),
)

@Serializable
data class JournalEntryDto(
    val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val localDate: String,
    val title: String,
    val body: String,
    val kind: String,
    val sourceDeviceId: String,
    val deletedAt: Long?,
)

@Serializable
data class JournalContextDto(
    val id: String,
    val entryId: String,
    val recordType: String,
    val key: String,
    val value: String,
    val sourceStart: Int?,
    val sourceEnd: Int?,
    val confidence: Double,
    val extractorVersion: String,
    val createdAt: Long,
)

@Serializable
data class MorningMeasureDto(
    val id: String,
    val localDate: String,
    val createdAt: Long,
    val updatedAt: Long,
    val mood: Int,
    val anxiety: Int,
    val angerUrge: Int,
    val energyFunction: Int,
    val sleepQuality: Int,
    val instrumentVersion: String,
    val sourceDeviceId: String,
)

/**
 * One immutable research ledger event, field-for-field from
 * [org.mindanchor.data.db.ResearchLedgerEventEntity].
 *
 * [id] is the event's own hash, so a restore that re-inserts an event the
 * database already holds is an `INSERT OR IGNORE` on the same primary key
 * — duplicate-free by construction rather than by a de-duplication pass.
 */
@Serializable
data class ResearchLedgerEventDto(
    val id: String,
    val sequence: Long,
    val kind: String,
    val occurredAt: Long,
    val recordedAt: Long,
    val localDate: String,
    val studyPhaseId: String,
    val sourceDeviceId: String,
    val note: String,
    val payloadJson: String,
    val previousEventHash: String,
    val eventHash: String,
)

/** One study phase, field-for-field from [org.mindanchor.data.db.StudyPhaseEntity]. */
@Serializable
data class StudyPhaseDto(
    val id: String,
    val ordinal: Int,
    val startedAt: Long,
    val reason: String,
    val appVersionCode: Int,
    val appVersionName: String,
    val protocolCatalogSha256: String,
    val ruleSetVersion: String,
    val modelSetVersion: String,
    val transformationSetVersion: String,
    val missingDataPolicyVersion: String,
    val instrumentVersion: String,
    val dictionaryVersion: String,
    val sourceDeviceId: String,
)

@Serializable
data class NoteDto(
    val id: Long,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean,
    val type: String?,
)

@Serializable
data class LetterDto(
    val date: String,
    val body: String,
    val provider: String?,
    val model: String?,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val durationMs: Long?,
)

/**
 * [acknowledgedSnapshotId] is carried for completeness (a later task's
 * restore may want it) but is excluded from the content hash — see
 * [ContinuityContentHasher].
 */
@Serializable
data class ContinuityChangeDto(
    val id: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val occurredAt: Long,
    val acknowledgedSnapshotId: String?,
)

// --- Mapping from the app's own domain/entity types --------------------

fun JournalEntryEntity.toDto(): JournalEntryDto = JournalEntryDto(
    id = id,
    createdAt = createdAt,
    updatedAt = updatedAt,
    localDate = localDate,
    title = title,
    body = body,
    kind = kind,
    sourceDeviceId = sourceDeviceId,
    deletedAt = deletedAt,
)

fun JournalContextEntity.toDto(): JournalContextDto = JournalContextDto(
    id = id,
    entryId = entryId,
    recordType = recordType,
    key = key,
    value = value,
    sourceStart = sourceStart,
    sourceEnd = sourceEnd,
    confidence = confidence,
    extractorVersion = extractorVersion,
    createdAt = createdAt,
)

fun MorningMeasureEntity.toDto(): MorningMeasureDto = MorningMeasureDto(
    id = id,
    localDate = localDate,
    createdAt = createdAt,
    updatedAt = updatedAt,
    mood = mood,
    anxiety = anxiety,
    angerUrge = angerUrge,
    energyFunction = energyFunction,
    sleepQuality = sleepQuality,
    instrumentVersion = instrumentVersion,
    sourceDeviceId = sourceDeviceId,
)

fun Note.toDto(): NoteDto = NoteDto(
    id = id,
    body = body,
    createdAt = createdAt,
    updatedAt = updatedAt,
    pinned = pinned,
    type = type?.name,
)

fun Letter.toDto(): LetterDto = LetterDto(
    date = date.toString(),
    body = body,
    provider = provider,
    model = model,
    promptTokens = promptTokens,
    completionTokens = completionTokens,
    durationMs = durationMs,
)

fun ResearchLedgerEventEntity.toDto(): ResearchLedgerEventDto = ResearchLedgerEventDto(
    id = id,
    sequence = sequence,
    kind = kind,
    occurredAt = occurredAt,
    recordedAt = recordedAt,
    localDate = localDate,
    studyPhaseId = studyPhaseId,
    sourceDeviceId = sourceDeviceId,
    note = note,
    payloadJson = payloadJson,
    previousEventHash = previousEventHash,
    eventHash = eventHash,
)

fun StudyPhaseEntity.toDto(): StudyPhaseDto = StudyPhaseDto(
    id = id,
    ordinal = ordinal,
    startedAt = startedAt,
    reason = reason,
    appVersionCode = appVersionCode,
    appVersionName = appVersionName,
    protocolCatalogSha256 = protocolCatalogSha256,
    ruleSetVersion = ruleSetVersion,
    modelSetVersion = modelSetVersion,
    transformationSetVersion = transformationSetVersion,
    missingDataPolicyVersion = missingDataPolicyVersion,
    instrumentVersion = instrumentVersion,
    dictionaryVersion = dictionaryVersion,
    sourceDeviceId = sourceDeviceId,
)

fun ContinuityChangeEntity.toDto(): ContinuityChangeDto = ContinuityChangeDto(
    id = id,
    entityType = entityType,
    entityId = entityId,
    operation = operation,
    occurredAt = occurredAt,
    acknowledgedSnapshotId = acknowledgedSnapshotId,
)
