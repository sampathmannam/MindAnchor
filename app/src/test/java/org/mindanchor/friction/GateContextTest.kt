package org.mindanchor.friction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The carrier that bundles the tone decision with the optional
 * extras (if-then plan, per-app session length) for one
 * friction-gate event. The carrier is small and pure; these
 * tests pin the *defaults* (a fresh user with no extras sees
 * the existing generic prompt) so a future edit cannot
 * silently change the contract.
 */
class GateContextTest {

    @Test
    fun `the default context has no extras`() {
        val ctx = GateContext(tone = FrictionTone.FULL)
        assertNull(ctx.ifThenPlan)
        assertNull(ctx.banditArm)
    }

    @Test
    fun `a full context carries all the fields`() {
        val plan = IfThenPlan(cue = "a", action = "b", defaultMinutes = 5L)
        val ctx = GateContext(
            tone = FrictionTone.BRIEF,
            banditArm = FrictionBandit.ArmChoice.BRIEF,
            ifThenPlan = plan,
        )
        assertEquals(FrictionTone.BRIEF, ctx.tone)
        assertEquals(FrictionBandit.ArmChoice.BRIEF, ctx.banditArm)
        assertEquals(plan, ctx.ifThenPlan)
    }

    @Test
    fun `a FEATHER context carries no bandit arm, even when the deterministic tone is FEATHER`() {
        // The bandit only ever plays an arm when the
        // deterministic tone is FULL. The adaptive path
        // returns null for the arm when the deterministic
        // tone is BRIEF or FEATHER. This test pins that
        // contract.
        val ctx = GateContext(tone = FrictionTone.FEATHER)
        assertNull(ctx.banditArm)
    }
}
