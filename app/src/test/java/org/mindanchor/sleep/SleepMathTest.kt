package org.mindanchor.sleep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class SleepMathTest {

    private val zone: ZoneId = ZoneOffset.UTC

    private fun at(date: String, time: String): Long =
        LocalDateTime.parse("${date}T$time").toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun `longest overnight gap becomes the sleep window`() {
        val gaps = listOf(
            ScreenGap(at("2026-08-01", "21:30"), at("2026-08-01", "21:45")),
            ScreenGap(at("2026-08-01", "23:00"), at("2026-08-02", "06:45")),
        )
        val windows = SleepMath.sleepWindows(gaps, zone)
        assertEquals(1, windows.size)
        assertEquals(LocalDate.of(2026, 8, 2), windows[0].wakeDate)
        assertTrue(windows[0].durationHours > 7.0 && windows[0].durationHours < 8.0)
    }

    @Test
    fun `short gaps never count as sleep`() {
        val gaps = listOf(
            ScreenGap(at("2026-08-01", "23:00"), at("2026-08-02", "00:30")),
        )
        assertTrue(SleepMath.sleepWindows(gaps, zone).isEmpty())
    }

    @Test
    fun `after-midnight sleep belongs to the previous night`() {
        val gaps = listOf(
            ScreenGap(at("2026-08-02", "01:00"), at("2026-08-02", "08:00")),
        )
        val windows = SleepMath.sleepWindows(gaps, zone)
        assertEquals(1, windows.size)
        assertEquals(LocalDate.of(2026, 8, 2), windows[0].wakeDate)
    }

    @Test
    fun `identical nights score 100`() {
        val gaps = listOf(
            ScreenGap(at("2026-08-01", "23:00"), at("2026-08-02", "07:00")),
            ScreenGap(at("2026-08-02", "23:00"), at("2026-08-03", "07:00")),
            ScreenGap(at("2026-08-03", "23:00"), at("2026-08-04", "07:00")),
        )
        val windows = SleepMath.sleepWindows(gaps, zone)
        assertEquals(100, SleepMath.regularityScore(windows, zone))
    }

    @Test
    fun `shifted nights score lower`() {
        val gaps = listOf(
            ScreenGap(at("2026-08-01", "23:00"), at("2026-08-02", "07:00")),
            ScreenGap(at("2026-08-03", "02:00"), at("2026-08-03", "10:00")),
        )
        val windows = SleepMath.sleepWindows(gaps, zone)
        val score = SleepMath.regularityScore(windows, zone)!!
        assertTrue("expected < 60, was $score", score < 60)
    }

    @Test
    fun `one night has no score`() {
        val gaps = listOf(
            ScreenGap(at("2026-08-01", "23:00"), at("2026-08-02", "07:00")),
        )
        assertNull(SleepMath.regularityScore(SleepMath.sleepWindows(gaps, zone), zone))
    }
}
