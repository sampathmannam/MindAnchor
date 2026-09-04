package org.mindanchor.research

import org.mindanchor.friction.BreathingProtocol

/**
 * The seeded evidence protocol catalogue.
 *
 * @wording-reviewed — `userFacingExplanation`, `successInterpretation`,
 * every step `instruction`, and the exclusion and contraindication lines
 * are all text a person may read about a protocol whose own
 * `clinicalReviewStatus` is `NOT_REVIEWED`. Changes to any of them are
 * clinical-review-required.
 *
 * ## The seeding rule
 *
 * Only citations this repository has **already verified** —
 * `docs/research/23-citation-audit.md` and `docs/research/22-research-index.md`
 * are the source of truth — or primary and authoritative sources. Never a
 * fabricated one. Where a source cannot be verified, the registry
 * capability ships and the protocol does not.
 *
 * That rule leaves exactly one protocol today. A catalogue padded with
 * protocols the evidence does not support would disprove the registry
 * rather than demonstrate it; the rejection tests in
 * `EvidenceProtocolRegistryTest` are what show the machinery works, and
 * [DELIBERATELY_NOT_SEEDED] records the candidates the rule turned away.
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
     * practice.
     *
     * ## Where each value comes from
     *
     * Two different kinds of number live in this definition, and
     * presenting the second kind as the first would be the same
     * dishonesty as a fabricated citation:
     *
     *  - **`maxDurationSeconds = 300` — the trialled dose.**
     *    `22-research-index.md` records "5 min/day x 28 days".
     *  - **Step durations 2 s / 1 s / 6 s — the launcher's own
     *    constants, not the trialled ones.**
     *    `12-breathing-protocols-comparison.md` records the trialled cycle
     *    as roughly 3–4 s inhale plus a 1–2 s sip plus a 6–10 s exhale,
     *    about 10–20 s in total. MindAnchor's cycle sits at or below the
     *    low end of every one of those ranges.
     *  - **`cooldownSeconds`, `outcomeWindowSeconds`, `stopRules`,
     *    `exclusions`, `contraindicationRules` — conservative operational
     *    defaults, not findings.** No trial reports a cooldown or a stop
     *    rule. §4.4 requires them anyway, because a protocol contract
     *    without them is incomplete, so they are chosen to be cautious and
     *    are pending clinical review.
     *
     * Deliberately **not** the same thing as the launcher's friction-gate
     * breath: that plays a single cycle, which `BreathingProtocol`'s own
     * KDoc calls "a *trigger*, not a dose" and which
     * `12-breathing-protocols-comparison.md` records as
     * "mechanism-plausible but not the tested dose". See
     * [DELIBERATELY_NOT_SEEDED].
     */
    private val CYCLIC_SIGHING_V1 = EvidenceProtocol(
        id = "cyclic-sighing",
        version = 1,
        targetState = "Elevated self-reported tension or arousal that the person has noticed themselves.",
        intendedPopulation = "One adult using MindAnchor for personal, non-clinical self-experimentation.",
        // Conservative defaults pending clinical review, not findings:
        // nothing in this repository's record enumerates exclusions for
        // this protocol. §4.4 requires them, so they are chosen to be
        // cautious rather than derived.
        exclusions = listOf(
            "Not established for anyone under 18.",
            "Not established during pregnancy.",
            "Not established for any respiratory or cardiovascular condition.",
            "Not for anyone whose clinician has advised against breathing exercises.",
        ),
        evidenceSources = listOf(
            EvidenceSource(
                title = "Brief structured respiration practices enhance mood and reduce physiological arousal",
                citation = "Balban MY, Neri E, Kogon MM, Weed L, Nouriani B, Jo B, Holl G, Zeitzer JM, " +
                    "Spiegel D, & Huberman AD (2023). Cell Reports Medicine 4(1):100895.",
                reference = "https://doi.org/10.1016/j.xcrm.2022.100895",
                strength = EvidenceStrength.RANDOMIZED_OR_CONTROLLED_TRIAL,
                sourceType = EvidenceSourceType.PEER_REVIEWED_ARTICLE,
            ),
            EvidenceSource(
                title = "Slow breathing reduces chemoreflex response to hypoxia and hypercapnia, " +
                    "and increases baroreflex sensitivity",
                citation = "Bernardi L, Gabutti A, Porta C, & Spicuzza L (2001). " +
                    "J. Hypertens. 19(12):2221-2229.",
                reference = "https://doi.org/10.1097/00004872-200112000-00016",
                strength = EvidenceStrength.MECHANISTIC_STUDY,
                sourceType = EvidenceSourceType.PEER_REVIEWED_ARTICLE,
            ),
        ),
        mechanism = "Bernardi et al. measured slow breathing at six breaths a minute and found it " +
            "depressed chemoreflex responses and increased baroreflex sensitivity, which is the " +
            "parasympathetic route the long exhale is thought to take. The double inhale is what makes " +
            "the breath a sigh rather than an ordinary slow breath. Neither paper isolated which " +
            "component carries the effect.",
        expectedOutcome = "A hypothesis, not a finding: higher same-day self-reported mood, relative to " +
            "this person's own recent days. Balban et al. attribute the largest positive-affect gain " +
            "and the largest resting-respiratory-rate drop to this arm; state anxiety moved equally " +
            "across all of their breathing arms, so this protocol claims nothing distinctive there.",
        eligibilityRules = listOf(
            "The person chose to start it.",
            "No exclusion listed in this protocol applies to them.",
        ),
        // Conservative defaults pending clinical review. See `exclusions`.
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
                instruction = "Let the breath out slowly through the mouth until it runs out, then " +
                    "begin the cycle again until the five minutes are up.",
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
        userFacingExplanation = "A five-minute breathing practice. A randomised trial with healthy " +
            "adults compared a version of it against other breathing patterns; MindAnchor's cycle is " +
            "shorter than the one that trial used. MindAnchor records this as a research protocol. It " +
            "is not treatment, and it makes no promise about how you will feel.",
    )

    /** The validated, content-hashed catalogue. */
    val registry: EvidenceProtocolRegistry = EvidenceProtocolRegistry.of(listOf(CYCLIC_SIGHING_V1))

    /**
     * Candidates considered and deliberately left unseeded:
     *
     *  - `symmetric-slow-paced-breathing` — the citation audit records the
     *    outcome literature as mixed and the cited review as unverified.
     *    Bernardi 2001 supports the *mechanism* only. Seeding it would be
     *    an efficacy claim this repository cannot support.
     *  - `self-compassion-moment` — the app rotates the *user's own*
     *    phrases. No fixed steps, no fixed modality, so §4.4's contract
     *    cannot be met without inventing a protocol the evidence does not
     *    describe.
     *  - `behavioural-activation` — Dimidjian 2006 is a real RCT, but its
     *    steps and dose are defined nowhere in this repository, so the
     *    substantive half of a §4.4 contract could only be filled in by
     *    inventing the protocol itself. Its cooldown and stop rules would
     *    be conservative defaults exactly as cyclic sighing's are; that
     *    was never the disqualifier. It is its own protocol-evidence
     *    project (Program 6).
     *  - `friction-gate-breath-trigger` — a single cycle is a different
     *    dose from the trialled practice, and `BreathingProtocol` already
     *    says so.
     *
     * This set is documentation, not a guard. The guard is the test that
     * pins the catalogue to exactly its current contents: no protocol,
     * listed here or not, can be seeded without editing that first.
     * Recording the rejected candidates keeps the reasoning next to the
     * code that acted on it.
     */
    val DELIBERATELY_NOT_SEEDED = setOf(
        "symmetric-slow-paced-breathing",
        "self-compassion-moment",
        "behavioural-activation",
        "friction-gate-breath-trigger",
    )

    private const val MILLIS_PER_SECOND = 1_000L

    /** Five minutes a day — the dose the trial used. */
    private const val TRIALLED_DOSE_SECONDS = 300

    /**
     * A day. A conservative operational default matching the trial's
     * once-daily cadence — not a value any paper reports, because no trial
     * measures a cooldown.
     */
    private const val ONCE_DAILY_COOLDOWN_SECONDS = 86_400

    /**
     * A day. A conservative operational default: the outcomes this
     * protocol names are same-day self-reports, so a longer window would
     * attribute tomorrow's mood to today's practice. The repository's
     * record does not state the trial's measurement cadence.
     */
    private const val SAME_DAY_OUTCOME_WINDOW_SECONDS = 86_400
}
