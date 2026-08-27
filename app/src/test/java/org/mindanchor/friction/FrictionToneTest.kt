package org.mindanchor.friction

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The gate decides how hard to push, and getting that wrong in either
 * direction has a cost: too soft and it is decoration, too hard and it
 * teaches people to swipe through anything this app ever shows them.
 */
class FrictionToneTest {

    @Test
    fun `the first reach gets the full pause`() {
        assertEquals(FrictionTone.FULL, FrictionContext.toneFor(0, insideSleepWindow = false))
    }

    @Test
    fun `a second reach soon after drops the breath but keeps the question`() {
        // The first pause plainly did not settle it. Repeating the whole
        // ceremony would be theatre; the question still has something to ask.
        assertEquals(FrictionTone.BRIEF, FrictionContext.toneFor(1, insideSleepWindow = false))
        assertEquals(FrictionTone.BRIEF, FrictionContext.toneFor(2, insideSleepWindow = false))
    }

    @Test
    fun `by the fourth reach it stops asking`() {
        // Anything more here is training someone to dismiss this app.
        assertEquals(FrictionTone.FEATHER, FrictionContext.toneFor(3, insideSleepWindow = false))
        assertEquals(FrictionTone.FEATHER, FrictionContext.toneFor(9, insideSleepWindow = false))
    }

    // --- v0.70.0 AnchorCore Hook B ---
    // On a flagged week, the ceremony holds its weight one reach
    // longer (1/3 → 2/5). The reasoning is in
    // FrictionTone.kt: "Repetition inside a hard week is more
    // likely the loop talking than weak resolve." The original
    // test file did not exercise the weekFlagged parameter at
    // all — the per-hook toggle wiring in
    // LauncherViewModel/FrictionViewModel passes it, but if the
    // decision ever regressed (e.g. the constants flip back) the
    // test would have stayed green.
    //
    // This is the Robolectric-level assertion Hook B never had
    // (#7 in the TestGuild QA tool chain).
    @Test
    fun `flagged week holds full one reach longer`() {
        // First reach: still FULL on a flagged week (not BRIEF).
        assertEquals(
            FrictionTone.FULL,
            FrictionContext.toneFor(1, insideSleepWindow = false, weekFlagged = true),
        )
        // Second reach: BRIEF (one step past the 2-reach threshold).
        assertEquals(
            FrictionTone.BRIEF,
            FrictionContext.toneFor(2, insideSleepWindow = false, weekFlagged = true),
        )
        // Third reach: still BRIEF (one short of the 5-reach
        // threshold), not FEATHER.
        assertEquals(
            FrictionTone.BRIEF,
            FrictionContext.toneFor(4, insideSleepWindow = false, weekFlagged = true),
        )
        // Fifth reach: FEATHER.
        assertEquals(
            FrictionTone.FEATHER,
            FrictionContext.toneFor(5, insideSleepWindow = false, weekFlagged = true),
        )
    }

    @Test
    fun `flagged week does not override the sleep window`() {
        // The sleep-window rule wins over the flagged-week rule
        // (FrictionTone.kt: "The sleep window still wins over
        // everything, exactly as before"). The Hook B shift
        // must not soften the lateness hold.
        assertEquals(
            FrictionTone.FULL,
            FrictionContext.toneFor(5, insideSleepWindow = true, weekFlagged = true),
        )
    }

    @Test
    fun `default weekFlagged preserves the original ladder`() {
        // The plan's HARD rule: a regression that defaulted
        // weekFlagged to true would change every gate-tap for
        // every user. This pins the default-value path.
        assertEquals(
            FrictionTone.BRIEF,
            FrictionContext.toneFor(1, insideSleepWindow = false),
        )
        assertEquals(
            FrictionTone.FEATHER,
            FrictionContext.toneFor(3, insideSleepWindow = false),
        )
    }

    @Test
    fun `inside the sleep window the pause keeps its full weight however many times`() {
        // Sleepiness lowers reactance, so this is when a pause is most
        // likely to be accepted — and the fourth reach at 2am is precisely
        // the case bedtime procrastination describes.
        for (opens in 0..9) {
            assertEquals(
                "reach $opens inside the sleep window",
                FrictionTone.FULL,
                FrictionContext.toneFor(opens, insideSleepWindow = true),
            )
        }
    }

    @Test
    fun `the tone never hardens as reaching repeats`() {
        // Escalation is the intuitive design and the wrong one: reactance
        // rises with repetition, so pushing harder each time is how an
        // intervention earns itself ignored.
        val order = listOf(FrictionTone.FEATHER, FrictionTone.BRIEF, FrictionTone.FULL)
        var previous = Int.MAX_VALUE
        for (opens in 0..12) {
            val rank = order.indexOf(FrictionContext.toneFor(opens, insideSleepWindow = false))
            assert(rank <= previous) { "tone hardened at $opens reaches" }
            previous = rank
        }
    }

    @Test
    fun `a negative or absurd count is treated as a first reach`() {
        assertEquals(FrictionTone.FULL, FrictionContext.toneFor(-1, insideSleepWindow = false))
    }
}
