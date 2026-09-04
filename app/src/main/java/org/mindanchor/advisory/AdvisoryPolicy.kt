package org.mindanchor.advisory

import org.mindanchor.intelligence.PassiveDataStatus
import org.mindanchor.intelligence.PassiveObservationState
import org.mindanchor.research.ClinicalReviewStatus
import org.mindanchor.research.EvidenceProtocol
import org.mindanchor.research.EvidenceProtocolRegistry

/**
 * Program 3 Task 3 — the pure eligibility rule.
 *
 * Nothing here reads a database, a clock, a build flag, or a registry
 * lookup. Every fact [evaluate] needs arrives already resolved on
 * [AdvisoryPolicyInput], so the same input always produces the same
 * result and every gate is independently testable by changing one field.
 *
 * The checks run in one fixed order and stop at the first failure, so a
 * result names exactly one reason — never a list, and never a reason
 * that describes the person rather than the gate.
 */
object AdvisoryPolicy {
    const val RULE_VERSION = "advisory-opportunity-v1"

    @Suppress("ReturnCount", "CyclomaticComplexMethod", "LongMethod")
    fun evaluate(input: AdvisoryPolicyInput, action: AdvisoryAction): AdvisoryPolicyResult {
        if (input.authorization.buildMode != AdvisoryBuildMode.PERSONAL_RESEARCH) {
            return ineligible(AdvisoryIneligibleReason.BUILD_NOT_AUTHORIZED)
        }
        if (!input.authorization.operationalEvidenceApproved) {
            return ineligible(AdvisoryIneligibleReason.OPERATIONAL_EVIDENCE_NOT_APPROVED)
        }
        if (!input.masterAdvisoryEnabled) {
            return ineligible(AdvisoryIneligibleReason.MASTER_ADVISORY_DISABLED)
        }
        val source = input.source ?: return ineligible(AdvisoryIneligibleReason.SOURCE_MISSING)
        if (source.dataStatus != PassiveDataStatus.AVAILABLE_FINAL) {
            return ineligible(AdvisoryIneligibleReason.SOURCE_NOT_FINAL)
        }
        if (source.observationState != PassiveObservationState.SUSTAINED_DEVIATION) {
            return ineligible(AdvisoryIneligibleReason.SOURCE_NOT_SUSTAINED_DEVIATION)
        }
        if (!input.sourceDecodeSucceeded) {
            return ineligible(AdvisoryIneligibleReason.SOURCE_DECODE_FAILED)
        }
        if (!input.sourceProvenanceComplete) {
            return ineligible(AdvisoryIneligibleReason.SOURCE_PROVENANCE_INCOMPLETE)
        }
        if (!input.sourceProducedAfterStudyStart) {
            return ineligible(AdvisoryIneligibleReason.SOURCE_PREDATES_STUDY_PHASE)
        }
        val protocol = input.protocol ?: return ineligible(AdvisoryIneligibleReason.PROTOCOL_MISSING)
        val definitionSha256 = input.protocolDefinitionSha256
        if (definitionSha256 == null || definitionSha256 != EvidenceProtocolRegistry.definitionSha256(protocol)) {
            return ineligible(AdvisoryIneligibleReason.PROTOCOL_HASH_MISMATCH)
        }
        if (input.protocolCatalogSha256 != AdvisoryBuildAuthorization.PROGRAM_THREE_CATALOG_SHA256) {
            return ineligible(AdvisoryIneligibleReason.PROTOCOL_HASH_MISMATCH)
        }
        val key = ProtocolKey(protocol.id, protocol.version, definitionSha256)
        if (key !in input.authorization.protocolAllowlist) {
            return ineligible(AdvisoryIneligibleReason.PROTOCOL_NOT_ALLOWLISTED)
        }
        // The current allowlists never put anything ordinary mode could
        // reach this line with, so this never fires today. It stays
        // explicit rather than assumed, because the allowlist is the
        // only thing standing between "ordinary" and "delivers a
        // not-clinically-reviewed protocol" if that ever changed.
        if (input.authorization.buildMode == AdvisoryBuildMode.ORDINARY &&
            protocol.clinicalReviewStatus != ClinicalReviewStatus.REVIEWED_AND_ACCEPTED
        ) {
            return ineligible(AdvisoryIneligibleReason.PROTOCOL_NOT_CLINICALLY_REVIEWED)
        }
        return when (action) {
            AdvisoryAction.PRESENT -> evaluatePresent(input, source, protocol, key)
            AdvisoryAction.START -> evaluateStart(input, source, protocol, key)
        }
    }

    private fun evaluatePresent(
        input: AdvisoryPolicyInput,
        source: AdvisorySource,
        protocol: EvidenceProtocol,
        key: ProtocolKey,
    ): AdvisoryPolicyResult = if (input.opportunityAlreadyRecorded) {
        ineligible(AdvisoryIneligibleReason.OPPORTUNITY_ALREADY_RECORDED)
    } else {
        eligible(input, source, protocol, key)
    }

    @Suppress("ReturnCount")
    private fun evaluateStart(
        input: AdvisoryPolicyInput,
        source: AdvisorySource,
        protocol: EvidenceProtocol,
        key: ProtocolKey,
    ): AdvisoryPolicyResult {
        if (input.opportunityAlreadyHandled) return ineligible(AdvisoryIneligibleReason.OPPORTUNITY_ALREADY_HANDLED)
        if (!input.deliveryAllowed) return ineligible(AdvisoryIneligibleReason.DELIVERY_DISABLED)
        if (input.activeEpisodeExists) return ineligible(AdvisoryIneligibleReason.ACTIVE_EPISODE_EXISTS)
        val lastStartedAt = input.lastStartedAt
        if (lastStartedAt != null && input.now < lastStartedAt + protocol.cooldownSeconds * MILLIS_PER_SECOND) {
            return ineligible(AdvisoryIneligibleReason.COOLDOWN_ACTIVE)
        }
        return eligible(input, source, protocol, key)
    }

    private fun eligible(
        input: AdvisoryPolicyInput,
        source: AdvisorySource,
        protocol: EvidenceProtocol,
        key: ProtocolKey,
    ) = AdvisoryPolicyResult.Eligible(
        source = source,
        protocol = protocol,
        protocolKey = key,
        advisoryRuleVersion = RULE_VERSION,
        buildMode = input.authorization.buildMode,
    )

    private fun ineligible(reason: AdvisoryIneligibleReason) = AdvisoryPolicyResult.Ineligible(reason)

    private const val MILLIS_PER_SECOND = 1_000L
}
