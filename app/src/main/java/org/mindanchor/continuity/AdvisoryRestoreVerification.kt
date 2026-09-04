package org.mindanchor.continuity

import org.mindanchor.advisory.AdvisoryBuildAuthorization
import org.mindanchor.advisory.AdvisoryBuildMode
import org.mindanchor.advisory.AdvisoryCodec
import org.mindanchor.advisory.EligibilityAttestedPayloadV1
import org.mindanchor.advisory.EpisodeEventType
import org.mindanchor.advisory.EpisodeTransitions
import org.mindanchor.advisory.EventChainVerdict
import org.mindanchor.advisory.MissingOutcomePayloadV1
import org.mindanchor.advisory.OutcomeWindowOpenedPayloadV1
import org.mindanchor.advisory.ProtocolKey
import org.mindanchor.advisory.TerminalPayloadV1
import org.mindanchor.data.db.AdvisoryOpportunityEntity
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.data.db.InterventionEpisodeEventEntity
import org.mindanchor.intelligence.PassiveDataStatus
import org.mindanchor.intelligence.PassiveObservationState
import org.mindanchor.research.ClinicalReviewStatus
import org.mindanchor.research.EvidenceProtocolCatalog
import org.mindanchor.research.EvidenceProtocolRegistry

/**
 * Verifies every restored opportunity's content hash/id and every
 * restored episode's hash chain, enum vocabulary, payload shape, and
 * registry tuple **before** any `insertOpportunity`/`insertEvents` call —
 * so a single corrupt row throws from inside the same Room transaction
 * [RestoreCoordinator]'s `mergeRoom` runs in and the whole restore attempt
 * (Program 3 rows and everything else merged earlier in that transaction)
 * rolls back atomically, rather than merging a partially-corrupt advisory
 * history.
 */
internal suspend fun mergeAdvisoryRows(database: AnchorDatabase, payload: ContinuityPayload) {
    val opportunities = payload.advisoryOpportunities.map { it.toEntity() }
    val events = payload.interventionEpisodeEvents.map { it.toEntity() }
    check(opportunities.all { it.isRestorableAdvisoryOpportunity() }) {
        "a restored advisory opportunity failed content hash, id, or registry verification"
    }
    check(events.groupBy { it.episodeId }.values.all { it.isRestorableEpisodeChain() }) {
        "a restored intervention episode failed hash-chain, payload, or registry verification"
    }

    val dao = database.advisory()
    opportunities.forEach { dao.insertOpportunity(it) }
    dao.insertEvents(events)

    check(
        missingRestoredIds(
            dao.opportunitiesNow().map { it.id },
            opportunities.map { it.id },
        ).isEmpty(),
    )
    check(
        missingRestoredIds(
            dao.eventsNow().map { it.id },
            events.map { it.id },
        ).isEmpty(),
    )
}

@Suppress("ReturnCount")
private fun AdvisoryOpportunityEntity.isRestorableAdvisoryOpportunity(): Boolean {
    val recomputedId = runCatching {
        AdvisoryCodec.opportunityId(
            sourceDecisionId = sourceDecisionId,
            sourceDecisionHash = sourceDecisionContentHash,
            key = ProtocolKey(protocolId, protocolVersion, protocolDefinitionSha256),
            advisoryRule = advisoryRuleVersion,
        )
    }.getOrNull()
    if (recomputedId != id || AdvisoryCodec.opportunityContentHash(this) != contentHash) return false
    if (runCatching { AdvisoryBuildMode.valueOf(buildMode) }.isFailure) return false
    if (runCatching { PassiveDataStatus.valueOf(sourceDataStatus) }.isFailure) return false
    if (runCatching { PassiveObservationState.valueOf(sourceObservationState) }.isFailure) return false
    if (runCatching { ClinicalReviewStatus.valueOf(protocolClinicalReviewStatus) }.isFailure) return false
    if (protocolCatalogSha256 != AdvisoryBuildAuthorization.PROGRAM_THREE_CATALOG_SHA256) return false
    val protocol = EvidenceProtocolCatalog.registry.find(protocolId, protocolVersion) ?: return false
    return EvidenceProtocolRegistry.definitionSha256(protocol) == protocolDefinitionSha256
}

private fun List<InterventionEpisodeEventEntity>.isRestorableEpisodeChain(): Boolean {
    if (AdvisoryCodec.verifyEpisodeChain(this) != EventChainVerdict.VALID) return false
    if (!all { it.isRestorableEpisodeEvent() }) return false
    val appended = mutableListOf<EpisodeEventType>()
    sortedBy { it.sequence }.forEach { row ->
        val type = EpisodeEventType.valueOf(row.eventType)
        if (!EpisodeTransitions.mayAppend(appended, type)) return false
        appended += type
    }
    return true
}

@Suppress("ReturnCount")
private fun InterventionEpisodeEventEntity.isRestorableEpisodeEvent(): Boolean {
    if (runCatching { EpisodeEventType.valueOf(eventType) }.isFailure) return false
    if (runCatching { AdvisoryBuildMode.valueOf(buildMode) }.isFailure) return false
    if (payloadSchemaVersion != AdvisoryCodec.EVENT_PAYLOAD_SCHEMA_VERSION) return false
    if (protocolCatalogSha256 != AdvisoryBuildAuthorization.PROGRAM_THREE_CATALOG_SHA256) return false
    if (!eventPayloadShapeIsValid()) return false
    val protocol = EvidenceProtocolCatalog.registry.find(protocolId, protocolVersion) ?: return false
    return EvidenceProtocolRegistry.definitionSha256(protocol) == protocolDefinitionSha256
}

private fun InterventionEpisodeEventEntity.eventPayloadShapeIsValid(): Boolean = runCatching {
    when (EpisodeEventType.valueOf(eventType)) {
        EpisodeEventType.DISMISSED, EpisodeEventType.STARTED -> payloadJson == AdvisoryCodec.EMPTY_PAYLOAD
        EpisodeEventType.ELIGIBILITY_ATTESTED -> {
            AdvisoryCodec.json.decodeFromString<EligibilityAttestedPayloadV1>(payloadJson)
            true
        }
        EpisodeEventType.COMPLETED_MAX_DURATION,
        EpisodeEventType.STOPPED_BY_USER,
        EpisodeEventType.STOPPED_DISCOMFORT_REPORTED,
        EpisodeEventType.INTERRUPTED_APP_BACKGROUND,
        EpisodeEventType.INTERRUPTED_PROCESS_RECOVERY,
        EpisodeEventType.STOPPED_KILL_SWITCH,
        -> {
            AdvisoryCodec.json.decodeFromString<TerminalPayloadV1>(payloadJson)
            true
        }
        EpisodeEventType.OUTCOME_WINDOW_OPENED -> {
            AdvisoryCodec.json.decodeFromString<OutcomeWindowOpenedPayloadV1>(payloadJson)
            true
        }
        EpisodeEventType.OUTCOME_WINDOW_CLOSED_MISSING -> {
            AdvisoryCodec.json.decodeFromString<MissingOutcomePayloadV1>(payloadJson)
            true
        }
    }
}.getOrDefault(false)
