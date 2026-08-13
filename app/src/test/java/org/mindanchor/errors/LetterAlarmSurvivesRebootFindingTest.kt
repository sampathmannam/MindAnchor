package org.mindanchor.errors

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B1 (SOTA v2 bug-hunt, agent #5): the v0.25.5 LetterScheduler is not in
 * Alarms.ensureAll. After every device reboot the daily letter alarm is
 * lost; the only re-arm path is the alarm itself firing, which it
 * never does. The Alarms.kt class KDoc explicitly says alarms do not
 * survive a reboot and that the list of what to re-arm is centralised
 * here for that reason.
 *
 * This is a file-shape pin: the fix PR replaces the asserts with a
 * proper integration test that drives the boot path; until then, the
 * shape is the regression guard.
 */
class LetterAlarmSurvivesRebootFindingTest {

    @Test
    fun `Alarms ensureAll arms the letter scheduler (regression guard for B1)`() {
        val source = java.io.File(
            "src/main/java/org/mindanchor/Alarms.kt",
        ).readText()
        assertTrue(
            "Alarms.ensureAll must call LetterScheduler.ensureScheduled — " +
                "the letter alarm is otherwise lost on every reboot.",
            source.contains("LetterScheduler.ensureScheduled"),
        )
        assertTrue(
            "Alarms.ensureAll must call it exactly once (no double-arming).",
            "LetterScheduler.ensureScheduled".toRegex()
                .findAll(source)
                .count() == 1,
        )
    }
}
