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
