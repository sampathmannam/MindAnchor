package org.mindanchor.friction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The v1.2 adaptive-friction policy — see [FrictionBandit] and
 * `docs/research/16`. The brief is a 120-line pure-function core
 * that runs in <1ms per decision on a mid-range phone; these
 * tests pin the contract the data plumbing (per-arm Beta
 * posteriors, nightly deviation-triggered reset) relies on.
 */
class FrictionBanditTest {

    @Test
    fun `the prior is uniform, not informative`() {
        // Beta(1, 1) is the uniform prior on [0, 1]. A prior
        // that wasn't uniform would give a fresh user a
        // deterministic first decision, which is the bias
        // the brief calls out against.
        val arm = FrictionBandit.Arm()
        assertEquals(0.5, arm.mean, 0.0001)
        assertEquals(0, arm.observations)
    }

    @Test
    fun `an observed arm's posterior mean moves toward its observed reward rate`() {
        var arm = FrictionBandit.Arm()
        repeat(10) { arm = FrictionBandit.update(arm, reward = true) }
        // 10 rewards on a (1, 1) prior → (11, 1) → mean ~ 0.92.
        // Far from 0.5; far from 1.0 (we want shrinkage, not
        // overconfidence on a small sample).
        assertTrue(arm.mean > 0.85)
        assertEquals(10, arm.observations)
    }

    @Test
    fun `the sleep window bypasses the bandit and returns FULL`() {
        // The brief is explicit: the OS-level sleep lever
        // (Windred 2024, SRI beats duration) is too important
        // to leave to a posterior sample. A person at 2am
        // should not be on a "should I show the breath"
        // question. The deterministic policy does the same
        // thing; the bandit must too.
        val state = FrictionBandit.BanditState(
            full = FrictionBandit.Arm(alpha = 1.0, beta = 100.0), // very low mean
            brief = FrictionBandit.Arm(alpha = 100.0, beta = 1.0), // very high mean
        )
        val ctx = FrictionBandit.Context(
            recentAbandonRateBucket = 0,
            timeOfDayBucket = 3,
            insideSleepWindow = 1,
        )
        // Even with BRIEF overwhelmingly the posterior winner,
        // the sleep window forces FULL.
        assertEquals(
            FrictionBandit.ArmChoice.FULL,
            FrictionBandit.choose(state, ctx, random = Random(0)),
        )
    }

    @Test
    fun `choose returns the arm with the higher posterior mean on the deterministic path`() {
        val state = FrictionBandit.BanditState(
            full = FrictionBandit.Arm(alpha = 5.0, beta = 5.0), // mean 0.5
            brief = FrictionBandit.Arm(alpha = 9.0, beta = 1.0), // mean 0.9
        )
        val ctx = FrictionBandit.Context(
            recentAbandonRateBucket = 0,
            timeOfDayBucket = 1,
            insideSleepWindow = 0,
        )
        // Force the deterministic path with random.nextDouble() ≥ 0.10.
        // Random(42) first call gives 0.86..., which is above
        // the 10% exploration floor.
        val choice = FrictionBandit.choose(state, ctx, random = Random(42))
        assertEquals(FrictionBandit.ArmChoice.BRIEF, choice)
    }

    @Test
    fun `exploration floor samples the lesser arm on a fraction of decisions`() {
        // The brief mandates a 10% exploration floor. To
        // verify it actually fires, run the bandit many times
        // with BRIEF overwhelmingly the posterior winner and
        // confirm that FULL is still chosen on a meaningful
        // fraction of decisions.
        val state = FrictionBandit.BanditState(
            full = FrictionBandit.Arm(alpha = 1.0, beta = 50.0), // very low
            brief = FrictionBandit.Arm(alpha = 50.0, beta = 1.0), // very high
        )
        val ctx = FrictionBandit.Context(
            recentAbandonRateBucket = 0,
            timeOfDayBucket = 1,
            insideSleepWindow = 0,
        )
        val trials = 10_000
        var fullCount = 0
        for (i in 0 until trials) {
            if (FrictionBandit.choose(state, ctx, random = Random(i.toLong())) == FrictionBandit.ArmChoice.FULL) {
                fullCount++
            }
        }
        // Expected ~ 10% (5% from each arm's half of the
        // exploration floor; BRIEF is 50% of the uniform
        // random and FULL is the other 50%). The exact 5%
        // expectation is what we check, with a ±1% tolerance
        // for finite sample noise.
        val rate = fullCount.toDouble() / trials
        assertTrue("exploration rate $rate outside [0.04, 0.06]", rate in 0.04..0.06)
    }

    @Test
    fun `observe updates only the played arm`() {
        val s0 = FrictionBandit.BanditState()
        // reward = true means "arm did its job" (user backed out)
        val s1 = FrictionBandit.observe(s0, FrictionBandit.ArmChoice.FULL, reward = true)
        assertEquals(2.0, s1.full.alpha, 0.0001)
        assertEquals(1.0, s1.full.beta, 0.0001)
        // BRIEF arm is untouched.
        assertEquals(1.0, s1.brief.alpha, 0.0001)
        assertEquals(1.0, s1.brief.beta, 0.0001)
    }

    @Test
    fun `observe with a failure increments beta, not alpha`() {
        val s0 = FrictionBandit.BanditState()
        // reward = false means "arm did not work" (user went through)
        val s1 = FrictionBandit.observe(s0, FrictionBandit.ArmChoice.BRIEF, reward = false)
        // BRIEF arm: alpha stays at 1, beta → 2.
        assertEquals(1.0, s1.brief.alpha, 0.0001)
        assertEquals(2.0, s1.brief.beta, 0.0001)
    }

    @Test
    fun `resetDominant resets only the higher-mean arm`() {
        val s0 = FrictionBandit.BanditState(
            full = FrictionBandit.Arm(alpha = 10.0, beta = 1.0),  // mean 0.91
            brief = FrictionBandit.Arm(alpha = 1.0, beta = 10.0), // mean 0.09
        )
        val s1 = FrictionBandit.resetDominant(s0)
        // FULL was dominant → reset to prior.
        assertEquals(1.0, s1.full.alpha, 0.0001)
        assertEquals(1.0, s1.full.beta, 0.0001)
        // BRIEF arm is untouched.
        assertEquals(1.0, s1.brief.alpha, 0.0001)
        assertEquals(10.0, s1.brief.beta, 0.0001)
    }

    @Test
    fun `resetDominant is a no-op when both arms are at the prior`() {
        val s0 = FrictionBandit.BanditState()
        val s1 = FrictionBandit.resetDominant(s0)
        // FULL is "dominant" at mean 0.5 == 0.5; reset still
        // resets FULL. The behaviour is documented and
        // tested: the brief recommends "the dominant arm",
        // and on a fresh state the dominant is the FULL arm
        // by the ≥ rule. This is intentional, not a bug.
        assertEquals(1.0, s1.full.alpha, 0.0001)
        assertEquals(1.0, s1.full.beta, 0.0001)
    }

    @Test
    fun `observations count matches the data lifetime, not the state mean`() {
        // The brief uses observation counts for diagnostics
        // ("how confident is this posterior"). 10 rewards and
        // 5 failures = 15 observations on that arm.
        var arm = FrictionBandit.Arm()
        repeat(10) { arm = FrictionBandit.update(arm, reward = true) }
        repeat(5) { arm = FrictionBandit.update(arm, reward = false) }
        assertEquals(15, arm.observations)
    }

    @Test
    fun `posterior means of the two arms are not coupled`() {
        // A reward on FULL must not change BRIEF. The bandit
        // decomposes cleanly; the integration test would
        // catch a future refactor that broke the independence.
        val s0 = FrictionBandit.BanditState(
            full = FrictionBandit.Arm(alpha = 5.0, beta = 5.0),
            brief = FrictionBandit.Arm(alpha = 5.0, beta = 5.0),
        )
        val s1 = FrictionBandit.observe(s0, FrictionBandit.ArmChoice.FULL, reward = true)
        assertEquals(6.0, s1.full.alpha, 0.0001)
        assertEquals(5.0, s1.brief.alpha, 0.0001)
        assertEquals(s0.brief.mean, s1.brief.mean, 0.0001) // BRIEF is untouched
        assertEquals(5.0, s1.brief.beta, 0.0001)
    }

    /**
     * Pinned behaviour — the bandit must prefer the arm that
     * is doing its job, not the arm the user is clicking
     * through. A friction gate is supposed to make the user
     * back out; a sampler that rewards the most-clicked-
     * through arm is the sampler that learns to give up.
     *
     * The sign convention is: `reward = true` means the arm
     * did its job (user backed out), `reward = false` means
     * it did not (user proceeded). See [FrictionBandit.update].
     */
    @Test
    fun `bandit should prefer the arm that works (causes back-out), not the arm that is clicked through`() {
        // Scenario:
        //  - FULL is consistently effective: 20 plays, every
        //    time the user backs out within 60s.
        //    reward = true on every FULL play → alpha += 1.
        //  - BRIEF is consistently ineffective: 20 plays,
        //    every time the user proceeds past the gate.
        //    reward = false on every BRIEF play → beta += 1.
        // Expected: the bandit's chosen arm, on a
        // deterministic path (force the floor out of the
        // way), is FULL — the arm that is doing the job.
        val s0 = FrictionBandit.BanditState()
        var s = s0
        repeat(20) {
            s = FrictionBandit.observe(s, FrictionBandit.ArmChoice.FULL, reward = true)
            s = FrictionBandit.observe(s, FrictionBandit.ArmChoice.BRIEF, reward = false)
        }
        val ctx = FrictionBandit.Context(
            recentAbandonRateBucket = 0,
            timeOfDayBucket = 1,
            insideSleepWindow = 0,
        )
        // Force the deterministic path. We need a random
        // whose first nextDouble() returns ≥ 0.10. We probe
        // a few seeds and pick one that lands above the
        // floor. The point of the test is the posterior, not
        // the seed.
        val seed = (0L..1000L).first { seed ->
            kotlin.random.Random(seed).nextDouble() >= FrictionBandit.EXPLORATION_FLOOR
        }
        val choice = FrictionBandit.choose(s, ctx, random = Random(seed))
        assertEquals(
            FrictionBandit.ArmChoice.FULL,
            choice,
        )
    }
}
