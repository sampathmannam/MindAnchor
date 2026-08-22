package org.mindanchor.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class BatchScheduleTest {

    @Test
    fun `before first release picks this morning`() {
        val now = LocalDateTime.of(2026, 8, 6, 6, 30).atZone(ZoneId.systemDefault())
        assertEquals(
            LocalDateTime.of(2026, 8, 6, 8, 0).atZone(ZoneId.systemDefault()),
            BatchSchedule.nextRelease(now),
        )
    }

    @Test
    fun `between releases picks the next one today`() {
        val now = LocalDateTime.of(2026, 8, 6, 9, 15).atZone(ZoneId.systemDefault())
        assertEquals(
            LocalDateTime.of(2026, 8, 6, 12, 30).atZone(ZoneId.systemDefault()),
            BatchSchedule.nextRelease(now),
        )
    }

    @Test
    fun `after last release rolls to tomorrow morning`() {
        val now = LocalDateTime.of(2026, 8, 6, 21, 0).atZone(ZoneId.systemDefault())
        assertEquals(
            LocalDateTime.of(2026, 8, 7, 8, 0).atZone(ZoneId.systemDefault()),
            BatchSchedule.nextRelease(now),
        )
    }

    @Test
    fun `exactly at a release time rolls forward not same minute`() {
        val now = LocalDateTime.of(2026, 8, 6, 12, 30).atZone(ZoneId.systemDefault())
        assertEquals(
            LocalDateTime.of(2026, 8, 6, 18, 0).atZone(ZoneId.systemDefault()),
            BatchSchedule.nextRelease(now),
        )
    }

    @Test
    fun `custom times are respected`() {
        val now = LocalDateTime.of(2026, 8, 6, 10, 0).atZone(ZoneId.systemDefault())
        assertEquals(
            LocalDateTime.of(2026, 8, 6, 20, 0).atZone(ZoneId.systemDefault()),
            BatchSchedule.nextRelease(now, listOf(LocalTime.of(20, 0), LocalTime.of(9, 0))),
        )
    }

    // --- moving a release time ---

    @Test
    fun `a nudge moves one time and leaves the others alone`() {
        val moved = BatchSchedule.nudged(BatchSchedule.DEFAULT_TIMES, 1, BatchSchedule.NUDGE_MINUTES)
        assertEquals(
            listOf(LocalTime.of(8, 0), LocalTime.of(13, 0), LocalTime.of(18, 0)),
            moved,
        )
    }

    @Test
    fun `a nudge can go backwards`() {
        val moved = BatchSchedule.nudged(BatchSchedule.DEFAULT_TIMES, 0, -BatchSchedule.NUDGE_MINUTES)
        assertEquals(LocalTime.of(7, 30), moved!!.first())
    }

    @Test
    fun `a move onto another release is refused rather than stored`() {
        // Two batches at the same minute is not harmful - nextRelease
        // takes the first strictly after now, so the duplicate is simply
        // skipped - but the screen would show the same time twice and
        // there would be no way to tell three batches from two.
        val times = listOf(LocalTime.of(8, 0), LocalTime.of(8, 30), LocalTime.of(18, 0))
        assertNull(BatchSchedule.nudged(times, 0, BatchSchedule.NUDGE_MINUTES))
    }

    @Test
    fun `a move away from a collision is allowed`() {
        val times = listOf(LocalTime.of(8, 0), LocalTime.of(8, 30), LocalTime.of(18, 0))
        assertNotNull(BatchSchedule.nudged(times, 0, -BatchSchedule.NUDGE_MINUTES))
    }

    @Test
    fun `nudging past midnight wraps rather than clamping`() {
        // A night worker's batches belong in their evening whatever the
        // clock calls it - the same reasoning that lets the quiet-hours
        // window cross midnight.
        val times = listOf(LocalTime.of(23, 45), LocalTime.of(8, 0), LocalTime.of(18, 0))
        assertEquals(LocalTime.of(0, 15), BatchSchedule.nudged(times, 0, 30)!!.first())
    }

    @Test
    fun `an index that is not there is refused rather than crashing`() {
        assertNull(BatchSchedule.nudged(BatchSchedule.DEFAULT_TIMES, 3, 30))
        assertNull(BatchSchedule.nudged(BatchSchedule.DEFAULT_TIMES, -1, 30))
    }

    @Test
    fun `the shipped default is usable`() {
        assertTrue(BatchSchedule.isUsable(BatchSchedule.DEFAULT_TIMES))
        assertEquals(BatchSchedule.SLOTS, BatchSchedule.DEFAULT_TIMES.size)
    }

    @Test
    fun `a stored set that is short or duplicated is not usable`() {
        // Discarded in favour of the default rather than repaired: a
        // corrupt preference should cost somebody their setting, never
        // their notifications.
        assertFalse(BatchSchedule.isUsable(listOf(LocalTime.of(8, 0))))
        assertFalse(
            BatchSchedule.isUsable(
                listOf(LocalTime.of(8, 0), LocalTime.of(8, 0), LocalTime.of(18, 0)),
            ),
        )
    }

    @Test
    fun `a nudged set is still usable, so it can always be stored`() {
        var times = BatchSchedule.DEFAULT_TIMES
        repeat(20) { step ->
            times = BatchSchedule.nudged(times, step % BatchSchedule.SLOTS, BatchSchedule.NUDGE_MINUTES)
                ?: times
            assertTrue("a nudge produced a set that could not be stored: $times", BatchSchedule.isUsable(times))
        }
    }

    @Test
    fun `moved times are what the next release is computed from`() {
        val times = listOf(LocalTime.of(9, 0), LocalTime.of(14, 0), LocalTime.of(21, 0))
        assertEquals(
            LocalDateTime.of(2026, 8, 6, 14, 0).atZone(ZoneId.systemDefault()),
            BatchSchedule.nextRelease(
                LocalDateTime.of(2026, 8, 6, 12, 0).atZone(ZoneId.systemDefault()),
                times,
            ),
        )
    }
}
