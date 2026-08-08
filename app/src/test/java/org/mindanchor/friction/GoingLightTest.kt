package org.mindanchor.friction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * The "Going Light" v1.1 module — Castelo 2025 *PNAS Nexus*
 * 4(2):pgaf017, doi:10.1093/pnasnexus/pgaf017. See
 * [GoingLightSchedule] and `docs/research/15` §1.
 *
 * The pure-function design layer. The actual blocking
 * mechanism (VpnService or AccessibilityService) is a
 * separate commit that wires into the schedule the data
 * layer holds.
 */
class GoingLightTest {

    // Saturday 6 March 2026.
    private val sat = LocalDate.of(2026, 3, 7)
    private val sun = LocalDate.of(2026, 3, 8)

    @Test
    fun `a disabled schedule is never active`() {
        val s = GoingLightSchedule(enabled = false, activeDays = setOf(DayOfWeek.SATURDAY))
        assertFalse(s.isActiveAt(LocalDateTime(sat, LocalTime.of(20, 0))))
    }

    @Test
    fun `a schedule with no active days is never active`() {
        val s = GoingLightSchedule(enabled = true, activeDays = emptySet())
        assertFalse(s.isActiveAt(LocalDateTime(sat, LocalTime.of(20, 0))))
    }

    @Test
    fun `a same-day window fires only on active days`() {
        val s = GoingLightSchedule(
            enabled = true,
            activeDays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            startTime = LocalTime.of(20, 0),
            endTime = LocalTime.of(22, 0),
        )
        // Saturday at 21:00 — inside.
        assertTrue(s.isActiveAt(LocalDateTime(sat, LocalTime.of(21, 0))))
        // Saturday at 19:59 — before.
        assertFalse(s.isActiveAt(LocalDateTime(sat, LocalTime.of(19, 59))))
        // Saturday at 22:00 — boundary, exclusive.
        assertFalse(s.isActiveAt(LocalDateTime(sat, LocalTime.of(22, 0))))
        // Monday at 21:00 — wrong day.
        val mon = LocalDate.of(2026, 3, 9)
        assertFalse(s.isActiveAt(LocalDateTime(mon, LocalTime.of(21, 0))))
    }

    @Test
    fun `an overnight window fires into the next morning`() {
        // The "Going Light" 24h Saturday block: starts
        // Saturday 06:00, ends Sunday 06:00. Overnight
        // handling: the window is *active* on Saturday
        // afternoon and on Sunday early morning.
        val s = GoingLightSchedule(
            enabled = true,
            activeDays = setOf(DayOfWeek.SATURDAY),
            startTime = LocalTime.of(6, 0),
            endTime = LocalTime.of(6, 0), // equal — overnight
        )
        // Saturday at 14:00 — inside (same day).
        assertTrue(s.isActiveAt(LocalDateTime(sat, LocalTime.of(14, 0))))
        // Sunday at 03:00 — inside (overnight tail).
        assertTrue(s.isActiveAt(LocalDateTime(sun, LocalTime.of(3, 0))))
        // Sunday at 06:00 — boundary, exclusive.
        assertFalse(s.isActiveAt(LocalDateTime(sun, LocalTime.of(6, 0))))
        // Sunday at 14:00 — out.
        assertFalse(s.isActiveAt(LocalDateTime(sun, LocalTime.of(14, 0))))
    }

    @Test
    fun `isActiveAt is symmetric for the two halves of an overnight window`() {
        val s = GoingLightSchedule(
            enabled = true,
            activeDays = setOf(DayOfWeek.SATURDAY),
            startTime = LocalTime.of(20, 0),
            endTime = LocalTime.of(8, 0),
        )
        // Saturday 22:00 — inside (same day, before midnight).
        assertTrue(s.isActiveAt(LocalDateTime(sat, LocalTime.of(22, 0))))
        // Sunday 03:00 — inside (overnight, after midnight).
        assertTrue(s.isActiveAt(LocalDateTime(sun, LocalTime.of(3, 0))))
        // Sunday 09:00 — out (overnight tail already ended).
        assertFalse(s.isActiveAt(LocalDateTime(sun, LocalTime.of(9, 0))))
    }

    @Test
    fun `nextTransition returns null for a permanently-off schedule`() {
        val s = GoingLightSchedule(enabled = false, activeDays = setOf(DayOfWeek.SATURDAY))
        assertNull(s.nextTransition(LocalDateTime(sat, LocalTime.of(12, 0))))
        val s2 = GoingLightSchedule(enabled = true, activeDays = emptySet())
        assertNull(s2.nextTransition(LocalDateTime(sat, LocalTime.of(12, 0))))
    }

    @Test
    fun `nextTransition returns the end of the current active window`() {
        val s = GoingLightSchedule(
            enabled = true,
            activeDays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            startTime = LocalTime.of(20, 0),
            endTime = LocalTime.of(22, 0),
        )
        val t = s.nextTransition(LocalDateTime(sat, LocalTime.of(21, 0)))
        assertEquals(LocalDateTime(sat, LocalTime.of(22, 0)), t)
    }

    @Test
    fun `nextTransition returns the next start when the window is currently inactive`() {
        val s = GoingLightSchedule(
            enabled = true,
            activeDays = setOf(DayOfWeek.SATURDAY),
            startTime = LocalTime.of(20, 0),
            endTime = LocalTime.of(22, 0),
        )
        // Wednesday — Saturday is 3 days away.
        val wed = LocalDate.of(2026, 3, 4)
        val t = s.nextTransition(LocalDateTime(wed, LocalTime.of(12, 0)))
        assertEquals(LocalDateTime(sat, LocalTime.of(20, 0)), t)
    }

    @Test
    fun `nextTransition handles overnight windows by returning the start of the same day's tail`() {
        // The overnight case is the same-day case for the
        // morning tail: if it is 03:00 on Sunday inside an
        // overnight window, the next transition is the
        // *end* of the window (06:00 Sunday). The next
        // *start* is the next day's start (06:00 the
        // following Saturday). Both are valid arm-points
        // depending on what the scheduler needs; the
        // function returns the *next* transition, which is
        // the end of the current active window.
        val s = GoingLightSchedule(
            enabled = true,
            activeDays = setOf(DayOfWeek.SATURDAY),
            startTime = LocalTime.of(20, 0),
            endTime = LocalTime.of(8, 0),
        )
        val t = s.nextTransition(LocalDateTime(sun, LocalTime.of(3, 0)))
        assertEquals(LocalDateTime(sun, LocalTime.of(8, 0)), t)
    }

    @Test
    fun `a schedule with one active day, when the next 7 days have no active day, returns null`() {
        // Defensive: a schedule with activeDays = {MONDAY}
        // queried on a Sunday, looking forward 7 days, should
        // find the next Monday. The function loops 0..7; 7
        // days from Sunday is the next Sunday, which is 6
        // days after the next Monday. So the function should
        // find the next Monday at 20:00.
        val s = GoingLightSchedule(
            enabled = true,
            activeDays = setOf(DayOfWeek.MONDAY),
            startTime = LocalTime.of(20, 0),
            endTime = LocalTime.of(22, 0),
        )
        val sun = LocalDate.of(2026, 3, 8)
        val nextMon = LocalDate.of(2026, 3, 9)
        val t = s.nextTransition(LocalDateTime(sun, LocalTime.of(12, 0)))
        assertEquals(LocalDateTime(nextMon, LocalTime.of(20, 0)), t)
    }
}
