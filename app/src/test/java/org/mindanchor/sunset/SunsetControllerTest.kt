package org.mindanchor.sunset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun `the default window's boundaries fall the right way`() {
        // Start is inclusive, end exclusive — so 07:00 is morning, not
        // still night. Friction stays FULL right up to that minute.
        val quiet = { t: LocalTime ->
            SunsetPrefs.isInWindow(t, SunsetPrefs.DEFAULT_START, SunsetPrefs.DEFAULT_END)
        }
        assertTrue(quiet(LocalTime.of(22, 0)))
        assertTrue(quiet(LocalTime.of(2, 0)))
        assertTrue(quiet(LocalTime.of(6, 59)))
        assertFalse(quiet(LocalTime.of(7, 0)))
        assertFalse(quiet(LocalTime.of(21, 59)))
        assertFalse(quiet(LocalTime.of(15, 0)))
    }

    @Test
    fun `a night worker's daytime window behaves, where the naive form would not`() {
        // 09:00 to 17:00 for somebody who sleeps through the day. The
        // naive "now >= start || now < end" answers true at every hour of
        // the clock for this window; that is the bug the shared helper
        // exists to prevent, and it stops being hypothetical the moment
        // these times became editable.
        val day = LocalTime.of(9, 0)
        val evening = LocalTime.of(17, 0)
        assertTrue(SunsetPrefs.isInWindow(LocalTime.of(12, 0), day, evening))
        assertFalse(SunsetPrefs.isInWindow(LocalTime.of(3, 0), day, evening))
        assertFalse(SunsetPrefs.isInWindow(LocalTime.of(23, 0), day, evening))
        assertFalse(SunsetPrefs.isInWindow(LocalTime.of(17, 0), day, evening))
    }

    @Test
    fun `a stored minute-of-day reads back as the time it was`() {
        assertEquals(LocalTime.of(22, 30), SunsetPrefs.timeOf(22 * 60 + 30, SunsetPrefs.DEFAULT_START))
        assertEquals(LocalTime.of(0, 0), SunsetPrefs.timeOf(0, SunsetPrefs.DEFAULT_START))
        assertEquals(LocalTime.of(23, 59), SunsetPrefs.timeOf(1439, SunsetPrefs.DEFAULT_START))
    }

    @Test
    fun `a missing or impossible stored time falls back rather than throwing`() {
        // A corrupt preference must cost somebody their bedtime setting,
        // not their launcher. LocalTime.of would throw on any of these.
        listOf(null, -1, 1440, 99999).forEach { stored ->
            assertEquals(
                "stored=$stored",
                SunsetPrefs.DEFAULT_START,
                SunsetPrefs.timeOf(stored, SunsetPrefs.DEFAULT_START),
            )
        }
    }

    @Test
    fun `a window with both ends equal is refused`() {
        // It reads as "all day" and behaves as "never", and the person
        // would have no way to tell which they had asked for.
        assertFalse(SunsetPrefs.isValidWindow(LocalTime.of(22, 0), LocalTime.of(22, 0)))
        assertTrue(SunsetPrefs.isValidWindow(LocalTime.of(22, 0), LocalTime.of(7, 0)))
        assertTrue(SunsetPrefs.isValidWindow(LocalTime.of(9, 0), LocalTime.of(17, 0)))
    }

    @Test
    fun `nudging past midnight wraps instead of clamping`() {
        // The stepper adds minutes to a LocalTime. Somebody moving a 00:15
        // bedtime half an hour earlier means 23:45, not 00:00.
        assertEquals(LocalTime.of(23, 45), LocalTime.of(0, 15).plusMinutes(-30))
        assertEquals(LocalTime.of(0, 15), LocalTime.of(23, 45).plusMinutes(30))
    }

    @Test
    fun `setChronotype returns a default window when the user has not set one`() {
        // First run / never-touched-by-hand: a chronotype set should
        // also install that chronotype's default window. Otherwise
        // the onboarding answer would have no visible effect and the
        // user would assume the question was decorative.
        assertEquals(
            Chronotype.NIGHT_OWL.defaultWindow(),
            SunsetPrefs.newWindowFor(Chronotype.NIGHT_OWL, isWindowCustomized = false),
        )
        assertEquals(
            Chronotype.SHIFT_WORKER.defaultWindow(),
            SunsetPrefs.newWindowFor(Chronotype.SHIFT_WORKER, isWindowCustomized = false),
        )
    }

    @Test
    fun `setChronotype does not stomp a window the user has set by hand`() {
        // The whole point of the guard: someone picked 21:30 six
        // months ago, and is now answering the chronotype question
        // for unrelated reasons. Returning null from newWindowFor
        // means setChronotype leaves the stored window alone, and
        // the user's bedtime does not move without warning.
        assertNull(SunsetPrefs.newWindowFor(Chronotype.NIGHT_OWL, isWindowCustomized = true))
        assertNull(SunsetPrefs.newWindowFor(Chronotype.SHIFT_WORKER, isWindowCustomized = true))
        assertNull(SunsetPrefs.newWindowFor(Chronotype.NEUTRAL, isWindowCustomized = true))
    }
}
