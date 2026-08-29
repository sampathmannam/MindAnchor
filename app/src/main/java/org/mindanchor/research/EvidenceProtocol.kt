package org.mindanchor.research

import kotlinx.serialization.Serializable

/**
 * The evidence hierarchy from the mental-health OS design's §4.4, ordered
 * strongest first. The order is load-bearing: a reader comparing two
 * protocols compares their strongest source, and `EvidenceStrength.entries`
 * is asserted against this order by test so a later insertion cannot
 * silently reshuffle it.
 */
@Serializable
enum class EvidenceStrength {
    CLINICAL_GUIDELINE_OR_SYSTEMATIC_REVIEW,
    RANDOMIZED_OR_CONTROLLED_TRIAL,
    VALIDATED_TREATMENT_MANUAL,
    MECHANISTIC_STUDY,
    EXPERT_BOOK_CONSISTENT_WITH_STRONGER_EVIDENCE,
}

/**
 * What kind of thing a source is. The four in [EXCLUDED] are named
 * explicitly by §4.4 — "blog-only, influencer, marketing, or AI-generated
 * interventions are excluded" — and exist here as enum cases rather than as
 * a comment so a protocol carrying one fails validation instead of relying
 * on an author's restraint.
 */
@Serializable
enum class EvidenceSourceType {
    PEER_REVIEWED_ARTICLE,
    SYSTEMATIC_REVIEW,
    CLINICAL_GUIDELINE,
    TREATMENT_MANUAL,
    ACADEMIC_BOOK,
    BLOG,
    INFLUENCER,
    MARKETING,
    AI_GENERATED,
    ;

    val isPermitted: Boolean get() = this !in EXCLUDED

    companion object {
        val EXCLUDED = setOf(BLOG, INFLUENCER, MARKETING, AI_GENERATED)
    }
}

/**
 * Whether a qualified clinician has looked at this protocol.
 *
 * A registered protocol records the truth, which today is
 * [NOT_REVIEWED] for everything: `docs/CLINICAL_REVIEW.md` still opens with
 * "not yet reviewed by a clinician". The registry never upgrades this
 * field on a protocol's behalf.
 */
@Serializable
enum class ClinicalReviewStatus { NOT_REVIEWED, REVIEW_REQUESTED, REVIEWED_WITH_CHANGES, REVIEWED_AND_ACCEPTED }

/** How a step may be presented. Program 1 records these; it delivers nothing. */
@Serializable
enum class Modality { VISUAL, AUDIO, HAPTIC, TEXT }

/** The conditions under which a run of the protocol ends. Triggers, not clinical claims. */
@Serializable
enum class StopRule { USER_STOPPED, MAX_DURATION_REACHED, DISCOMFORT_REPORTED, INTERRUPTED_BY_PROTECTED_APP }

/**
 * One piece of evidence behind a protocol. [reference] is a resolvable
 * identifier — a DOI URL for everything currently catalogued — so a reader
 * can go and check rather than take the [citation] string on trust.
 */
@Serializable
data class EvidenceSource(
    val citation: String,
    val reference: String,
    val strength: EvidenceStrength,
    val sourceType: EvidenceSourceType,
)

/** One fixed step. [ordinal] is 1-based and contiguous within a protocol. */
@Serializable
data class ProtocolStep(val ordinal: Int, val instruction: String, val durationSeconds: Int)

/**
 * A protocol and the complete evidence contract §4.4 requires of it.
 *
 * Every field is mandatory. [EvidenceProtocolRegistry.validate] rejects a
 * protocol missing any of them, and [EvidenceProtocolRegistry.of] refuses
 * to build a registry containing a rejected protocol at all — the design's
 * §15 criterion is "no protocol can *run* without a complete evidence
 * contract", and the cheapest way to guarantee that is for an incomplete
 * protocol never to exist as a registered object in the first place.
 *
 * Program 1 only *records* protocols. Nothing here is selected, sequenced,
 * scheduled, delivered, or played; that begins in Programs 3–5.
 */
@Serializable
data class EvidenceProtocol(
    /** Stable lowercase-kebab identifier, stable across versions. */
    val id: String,
    /** 1-based. A changed definition requires a new version, never an edit in place. */
    val version: Int,
    /** The observable state this protocol targets, in plain language. */
    val targetState: String,
    /** Who this protocol is for. */
    val intendedPopulation: String,
    /** Who it is *not* established for. Never empty. */
    val exclusions: List<String>,
    /** The evidence behind it. Never empty, never an excluded source type. */
    val evidenceSources: List<EvidenceSource>,
    /** The proposed mechanism. */
    val mechanism: String,
    /** What is expected to change. */
    val expectedOutcome: String,
    /** When a person may run it. */
    val eligibilityRules: List<String>,
    /** When they should not. */
    val contraindicationRules: List<String>,
    /** The fixed steps, ordinals 1..n. */
    val steps: List<ProtocolStep>,
    /** How a step may be presented. */
    val permittedModalities: Set<Modality>,
    /** The longest a single run may last. */
    val maxDurationSeconds: Int,
    /** How a run ends. */
    val stopRules: Set<StopRule>,
    /** The minimum gap between runs. */
    val cooldownSeconds: Int,
    /** How long after a run an outcome is attributed to it. */
    val outcomeWindowSeconds: Int,
    /** How to read the outcome — self-referenced only, never a clinical threshold. */
    val successInterpretation: String,
    /** Whether a clinician has reviewed it. */
    val clinicalReviewStatus: ClinicalReviewStatus,
    /** What the person is told, in plain words, with no efficacy promise. */
    val userFacingExplanation: String,
)
