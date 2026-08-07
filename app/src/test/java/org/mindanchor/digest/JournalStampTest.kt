package org.mindanchor.digest

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * When a journal entry has to show its day.
 *
 * The journal is the batcher's evidence that nothing was lost, and it
 * used to print `HH:mm` on every row. As soon as anything had been
 * released the list read 08:19, 07:42, 07:05, 14:55 — today's held
 * entries followed by yesterday's delivered ones, with the day boundary
 * invisible, so the clock looked like it ran backwards.
 */
class JournalStampTest {

    private val zone = ZoneId.of("Europe/London")
    private val today = LocalDate.of(2026, 8, 8)

    private fun at(date: LocalDate, hour: Int, minute: Int): Long =
        LocalDateTime.of(date, java.time.LocalTime.of(hour, minute))
            .atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `today shows the time alone`() {
        val (time, day) = journalStamp(at(today, 7, 5), zone, today)
        assertEquals("07:05", time)
        assertNull(day)
    }

    @Test
    fun `yesterday carries its day`() {
        val (time, day) = journalStamp(at(today.minusDays(1), 14, 55), zone, today)
        assertEquals("14:55", time)
        assertEquals("Fri 7 Aug", day)
    }

    @Test
    fun `one minute either side of midnight lands on the right day`() {
        // The off-by-one this helper exists to prevent.
        val (_, lastNight) = journalStamp(at(today.minusDays(1), 23, 59), zone, today)
        assertEquals("Fri 7 Aug", lastNight)

        val (_, thisMorning) = journalStamp(at(today, 0, 0), zone, today)
        assertNull(thisMorning)
    }

    @Test
    fun `the day comes from the reader's zone, not from UTC`() {
        // 23:30 in London on the 7th is 22:30 UTC on the 7th, but the same
        // instant is already the 8th in Kolkata. Whether a row is dated
        // must follow the phone the person is holding.
        val instant = at(today.minusDays(1), 23, 30)
        val kolkata = ZoneId.of("Asia/Kolkata")

        assertEquals("Fri 7 Aug", journalStamp(instant, zone, today).second)
        assertNull(journalStamp(instant, kolkata, today).second)
    }
}
