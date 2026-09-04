package org.mindanchor.advisory

import android.content.Context
import androidx.room.withTransaction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.mindanchor.continuity.ContinuityWorkScheduler
import org.mindanchor.data.db.AdvisoryOpportunityEntity
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.data.db.ContinuityChangeEntity
import org.mindanchor.data.db.InterventionEpisodeEventEntity
import org.mindanchor.data.db.PassiveObservationDecisionEntity
import org.mindanchor.data.db.StudyPhaseEntity
import org.mindanchor.intelligence.PassiveDataStatus
import org.mindanchor.intelligence.PassiveObservation
import org.mindanchor.intelligence.PassiveObservationState
import org.mindanchor.intelligence.PassivePipelineCodec
import org.mindanchor.journal.ChangeOperation
import org.mindanchor.research.EvidenceProtocol
import org.mindanchor.research.EvidenceProtocolCatalog
import org.mindanchor.research.EvidenceProtocolRegistry
import org.mindanchor.research.ProvenanceVector
import org.mindanchor.research.ResearchLedgerRepository
import org.mindanchor.research.RuleSetVersionVector
import org.mindanchor.research.StudyPhase
import org.mindanchor.research.StudyPhaseDecision
import org.mindanchor.research.StudyPhaseReason

/**
 * Program 3 Tasks 3-4 — read the visible advisory state, materialize
 * today's opportunity, and record a person's own deliberate choice to
 * dismiss or start it, and how a started episode ended.
 *
 * The only valid [stop] kinds are [EpisodeEventType.STOPPED_BY_USER],
 * [EpisodeEventType.STOPPED_DISCOMFORT_REPORTED],
 * [EpisodeEventType.INTERRUPTED_APP_BACKGROUND],
 * [EpisodeEventType.INTERRUPTED_PROCESS_RECOVERY], and
 * [EpisodeEventType.STOPPED_KILL_SWITCH]. [completeMaximumDuration]
 * alone may append [EpisodeEventType.COMPLETED_MAX_DURATION] and
 * [EpisodeEventType.OUTCOME_WINDOW_OPENED].
 */
interface AdvisoryRepository {
    fun observe(): Flow<AdvisoryReadModel>
    suspend fun refreshOpportunity(now: Long, zoneId: ZoneId): AdvisoryRefreshResult
    suspend fun dismiss(opportunityId: String, now: Long, zoneId: ZoneId): AdvisoryMutationResult
    suspend fun start(opportunityId: String, now: Long, zoneId: ZoneId): AdvisoryStartResult
    suspend fun stop(
        episodeId: String,
        kind: EpisodeEventType,
        now: Long,
        deliveredForegroundMillis: Long,
    ): AdvisoryMutationResult
    suspend fun completeMaximumDuration(
        episodeId: String,
        now: Long,
        deliveredForegroundMillis: Long,
        completedCycles: Int,
    ): AdvisoryMutationResult
}

/**
 * Program 3 Task 4 — which event may legally follow which, checked
 * against the exact ordered types already on the chain.
 *
 * This is the in-memory mirror of what the append-only tables and their
 * content hashes already make true at the storage level: there is no
 * transition this validates that a corrupted chain could pass and no
 * transition it forbids that the chain would otherwise accept. It exists
 * so a caller gets a typed refusal before ever building a row, not an
 * `INSERT OR IGNORE` no-op indistinguishable from a duplicate.
 */
object EpisodeTransitions {
    val terminal = setOf(
        EpisodeEventType.COMPLETED_MAX_DURATION,
        EpisodeEventType.STOPPED_BY_USER,
        EpisodeEventType.STOPPED_DISCOMFORT_REPORTED,
        EpisodeEventType.INTERRUPTED_APP_BACKGROUND,
        EpisodeEventType.INTERRUPTED_PROCESS_RECOVERY,
        EpisodeEventType.STOPPED_KILL_SWITCH,
    )

    // Kotlin's exhaustiveness checker cannot verify an `in terminal` branch
    // against a runtime Set, so the six terminal members are enumerated
    // directly here; they share the identical body the plan's `in terminal`
    // shorthand meant.
    fun mayAppend(existing: List<EpisodeEventType>, next: EpisodeEventType): Boolean = when (next) {
        EpisodeEventType.DISMISSED -> existing.isEmpty()
        EpisodeEventType.ELIGIBILITY_ATTESTED -> existing.isEmpty()
        EpisodeEventType.STARTED -> existing == listOf(EpisodeEventType.ELIGIBILITY_ATTESTED)
        EpisodeEventType.COMPLETED_MAX_DURATION,
        EpisodeEventType.STOPPED_BY_USER,
        EpisodeEventType.STOPPED_DISCOMFORT_REPORTED,
        EpisodeEventType.INTERRUPTED_APP_BACKGROUND,
        EpisodeEventType.INTERRUPTED_PROCESS_RECOVERY,
        EpisodeEventType.STOPPED_KILL_SWITCH,
        -> existing.lastOrNull() == EpisodeEventType.STARTED && existing.none { it in terminal }
        EpisodeEventType.OUTCOME_WINDOW_OPENED -> existing.lastOrNull() == EpisodeEventType.COMPLETED_MAX_DURATION
        EpisodeEventType.OUTCOME_WINDOW_CLOSED_MISSING ->
            existing.contains(EpisodeEventType.OUTCOME_WINDOW_OPENED) &&
                existing.none { it == EpisodeEventType.OUTCOME_WINDOW_CLOSED_MISSING }
    }
}

/**
 * The only [AdvisoryRepository] implementation: Room plus the frozen
 * Program 1/2 registries, behind two injected suspend callbacks so a
 * test can supply a fake settings store and a fake build authorization
 * without touching DataStore or `BuildConfig`.
 */
class RoomAdvisoryRepository(
    private val context: Context,
    private val database: AnchorDatabase,
    private val researchLedger: ResearchLedgerRepository,
    private val settingsProvider: suspend () -> AdvisorySettings,
    private val buildAuthorization: () -> AdvisoryBuildAuthorization,
    private val setCurrentEpisodeId: suspend (String?) -> Unit,
) : AdvisoryRepository {

    override fun observe(): Flow<AdvisoryReadModel> = combine(
        database.advisory().observeOpportunities(),
        database.advisory().observeEvents(),
    ) { opportunities, events -> deriveReadModel(opportunities, events) }

    /**
     * At most one unhandled opportunity is ever visible, newest first.
     * An opportunity is handled the moment any [EpisodeEventType.DISMISSED]
     * or [EpisodeEventType.STARTED] event names it — neither row is ever
     * deleted or updated; this only excludes what it derives.
     *
     * Whether the visible opportunity's own protocol may still be started
     * is re-checked against the *live* registry and the *current*
     * settings, never against what presentation-time recorded: a registry
     * edit since presentation, a closed master switch, an active episode
     * elsewhere, or an unexpired cooldown must all be reflected the
     * moment they change, not frozen at materialization time.
     */
    private suspend fun deriveReadModel(
        opportunities: List<AdvisoryOpportunityEntity>,
        events: List<InterventionEpisodeEventEntity>,
    ): AdvisoryReadModel {
        val handledIds = events
            .filter { it.eventType == EpisodeEventType.DISMISSED.name || it.eventType == EpisodeEventType.STARTED.name }
            .map { it.opportunityId }
            .toSet()
        val visible = opportunities
            .filterNot { it.id in handledIds }
            .maxByOrNull { it.presentedAt }
            ?: return AdvisoryReadModel.Hidden
        val protocol = EvidenceProtocolCatalog.registry.find(visible.protocolId, visible.protocolVersion)
            ?: return AdvisoryReadModel.Hidden
        val liveDefinitionSha256 = EvidenceProtocolRegistry.definitionSha256(protocol)
        if (liveDefinitionSha256 != visible.protocolDefinitionSha256) return AdvisoryReadModel.Hidden

        val settings = settingsProvider()
        val authorization = buildAuthorization()
        val lastStartedAt = events
            .filter {
                it.eventType == EpisodeEventType.STARTED.name &&
                    it.protocolId == visible.protocolId &&
                    it.protocolVersion == visible.protocolVersion
            }
            .maxOfOrNull { it.occurredAt }
        val startInput = AdvisoryPolicyInput(
            authorization = authorization,
            masterAdvisoryEnabled = settings.masterAdvisoryEnabled,
            deliveryAllowed = settings.deliveryAllowed,
            source = visible.toAdvisorySource(),
            sourceDecodeSucceeded = true,
            sourceProvenanceComplete = true,
            sourceProducedAfterStudyStart = true,
            protocol = protocol,
            protocolDefinitionSha256 = liveDefinitionSha256,
            protocolCatalogSha256 = EvidenceProtocolCatalog.registry.catalogSha256,
            opportunityAlreadyRecorded = false,
            opportunityAlreadyHandled = visible.id in handledIds,
            activeEpisodeExists = settings.currentEpisodeId != null,
            lastStartedAt = lastStartedAt,
            now = System.currentTimeMillis(),
        )
        val startResult = AdvisoryPolicy.evaluate(startInput, AdvisoryAction.START)
        return AdvisoryReadModel.Opportunity(
            row = visible,
            protocol = protocol,
            startAvailable = startResult is AdvisoryPolicyResult.Eligible,
            startBlockedReason = (startResult as? AdvisoryPolicyResult.Ineligible)?.reason,
        )
    }

    private fun AdvisoryOpportunityEntity.toAdvisorySource() = AdvisorySource(
        decisionId = sourceDecisionId,
        decisionContentHash = sourceDecisionContentHash,
        localDate = sourceLocalDate,
        asOfTime = sourceAsOfTime,
        dataStatus = PassiveDataStatus.valueOf(sourceDataStatus),
        observationState = PassiveObservationState.valueOf(sourceObservationState),
        explanation = sourceExplanation,
        baselineSegment = sourceBaselineSegment,
        passiveRuleVersion = sourcePassiveRuleVersion,
        passiveModelVersion = sourcePassiveModelVersion,
        sourceStudyPhaseId = sourceStudyPhaseId,
        sourceDeviceId = sourceDeviceId,
    )

    @Suppress("ReturnCount", "LongMethod")
    override suspend fun refreshOpportunity(now: Long, zoneId: ZoneId): AdvisoryRefreshResult {
        val settings = settingsProvider()
        val authorization = buildAuthorization()
        val inserted = database.withTransaction {
            val existingRows = database.advisory().opportunitiesNow()
            val latest = database.passive().latestObservationDecisionNow()
                ?: return@withTransaction AdvisoryRefreshResult.Ineligible(AdvisoryIneligibleReason.SOURCE_MISSING)
            val decoded = runCatching { PassivePipelineCodec.decisionToDomain(latest) }
            val phases = database.research().studyPhasesNow().map { it.toAdvisoryStudyPhase() }
            val sourcePhase = StudyPhaseDecision.phaseAt(phases, latest.asOfTime)
            val currentPhase = researchLedger.provenance.ensureCurrentPhase(now)
            val protocol = EvidenceProtocolCatalog.registry.find("cyclic-sighing", 1)
            val key = protocol?.let {
                ProtocolKey(it.id, it.version, EvidenceProtocolRegistry.definitionSha256(it))
            }
            val opportunityId = if (key == null) {
                ""
            } else {
                AdvisoryCodec.opportunityId(latest.id, latest.contentHash, key, AdvisoryPolicy.RULE_VERSION)
            }
            val input = buildPolicyInput(
                latest = latest,
                decoded = decoded,
                sourcePhase = sourcePhase,
                currentPhase = currentPhase,
                protocol = protocol,
                key = key,
                settings = settings,
                authorization = authorization,
                existingRows = existingRows,
                opportunityId = opportunityId,
                zoneId = zoneId,
                now = now,
            )
            val result = AdvisoryPolicy.evaluate(input, AdvisoryAction.PRESENT)
            if (result is AdvisoryPolicyResult.Ineligible) {
                return@withTransaction AdvisoryRefreshResult.Ineligible(result.reason)
            }
            check(result is AdvisoryPolicyResult.Eligible)

            val row = result.toOpportunityEntity(
                id = opportunityId,
                presentedAt = now,
                zoneId = zoneId.id,
                currentPhase = currentPhase,
                settings = settings,
                authorization = authorization,
                catalogSha256 = EvidenceProtocolCatalog.registry.catalogSha256,
            )
            if (database.advisory().insertOpportunity(row) == IGNORED_ROW_ID) {
                return@withTransaction AdvisoryRefreshResult.AlreadyRecorded(opportunityId)
            }
            database.journal().insertChange(
                ContinuityChangeEntity(
                    id = UUID.randomUUID().toString(),
                    entityType = "ADVISORY_OPPORTUNITY",
                    entityId = row.id,
                    operation = ChangeOperation.CREATE.name,
                    occurredAt = now,
                    acknowledgedSnapshotId = null,
                ),
            )
            AdvisoryRefreshResult.Created(row.id)
        }
        if (inserted is AdvisoryRefreshResult.Created) {
            researchLedger.provenance.refreshAfterCommit()
            ContinuityWorkScheduler.requestCheckpoint(context)
        }
        return inserted
    }

    override suspend fun dismiss(opportunityId: String, now: Long, zoneId: ZoneId): AdvisoryMutationResult {
        val inserted = database.withTransaction {
            val opportunity = database.advisory().opportunity(opportunityId)
                ?: return@withTransaction AdvisoryMutationResult.Ignored(AdvisoryIneligibleReason.OPPORTUNITY_NOT_FOUND)
            val existing = database.advisory().eventsForOpportunity(opportunityId)
            if (existing.isNotEmpty()) {
                return@withTransaction AdvisoryMutationResult.Ignored(
                    AdvisoryIneligibleReason.OPPORTUNITY_ALREADY_HANDLED,
                )
            }
            val currentPhase = researchLedger.provenance.ensureCurrentPhase(now)
            val event = AdvisoryCodec.seal(
                InterventionEpisodeEventEntity(
                    id = "",
                    episodeId = AdvisoryCodec.dismissalStreamId(opportunityId),
                    opportunityId = opportunityId,
                    sequence = 1L,
                    eventType = EpisodeEventType.DISMISSED.name,
                    occurredAt = now,
                    localDate = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate().toString(),
                    zoneId = zoneId.id,
                    studyPhaseId = currentPhase.id,
                    sourceDeviceId = currentPhase.vector.sourceDeviceId,
                    protocolId = opportunity.protocolId,
                    protocolVersion = opportunity.protocolVersion,
                    protocolDefinitionSha256 = opportunity.protocolDefinitionSha256,
                    protocolCatalogSha256 = opportunity.protocolCatalogSha256,
                    advisoryRuleVersion = opportunity.advisoryRuleVersion,
                    buildMode = opportunity.buildMode,
                    operationalEvidenceApproved = opportunity.operationalEvidenceApproved,
                    masterAdvisoryEnabled = opportunity.masterAdvisoryEnabled,
                    deliveryAllowed = opportunity.deliveryAllowedAtPresentation,
                    payloadSchemaVersion = AdvisoryCodec.EVENT_PAYLOAD_SCHEMA_VERSION,
                    payloadJson = AdvisoryCodec.EMPTY_PAYLOAD,
                    previousEventHash = "",
                    eventHash = "",
                ),
            )
            if (database.advisory().insertEvents(listOf(event)).firstOrNull() == IGNORED_ROW_ID) {
                return@withTransaction AdvisoryMutationResult.Ignored(
                    AdvisoryIneligibleReason.OPPORTUNITY_ALREADY_HANDLED,
                )
            }
            insertEpisodeEventChange(event, now)
            AdvisoryMutationResult.Appended(listOf(event.id))
        }
        if (inserted is AdvisoryMutationResult.Appended) {
            researchLedger.provenance.refreshAfterCommit()
            ContinuityWorkScheduler.requestCheckpoint(context)
        }
        return inserted
    }

    @Suppress("LongMethod", "ReturnCount")
    override suspend fun start(opportunityId: String, now: Long, zoneId: ZoneId): AdvisoryStartResult {
        val settings = settingsProvider()
        val authorization = buildAuthorization()
        val started = database.withTransaction {
            val opportunity = database.advisory().opportunity(opportunityId)
                ?: return@withTransaction AdvisoryStartResult.NotStarted(AdvisoryIneligibleReason.OPPORTUNITY_NOT_FOUND)
            val existingForOpportunity = database.advisory().eventsForOpportunity(opportunityId)
            val handled = existingForOpportunity.any {
                it.eventType == EpisodeEventType.DISMISSED.name || it.eventType == EpisodeEventType.STARTED.name
            }
            if (handled) {
                return@withTransaction AdvisoryStartResult.NotStarted(
                    AdvisoryIneligibleReason.OPPORTUNITY_ALREADY_HANDLED,
                )
            }

            // The opportunity's own source snapshot is the record of what
            // was shown; starting it re-verifies that snapshot is still
            // the standing Program 2 truth rather than trusting a frozen
            // copy. A superseded source is treated the same as no
            // current source — there is no dedicated reason for "this
            // reading has since been corrected," and the effect on
            // eligibility is identical either way.
            val latestDecision = database.passive().latestObservationDecisionNow()
            val sourceStillCurrent = latestDecision != null &&
                latestDecision.id == opportunity.sourceDecisionId &&
                latestDecision.contentHash == opportunity.sourceDecisionContentHash
            if (!sourceStillCurrent) {
                return@withTransaction AdvisoryStartResult.NotStarted(AdvisoryIneligibleReason.SOURCE_MISSING)
            }

            val protocol = EvidenceProtocolCatalog.registry.find(opportunity.protocolId, opportunity.protocolVersion)
                ?: return@withTransaction AdvisoryStartResult.NotStarted(AdvisoryIneligibleReason.PROTOCOL_MISSING)
            val currentPhase = researchLedger.provenance.ensureCurrentPhase(now)
            val allEvents = database.advisory().eventsNow()
            val lastStartedAt = allEvents
                .filter {
                    it.eventType == EpisodeEventType.STARTED.name &&
                        it.protocolId == opportunity.protocolId &&
                        it.protocolVersion == opportunity.protocolVersion
                }
                .maxOfOrNull { it.occurredAt }

            val input = AdvisoryPolicyInput(
                authorization = authorization,
                masterAdvisoryEnabled = settings.masterAdvisoryEnabled,
                deliveryAllowed = settings.deliveryAllowed,
                source = opportunity.toAdvisorySource(),
                sourceDecodeSucceeded = true,
                sourceProvenanceComplete = true,
                sourceProducedAfterStudyStart = true,
                protocol = protocol,
                // The opportunity's own frozen hash, verified against
                // what the live registry computes for this protocol
                // right now: an edited definition since presentation
                // fails this the same way a first-time hash mismatch
                // would at presentation.
                protocolDefinitionSha256 = opportunity.protocolDefinitionSha256,
                protocolCatalogSha256 = opportunity.protocolCatalogSha256,
                opportunityAlreadyRecorded = false,
                opportunityAlreadyHandled = false,
                activeEpisodeExists = settings.currentEpisodeId != null,
                lastStartedAt = lastStartedAt,
                now = now,
            )
            val result = AdvisoryPolicy.evaluate(input, AdvisoryAction.START)
            if (result is AdvisoryPolicyResult.Ineligible) {
                return@withTransaction AdvisoryStartResult.NotStarted(result.reason)
            }
            check(result is AdvisoryPolicyResult.Eligible)

            val episodeId = AdvisoryCodec.episodeId(opportunityId, now, currentPhase.vector.sourceDeviceId)
            val attested = AdvisoryCodec.seal(
                episodeEvent(
                    episodeId = episodeId,
                    opportunityId = opportunityId,
                    sequence = 1L,
                    type = EpisodeEventType.ELIGIBILITY_ATTESTED,
                    now = now,
                    zoneId = zoneId,
                    currentPhase = currentPhase,
                    opportunity = opportunity,
                    payloadJson = AdvisoryCodec.json.encodeToString(
                        ManualStartAttestation.fromSingleManualStartAction().toPayload(),
                    ),
                    previousEventHash = "",
                ),
            )
            val startedEvent = AdvisoryCodec.seal(
                episodeEvent(
                    episodeId = episodeId,
                    opportunityId = opportunityId,
                    sequence = 2L,
                    type = EpisodeEventType.STARTED,
                    now = now,
                    zoneId = zoneId,
                    currentPhase = currentPhase,
                    opportunity = opportunity,
                    payloadJson = AdvisoryCodec.EMPTY_PAYLOAD,
                    previousEventHash = attested.eventHash,
                ),
            )
            val insertedIds = database.advisory().insertEvents(listOf(attested, startedEvent))
            if (insertedIds.any { it == IGNORED_ROW_ID }) {
                return@withTransaction AdvisoryStartResult.IntegrityFailure(opportunityId)
            }
            insertEpisodeEventChange(attested, now)
            insertEpisodeEventChange(startedEvent, now)
            AdvisoryStartResult.Started(episodeId)
        }
        if (started is AdvisoryStartResult.Started) {
            setCurrentEpisodeId(started.episodeId)
            AdvisoryProcessSessionRegistry.register(started.episodeId)
            researchLedger.provenance.refreshAfterCommit()
            ContinuityWorkScheduler.requestCheckpoint(context)
        }
        return started
    }

    override suspend fun stop(
        episodeId: String,
        kind: EpisodeEventType,
        now: Long,
        deliveredForegroundMillis: Long,
    ): AdvisoryMutationResult {
        require(kind in EpisodeTransitions.terminal && kind != EpisodeEventType.COMPLETED_MAX_DURATION) {
            "stop() does not accept $kind; use completeMaximumDuration()"
        }
        val appended = database.withTransaction {
            val chain = database.advisory().eventsForEpisode(episodeId)
            if (chain.isEmpty()) {
                return@withTransaction AdvisoryMutationResult.Ignored(AdvisoryIneligibleReason.OPPORTUNITY_NOT_FOUND)
            }
            val types = chain.sortedBy { it.sequence }.map { EpisodeEventType.valueOf(it.eventType) }
            if (!EpisodeTransitions.mayAppend(types, kind)) {
                return@withTransaction AdvisoryMutationResult.Ignored(
                    AdvisoryIneligibleReason.OPPORTUNITY_ALREADY_HANDLED,
                )
            }
            val last = chain.maxByOrNull { it.sequence }!!
            val protocol = EvidenceProtocolCatalog.registry.find(last.protocolId, last.protocolVersion)
            val cappedMillis = deliveredForegroundMillis.coerceIn(
                0L,
                (protocol?.maxDurationSeconds ?: 0) * MILLIS_PER_SECOND,
            )
            val completedCycles = (cappedMillis / CYCLE_MILLIS).toInt()
            val terminalEvent = AdvisoryCodec.seal(
                last.copy(
                    id = "",
                    sequence = last.sequence + 1,
                    eventType = kind.name,
                    occurredAt = now,
                    localDate = Instant.ofEpochMilli(now).atZone(ZoneId.of(last.zoneId)).toLocalDate().toString(),
                    payloadJson = AdvisoryCodec.json.encodeToString(TerminalPayloadV1(cappedMillis, completedCycles)),
                    previousEventHash = last.eventHash,
                    eventHash = "",
                ),
            )
            if (database.advisory().insertEvents(listOf(terminalEvent)).firstOrNull() == IGNORED_ROW_ID) {
                return@withTransaction AdvisoryMutationResult.Ignored(
                    AdvisoryIneligibleReason.OPPORTUNITY_ALREADY_HANDLED,
                )
            }
            insertEpisodeEventChange(terminalEvent, now)
            AdvisoryMutationResult.Appended(listOf(terminalEvent.id))
        }
        if (appended is AdvisoryMutationResult.Appended) {
            clearEpisodeIfCurrent(episodeId)
            researchLedger.provenance.refreshAfterCommit()
            ContinuityWorkScheduler.requestCheckpoint(context)
        }
        return appended
    }

    override suspend fun completeMaximumDuration(
        episodeId: String,
        now: Long,
        deliveredForegroundMillis: Long,
        completedCycles: Int,
    ): AdvisoryMutationResult {
        val appended = database.withTransaction {
            val chain = database.advisory().eventsForEpisode(episodeId)
            if (chain.isEmpty()) {
                return@withTransaction AdvisoryMutationResult.Ignored(AdvisoryIneligibleReason.OPPORTUNITY_NOT_FOUND)
            }
            val types = chain.sortedBy { it.sequence }.map { EpisodeEventType.valueOf(it.eventType) }
            if (!EpisodeTransitions.mayAppend(types, EpisodeEventType.COMPLETED_MAX_DURATION)) {
                return@withTransaction AdvisoryMutationResult.Ignored(
                    AdvisoryIneligibleReason.OPPORTUNITY_ALREADY_HANDLED,
                )
            }
            val last = chain.maxByOrNull { it.sequence }!!
            val protocol = EvidenceProtocolCatalog.registry.find(last.protocolId, last.protocolVersion)
            val cappedMillis = deliveredForegroundMillis.coerceIn(
                0L,
                (protocol?.maxDurationSeconds ?: 0) * MILLIS_PER_SECOND,
            )
            val completion = AdvisoryCodec.seal(
                last.copy(
                    id = "",
                    sequence = last.sequence + 1,
                    eventType = EpisodeEventType.COMPLETED_MAX_DURATION.name,
                    occurredAt = now,
                    localDate = Instant.ofEpochMilli(now).atZone(ZoneId.of(last.zoneId)).toLocalDate().toString(),
                    payloadJson = AdvisoryCodec.json.encodeToString(TerminalPayloadV1(cappedMillis, completedCycles)),
                    previousEventHash = last.eventHash,
                    eventHash = "",
                ),
            )
            val opensAt = now
            val closesAt = now + (protocol?.outcomeWindowSeconds ?: 0) * MILLIS_PER_SECOND
            val windowOpened = AdvisoryCodec.seal(
                completion.copy(
                    id = "",
                    sequence = completion.sequence + 1,
                    eventType = EpisodeEventType.OUTCOME_WINDOW_OPENED.name,
                    payloadJson = AdvisoryCodec.json.encodeToString(OutcomeWindowOpenedPayloadV1(opensAt, closesAt)),
                    previousEventHash = completion.eventHash,
                    eventHash = "",
                ),
            )
            val insertedIds = database.advisory().insertEvents(listOf(completion, windowOpened))
            if (insertedIds.any { it == IGNORED_ROW_ID }) {
                return@withTransaction AdvisoryMutationResult.Ignored(
                    AdvisoryIneligibleReason.OPPORTUNITY_ALREADY_HANDLED,
                )
            }
            insertEpisodeEventChange(completion, now)
            insertEpisodeEventChange(windowOpened, now)
            AdvisoryMutationResult.Appended(listOf(completion.id, windowOpened.id))
        }
        if (appended is AdvisoryMutationResult.Appended) {
            clearEpisodeIfCurrent(episodeId)
            researchLedger.provenance.refreshAfterCommit()
            ContinuityWorkScheduler.requestCheckpoint(context)
        }
        return appended
    }

    private suspend fun clearEpisodeIfCurrent(episodeId: String) {
        AdvisoryProcessSessionRegistry.unregister(episodeId)
        if (settingsProvider().currentEpisodeId == episodeId) {
            setCurrentEpisodeId(null)
        }
    }

    private suspend fun insertEpisodeEventChange(event: InterventionEpisodeEventEntity, now: Long) {
        database.journal().insertChange(
            ContinuityChangeEntity(
                id = UUID.randomUUID().toString(),
                entityType = "INTERVENTION_EPISODE_EVENT",
                entityId = event.id,
                operation = ChangeOperation.CREATE.name,
                occurredAt = now,
                acknowledgedSnapshotId = null,
            ),
        )
    }

    @Suppress("LongParameterList")
    private fun episodeEvent(
        episodeId: String,
        opportunityId: String,
        sequence: Long,
        type: EpisodeEventType,
        now: Long,
        zoneId: ZoneId,
        currentPhase: StudyPhase,
        opportunity: AdvisoryOpportunityEntity,
        payloadJson: String,
        previousEventHash: String,
    ) = InterventionEpisodeEventEntity(
        id = "",
        episodeId = episodeId,
        opportunityId = opportunityId,
        sequence = sequence,
        eventType = type.name,
        occurredAt = now,
        localDate = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate().toString(),
        zoneId = zoneId.id,
        studyPhaseId = currentPhase.id,
        sourceDeviceId = currentPhase.vector.sourceDeviceId,
        protocolId = opportunity.protocolId,
        protocolVersion = opportunity.protocolVersion,
        protocolDefinitionSha256 = opportunity.protocolDefinitionSha256,
        protocolCatalogSha256 = opportunity.protocolCatalogSha256,
        advisoryRuleVersion = opportunity.advisoryRuleVersion,
        buildMode = opportunity.buildMode,
        operationalEvidenceApproved = opportunity.operationalEvidenceApproved,
        masterAdvisoryEnabled = opportunity.masterAdvisoryEnabled,
        deliveryAllowed = opportunity.deliveryAllowedAtPresentation,
        payloadSchemaVersion = AdvisoryCodec.EVENT_PAYLOAD_SCHEMA_VERSION,
        payloadJson = payloadJson,
        previousEventHash = previousEventHash,
        eventHash = "",
    )

    private fun ManualStartAttestation.toPayload() = EligibilityAttestedPayloadV1(
        currentlySelfNoticesTensionOrArousal = currentlySelfNoticesTensionOrArousal,
        choosesProtocol = choosesProtocol,
        exclusionsAndContraindicationsClear = exclusionsAndContraindicationsClear,
        notDrivingOperatingMachineryOrExerting = notDrivingOperatingMachineryOrExerting,
    )

    @Suppress("LongParameterList")
    private fun buildPolicyInput(
        latest: PassiveObservationDecisionEntity,
        decoded: Result<PassiveObservation>,
        sourcePhase: StudyPhase?,
        currentPhase: StudyPhase,
        protocol: EvidenceProtocol?,
        key: ProtocolKey?,
        settings: AdvisorySettings,
        authorization: AdvisoryBuildAuthorization,
        existingRows: List<AdvisoryOpportunityEntity>,
        opportunityId: String,
        zoneId: ZoneId,
        now: Long,
    ): AdvisoryPolicyInput {
        // dataStatus, observationState, and baselineSegment are their own
        // entity columns, independent of decisionJson — a source is
        // constructible from them whether or not the JSON blob decodes,
        // which is what lets SOURCE_MISSING (no row at all) and
        // SOURCE_DECODE_FAILED (a row whose rich JSON is corrupt) stay
        // two distinct, independently reachable gates. Only explanation
        // is JSON-only, so it falls back to empty when decode fails —
        // never read, since eligibility already stops at
        // SOURCE_DECODE_FAILED before an ineligible source's explanation
        // could matter to anyone.
        val source = AdvisorySource(
            decisionId = latest.id,
            decisionContentHash = latest.contentHash,
            localDate = latest.localDate,
            asOfTime = latest.asOfTime,
            dataStatus = PassiveDataStatus.valueOf(latest.dataStatus),
            observationState = PassiveObservationState.valueOf(latest.observationState),
            explanation = decoded.getOrNull()?.explanation.orEmpty(),
            baselineSegment = latest.baselineSegment,
            passiveRuleVersion = sourcePhase
                ?.let { phase -> RuleSetVersionVector.passive(phase.vector.ruleSetVersion) }
                .orEmpty(),
            passiveModelVersion = sourcePhase?.vector?.modelSetVersion.orEmpty(),
            sourceStudyPhaseId = sourcePhase?.id.orEmpty(),
            sourceDeviceId = sourcePhase?.vector?.sourceDeviceId.orEmpty(),
        )
        val sourceLocalDate = LocalDate.parse(latest.localDate)
        val currentPhaseLocalDate = Instant.ofEpochMilli(currentPhase.startedAt).atZone(zoneId).toLocalDate()
        val producedAfterStudyStart = latest.asOfTime >= currentPhase.startedAt &&
            !sourceLocalDate.isBefore(currentPhaseLocalDate)
        return AdvisoryPolicyInput(
            authorization = authorization,
            masterAdvisoryEnabled = settings.masterAdvisoryEnabled,
            deliveryAllowed = settings.deliveryAllowed,
            source = source,
            sourceDecodeSucceeded = decoded.isSuccess,
            sourceProvenanceComplete = sourcePhase != null,
            sourceProducedAfterStudyStart = producedAfterStudyStart,
            protocol = protocol,
            protocolDefinitionSha256 = key?.definitionSha256,
            protocolCatalogSha256 = EvidenceProtocolCatalog.registry.catalogSha256,
            opportunityAlreadyRecorded = existingRows.any { it.id == opportunityId },
            opportunityAlreadyHandled = false,
            activeEpisodeExists = settings.currentEpisodeId != null,
            lastStartedAt = null,
            now = now,
        )
    }

    private fun StudyPhaseEntity.toAdvisoryStudyPhase(): StudyPhase = StudyPhase(
        id = id,
        ordinal = ordinal,
        startedAt = startedAt,
        reason = StudyPhaseReason.valueOf(reason),
        vector = ProvenanceVector(
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
        ),
    )

    private fun AdvisoryPolicyResult.Eligible.toOpportunityEntity(
        id: String,
        presentedAt: Long,
        zoneId: String,
        currentPhase: StudyPhase,
        settings: AdvisorySettings,
        authorization: AdvisoryBuildAuthorization,
        catalogSha256: String,
    ): AdvisoryOpportunityEntity {
        val unsealed = AdvisoryOpportunityEntity(
            id = id,
            presentedAt = presentedAt,
            localDate = Instant.ofEpochMilli(presentedAt).atZone(ZoneId.of(zoneId)).toLocalDate().toString(),
            zoneId = zoneId,
            sourceDecisionId = source.decisionId,
            sourceDecisionContentHash = source.decisionContentHash,
            sourceLocalDate = source.localDate,
            sourceAsOfTime = source.asOfTime,
            sourceDataStatus = source.dataStatus.name,
            sourceObservationState = source.observationState.name,
            sourceExplanation = source.explanation,
            sourceBaselineSegment = source.baselineSegment,
            sourcePassiveRuleVersion = source.passiveRuleVersion,
            sourcePassiveModelVersion = source.passiveModelVersion,
            sourceStudyPhaseId = source.sourceStudyPhaseId,
            protocolId = protocolKey.protocolId,
            protocolVersion = protocolKey.protocolVersion,
            protocolDefinitionSha256 = protocolKey.definitionSha256,
            protocolCatalogSha256 = catalogSha256,
            protocolClinicalReviewStatus = protocol.clinicalReviewStatus.name,
            advisoryRuleVersion = advisoryRuleVersion,
            buildMode = buildMode.name,
            operationalEvidenceApproved = authorization.operationalEvidenceApproved,
            masterAdvisoryEnabled = settings.masterAdvisoryEnabled,
            deliveryAllowedAtPresentation = settings.deliveryAllowed,
            studyPhaseId = currentPhase.id,
            sourceDeviceId = currentPhase.vector.sourceDeviceId,
            contentHash = "",
        )
        return unsealed.copy(contentHash = AdvisoryCodec.opportunityContentHash(unsealed))
    }

    companion object {
        private const val IGNORED_ROW_ID = -1L
        private const val MILLIS_PER_SECOND = 1_000L

        /** The registry's own one-cycle length: 2s inhale + 1s second inhale + 6s exhale. */
        private const val CYCLE_MILLIS = 9_000L

        fun build(context: Context): RoomAdvisoryRepository {
            val app = context.applicationContext
            val database = AnchorDatabase.get(app)
            val researchLedger = ResearchLedgerRepository.build(app)
            val prefs = AdvisoryPrefs(app)
            return RoomAdvisoryRepository(
                context = app,
                database = database,
                researchLedger = researchLedger,
                settingsProvider = { prefs.settings.first() },
                buildAuthorization = AdvisoryBuildAuthorization::current,
                setCurrentEpisodeId = prefs::setCurrentEpisodeId,
            )
        }
    }
}
