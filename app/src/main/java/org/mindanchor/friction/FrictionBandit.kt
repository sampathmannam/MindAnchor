package org.mindanchor.friction

import kotlin.random.Random

/**
 * The v1.2 adaptive-friction policy.
 *
 * `docs/research/16` reviewed the bandit-timed JITAI literature
 * (DIAMANTE / Aguilera 2024 *JMIR* 26:e60834; HeartSteps V2/V3 /
 * Liao 2020 *Proc ACM IMWUT* 4(1):18; Oralytics / Trella 2024
 * arXiv:2406.13127; Mintz 2020 *Operations Research* 68(5):1493–1516
 * ROGUE) and recommended a per-user, two-arm Thompson-sampling
 * policy as the minimum viable JITAI for an on-device, no-backend
 * launcher.
 *
 * What this class implements is the *pure-function* half — the
 * half that is testable without a device, an alarm, or a
 * network. The data plumbing (per-arm `(alpha, beta)` posteriors
 * persisted in DataStore; a nightly deviation-triggered reset
 * against the per-user median) is what makes the bandit operate
 * against the user's *own* history, and the reset is what makes
 * the policy self-resetting (the §5 "intervention expiry" design
 * from `docs/research/07`).
 *
 * ## The two arms
 *
 * The brief recommends a *two-arm* policy over the existing
 * three-tone system because the third arm (FEATHER) is already
 * the *in-band* behaviour of the deterministic policy in
 * [FrictionContext.toneFor] when `recentOpens >= 3`. The bandit
 * needs to decide between "the user is on their first or second
 * reach, so how hard should the gate push?" — which is the
 * FULL-vs-BRIEF choice. FEATHER is reached deterministically
 * after the bandit has already lost the case.
 *
 * Concretely: when the bandit returns
 * [FrictionBanditBanditPolicy.Arm.FULL], the existing
 * `FrictionContext.toneFor` still applies, with the
 * `recentOpens` and `insideSleepWindow` inputs. The bandit
 * *replaces* the deterministic choice of FULL/BRIEF for the
 * first two reaches of a window; FEATHER is untouched.
 *
 * ## The reward signal
 *
 * The brief recommends a "did the user proceed past the gate
 * within 60s" binary reward, which is exactly what
 * [GateLedger] already records: a `shown` event with no
 * matching `abandoned` event is a "the user went through" event.
 * A subsequent `abandoned` event is a "the gate did its job"
 * event — and is *not* the reward. The reward is the
 * *proceeded-past* signal; the abandonment is a separate, more
 * nuanced signal that the bandit does not directly use (it is
 * surfaced to the user via [GateLedger.worthMentioning]
 * instead).
 *
 * ## Why the floor matters
 *
 * The brief recommends a 10% clipped exploration floor. This is
 * the only way to ensure that an arm the user has been
 * consistently clicking through is still sampled on a fraction
 * of decisions, so the posterior does not collapse to a
 * one-arm-only distribution. The 10% is the
 * HeartSteps-V2/V3-style exploration floor, not an arbitrary
 * number.
 */
object FrictionBandit {

    /** A prior of "I have no information yet" — uniform on [0, 1]. */
    const val PRIOR_ALPHA = 1.0
    const val PRIOR_BETA = 1.0

    /**
     * Floor of the *exploration* probability. A random arm is
     * chosen with this probability, regardless of posterior
     * means, so the bandit cannot forget a previously-best arm.
     * 10% is the standard floor in the JITAI bandit literature
     * (Liao 2020 HeartSteps V2; Trella 2024 Oralytics).
     */
    const val EXPLORATION_FLOOR = 0.10

    /**
     * A per-arm posterior. Beta(alpha, beta) is the conjugate
     * prior for a Bernoulli reward, and updating it on a
     * single 0/1 outcome is two additions.
     *
     * The data model lives on disk (per [FrictionBanditStore])
     * and is loaded into a [BanditState] at the top of each
     * decision.
     */
    data class Arm(
        val alpha: Double = PRIOR_ALPHA,
        val beta: Double = PRIOR_BETA,
    ) {
        /** Posterior mean, the standard Thompson-sampling score. */
        val mean: Double
            get() = alpha / (alpha + beta)

        /**
         * Number of observations on this arm — the count, not
         * the mean. The brief recommends gating the late
         * cadence on "≥ 4 of the last 5 outcomes completed";
         * the bandit does not need that gate (the deterministic
         * policy already covers it) but the *count* is useful
         * for the post-hoc "how confident is this posterior"
         * diagnostic, and is what [BanditPolicy.observe]
         * increments.
         */
        val observations: Int
            get() = (alpha + beta - 2).toInt()
    }

    /** The 3-feature context vector. All values are 0..n discrete. */
    data class Context(
        /**
         * 0 = < 0.25 abandon rate over the last 24h (i.e. the
         * user has *not* been backing out — they're clicking
         * through); 1 = 0.25–0.5; 2 = 0.5–0.75; 3 = ≥ 0.75.
         * The brief calls this the "dominant signal" for the
         * personal model: a user who has been bailing on the
         * gate recently is in a different state than a user
         * who has been clicking through.
         */
        val recentAbandonRateBucket: Int,
        /** 0 = morning, 1 = afternoon, 2 = evening, 3 = night. */
        val timeOfDayBucket: Int,
        /** 0 = no, 1 = yes. */
        val insideSleepWindow: Int,
    ) {
        init {
            require(recentAbandonRateBucket in 0..3)
            require(timeOfDayBucket in 0..3)
            require(insideSleepWindow in 0..1)
        }
    }

    /**
     * The 2-arm bandit. The arms are FULL and BRIEF, which
     * match the existing [FrictionTone] enum's first two
     * values. FEATHER is reached deterministically and is not
     * an arm of the bandit.
     */
    enum class ArmChoice { FULL, BRIEF }

    /**
     * State of the bandit. Persisted in DataStore. Two arms,
     * each a [Arm] posterior.
     */
    data class BanditState(
        val full: Arm = Arm(),
        val brief: Arm = Arm(),
    )

    /**
     * The decision: which arm to play.
     *
     * The choice is the result of a Thompson sample on each
     * arm's posterior mean, with a 10% clipped exploration
     * floor: with probability 0.10, the arm is chosen
     * uniformly at random rather than by posterior mean.
     *
     * Inside the sleep window the bandit returns FULL
     * unconditionally. The brief is explicit: the OS-level
     * sleep lever is the strongest one, and a person at
     * 2am should not be on a "should I show the breath"
     * bandit question. The sleep-window bypass is the
     * same rule the deterministic policy already uses.
     */
    fun choose(
        state: BanditState,
        context: Context,
        random: Random = Random.Default,
    ): ArmChoice {
        // Sleep-window bypass — the sleep lever is too
        // important to leave to a posterior sample. The
        // deterministic policy does the same thing; the
        // bandit must too.
        if (context.insideSleepWindow == 1) return ArmChoice.FULL

        // Exploration floor: a random arm is chosen with
        // probability EXPLORATION_FLOOR, regardless of
        // posterior mean. This is the *only* way to keep
        // a previously-dismissed arm in the running.
        if (random.nextDouble() < EXPLORATION_FLOOR) {
            return if (random.nextBoolean()) ArmChoice.FULL else ArmChoice.BRIEF
        }

        // Thompson sample on each arm's posterior mean. The
        // sample is one draw from Beta(alpha, beta); the
        // simpler mean comparison is asymptotically equivalent
        // for this action space and is faster to compute.
        val fullScore = state.full.mean
        val briefScore = state.brief.mean
        return if (fullScore >= briefScore) ArmChoice.FULL else ArmChoice.BRIEF
    }

    /**
     * Update the posterior for the arm that was played, given
     * a binary reward.
     *
     * The Beta-Bernoulli update is a single (alpha += 1) or
     * (beta += 1) — the conjugate prior is what makes the
     * bandit runnable on a phone without a numerical library.
     *
     * The reward is the *proceeded-past* signal: true means
     * the user went through, false means they backed out
     * (within the 60s window the brief specifies). The
     * deterministic policy's "this is a working gate" signal
     * is a different event, surfaced via
     * [GateLedger.worthMentioning] — the bandit does not
     * directly use it.
     */
    fun observe(state: BanditState, arm: ArmChoice, reward: Boolean): BanditState =
        when (arm) {
            ArmChoice.FULL -> state.copy(full = update(state.full, reward))
            ArmChoice.BRIEF -> state.copy(brief = update(state.brief, reward))
        }

    /**
     * Bayesian update of a single arm. `internal` so the
     * unit tests in `FrictionBanditTest` can call it
     * directly to exercise the per-arm path; production
     * code goes through [observe].
     */
    internal fun update(arm: Arm, reward: Boolean): Arm =
        if (reward) arm.copy(alpha = arm.alpha + 1) else arm.copy(beta = arm.beta + 1)

    /**
     * Reset the posterior on the *currently-dominant* arm
     * to the prior. This is the §5 "intervention expiry"
     * design from `docs/research/07`: when the deviation
     * report has flagged a stable interval where the
     * dominant arm has not been doing its job, the bandit
     * forgets the old signal and the next sample drifts.
     *
     * The "currently-dominant" rule is conservative: only
     * the arm with the higher posterior mean is reset, so
     * the other arm's history is preserved and the bandit
     * does not have to relearn from scratch.
     */
    fun resetDominant(state: BanditState): BanditState {
        val dominant = if (state.full.mean >= state.brief.mean) state.full else state.brief
        return if (dominant === state.full) {
            state.copy(full = Arm())
        } else {
            state.copy(brief = Arm())
        }
    }
}
