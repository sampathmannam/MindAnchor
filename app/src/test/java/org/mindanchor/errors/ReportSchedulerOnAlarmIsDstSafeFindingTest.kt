package org.mindanchor.errors

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B4 (SOTA v2 bug-hunt, agent #5): the v0.25.5 WP-B fix was applied
 * to ReportSchedule.nextRun and its arming caller armNext, but the
 * firing site ReportScheduler.onAlarm was not migrated and still
 * reads `val now = LocalDateTime.now()` for the hourOfDay decision.
 * The previous bug-hunt (v0.25.7, B5) identified this as S2; the
 * v0.25.5+ release notes claim a fix that is incomplete. A user who
 * changes timezone between arming and firing sees the report
 * silently dropped.
 *
 * File-shape pin: the fix PR captures the arm-time zone and reads
 * ZonedDateTime in that zone at fire time. The asserts below are
 * the regression guard.
 */
class ReportSchedulerOnAlarmIsDstSafeFindingTest {

    @Test
    fun `ReportScheduler onAlarm does not read LocalDateTime now (regression guard for B4)`() {
        val source = java.io.File(
            "src/main/java/org/mindanchor/report/ReportScheduler.kt",
        ).readText()
        // The `onAlarm` method specifically. The grep is narrow: the
        // literal `LocalDateTime.now()` is the pre-fix shape; the
        // post-fix shape is `ZonedDateTime.now(armingZone)`.
        val onAlarmBlock = source.substringAfter("internal suspend fun onAlarm")
            .substringBefore("private fun armNext")
        assertFalse(
            "ReportScheduler.onAlarm must not call LocalDateTime.now() — " +
                "the v0.25.5 WP-B fix migrated armNext but left onAlarm on " +
                "the pre-fix shape. The hourOfDay read is DST-bound.",
            onAlarmBlock.contains("LocalDateTime.now()"),
        )
        assertTrue(
            "ReportScheduler.onAlarm must read ZonedDateTime in a captured " +
                "zone (the v0.25.5 WP-B shape extended to the firing site).",
            onAlarmBlock.contains("ZonedDateTime"),
        )
    }
}
