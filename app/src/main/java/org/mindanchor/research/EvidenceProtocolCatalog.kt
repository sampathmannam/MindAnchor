package org.mindanchor.research

import org.mindanchor.friction.BreathingProtocol

/**
 * The seeded evidence protocol catalogue.
 *
 * ## The seeding rule
 *
 * Only citations this repository has **already verified** —
 * `docs/research/23-citation-audit.md` is the source of truth — or primary
 * and authoritative sources. Never a fabricated one. Where a source cannot
 * be verified, the registry capability ships and the protocol does not.
 *
 * That rule leaves exactly one protocol today. A catalogue padded with
 * protocols the evidence does not support would disprove the registry
 * rather than demonstrate it; the rejection tests in
 * `EvidenceProtocolRegistryTest` are what show the machinery works, and
 * [DELIBERATELY_NOT_SEEDED] is what shows the rule was applied rather than
 * merely stated.
 *
 * ## Program 1 records; it does not deliver
 *
 * Nothing here is selected, scheduled, played, or evaluated. The steps and
 * durations describe a protocol so that a future study can reference a
 * fixed, versioned definition — not so that this build can run one.
 */
object EvidenceProtocolCatalog {

    /**
     * The five-minute cyclic-sighing (physiological sigh) breathing
     * practice, as trialled.
     *
     * Every number is traceable. The step durations are
     * [BreathingProtocol]'s own constants, already anchored in this
     * repository's verified citations. [EvidenceProtocol.maxDurationSeconds]
     * is 300 because five minutes a day is the dose Balban et al. trialled,
     * and [EvidenceProtocol.cooldownSeconds] is twenty hours because that
     * dose was once daily. Neither is a number chosen for feel.
     *
     * Deliberately **not** the same thing as the launcher's friction-gate
     * breath: that plays one 9-second cycle, which `BreathingProtocol`'s own
     * KDoc already calls "a *trigger*, not a dose". See
     * [DELIBERATELY_NOT_SEEDED].
     */
    private val CYCLIC_SIGHING_V1 = EvidenceProtocol(
        id = "cyclic-sighing",
        version = 1,
        targetState = "Elevated self-reported tension or arousal that the person has noticed themselves.",
        intendedPopulation = "One adult using MindAnchor for personal, non-clinical self-experimentation.",
        exclusions = listOf(
            "Not established for anyone under 18.",
            "Not established during pregnancy.",
            "Not established for any respiratory or cardiovascular condition.",
            "Not for anyone whose clinician has advised against breathing exercises.",
        ),
        evidenceSources = listOf(
            EvidenceSource(
                citation = "Balban MY et al. (2023). Brief structured respiration practices enhance mood " +
                    "and reduce physiological arousal. Cell Reports Medicine 4(1):100895.",
                reference = "https://doi.org/10.1016/j.xcrm.2022.100895",
                strength = EvidenceStrength.RANDOMIZED_OR_CONTROLLED_TRIAL,
                sourceType = EvidenceSourceType.PEER_REVIEWED_ARTICLE,
            ),
            EvidenceSource(
                citation = "Bernardi L et al. (2001). Modulatory effects of respiration. " +
                    "Journal of Hypertension 19(12):2221-2229.",
                reference = "https://doi.org/10.1097/00004872-200112000-00016",
                strength = EvidenceStrength.MECHANISTIC_STUDY,
                sourceType = EvidenceSourceType.PEER_REVIEWED_ARTICLE,
            ),
        ),
        mechanism = "The extended exhale is the active component: Bernardi et al. found slow breathing " +
            "increased baroreflex sensitivity and depressed chemoreflex response. The double inhale is " +
            "what makes the breath a sigh rather than an ordinary slow breath.",
        expectedOutcome = "Higher same-day self-reported mood and lower same-day self-reported tension, " +
            "relative to this person's own recent days.",
        eligibilityRules = listOf(
            "The person chose to start it.",
            "No exclusion listed in this protocol applies to them.",
        ),
        contraindicationRules = listOf(
            "Stop if breathing becomes uncomfortable or the person feels lightheaded.",
            "Do not run while driving or operating machinery.",
            "Do not run during physical exertion.",
        ),
        steps = listOf(
            ProtocolStep(
                ordinal = 1,
                instruction = "Breathe in through the nose until the lungs are full.",
                durationSeconds = (BreathingProtocol.INHALE_MILLIS / MILLIS_PER_SECOND).toInt(),
            ),
            ProtocolStep(
                ordinal = 2,
                instruction = "Take a second, shorter sip of air through the nose on top of the first.",
                durationSeconds = (BreathingProtocol.SIP_MILLIS / MILLIS_PER_SECOND).toInt(),
            ),
            ProtocolStep(
                ordinal = 3,
                instruction = "Let the breath out slowly through the mouth until it runs out.",
                durationSeconds = (BreathingProtocol.EXHALE_MILLIS / MILLIS_PER_SECOND).toInt(),
            ),
        ),
        permittedModalities = setOf(Modality.VISUAL, Modality.AUDIO, Modality.HAPTIC, Modality.TEXT),
        maxDurationSeconds = TRIALLED_DOSE_SECONDS,
        stopRules = setOf(
            StopRule.USER_STOPPED,
            StopRule.MAX_DURATION_REACHED,
            StopRule.DISCOMFORT_REPORTED,
            StopRule.INTERRUPTED_BY_PROTECTED_APP,
        ),
        cooldownSeconds = ONCE_DAILY_COOLDOWN_SECONDS,
        outcomeWindowSeconds = SAME_DAY_OUTCOME_WINDOW_SECONDS,
        successInterpretation = "Compared only against this person's own recent days. No clinical " +
            "threshold, cut-off, or score is applied, and no result is evidence of a treatment effect.",
        // docs/CLINICAL_REVIEW.md still opens with "not yet reviewed by a
        // clinician". The registry records that, and does not upgrade it.
        clinicalReviewStatus = ClinicalReviewStatus.NOT_REVIEWED,
        userFacingExplanation = "A five-minute breathing practice studied in a randomised trial with " +
            "healthy adults. MindAnchor records it as a research protocol. It is not treatment, and it " +
            "makes no promise about how you will feel.",
    )

    /** The validated, content-hashed catalogue. */
    val registry: EvidenceProtocolRegistry = EvidenceProtocolRegistry.of(listOf(CYCLIC_SIGHING_V1))

    /**
     * Candidates considered and deliberately left unseeded, with the reason
     * recorded in the approved design's §4.4 table:
     *
     *  - `symmetric-slow-paced-breathing` — the citation audit records the
     *    outcome literature as mixed and the cited review as unverified.
     *    Bernardi 2001 supports the *mechanism* only. Seeding it would be an
     *    efficacy claim this repository cannot support.
     *  - `self-compassion-moment` — the app rotates the *user's own*
     *    phrases. No fixed steps, no fixed modality, so §4.4's contract
     *    cannot be met without inventing a protocol the evidence does not
     *    describe.
     *  - `behavioural-activation` — Dimidjian 2006 is a real RCT, but its
     *    steps, maximum duration, stop rules and cooldown are defined
     *    nowhere in this repository. It is its own protocol-evidence
     *    project (Program 6).
     *  - `friction-gate-breath-trigger` — one 9-second cycle is a different
     *    dose from the trialled practice, and `BreathingProtocol` already
     *    says so.
     *
     * Asserted by test to be disjoint from [registry], so a later author
     * cannot quietly seed one of them.
     */
    val DELIBERATELY_NOT_SEEDED = setOf(
        "symmetric-slow-paced-breathing",
        "self-compassion-moment",
        "behavioural-activation",
        "friction-gate-breath-trigger",
    )

    private const val MILLIS_PER_SECOND = 1_000L

    /** Five minutes a day — the dose Balban et al. trialled. */
    private const val TRIALLED_DOSE_SECONDS = 300

    /** Twenty hours: the trialled dose was once daily. */
    private const val ONCE_DAILY_COOLDOWN_SECONDS = 72_000

    /** Twenty-four hours: the trial's outcomes were same-day self-reports. */
    private const val SAME_DAY_OUTCOME_WINDOW_SECONDS = 86_400
}
