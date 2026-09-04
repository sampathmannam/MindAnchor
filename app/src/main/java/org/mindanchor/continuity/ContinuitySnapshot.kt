package org.mindanchor.continuity

import kotlinx.serialization.Serializable
import org.mindanchor.data.db.AdvisoryOpportunityEntity
import org.mindanchor.data.db.ContinuityChangeEntity
import org.mindanchor.data.db.InterventionEpisodeEventEntity
import org.mindanchor.data.db.JournalContextEntity
import org.mindanchor.data.db.JournalEntryEntity
import org.mindanchor.data.db.MorningMeasureEntity
import org.mindanchor.data.db.PassiveBaselineSegmentEntity
import org.mindanchor.data.db.PassiveDailyRevisionEntity
import org.mindanchor.data.db.PassiveObservationDecisionEntity
import org.mindanchor.data.db.PassivePipelineRunEntity
import org.mindanchor.data.db.PassiveRawProvenanceEntity
import org.mindanchor.data.db.PassiveSourceLagEntity
import org.mindanchor.data.db.PassiveSourceReadEntity
import org.mindanchor.data.db.PassiveWindowRevisionEntity
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
    // Program 2, appended never inserted: v1 and v2 projections freeze
    // the payload shapes already written by earlier builds.
    val passiveRawProvenance: List<PassiveRawProvenanceDto> = emptyList(),
    val passiveSourceReads: List<PassiveSourceReadDto> = emptyList(),
    val passiveSourceLags: List<PassiveSourceLagDto> = emptyList(),
    val passiveBaselineSegments: List<PassiveBaselineSegmentDto> = emptyList(),
    val passivePipelineRuns: List<PassivePipelineRunDto> = emptyList(),
    val passiveWindowRevisions: List<PassiveWindowRevisionDto> = emptyList(),
    val passiveDailyRevisions: List<PassiveDailyRevisionDto> = emptyList(),
    val passiveObservationDecisions: List<PassiveObservationDecisionDto> = emptyList(),
    // Program 3, appended never inserted: v1, v2, and v3 projections
    // freeze the payload shapes already written by earlier builds.
    val advisoryOpportunities: List<AdvisoryOpportunityDto> = emptyList(),
    val interventionEpisodeEvents: List<InterventionEpisodeEventDto> = emptyList(),
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
data class PassiveRawProvenanceDto(
    val id: String,
    val sourceFamily: String,
    val recordKind: String,
    val eventStart: Long,
    val eventEnd: Long,
    val unit: String,
    val dataOriginPackage: String,
    val deviceManufacturer: String?,
    val deviceModel: String?,
    val deviceType: String?,
    val sourceUpdatedTime: Long?,
    val ingestedAt: Long,
    val zoneId: String,
    val zoneOffsetSeconds: Int,
    val recordId: String,
    val recordVersion: Long,
)

@Serializable
data class PassiveSourceReadDto(
    val id: String,
    val runId: String,
    val sourceFamily: String,
    val state: String,
    val rangeStart: Long,
    val rangeEnd: Long,
    val zoneId: String,
    val attemptedAt: Long,
    val recordCount: Int,
    val errorCode: String?,
)

@Serializable
data class PassiveSourceLagDto(
    val id: String,
    val sourceFamily: String,
    val eventEnd: Long,
    val observedUpdatedAt: Long,
    val ingestedAt: Long,
    val lagMillis: Long,
    val usedIngestedAtFallback: Boolean,
    val observedAt: Long,
)

@Serializable
data class PassiveBaselineSegmentDto(
    val id: String,
    val openedAt: Long,
    val fingerprintsJson: String,
    val windowTransformationVersion: String,
    val dailyTransformationVersion: String,
)

@Serializable
data class PassivePipelineRunDto(
    val id: String,
    val startedAt: Long,
    val completedAt: Long,
    val scanStart: Long,
    val scanEnd: Long,
    val zoneId: String,
    val historyPermissionGranted: Boolean,
    val firstSuccessfulPermissionedRun: Boolean,
    val result: String,
    val sourceStatesJson: String,
)

@Serializable
data class PassiveWindowRevisionDto(
    val id: String,
    val windowStart: Long,
    val windowEnd: Long,
    val asOfTime: Long,
    val zoneId: String,
    val zoneOffsetSeconds: Int,
    val wakeRelativeMinute: Int?,
    val baselineSegment: String,
    val featureRowsJson: String,
    val heartRateCoverage: Double,
    val physiologyEligible: Boolean,
    val exerciseOverlapMillis: Long,
    val provenanceRecordIdsJson: String,
    val missingnessJson: String,
    val exclusionsJson: String,
    val transformationVersion: String,
    val sourceUpdatedTime: Long,
    val ingestedAt: Long,
    val final: Boolean,
    val revisionReason: String,
    val contentHash: String,
)

@Serializable
data class PassiveDailyRevisionDto(
    val id: String,
    val localDate: String,
    val asOfTime: Long,
    val dataStatus: String,
    val featuresJson: String,
    val excludedFeaturesJson: String,
    val baselineSegment: String,
    val sourceUpdatedTime: Long,
    val ingestedAt: Long,
    val sourceReadStatesJson: String,
    val coverageJson: String,
    val missingnessJson: String,
    val exclusionsJson: String,
    val provenanceJson: String,
    val windowTransformationVersion: String,
    val dailyTransformationVersion: String,
    val watermark: Long,
    val revisionReason: String,
    val contentHash: String,
)

@Serializable
data class PassiveObservationDecisionDto(
    val id: String,
    val localDate: String,
    val asOfTime: Long,
    val dataStatus: String,
    val observationState: String,
    val baselineSegment: String,
    val calibrationSeed: Long?,
    val frozenBaselineAsOfTime: Long?,
    val frozenBaselineThroughDay: String?,
    val decisionJson: String,
    val revisionReason: String,
    val contentHash: String,
)

@Serializable
data class AdvisoryOpportunityDto(
    val id: String,
    val presentedAt: Long,
    val localDate: String,
    val zoneId: String,
    val sourceDecisionId: String,
    val sourceDecisionContentHash: String,
    val sourceLocalDate: String,
    val sourceAsOfTime: Long,
    val sourceDataStatus: String,
    val sourceObservationState: String,
    val sourceExplanation: String,
    val sourceBaselineSegment: String,
    val sourcePassiveRuleVersion: String,
    val sourcePassiveModelVersion: String,
    val sourceStudyPhaseId: String,
    val protocolId: String,
    val protocolVersion: Int,
    val protocolDefinitionSha256: String,
    val protocolCatalogSha256: String,
    val protocolClinicalReviewStatus: String,
    val advisoryRuleVersion: String,
    val buildMode: String,
    val operationalEvidenceApproved: Boolean,
    val masterAdvisoryEnabled: Boolean,
    val deliveryAllowedAtPresentation: Boolean,
    val studyPhaseId: String,
    val sourceDeviceId: String,
    val contentHash: String,
)

@Serializable
data class InterventionEpisodeEventDto(
    val id: String,
    val episodeId: String,
    val opportunityId: String,
    val sequence: Long,
    val eventType: String,
    val occurredAt: Long,
    val localDate: String,
    val zoneId: String,
    val studyPhaseId: String,
    val sourceDeviceId: String,
    val protocolId: String,
    val protocolVersion: Int,
    val protocolDefinitionSha256: String,
    val protocolCatalogSha256: String,
    val advisoryRuleVersion: String,
    val buildMode: String,
    val operationalEvidenceApproved: Boolean,
    val masterAdvisoryEnabled: Boolean,
    val deliveryAllowed: Boolean,
    val payloadSchemaVersion: Int,
    val payloadJson: String,
    val previousEventHash: String,
    val eventHash: String,
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

fun PassiveRawProvenanceEntity.toDto(): PassiveRawProvenanceDto = PassiveRawProvenanceDto(
    id = id,
    sourceFamily = sourceFamily,
    recordKind = recordKind,
    eventStart = eventStart,
    eventEnd = eventEnd,
    unit = unit,
    dataOriginPackage = dataOriginPackage,
    deviceManufacturer = deviceManufacturer,
    deviceModel = deviceModel,
    deviceType = deviceType,
    sourceUpdatedTime = sourceUpdatedTime,
    ingestedAt = ingestedAt,
    zoneId = zoneId,
    zoneOffsetSeconds = zoneOffsetSeconds,
    recordId = recordId,
    recordVersion = recordVersion,
)

fun PassiveSourceReadEntity.toDto(): PassiveSourceReadDto = PassiveSourceReadDto(
    id = id,
    runId = runId,
    sourceFamily = sourceFamily,
    state = state,
    rangeStart = rangeStart,
    rangeEnd = rangeEnd,
    zoneId = zoneId,
    attemptedAt = attemptedAt,
    recordCount = recordCount,
    errorCode = errorCode,
)

fun PassiveSourceLagEntity.toDto(): PassiveSourceLagDto = PassiveSourceLagDto(
    id = id,
    sourceFamily = sourceFamily,
    eventEnd = eventEnd,
    observedUpdatedAt = observedUpdatedAt,
    ingestedAt = ingestedAt,
    lagMillis = lagMillis,
    usedIngestedAtFallback = usedIngestedAtFallback,
    observedAt = observedAt,
)

fun PassiveBaselineSegmentEntity.toDto(): PassiveBaselineSegmentDto = PassiveBaselineSegmentDto(
    id = id,
    openedAt = openedAt,
    fingerprintsJson = fingerprintsJson,
    windowTransformationVersion = windowTransformationVersion,
    dailyTransformationVersion = dailyTransformationVersion,
)

fun PassivePipelineRunEntity.toDto(): PassivePipelineRunDto = PassivePipelineRunDto(
    id = id,
    startedAt = startedAt,
    completedAt = completedAt,
    scanStart = scanStart,
    scanEnd = scanEnd,
    zoneId = zoneId,
    historyPermissionGranted = historyPermissionGranted,
    firstSuccessfulPermissionedRun = firstSuccessfulPermissionedRun,
    result = result,
    sourceStatesJson = sourceStatesJson,
)

fun PassiveWindowRevisionEntity.toDto(): PassiveWindowRevisionDto = PassiveWindowRevisionDto(
    id = id,
    windowStart = windowStart,
    windowEnd = windowEnd,
    asOfTime = asOfTime,
    zoneId = zoneId,
    zoneOffsetSeconds = zoneOffsetSeconds,
    wakeRelativeMinute = wakeRelativeMinute,
    baselineSegment = baselineSegment,
    featureRowsJson = featureRowsJson,
    heartRateCoverage = heartRateCoverage,
    physiologyEligible = physiologyEligible,
    exerciseOverlapMillis = exerciseOverlapMillis,
    provenanceRecordIdsJson = provenanceRecordIdsJson,
    missingnessJson = missingnessJson,
    exclusionsJson = exclusionsJson,
    transformationVersion = transformationVersion,
    sourceUpdatedTime = sourceUpdatedTime,
    ingestedAt = ingestedAt,
    final = final,
    revisionReason = revisionReason,
    contentHash = contentHash,
)

fun PassiveDailyRevisionEntity.toDto(): PassiveDailyRevisionDto = PassiveDailyRevisionDto(
    id = id,
    localDate = localDate,
    asOfTime = asOfTime,
    dataStatus = dataStatus,
    featuresJson = featuresJson,
    excludedFeaturesJson = excludedFeaturesJson,
    baselineSegment = baselineSegment,
    sourceUpdatedTime = sourceUpdatedTime,
    ingestedAt = ingestedAt,
    sourceReadStatesJson = sourceReadStatesJson,
    coverageJson = coverageJson,
    missingnessJson = missingnessJson,
    exclusionsJson = exclusionsJson,
    provenanceJson = provenanceJson,
    windowTransformationVersion = windowTransformationVersion,
    dailyTransformationVersion = dailyTransformationVersion,
    watermark = watermark,
    revisionReason = revisionReason,
    contentHash = contentHash,
)

fun PassiveObservationDecisionEntity.toDto(): PassiveObservationDecisionDto = PassiveObservationDecisionDto(
    id = id,
    localDate = localDate,
    asOfTime = asOfTime,
    dataStatus = dataStatus,
    observationState = observationState,
    baselineSegment = baselineSegment,
    calibrationSeed = calibrationSeed,
    frozenBaselineAsOfTime = frozenBaselineAsOfTime,
    frozenBaselineThroughDay = frozenBaselineThroughDay,
    decisionJson = decisionJson,
    revisionReason = revisionReason,
    contentHash = contentHash,
)

fun AdvisoryOpportunityEntity.toDto(): AdvisoryOpportunityDto = AdvisoryOpportunityDto(
    id = id,
    presentedAt = presentedAt,
    localDate = localDate,
    zoneId = zoneId,
    sourceDecisionId = sourceDecisionId,
    sourceDecisionContentHash = sourceDecisionContentHash,
    sourceLocalDate = sourceLocalDate,
    sourceAsOfTime = sourceAsOfTime,
    sourceDataStatus = sourceDataStatus,
    sourceObservationState = sourceObservationState,
    sourceExplanation = sourceExplanation,
    sourceBaselineSegment = sourceBaselineSegment,
    sourcePassiveRuleVersion = sourcePassiveRuleVersion,
    sourcePassiveModelVersion = sourcePassiveModelVersion,
    sourceStudyPhaseId = sourceStudyPhaseId,
    protocolId = protocolId,
    protocolVersion = protocolVersion,
    protocolDefinitionSha256 = protocolDefinitionSha256,
    protocolCatalogSha256 = protocolCatalogSha256,
    protocolClinicalReviewStatus = protocolClinicalReviewStatus,
    advisoryRuleVersion = advisoryRuleVersion,
    buildMode = buildMode,
    operationalEvidenceApproved = operationalEvidenceApproved,
    masterAdvisoryEnabled = masterAdvisoryEnabled,
    deliveryAllowedAtPresentation = deliveryAllowedAtPresentation,
    studyPhaseId = studyPhaseId,
    sourceDeviceId = sourceDeviceId,
    contentHash = contentHash,
)

fun InterventionEpisodeEventEntity.toDto(): InterventionEpisodeEventDto = InterventionEpisodeEventDto(
    id = id,
    episodeId = episodeId,
    opportunityId = opportunityId,
    sequence = sequence,
    eventType = eventType,
    occurredAt = occurredAt,
    localDate = localDate,
    zoneId = zoneId,
    studyPhaseId = studyPhaseId,
    sourceDeviceId = sourceDeviceId,
    protocolId = protocolId,
    protocolVersion = protocolVersion,
    protocolDefinitionSha256 = protocolDefinitionSha256,
    protocolCatalogSha256 = protocolCatalogSha256,
    advisoryRuleVersion = advisoryRuleVersion,
    buildMode = buildMode,
    operationalEvidenceApproved = operationalEvidenceApproved,
    masterAdvisoryEnabled = masterAdvisoryEnabled,
    deliveryAllowed = deliveryAllowed,
    payloadSchemaVersion = payloadSchemaVersion,
    payloadJson = payloadJson,
    previousEventHash = previousEventHash,
    eventHash = eventHash,
)

fun ContinuityChangeEntity.toDto(): ContinuityChangeDto = ContinuityChangeDto(
    id = id,
    entityType = entityType,
    entityId = entityId,
    operation = operation,
    occurredAt = occurredAt,
    acknowledgedSnapshotId = acknowledgedSnapshotId,
)
