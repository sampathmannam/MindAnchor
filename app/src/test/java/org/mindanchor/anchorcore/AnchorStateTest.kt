package org.mindanchor.anchorcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AnchorStateTest {

    @Test
    fun `fewer than seven observed days warms up`() {
        assertEquals(
            AnchorState.WarmingUp(6),
            AnchorState.of(daysObserved = 6, facts = emptyList(), now = 0L),
        )
    }

    @Test
    fun `seven days with no facts is steady and unflagged`() {
        val s = AnchorState.of(daysObserved = 7, facts = emptyList(), now = 5L)
        assertTrue(s is AnchorState.Steady)
        assertEquals(false, (s as AnchorState.Steady).weekFlagged)
    }

    @Test
    fun `a fact today flags the week by default`() {
        val fact = DayFact(FactKind.LATE_NIGHT_CLUSTER, "3|300", LocalDate.of(2026, 8, 26))
        val s = AnchorState.of(daysObserved = 10, facts = listOf(fact), now = 5L)
        assertEquals(true, (s as AnchorState.Steady).weekFlagged)
    }

    @Test
    fun `an explicit hysteresis flag overrides the default`() {
        // Yesterday's fact keeps the week flagged even on a clean today.
        val s = AnchorState.of(daysObserved = 10, facts = emptyList(), weekFlagged = true, now = 5L)
        assertEquals(true, (s as AnchorState.Steady).weekFlagged)
    }

    @Test
    fun `clean streak resets on a flagged day`() {
        assertEquals(0, WeekPicture.reduce(flaggedToday = true, cleanStreak = 4))
    }

    @Test
    fun `clean streak grows on a clean day and caps at seven`() {
        assertEquals(3, WeekPicture.reduce(flaggedToday = false, cleanStreak = 2))
        assertEquals(7, WeekPicture.reduce(flaggedToday = false, cleanStreak = 7))
    }

    @Test
    fun `seven clean days unflag`() {
        var streak = 0
        repeat(7) { streak = WeekPicture.reduce(flaggedToday = false, cleanStreak = streak) }
        assertEquals(WeekPicture.CLEAN_DAYS_TO_UNFLAG, streak)
        assertEquals(false, WeekPicture.isFlagged(flaggedToday = false, cleanStreak = streak))
    }

    @Test
    fun `flagged while a fact fired today or the streak is short`() {
        assertEquals(true, WeekPicture.isFlagged(flaggedToday = true, cleanStreak = 7))
        assertEquals(true, WeekPicture.isFlagged(flaggedToday = false, cleanStreak = 6))
        assertEquals(false, WeekPicture.isFlagged(flaggedToday = false, cleanStreak = 7))
    }
}
