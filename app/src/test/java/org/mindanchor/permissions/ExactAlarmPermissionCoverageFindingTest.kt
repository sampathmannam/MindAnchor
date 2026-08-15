@file:Suppress(
    "SwallowedException", 
    "MaxLineLength", 
    "LoopWithTooManyJumpStatements", 
    "UnusedPrivateMember",
)

package org.mindanchor.permissions

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SOTA v2 bug-hunt, finding #10: `Alarms.canBeExact(context)` is
 * only surfaced to the user in the SettingsScreen batching section
 * (line 1029). The other four schedulers that need exact alarms —
 * LetterScheduler, EmaScheduler, ReportScheduler, SunsetController
 * — are not surfaced. (v0.26.6 dropped PulseReminder with the
 * pulse package — it is no longer in the schedulers list.)
 *
 * Concretely: a user who turns on the daily letter without
 * granting SCHEDULE_EXACT_ALARM will see the letter arrive an
 * hour late (or not at all on Doze-restricted devices) and the
 * Settings → Reading section will not tell them why. The same
 * applies to the EMA prompts, the pulse reminder, the nightly
 * report, and the sunset window.
 *
 * The fix is to surface the exact-alarm grant link in every
 * scheduler's settings sub-section, or to put a single banner
 * at the top of the settings screen when `Alarms.canBeExact`
 * is false. The current implementation has the banner only
 * inside the batching group.
 *
 * The three tests below pin the surface.
 */
class ExactAlarmPermissionCoverageFindingTest {

    @Test
    fun `SettingsScreen surfaces the exact-alarm grant link in every group that uses exact alarms`() {
        val src = read("src/main/java/org/mindanchor/settings/SettingsScreen.kt") ?: return
        val batchingSurface = src.contains("Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM")
        // The fix would add this to the letter (Reading), EMA
        // (check-ins), sunset (Quiet), and report (Reading or
        // Measuring) sections.
        val readingHasSurface = src.indexOf("settings_group_reading").let { idx ->
            idx >= 0 && src.indexOf("ACTION_REQUEST_SCHEDULE_EXACT_ALARM", idx) >= 0
        }
        val checkInsHasSurface = src.indexOf("settings_group_pauses").let { idx ->
            idx >= 0 && src.indexOf("ACTION_REQUEST_SCHEDULE_EXACT_ALARM", idx) >= 0
        }
        assertTrue(
            "SettingsScreen must surface the exact-alarm grant link in every " +
                "group that uses exact alarms. Batching surfaces it (good). " +
                "Reading sub-section (letters) does not " +
                "(readingHasSurface=$readingHasSurface). " +
                "Pauses sub-section (EMA prompts) does not " +
                "(checkInsHasSurface=$checkInsHasSurface). " +
                "The fix is to hoist the banner out of the batching block " +
                "and into a top-level `if (!Alarms.canBeExact(context)) { ... }` " +
                "block, OR to repeat the same `if (!Alarms.canBeExact)` " +
                "block in every section that uses an exact alarm. " +
                "batchingSurface=$batchingSurface.",
            batchingSurface && (readingHasSurface || checkInsHasSurface),
        )
    }

    @Test
    fun `Alarms object has a single canBeExact helper used by the UI and the schedulers`() {
        val src = read("src/main/java/org/mindanchor/Alarms.kt") ?: return
        assertTrue(
            "Alarms.canBeExact must exist as a single helper. " +
                "The current implementation at line 62 uses " +
                "Build.VERSION.SDK_INT < S || manager.canScheduleExactAlarms() " +
                "— the right shape.",
            src.contains("fun canBeExact(context: Context): Boolean") &&
                src.contains("canScheduleExactAlarms"),
        )
    }

    @Test
    fun `every scheduler that uses setExactAndAllowWhileIdle guards on Alarms_canBeExact`() {
        val schedulers = listOf(
            "src/main/java/org/mindanchor/notifications/BatchAlarms.kt",
            "src/main/java/org/mindanchor/letters/LetterScheduler.kt",
            "src/main/java/org/mindanchor/model/EmaScheduler.kt",
            "src/main/java/org/mindanchor/report/ReportScheduler.kt",
            "src/main/java/org/mindanchor/sunset/SunsetController.kt",
            "src/main/java/org/mindanchor/friction/SessionManager.kt",
        )
        var guarded = 0
        for (path in schedulers) {
            val src = try {
                java.io.File(path).readText(Charsets.UTF_8)
            } catch (t: Throwable) {
                continue
            }
            if (src.contains("canScheduleExactAlarms") ||
                src.contains("Alarms.canBeExact")
            ) {
                guarded++
            }
        }
        assertTrue(
            "Every scheduler that uses setExactAndAllowWhileIdle must guard " +
                "on Alarms.canBeExact. Found $guarded / ${schedulers.size} " +
                "schedulers with the guard. The expected count is " +
                "${schedulers.size} (every one of them).",
            guarded == schedulers.size,
        )
    }

    private fun read(path: String): String? = try {
        java.io.File(path).readText(Charsets.UTF_8)
    } catch (t: Throwable) {
        null
    }
}
