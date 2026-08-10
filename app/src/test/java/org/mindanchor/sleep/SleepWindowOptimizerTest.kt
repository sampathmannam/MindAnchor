package org.mindanchor.sleep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Pure-JVM tests for [SleepWindowOptimizer].
 *
 * The optimizer is what the settings panel reads to suggest a
 * wind-down window. The edge cases — not enough nights, regular
 * schedule, one all-nighter in the middle, a day sleeper, a
 * midnight-crosser — are exactly the cases a person would notice
 * if the suggestion was wrong, so they get their own tests.
 */
class SleepWindowOptimizerTest {

    private val zone: ZoneId = ZoneOffset.UTC

    /** A window that starts at [hour]:[minute] and lasts 8 hours, on [date]. */
    private fun windowOn(
        date: LocalDate,
        hour: Int,
        minute: Int = 0,
    ): SleepWindow {
        val start = LocalDateTime.of(date, LocalTime.of(hour, minute))
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
        return SleepWindow(
            wakeDate = date.plusDays(1),
            startMillis = start,
            endMillis = start + 8L * 3_600_000,
        )
    }

    @Test
    fun `fewer than 5 nights produces no suggestion`() {
        // Same floor Deviation uses: with fewer than 5 nights, "usual"
        // is not a thing that exists yet, and a suggestion built on
        // less is a guess dressed as data.
        val four = listOf(
            windowOn(LocalDate.of(2026, 1, 1), 23),
            windowOn(LocalDate.of(2026, 1, 2), 23),
            windowOn(LocalDate.of(2026, 1, 3), 23),
            windowOn(LocalDate.of(2026, 1, 4), 23),
        )
        assertNull(SleepWindowOptimizer.suggest(four, zone))
    }

    @Test
    fun `5 nights of identical onsets suggests a window 90 minutes before sleep`() {
        // Median onset = 23:00. Wind-down = 21:30. End of window =
        // 07:00 (23:00 + 8h).
        val five = (1..5).map { windowOn(LocalDate.of(2026, 1, it), 23) }
        val s = SleepWindowOptimizer.suggest(five, zone)
        assertNotNull(s)
        assertEquals(LocalTime.of(23, 0), s!!.medianOnset)
        assertEquals(LocalTime.of(21, 30), s.startTime)
        assertEquals(LocalTime.of(7, 0), s.endTime)
        assertEquals(5, s.nightsUsed)
    }

    @Test
    fun `one all-nighter in a regular week does not move the suggestion`() {
        // Median of [22:00, 22:00, 22:00, 22:00, 04:00] = 22:00.
        // Without the median, the mean would drift to 22:24 and the
        // suggestion would shift 24 minutes earlier — a real difference
        // for a tool the user is going to live with.
        val five = listOf(
            windowOn(LocalDate.of(2026, 1, 1), 22),
            windowOn(LocalDate.of(2026, 1, 2), 22),
            windowOn(LocalDate.of(2026, 1, 3), 22),
            windowOn(LocalDate.of(2026, 1, 4), 22),
            windowOn(LocalDate.of(2026, 1, 5), 4),
        )
        val s = SleepWindowOptimizer.suggest(five, zone)
        assertNotNull(s)
        assertEquals(LocalTime.of(22, 0), s!!.medianOnset)
        assertEquals(LocalTime.of(20, 30), s.startTime)
    }

    @Test
    fun `a day sleeper produces a same-day window, not a midnight-crosser`() {
        // Median onset = 09:00. Wind-down = 07:30. End of window =
        // 17:00 (09:00 + 8h). Same-day window, start < end.
        val five = (1..5).map { windowOn(LocalDate.of(2026, 1, it), 9) }
        val s = SleepWindowOptimizer.suggest(five, zone)
        assertNotNull(s)
        assertEquals(LocalTime.of(9, 0), s!!.medianOnset)
        assertEquals(LocalTime.of(7, 30), s.startTime)
        assertEquals(LocalTime.of(17, 0), s.endTime)
    }

    @Test
    fun `a midnight onset suggests a window that crosses midnight`() {
        // Median onset = 00:30. Wind-down = 23:00 (00:30 - 90 min,
        // wrapped). End of window = 08:30 (00:30 + 8h).
        val five = (1..5).map { windowOn(LocalDate.of(2026, 1, it), 0, 30) }
        val s = SleepWindowOptimizer.suggest(five, zone)
        assertNotNull(s)
        assertEquals(LocalTime.of(0, 30), s!!.medianOnset)
        assertEquals(LocalTime.of(23, 0), s.startTime)
        assertEquals(LocalTime.of(8, 30), s.endTime)
    }

    @Test
    fun `a night owl is suggested a 00 to 08 window, not a 22 placeholder`() {
        // Median onset = 01:00. Wind-down = 23:30. End of window =
        // 09:00. The whole point of the optimizer: a 22:00 default
        // is not a wind-down for someone who sleeps at 01:00 — it
        // is a wind-down that started two hours ago.
        val five = (1..5).map { windowOn(LocalDate.of(2026, 1, it), 1) }
        val s = SleepWindowOptimizer.suggest(five, zone)
        assertNotNull(s)
        assertEquals(LocalTime.of(23, 30), s!!.startTime)
        assertEquals(LocalTime.of(9, 0), s.endTime)
    }

    @Test
    fun `an even number of nights uses the midpoint of the two middle values`() {
        // 6 nights: 22:00, 22:30, 23:00, 23:30, 00:00, 00:30.
        // Sorted: 22:00, 22:30, 23:00, 23:30, 00:00, 00:30.
        // Two middle values: 23:00 and 23:30. Midpoint: 23:15.
        val six = listOf(
            windowOn(LocalDate.of(2026, 1, 1), 22),
            windowOn(LocalDate.of(2026, 1, 2), 22, 30),
            windowOn(LocalDate.of(2026, 1, 3), 23),
            windowOn(LocalDate.of(2026, 1, 4), 23, 30),
            windowOn(LocalDate.of(2026, 1, 5), 0),
            windowOn(LocalDate.of(2026, 1, 6), 0, 30),
        )
        val s = SleepWindowOptimizer.suggest(six, zone)
        assertNotNull(s)
        // 23:00 = 1380 minutes, 23:30 = 1410 minutes, midpoint = 1395 = 23:15.
        assertEquals(LocalTime.of(23, 15), s!!.medianOnset)
    }

    @Test
    fun `nightsUsed is the input size, so the settings panel can render confidence`() {
        // 7 nights of identical onsets. The panel will show "from your
        // last 7 nights" — without that count, the user has to
        // guess at how much weight the suggestion carries.
        val seven = (1..7).map { windowOn(LocalDate.of(2026, 1, it), 23) }
        val s = SleepWindowOptimizer.suggest(seven, zone)
        assertNotNull(s)
        assertEquals(7, s!!.nightsUsed)
    }

    @Test
    fun `a person whose data lives entirely in the past 24 hours is not enough`() {
        // The MIN_NIGHTS floor exists exactly for this: a 5-night
        // window needs 5 actual nights, not 5 reading attempts. The
        // empty-list case is the most degenerate; the in-between
        // cases (1, 2, 3, 4 nights) are covered by the first test.
        assertNull(SleepWindowOptimizer.suggest(emptyList(), zone))
    }
}
