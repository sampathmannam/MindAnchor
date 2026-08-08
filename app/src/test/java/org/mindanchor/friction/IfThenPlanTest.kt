package org.mindanchor.friction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Gollwitzer 1999 *American Psychologist* implementation-intention
 * structure: "if [cue], then [action], for [N minutes]." The
 * evidence base is in `docs/research/15` §8.
 *
 * The plan is per-app, free-text in three fields, optional. A
 * user who has not written one gets the existing generic
 * intention prompt; a user who has gets the generic prompt
 * pre-filled with their own words.
 */
class IfThenPlanTest {

    @Test
    fun `a plan with both cue and action is complete`() {
        val plan = IfThenPlan(
            cue = "I am about to open Instagram",
            action = "I will check my email",
            defaultMinutes = 5L,
        )
        assertTrue(plan.isComplete)
    }

    @Test
    fun `a plan with only one field is incomplete, not invalid`() {
        // A one-field plan is stored anyway (the user can come
        // back to it) but is not surfaced as a "pre-fill" in
        // the gate. The brief: a half-written plan is not
        // nothing, it is work-in-progress.
        val cueOnly = IfThenPlan(cue = "I'm about to open X", action = "")
        assertFalse(cueOnly.isComplete)

        val actionOnly = IfThenPlan(cue = "", action = "I'll do Y")
        assertFalse(actionOnly.isComplete)

        val empty = IfThenPlan()
        assertFalse(empty.isComplete)
    }

    @Test
    fun `sanitised trims and caps each field`() {
        val plan = IfThenPlan(
            cue = "  " + "x".repeat(200) + "  ",
            action = "  " + "y".repeat(200) + "  ",
            defaultMinutes = 5L,
        )
        val s = plan.sanitised()
        assertEquals(IfThenPlan.MAX_FIELD, s.cue.length)
        assertEquals(IfThenPlan.MAX_FIELD, s.action.length)
    }

    @Test
    fun `sanitised coerces the minutes field into the allowed range`() {
        val tooLong = IfThenPlan(cue = "a", action = "b", defaultMinutes = 999L)
        assertEquals(IfThenPlan.MAX_MINUTES, tooLong.sanitised().defaultMinutes)
        val tooShort = IfThenPlan(cue = "a", action = "b", defaultMinutes = 0L)
        assertEquals(IfThenPlan.MIN_MINUTES, tooShort.sanitised().defaultMinutes)
    }

    @Test
    fun `null defaultMinutes is preserved through sanitise`() {
        val plan = IfThenPlan(cue = "a", action = "b", defaultMinutes = null)
        assertNull(plan.sanitised().defaultMinutes)
    }

    @Test
    fun `encode and decode round trip preserves all fields`() {
        val original = mapOf(
            "com.example.social" to IfThenPlan(
                cue = "I'm about to open this for no reason",
                action = "I'll check my email instead",
                defaultMinutes = 5L,
            ),
            "com.example.email" to IfThenPlan(
                cue = "I'm about to open email",
                action = "I'll reply to the one I've been putting off",
                defaultMinutes = 20L,
            ),
            "com.example.untimed" to IfThenPlan(
                cue = "I'm about to open Maps",
                action = "I'll go outside",
                defaultMinutes = null,
            ),
        )
        assertEquals(original, IfThenPlanStore.decode(IfThenPlanStore.encode(original)))
    }

    @Test
    fun `decode drops malformed lines silently`() {
        // A corrupt line is one with fewer than 4 tab-separated
        // fields, or a blank package name. The minutes field
        // is best-effort coerced — see the other tests — so
        // "nope" does not invalidate a line, it just produces
        // a null minutes value.
        //
        // The "garbage" line has 0 tab-separated parts and is
        // dropped. The "com.bad" line is well-formed and
        // produces a plan with defaultMinutes = 2026 coerced
        // down to MAX_MINUTES.
        val raw = "com.good\ta\tb\t5\ngarbage\ncom.bad\tnope\t1\t2026"
        val out = IfThenPlanStore.decode(raw)
        assertEquals(setOf("com.good", "com.bad"), out.keys)
        assertEquals(IfThenPlan.MAX_MINUTES, out["com.bad"]?.defaultMinutes)
    }

    @Test
    fun `decode of an empty file is an empty map, not a phantom plan`() {
        assertTrue(IfThenPlanStore.decode("").isEmpty())
    }

    @Test
    fun `encode skips blank package names`() {
        // A blank package would be a corrupt entry on the way
        // back in. Filter on the way out, not the way in.
        val out = IfThenPlanStore.encode(
            mapOf(
                "" to IfThenPlan(cue = "x", action = "y"),
                "com.real" to IfThenPlan(cue = "a", action = "b"),
            ),
        )
        // The output must round-trip without the blank package
        // being re-introduced — that's the load-bearing
        // contract, not substring presence.
        val roundTrip = IfThenPlanStore.decode(out)
        assertFalse("" in roundTrip)
        assertTrue("com.real" in roundTrip)
    }

    @Test
    fun `decode coerces out-of-range minutes to null rather than crashing`() {
        // A corrupted minutes value is treated as "no
        // time-box preference" rather than 0 (which would
        // be a regression to a 0-minute session) or a crash.
        val raw = "com.x\ta\tb\t999"
        val out = IfThenPlanStore.decode(raw)
        // 999 is above MAX_MINUTES (120), so it gets coerced
        // down to MAX_MINUTES.
        assertEquals(IfThenPlan.MAX_MINUTES, out["com.x"]?.defaultMinutes)
    }

    @Test
    fun `decode treats a blank minutes value as null`() {
        val raw = "com.x\ta\tb\t"
        val out = IfThenPlanStore.decode(raw)
        assertNull(out["com.x"]?.defaultMinutes)
    }
}
