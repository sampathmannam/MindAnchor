package org.mindanchor.advisory

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
