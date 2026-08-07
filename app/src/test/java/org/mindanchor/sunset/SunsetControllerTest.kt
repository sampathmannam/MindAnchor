package org.mindanchor.sunset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.data.SunsetPrefs
import java.time.LocalTime

class SunsetControllerTest {

    private val start = LocalTime.of(22, 0)
    private val end = LocalTime.of(7, 0)

    @Test
    fun `overnight window covers late evening and early morning`() {
        assertTrue(SunsetController.isInWindow(LocalTime.of(23, 30), start, end))
        assertTrue(SunsetController.isInWindow(LocalTime.of(3, 0), start, end))
        assertTrue(SunsetController.isInWindow(LocalTime.of(22, 0), start, end))
    }

    @Test
    fun `overnight window excludes daytime`() {
        assertFalse(SunsetController.isInWindow(LocalTime.of(12, 0), start, end))
        assertFalse(SunsetController.isInWindow(LocalTime.of(7, 0), start, end))
        assertFalse(SunsetController.isInWindow(LocalTime.of(21, 59), start, end))
    }

    @Test
    fun `same-day window works too`() {
        val dayStart = LocalTime.of(9, 0)
        val dayEnd = LocalTime.of(17, 0)
        assertTrue(SunsetController.isInWindow(LocalTime.of(12, 0), dayStart, dayEnd))
        assertFalse(SunsetController.isInWindow(LocalTime.of(18, 0), dayStart, dayEnd))
    }

    @Test
    fun `the controller and the prefs agree on what inside the window means`() {
        // They are one implementation now, and this is what keeps them one:
        // the day somebody re-inlines the naive form into either, this fails.
        listOf(
            LocalTime.of(0, 0), LocalTime.of(3, 0), LocalTime.of(6, 59),
            LocalTime.of(7, 0), LocalTime.of(12, 0), LocalTime.of(21, 59),
            LocalTime.of(22, 0), LocalTime.of(23, 59),
        ).forEach { now ->
            assertEquals(
                "disagreement at $now",
                SunsetController.isInWindow(now, start, end),
                SunsetPrefs.isInWindow(now, start, end),
            )
        }
    }

    @Test
    fun `quiet hours are the configured window, and the boundaries fall the right way`() {
        // Start is inclusive, end exclusive — so 07:00 is morning, not
        // still night. Friction stays FULL right up to that minute.
        assertTrue(SunsetPrefs.isQuietHour(LocalTime.of(22, 0)))
        assertTrue(SunsetPrefs.isQuietHour(LocalTime.of(2, 0)))
        assertTrue(SunsetPrefs.isQuietHour(LocalTime.of(6, 59)))
        assertFalse(SunsetPrefs.isQuietHour(LocalTime.of(7, 0)))
        assertFalse(SunsetPrefs.isQuietHour(LocalTime.of(21, 59)))
        assertFalse(SunsetPrefs.isQuietHour(LocalTime.of(15, 0)))
    }
}
