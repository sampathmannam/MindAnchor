package org.mindanchor.errors

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B5 (SOTA v2 bug-hunt, agent #5): the DST-bound
 * `LocalDateTime.now()` + `atZone(ZoneId.systemDefault())` arming
 * pattern that the v0.25.5 WP-B fix was meant to retire. The fix
 * landed in exactly one place (ReportSchedule.nextRun) and left
 * four other schedulers on the old shape: BatchAlarms,
 * SunsetController, GoingLightScheduler, EmaScheduler. A user
 * who changes timezone between arming and firing sees the alarm
 * fire at the arm-time zone's wall-clock, not the current
 * zone's.
 *
 * File-shape pin: the fix PR migrates the four sites to the
 * (Instant, ZoneId) shape and re-shapes the pure helpers to
 * take the same. The asserts below are the regression guard.
 */
class ArmingSchedulersAreDstSafeFindingTest {

    @Test
    fun `BatchAlarms does not arm via LocalDateTime now (regression guard for B5)`() {
        val source = java.io.File(
            "src/main/java/org/mindanchor/notifications/BatchAlarms.kt",
        ).readText()
        assertTrue(
            "BatchAlarms must not arm via LocalDateTime.now() + " +
                "atZone(systemDefault()) — the v0.25.5 WP-B fix retired " +
                "this shape; the four pre-fix survivors are " +
                "BatchAlarms, SunsetController, GoingLightScheduler, " +
                "EmaScheduler.",
            !source.contains("LocalDateTime.now()"),
        )
    }

    @Test
    fun `SunsetController does not arm via LocalDateTime now (regression guard for B5)`() {
        val source = java.io.File(
            "src/main/java/org/mindanchor/sunset/SunsetController.kt",
        ).readText()
        assertTrue(
            "SunsetController must not arm via LocalDateTime.now() — same as " +
                "B5 BatchAlarms.",
            !source.contains("LocalDateTime.now()"),
        )
    }

    @Test
    fun `GoingLightScheduler does not arm via LocalDateTime now (regression guard for B5)`() {
        val source = java.io.File(
            "src/main/java/org/mindanchor/goinglight/GoingLightScheduler.kt",
        ).readText()
        assertTrue(
            "GoingLightScheduler must not arm via LocalDateTime.now() — same as " +
                "B5 BatchAlarms.",
            !source.contains("LocalDateTime.now()"),
        )
    }

    @Test
    fun `EmaScheduler does not arm via LocalDateTime now (regression guard for B5)`() {
        val source = java.io.File(
            "src/main/java/org/mindanchor/model/EmaScheduler.kt",
        ).readText()
        assertTrue(
            "EmaScheduler must not arm via LocalDateTime.now() — same as " +
                "B5 BatchAlarms.",
            !source.contains("LocalDateTime.now()"),
        )
    }
}
