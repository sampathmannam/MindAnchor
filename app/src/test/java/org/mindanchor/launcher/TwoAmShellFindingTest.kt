package org.mindanchor.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.26.0 §3.5 FindingTest: the "Now what?" 2am shell exists
 * and the heuristic that picks it is conservative.
 *
 * The shell shows when:
 *  - The current hour is 00:00–05:00, AND
 *  - The user has *not* set the `okAtNight` flag.
 *
 * File-shape pin: `NowWhatShell` is a top-level @Composable
 * fun in `app/src/main/java/org/mindanchor/launcher/NowWhatShell.kt`.
 * `NowWhatHeuristic.shouldShow` is a pure function in the
 * same file.
 */
class TwoAmShellFindingTest {

    @Test
    fun `heuristic fires in 2am hours when okAtNight is false`() {
        for (hour in 0..5) {
            assertTrue(
                "Hour $hour is in the 2am window and okAtNight=false → shell should show",
                NowWhatHeuristic.shouldShow(currentHour = hour, okAtNight = false),
            )
        }
    }

    @Test
    fun `heuristic does not fire outside the 2am window`() {
        for (hour in listOf(6, 9, 12, 18, 22, 23)) {
            assertFalse(
                "Hour $hour is outside the 2am window → shell must not show",
                NowWhatHeuristic.shouldShow(currentHour = hour, okAtNight = false),
            )
        }
    }

    @Test
    fun `okAtNight=true suppresses the shell at every hour`() {
        for (hour in 0..23) {
            assertFalse(
                "okAtNight=true must always suppress the shell, even at hour $hour",
                NowWhatHeuristic.shouldShow(currentHour = hour, okAtNight = true),
            )
        }
    }

    @Test
    fun `the quiet window is 0-5 inclusive on both ends`() {
        assertEquals(0, NowWhatHeuristic.QUIET_START_HOUR)
        assertEquals(5, NowWhatHeuristic.QUIET_END_HOUR)
    }

    @Test
    fun `NowWhatShell composable exists in launcher package`() {
        val cls = Class.forName("org.mindanchor.launcher.NowWhatShellKt")
        val method = cls.declaredMethods.firstOrNull { it.name == "NowWhatShell" }
        assertTrue(
            "NowWhatShell must be a top-level @Composable fun in launcher/NowWhatShell.kt",
            method != null,
        )
    }
}
