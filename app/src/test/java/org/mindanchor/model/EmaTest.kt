package org.mindanchor.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class EmaTest {

    private val wake = 7 * 60
    private val sleep = 22 * 60

    private fun minutes(times: List<LocalTime>) = times.map { it.hour * 60 + it.minute }

    @Test
    fun `the scales reject values outside their range`() {
        assertTrue(Moment.isValid(1))
        assertTrue(Moment.isValid(5))
        assertFalse(Moment.isValid(0))
        assertFalse(Moment.isValid(6))
    }

    @Test
    fun `it asks often while it knows nothing and rarely once it does`() {
        assertEquals(EmaSchedule.LEARNING_PROMPTS, EmaSchedule.promptsPerDay(0))
        assertEquals(EmaSchedule.LEARNING_PROMPTS, EmaSchedule.promptsPerDay(119))
        assertEquals(EmaSchedule.SETTLED_PROMPTS, EmaSchedule.promptsPerDay(120))
        assertEquals(EmaSchedule.SETTLED_PROMPTS, EmaSchedule.promptsPerDay(5000))
    }

    @Test
    fun `prompts land inside the waking window`() {
        val times = minutes(EmaSchedule.promptTimes(wake, sleep, 4, seed = 20260807))
        assertEquals(4, times.size)
        times.forEach {
            assertTrue("prompt at $it outside $wake..$sleep", it in wake until sleep)
        }
    }

    @Test
    fun `prompts are never crowded together`() {
        val times = minutes(EmaSchedule.promptTimes(wake, sleep, 4, seed = 42))
        times.zipWithNext().forEach { (a, b) ->
            assertTrue("gap of ${b - a} between $a and $b", b - a >= EmaSchedule.MIN_GAP_MINUTES)
        }
    }

    @Test
    fun `the same seed gives the same day, so tests and reality agree`() {
        assertEquals(
            EmaSchedule.promptTimes(wake, sleep, 4, seed = 7),
            EmaSchedule.promptTimes(wake, sleep, 4, seed = 7),
        )
    }

    @Test
    fun `different days are scattered differently`() {
        // The point of the scatter: a person who learns the schedule
        // starts composing the answer before the question arrives.
        val a = EmaSchedule.promptTimes(wake, sleep, 4, seed = 1)
        val b = EmaSchedule.promptTimes(wake, sleep, 4, seed = 2)
        val c = EmaSchedule.promptTimes(wake, sleep, 4, seed = 3)
        assertTrue("all three days identical", !(a == b && b == c))
    }

    @Test
    fun `a short waking window gets fewer prompts, but never none`() {
        // A shift worker awake for three hours. Crowding four prompts in
        // would make the app the interruption it exists to reduce — but
        // an earlier version refused outright and gave them nothing,
        // which means no labels, which means no model at all for exactly
        // the people whose hours are already awkward.
        val shortWindow = EmaSchedule.promptTimes(
            wakeMinute = 13 * 60,
            sleepMinute = 16 * 60,
            count = 4,
            seed = 9,
        )
        assertTrue("expected fewer than 4, got ${shortWindow.size}", shortWindow.size < 4)
        assertTrue("a short window must still get asked something", shortWindow.isNotEmpty())
        minutes(shortWindow).zipWithNext().forEach { (a, b) ->
            assertTrue("crowded: gap ${b - a}", b - a >= EmaSchedule.MIN_GAP_MINUTES)
        }
    }

    @Test
    fun `even a very short window is handled without crowding or crashing`() {
        listOf(30, 60, 75, 76, 150, 151).forEach { windowMinutes ->
            val times = EmaSchedule.promptTimes(600, 600 + windowMinutes, 4, seed = 3)
            minutes(times).zipWithNext().forEach { (a, b) ->
                assertTrue(
                    "window $windowMinutes crowded prompts ${b - a} apart",
                    b - a >= EmaSchedule.MIN_GAP_MINUTES,
                )
            }
        }
    }

    @Test
    fun `an inverted or empty window asks nothing rather than throwing`() {
        assertTrue(EmaSchedule.promptTimes(22 * 60, 7 * 60, 4, seed = 1).isEmpty())
        assertTrue(EmaSchedule.promptTimes(wake, wake, 4, seed = 1).isEmpty())
        assertTrue(EmaSchedule.promptTimes(wake, sleep, 0, seed = 1).isEmpty())
        assertTrue(EmaSchedule.promptTimes(wake, sleep, -1, seed = 1).isEmpty())
    }

    @Test
    fun `times are always legal clock values`() {
        // A scatter that overflowed past midnight used to be possible;
        // LocalTime.of throws on hour 24 and that would be a crash on
        // somebody's home screen.
        (1..200).forEach { seed ->
            EmaSchedule.promptTimes(wake, sleep, 4, seed).forEach { t ->
                assertTrue(t.hour in 0..23)
                assertTrue(t.minute in 0..59)
            }
        }
    }

    @Test
    fun `a settled schedule still asks once`() {
        val times = EmaSchedule.promptTimes(wake, sleep, EmaSchedule.SETTLED_PROMPTS, seed = 5)
        assertEquals(1, times.size)
    }
}
