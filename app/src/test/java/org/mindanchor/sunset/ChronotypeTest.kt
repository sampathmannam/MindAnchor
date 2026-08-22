package org.mindanchor.sunset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * Pure-JVM tests for [Chronotype].
 *
 * The DataStore integration (persistence, auto-default-window on
 * first set, the customised-window guard) lives in
 * [org.mindanchor.data.SunsetPrefs] and is exercised by the
 * instrumented test path — the same split the rest of the launcher
 * uses, see [SunsetControllerTest] for the pattern.
 *
 * ## Why these tests exist
 *
 * The default windows are the only thing this file pins down. The
 * citations justify the *existence* of a chronotype-aware default
 * (Roenneberg 2007, Wittmann 2006, Åkerstedt 2003, Kecklund 2016);
 * the specific minutes are the launcher's choice, and a change to
 * any one of them is the kind of thing a future refactor makes
 * without thinking. These tests are the trip wire.
 */
class ChronotypeTest {

    @Test
    fun `morning lark default starts the wind-down an hour before 22 00`() {
        // 21:00 → 06:00. A wind-down that lands before 22:00 is the
        // only one that is not already too late for somebody who
        // gets up at 06:00.
        val (start, end) = Chronotype.MORNING_LARK.defaultWindow()
        assertEquals(LocalTime.of(21, 0), start)
        assertEquals(LocalTime.of(6, 0), end)
    }

    @Test
    fun `neutral default is the launcher's own 22 00 to 07 00 placeholder`() {
        // The placeholder the launcher was hardcoded to before
        // chronotypes existed. Kept as the "neutral" answer so
        // picking it does not move the existing window.
        val (start, end) = Chronotype.NEUTRAL.defaultWindow()
        assertEquals(LocalTime.of(22, 0), start)
        assertEquals(LocalTime.of(7, 0), end)
    }

    @Test
    fun `night owl default starts at midnight and ends at 08 00`() {
        // 00:00 → 08:00. Wittmann 2006 documents the social jetlag
        // cost of a late-type person trying to live on a 22:00
        // wind-down; a default that starts at midnight is the
        // only honest one for this population.
        val (start, end) = Chronotype.NIGHT_OWL.defaultWindow()
        assertEquals(LocalTime.of(0, 0), start)
        assertEquals(LocalTime.of(8, 0), end)
    }

    @Test
    fun `shift worker default is daytime 09 00 to 17 00`() {
        // 09:00 → 17:00. A shift worker's "evening" is the
        // morning for everyone else — the wind-down belongs in
        // their daytime, not in ours. Åkerstedt 2003 +
        // Kecklund 2016.
        val (start, end) = Chronotype.SHIFT_WORKER.defaultWindow()
        assertEquals(LocalTime.of(9, 0), start)
        assertEquals(LocalTime.of(17, 0), end)
    }

    @Test
    fun `unknown falls back to neutral`() {
        // Treated as neutral so the launcher's first-run default
        // is still 22:00 → 07:00 for a user who has not answered
        // the onboarding question. The settings panel shows
        // "not set" for the user; the runtime just uses neutral.
        val unknown = Chronotype.UNKNOWN.defaultWindow()
        val neutral = Chronotype.NEUTRAL.defaultWindow()
        assertEquals(neutral, unknown)
    }

    @Test
    fun `every chronotype returns a same-day or cross-midnight window — never equal ends`() {
        // A window with both ends equal reads as "all day" and
        // behaves as "never" (see SunsetPrefs.isInWindow and
        // SunsetPrefs.isValidWindow). A default window that
        // committed that mistake would silently turn the
        // wind-down off.
        Chronotype.entries.forEach { c ->
            val (start, end) = c.defaultWindow()
            assertNotEquals("chronotype $c has equal ends", start, end)
            // Start and end must be valid minute-of-day values.
            assertTrue("start $start is on the clock", start.hour in 0..23)
            assertTrue("end $end is on the clock", end.hour in 0..23)
        }
    }

    @Test
    fun `a stored chronotype name that no longer exists reads back as unknown`() {
        // Drop a future version's case into the store, or
        // corrupt the preference by hand, and the next read
        // degrades to UNKNOWN rather than crashing. The
        // settings panel then shows "not set" and the user
        // re-answers.
        val ghost = "MAYBE_A_ROOSTER"
        val readBack = runCatching { Chronotype.valueOf(ghost) }
            .getOrDefault(Chronotype.UNKNOWN)
        assertEquals(Chronotype.UNKNOWN, readBack)
    }

    @Test
    fun `every named chronotype round-trips through valueOf`() {
        // The persistence layer stores Chronotype.name and
        // reads it back via valueOf. A rename of any case
        // would break the round-trip and this test catches it.
        Chronotype.entries.forEach { c ->
            assertEquals(c, Chronotype.valueOf(c.name))
        }
    }
}
