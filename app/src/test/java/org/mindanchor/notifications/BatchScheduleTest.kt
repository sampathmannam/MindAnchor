package org.mindanchor.notifications

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class BatchScheduleTest {

    @Test
    fun `before first release picks this morning`() {
        val now = LocalDateTime.of(2026, 8, 6, 6, 30)
        assertEquals(
            LocalDateTime.of(2026, 8, 6, 8, 0),
            BatchSchedule.nextRelease(now),
        )
    }

    @Test
    fun `between releases picks the next one today`() {
        val now = LocalDateTime.of(2026, 8, 6, 9, 15)
        assertEquals(
            LocalDateTime.of(2026, 8, 6, 12, 30),
            BatchSchedule.nextRelease(now),
        )
    }

    @Test
    fun `after last release rolls to tomorrow morning`() {
        val now = LocalDateTime.of(2026, 8, 6, 21, 0)
        assertEquals(
            LocalDateTime.of(2026, 8, 7, 8, 0),
            BatchSchedule.nextRelease(now),
        )
    }

    @Test
    fun `exactly at a release time rolls forward not same minute`() {
        val now = LocalDateTime.of(2026, 8, 6, 12, 30)
        assertEquals(
            LocalDateTime.of(2026, 8, 6, 18, 0),
            BatchSchedule.nextRelease(now),
        )
    }

    @Test
    fun `custom times are respected`() {
        val now = LocalDateTime.of(2026, 8, 6, 10, 0)
        assertEquals(
            LocalDateTime.of(2026, 8, 6, 20, 0),
            BatchSchedule.nextRelease(now, listOf(LocalTime.of(20, 0), LocalTime.of(9, 0))),
        )
    }
}
