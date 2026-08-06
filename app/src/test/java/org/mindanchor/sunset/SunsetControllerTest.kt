package org.mindanchor.sunset

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
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
}
