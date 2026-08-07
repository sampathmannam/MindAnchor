package org.mindanchor.friction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The rules here are about when *not* to speak. A suggestion landing at
 * the wrong moment hands somebody one more thing to have failed at.
 */
class SmallThingsTest {

    private val things = listOf("Two minutes outside", "Text Priya", "Put the kettle on")

    @Test
    fun `a small thing is offered on the first reach`() {
        assertEquals(
            "Two minutes outside",
            SmallThings.offer(things, 0, FrictionTone.FULL, quietHours = false),
        )
    }

    @Test
    fun `nothing is offered on the fourth reach in ten minutes`() {
        // FEATHER means somebody is having a hard time right now. That is
        // the exact moment not to ask whether they have tried a walk.
        assertNull(SmallThings.offer(things, 3, FrictionTone.FEATHER, quietHours = false))
    }

    @Test
    fun `nothing is offered in the quiet hours`() {
        // At three in the morning there is no small thing, and offering
        // one is a tool that has not noticed what time it is.
        assertNull(SmallThings.offer(things, 0, FrictionTone.FULL, quietHours = true))
        assertNull(SmallThings.offer(things, 1, FrictionTone.BRIEF, quietHours = true))
    }

    @Test
    fun `nothing is offered when the person has named nothing`() {
        // Only their own words, ever. No defaults, no starter suggestions.
        assertNull(SmallThings.offer(emptyList(), 0, FrictionTone.FULL, quietHours = false))
        assertNull(SmallThings.offer(listOf("", "   "), 0, FrictionTone.FULL, quietHours = false))
    }

    @Test
    fun `the offer rotates so one line does not become wallpaper`() {
        val seen = (0..5).map {
            SmallThings.offer(things, it, FrictionTone.BRIEF, quietHours = false)
        }
        assertEquals(things + things, seen)
    }

    @Test
    fun `a negative count does not divide by zero on somebody's home screen`() {
        assertEquals(
            "Two minutes outside",
            SmallThings.offer(things, -1, FrictionTone.FULL, quietHours = false),
        )
    }

    @Test
    fun `adding trims, deduplicates and caps`() {
        assertEquals(listOf("Walk"), SmallThings.add(emptyList(), "  Walk  "))
        assertEquals(listOf("Walk"), SmallThings.add(listOf("Walk"), "walk"))
        assertEquals(listOf("Walk"), SmallThings.add(listOf("Walk"), "   "))
        val full = (1..SmallThings.MAX).map { "thing $it" }
        val over = SmallThings.add(full, "one more")
        assertEquals(SmallThings.MAX, over.size)
        assertEquals("one more", over.last())
    }

    @Test
    fun `things survive a round trip and stray newlines cannot make an empty offer`() {
        assertEquals(things, SmallThings.decode(SmallThings.encode(things)))
        assertEquals(listOf("a", "b"), SmallThings.decode("\n a \n\n\n b \n"))
        assertEquals(emptyList<String>(), SmallThings.decode("\n\n   \n"))
    }
}
