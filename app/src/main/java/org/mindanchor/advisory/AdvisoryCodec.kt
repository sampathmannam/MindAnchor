package org.mindanchor.advisory

import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.mindanchor.data.db.AdvisoryOpportunityEntity
import org.mindanchor.data.db.InterventionEpisodeEventEntity

/** What a walk of one episode's events concluded about the chain. */
enum class EventChainVerdict { EMPTY, VALID, BROKEN }

/** The four facts one deliberate Start action records, all at once. */
@Serializable
data class EligibilityAttestedPayloadV1(
    val currentlySelfNoticesTensionOrArousal: Boolean,
    val choosesProtocol: Boolean,
    val exclusionsAndContraindicationsClear: Boolean,
    val notDrivingOperatingMachineryOrExerting: Boolean,
)

/**
 * How much of the protocol was actually delivered in the foreground.
 *
 * Two aggregates and nothing else: per-second samples or breath
 * timestamps would be a behavioural record of a person breathing, which
 * this design does not keep.
 */
@Serializable
data class TerminalPayloadV1(val deliveredForegroundMillis: Long, val completedCycles: Int)

/** When the registry's outcome window for a completed episode opens and closes. */
@Serializable
data class OutcomeWindowOpenedPayloadV1(val opensAt: Long, val closesAt: Long)

/** Why an outcome window closed with nothing measured in it. */
@Serializable
data class MissingOutcomePayloadV1(val reason: MissingOutcomeReason)

/**
 * Program 3 Task 2 — canonical identifiers, content hashes, and chain
 * verification for the advisory evidence tables.
 *
 * Identifiers are hashes of the content they identify, so re-running the
 * same materialization produces the same row and `INSERT OR IGNORE`
 * makes a rescan a no-op. That is also why a corrected Program 2
 * decision yields a *different* opportunity: the source content hash is
 * part of the identity, so a correction cannot be silently absorbed into
 * the advisory that was built from the superseded reading.
 *
 * The canonical encoding length-prefixes every named field. Joining
 * values with a delimiter would let a value containing that delimiter
 * imitate a field boundary and collide with a different record; a byte
 * count in front of each value cannot be forged from inside it.
 */
object AdvisoryCodec {

    const val EVENT_PAYLOAD_SCHEMA_VERSION = 1

    /** `DISMISSED` and `STARTED` carry no payload of their own. */
    const val EMPTY_PAYLOAD = "{}"

    val json = Json {
        encodeDefaults = true
        explicitNulls = true
    }

    private fun canonical(vararg fields: Pair<String, String?>): ByteArray = buildString {
        fields.forEach { (name, value) ->
            append(name.length).append(':').append(name).append('=')
            if (value == null) {
                append("-1:")
            } else {
                append(value.toByteArray(Charsets.UTF_8).size).append(':').append(value)
            }
            append('\n')
        }
    }.toByteArray(Charsets.UTF_8)

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { "%02x".format(it) }

    fun opportunityId(
        sourceDecisionId: String,
        sourceDecisionHash: String,
        key: ProtocolKey,
        advisoryRule: String,
    ): String = sha256(
        canonical(
            "sourceDecisionId" to sourceDecisionId,
            "sourceDecisionHash" to sourceDecisionHash,
            "protocolId" to key.protocolId,
            "protocolVersion" to key.protocolVersion.toString(),
            "definitionSha256" to key.definitionSha256,
            "advisoryRuleVersion" to advisoryRule,
        ),
    )

    /**
     * The episode identifier a dismissal's single event belongs to.
     *
     * A dismissal is still an episode-shaped fact — it has one event and
     * no start — so it needs a stream of its own rather than being
     * attached to an episode that was never started.
     */
    fun dismissalStreamId(opportunityId: String): String =
        sha256(canonical("kind" to "dismissal", "opportunityId" to opportunityId))

    fun episodeId(opportunityId: String, startedAt: Long, sourceDeviceId: String): String = sha256(
        canonical(
            "kind" to "started",
            "opportunityId" to opportunityId,
            "startedAt" to startedAt.toString(),
            "sourceDeviceId" to sourceDeviceId,
        ),
    )

    /**
     * Hashes every persisted opportunity field in entity declaration
     * order except [AdvisoryOpportunityEntity.id] and
     * [AdvisoryOpportunityEntity.contentHash], which are what this
     * produces.
     */
    fun opportunityContentHash(row: AdvisoryOpportunityEntity): String = sha256(
        canonical(
            "presentedAt" to row.presentedAt.toString(),
            "localDate" to row.localDate,
            "zoneId" to row.zoneId,
            "sourceDecisionId" to row.sourceDecisionId,
            "sourceDecisionContentHash" to row.sourceDecisionContentHash,
            "sourceLocalDate" to row.sourceLocalDate,
            "sourceAsOfTime" to row.sourceAsOfTime.toString(),
            "sourceDataStatus" to row.sourceDataStatus,
            "sourceObservationState" to row.sourceObservationState,
            "sourceExplanation" to row.sourceExplanation,
            "sourceBaselineSegment" to row.sourceBaselineSegment,
            "sourcePassiveRuleVersion" to row.sourcePassiveRuleVersion,
            "sourcePassiveModelVersion" to row.sourcePassiveModelVersion,
            "sourceStudyPhaseId" to row.sourceStudyPhaseId,
            "protocolId" to row.protocolId,
            "protocolVersion" to row.protocolVersion.toString(),
            "protocolDefinitionSha256" to row.protocolDefinitionSha256,
            "protocolCatalogSha256" to row.protocolCatalogSha256,
            "protocolClinicalReviewStatus" to row.protocolClinicalReviewStatus,
            "advisoryRuleVersion" to row.advisoryRuleVersion,
            "buildMode" to row.buildMode,
            "operationalEvidenceApproved" to row.operationalEvidenceApproved.toString(),
            "masterAdvisoryEnabled" to row.masterAdvisoryEnabled.toString(),
            "deliveryAllowedAtPresentation" to row.deliveryAllowedAtPresentation.toString(),
            "studyPhaseId" to row.studyPhaseId,
            "sourceDeviceId" to row.sourceDeviceId,
        ),
    )

    /**
     * Hashes every persisted event field in entity declaration order
     * except [InterventionEpisodeEventEntity.id] and
     * [InterventionEpisodeEventEntity.eventHash]; both are set to this
     * digest, so an event's identity is its content and its link.
     */
    fun eventHash(row: InterventionEpisodeEventEntity): String = sha256(
        canonical(
            "episodeId" to row.episodeId,
            "opportunityId" to row.opportunityId,
            "sequence" to row.sequence.toString(),
            "eventType" to row.eventType,
            "occurredAt" to row.occurredAt.toString(),
            "localDate" to row.localDate,
            "zoneId" to row.zoneId,
            "studyPhaseId" to row.studyPhaseId,
            "sourceDeviceId" to row.sourceDeviceId,
            "protocolId" to row.protocolId,
            "protocolVersion" to row.protocolVersion.toString(),
            "protocolDefinitionSha256" to row.protocolDefinitionSha256,
            "protocolCatalogSha256" to row.protocolCatalogSha256,
            "advisoryRuleVersion" to row.advisoryRuleVersion,
            "buildMode" to row.buildMode,
            "operationalEvidenceApproved" to row.operationalEvidenceApproved.toString(),
            "masterAdvisoryEnabled" to row.masterAdvisoryEnabled.toString(),
            "deliveryAllowed" to row.deliveryAllowed.toString(),
            "payloadSchemaVersion" to row.payloadSchemaVersion.toString(),
            "payloadJson" to row.payloadJson,
            "previousEventHash" to row.previousEventHash,
        ),
    )

    /** Stamps [row] with the digest of its own content, as both id and hash. */
    fun seal(row: InterventionEpisodeEventEntity): InterventionEpisodeEventEntity {
        val hash = eventHash(row)
        return row.copy(id = hash, eventHash = hash)
    }

    /**
     * Walks one episode and says whether its chain is intact.
     *
     * The rows are sorted by sequence first because serialized list
     * order is not evidence — a restore or an export could reorder them
     * without anything being wrong. What is evidence is that the
     * sequence numbers are exactly 1..n with no gap or repeat, that each
     * event links to the previous event's hash, and that every event
     * still hashes to the identity it was stored under.
     */
    fun verifyEpisodeChain(rows: List<InterventionEpisodeEventEntity>): EventChainVerdict {
        if (rows.isEmpty()) return EventChainVerdict.EMPTY
        var previous = ""
        rows.sortedBy { it.sequence }.forEachIndexed { index, row ->
            if (row.sequence != index + 1L || row.previousEventHash != previous) {
                return EventChainVerdict.BROKEN
            }
            if (eventHash(row) != row.eventHash || row.id != row.eventHash) {
                return EventChainVerdict.BROKEN
            }
            previous = row.eventHash
        }
        return EventChainVerdict.VALID
    }
}
