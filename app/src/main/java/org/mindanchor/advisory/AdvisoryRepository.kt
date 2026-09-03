package org.mindanchor.advisory

import android.content.Context
import androidx.room.withTransaction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
 * Program 3 Task 3 — read the visible advisory state, and materialize
 * today's opportunity from Program 2's latest decision if one is due.
 *
 * Task 4 extends this interface with [dismiss], [start], [stop], and
 * [completeMaximumDuration]; Task 3 wires only the two methods a
 * background refresh and the read-only surface need.
 */
interface AdvisoryRepository {
    fun observe(): Flow<AdvisoryReadModel>
    suspend fun refreshOpportunity(now: Long, zoneId: ZoneId): AdvisoryRefreshResult
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

        fun build(
            context: Context,
            settingsProvider: suspend () -> AdvisorySettings,
        ): RoomAdvisoryRepository {
            val app = context.applicationContext
            val database = AnchorDatabase.get(app)
            val researchLedger = ResearchLedgerRepository.build(app)
            return RoomAdvisoryRepository(
                context = app,
                database = database,
                researchLedger = researchLedger,
                settingsProvider = settingsProvider,
                buildAuthorization = AdvisoryBuildAuthorization::current,
            )
        }
    }
}
