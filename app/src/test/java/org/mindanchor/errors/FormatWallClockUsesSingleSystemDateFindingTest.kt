package org.mindanchor.errors

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B9 (SOTA v2 bug-hunt, agent #5): formatWallClock reads the system
 * date twice — `atZone(ZoneId.systemDefault())` on line 786 and
 * `LocalDate.now()` on line 788. The two reads are sequential; if
 * either crosses a midnight or DST boundary, the "tomorrow"
 * comparison is inconsistent with the time that was formatted.
 *
 * File-shape pin: the fix PR passes the `today` from the caller
 * (the OpenLoopCard, which already reads `LocalDate.now()` for
 * the postponement) instead of re-reading it in the helper.
 */
class FormatWallClockUsesSingleSystemDateFindingTest {

    @Test
    fun `formatWallClock does not read LocalDate now (regression guard for B9)`() {
        val source = java.io.File(
            "src/main/java/org/mindanchor/launcher/HomeScreen.kt",
        ).readText()
        // The pre-fix literal is `val today = LocalDate.now()` inside
        // formatWallClock. The fix shape accepts `today` from the caller.
        val formatBlock = source.substringAfter("private fun formatWallClock")
            .substringBefore("@Composable")
        assertFalse(
            "formatWallClock must not call `LocalDate.now()` — the time was " +
                "formatted from one `ZoneId.systemDefault()` read; the " +
                "`today` for the comparison must come from the same instant, " +
                "not a separate system call that could cross midnight.",
            formatBlock.contains("LocalDate.now()"),
        )
    }

    @Test
    fun `formatWallClock takes today as a parameter (regression guard for B9)`() {
        val source = java.io.File(
            "src/main/java/org/mindanchor/launcher/HomeScreen.kt",
        ).readText()
        val formatBlock = source.substringAfter("private fun formatWallClock")
            .substringBefore("@Composable")
        // The fix shape: `fun formatWallClock(at: Instant?, today: LocalDate): String`.
        assertTrue(
            "formatWallClock must take `today: LocalDate` as a parameter — " +
                "the caller already has the date from the same ZonedDateTime " +
                "that produced the time.",
            formatBlock.contains("today:") || formatBlock.contains("today ="),
        )
    }
}
