package org.mindanchor.advisory

import org.mindanchor.data.db.AdvisoryOpportunityEntity
import org.mindanchor.data.db.InterventionEpisodeEventEntity
import org.mindanchor.intelligence.PassiveDataStatus
import org.mindanchor.intelligence.PassiveObservationState
import org.mindanchor.research.EvidenceProtocol

/**
 * Program 3 Task 1 — the frozen vocabulary of the advisory path.
 *
 * Program 3 is an adapter over finalized Program 1 and Program 2
 * contracts, and this file is the one place its shared shapes live so
 * policy, storage, UI, continuity, and export cannot invent parallel
 * representations of the same fact.
 *
 * Two absences are deliberate and load-bearing. There is no enum member
 * for success, failure, effect, symptom, diagnosis, or any present-tense
 * state: an advisory reports a finalized historical observation and never
 * a claim about the person now. And there is no member for notifying,
 * blocking, or taking over the screen, because the delivery path this
 * vocabulary describes is an ordinary, dismissible, back-navigable
 * screen the person opens themselves.
 */

/**
 * The exact protocol a build is permitted to name: id, version, and the
 * SHA-256 of the definition that id and version resolved to.
 *
 * The hash is part of the identity rather than a property of it. An
 * allowlist naming only `cyclic-sighing@1` would still match a silently
 * edited definition; naming the definition hash means an edit in place
 * stops matching instead of being delivered under the old approval.
 */
data class ProtocolKey(
    val protocolId: String,
    val protocolVersion: Int,
    val definitionSha256: String,
)

/**
 * The person's own two switches, both closed until deliberately opened.
 *
 * [masterAdvisoryEnabled] is the standing opt-in to the advisory path at
 * all; [deliveryAllowed] is the separate kill switch for presenting and
 * starting anything. [currentEpisodeId] is the recovery key that lets a
 * process restart close an episode it can no longer observe — never a
 * summary of one, which lives only in the append-only event stream.
 */
data class AdvisorySettings(
    val masterAdvisoryEnabled: Boolean = false,
    val deliveryAllowed: Boolean = false,
    val currentEpisodeId: String? = null,
)

/** Which kind of build this is. Ordinary is every public build. */
enum class AdvisoryBuildMode { ORDINARY, PERSONAL_RESEARCH }

/**
 * Why an advisory was not offered or could not be started.
 *
 * Every gate has its own reason so a refusal is legible after the fact.
 * A reason is a statement about the build, the switches, the source
 * record, or the protocol registry — never about the person.
 */
enum class AdvisoryIneligibleReason {
    BUILD_NOT_AUTHORIZED,
    OPERATIONAL_EVIDENCE_NOT_APPROVED,
    MASTER_ADVISORY_DISABLED,
    DELIVERY_DISABLED,
    SOURCE_MISSING,
    SOURCE_NOT_FINAL,
    SOURCE_NOT_SUSTAINED_DEVIATION,
    SOURCE_DECODE_FAILED,
    SOURCE_PROVENANCE_INCOMPLETE,
    SOURCE_PREDATES_STUDY_PHASE,
    PROTOCOL_MISSING,
    PROTOCOL_HASH_MISMATCH,
    PROTOCOL_NOT_ALLOWLISTED,
    PROTOCOL_NOT_CLINICALLY_REVIEWED,
    OPPORTUNITY_ALREADY_RECORDED,
    OPPORTUNITY_NOT_FOUND,
    OPPORTUNITY_ALREADY_HANDLED,
    ACTIVE_EPISODE_EXISTS,
    COOLDOWN_ACTIVE,
}

/**
 * The complete set of things that can be appended to an episode.
 *
 * Only [COMPLETED_MAX_DURATION] means the registered maximum was
 * actually reached. Backgrounding, process recovery, Back, a user stop,
 * discomfort, and the kill switch are each their own terminal event, so
 * a later reader can never mistake an interruption for a completion.
 */
enum class EpisodeEventType {
    DISMISSED,
    ELIGIBILITY_ATTESTED,
    STARTED,
    COMPLETED_MAX_DURATION,
    STOPPED_BY_USER,
    STOPPED_DISCOMFORT_REPORTED,
    INTERRUPTED_APP_BACKGROUND,
    INTERRUPTED_PROCESS_RECOVERY,
    STOPPED_KILL_SWITCH,
    OUTCOME_WINDOW_OPENED,
    OUTCOME_WINDOW_CLOSED_MISSING,
}

/**
 * Why an outcome window closed with nothing in it.
 *
 * There is exactly one reason because there is exactly one truth: this
 * build registers no instrument compatible with the protocol, so an
 * outcome was never measurable. Recording that is the alternative to
 * inferring one.
 */
enum class MissingOutcomeReason {
    NO_REGISTERED_COMPATIBLE_INSTRUMENT,
}

/**
 * Program 3 Task 3 — the finalized historical fact an opportunity is
 * built from, copied out of a Program 2 decision rather than joined to
 * it live.
 *
 * A copy is required, not merely convenient: Program 2 may later record
 * a corrected revision of the same local date, and an opportunity must
 * keep saying what it actually showed — the decision content it was
 * built from — rather than silently reflecting whatever the source table
 * says today.
 */
data class AdvisorySource(
    val decisionId: String,
    val decisionContentHash: String,
    val localDate: String,
    val asOfTime: Long,
    val dataStatus: PassiveDataStatus,
    val observationState: PassiveObservationState,
    val explanation: String,
    val baselineSegment: String,
    val passiveRuleVersion: String,
    val passiveModelVersion: String,
    val sourceStudyPhaseId: String,
    val sourceDeviceId: String,
)

/** Which policy question is being asked: show an advisory, or begin its protocol. */
enum class AdvisoryAction { PRESENT, START }

/**
 * What [AdvisoryPolicy.evaluate] decided, and why.
 *
 * There is no partial or advisory-with-caveats result. Either every gate
 * held and the caller may proceed with exactly the source and protocol
 * named here, or it may not proceed and this says which gate stopped it.
 */
sealed interface AdvisoryPolicyResult {
    data class Eligible(
        val source: AdvisorySource,
        val protocol: EvidenceProtocol,
        val protocolKey: ProtocolKey,
        val advisoryRuleVersion: String,
        val buildMode: AdvisoryBuildMode,
    ) : AdvisoryPolicyResult

    data class Ineligible(val reason: AdvisoryIneligibleReason) : AdvisoryPolicyResult
}

/**
 * Everything [AdvisoryPolicy.evaluate] needs, already resolved.
 *
 * The policy itself never looks anything up — every field here is a
 * value the caller (the repository) has already read from the build,
 * the person's settings, Program 1/2's records, and the local clock.
 * That is what keeps the policy pure and table-testable: a field flips,
 * one reason changes, nothing else moves.
 *
 * Several fields are meaningful for one [AdvisoryAction] only.
 * [opportunityAlreadyRecorded] gates [AdvisoryAction.PRESENT];
 * [opportunityAlreadyHandled], [activeEpisodeExists], and
 * [lastStartedAt] gate [AdvisoryAction.START]. Presenting a materialized
 * opportunity is not gated by [deliveryAllowed] — that switch is the
 * kill switch for putting anything in front of the person, which
 * materializing evidence for later review does not do.
 */
data class AdvisoryPolicyInput(
    val authorization: AdvisoryBuildAuthorization,
    val masterAdvisoryEnabled: Boolean,
    val deliveryAllowed: Boolean,
    val source: AdvisorySource?,
    val sourceDecodeSucceeded: Boolean,
    val sourceProvenanceComplete: Boolean,
    val sourceProducedAfterStudyStart: Boolean,
    val protocol: EvidenceProtocol?,
    val protocolDefinitionSha256: String?,
    val protocolCatalogSha256: String,
    val opportunityAlreadyRecorded: Boolean,
    val opportunityAlreadyHandled: Boolean,
    val activeEpisodeExists: Boolean,
    val lastStartedAt: Long?,
    val now: Long,
)

/** What materializing today's opportunity, if any, produced. */
sealed interface AdvisoryRefreshResult {
    data class Created(val opportunityId: String) : AdvisoryRefreshResult
    data class AlreadyRecorded(val opportunityId: String) : AdvisoryRefreshResult
    data class Ineligible(val reason: AdvisoryIneligibleReason) : AdvisoryRefreshResult
}

/** What appending one or more episode events produced. */
sealed interface AdvisoryMutationResult {
    data class Appended(val eventIds: List<String>) : AdvisoryMutationResult
    data class Ignored(val reason: AdvisoryIneligibleReason) : AdvisoryMutationResult
    data class IntegrityFailure(val episodeId: String) : AdvisoryMutationResult
}

/** What attempting to start a protocol produced. */
sealed interface AdvisoryStartResult {
    data class Started(val episodeId: String) : AdvisoryStartResult
    data class NotStarted(val reason: AdvisoryIneligibleReason) : AdvisoryStartResult
    data class IntegrityFailure(val opportunityId: String) : AdvisoryStartResult
}

/**
 * The one thing the UI is allowed to render, derived without mutating
 * any evidence row.
 *
 * At most one unhandled opportunity is ever visible, newest first, and
 * an opportunity becomes invisible the moment it is [EpisodeEventType.DISMISSED]
 * or [EpisodeEventType.STARTED] — never deleted or updated, only excluded
 * from what this derives. [Hidden] also covers the case where the
 * registry's protocol can no longer be resolved or its hash no longer
 * matches: this never renders stale or changed instructions.
 */
sealed interface AdvisoryReadModel {
    data object Hidden : AdvisoryReadModel

    data class Opportunity(
        val row: AdvisoryOpportunityEntity,
        val protocol: EvidenceProtocol,
        val startAvailable: Boolean,
        val startBlockedReason: AdvisoryIneligibleReason?,
    ) : AdvisoryReadModel

    data class ActiveEpisode(
        val opportunity: AdvisoryOpportunityEntity,
        val events: List<InterventionEpisodeEventEntity>,
        val protocol: EvidenceProtocol,
    ) : AdvisoryReadModel
}

/**
 * Program 3 Task 4 — the four facts one deliberate Start action records,
 * all at once.
 *
 * The private constructor is the point: there is no public path that
 * builds this from caller-supplied booleans, because the only source of
 * these facts is a person's single Start tap on the evidence screen. A
 * UI or a test cannot construct a partial or a fabricated attestation —
 * only [fromSingleManualStartAction] exists, and it always means exactly
 * this action just happened.
 */
@ConsistentCopyVisibility
data class ManualStartAttestation private constructor(
    val currentlySelfNoticesTensionOrArousal: Boolean,
    val choosesProtocol: Boolean,
    val exclusionsAndContraindicationsClear: Boolean,
    val notDrivingOperatingMachineryOrExerting: Boolean,
) {
    companion object {
        fun fromSingleManualStartAction() = ManualStartAttestation(
            currentlySelfNoticesTensionOrArousal = true,
            choosesProtocol = true,
            exclusionsAndContraindicationsClear = true,
            notDrivingOperatingMachineryOrExerting = true,
        )
    }
}
