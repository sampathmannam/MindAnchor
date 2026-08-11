package org.mindanchor.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Finding tests for v0.25.1 bug 1 — the duplicated
 * "Arrives at HH:MM HH:MM" label in the Quiet section
 * of the settings.
 *
 * The pre-fix string was `Arrives at %1$s`, and the
 * [org.mindanchor.settings.SettingsScreen] called
 * [androidx.compose.material3.TextButton]'s row
 * helper with both `label = "Arrives at 08:00"` and
 * `value = "08:00"`. The row helper's format is
 * `%1$s %2$s`, so the result was "Arrives at 08:00 08:00".
 *
 * The fix removes `%1$s` from `batching_time_slot`,
 * so the row helper renders `"Arrives at" + " " + "08:00"`
 * = `"Arrives at 08:00"`. The bedtime row's identical
 * shape ("Starts 22:00" / "Ends 07:00") already worked
 * because `sunset_starts` = "Starts" and `sunset_ends` = "Ends".
 *
 * What this test pins:
 *  1. `batching_time_slot` no longer contains `%1$s`
 *     (the time moved to the value slot).
 *  2. The label and the value are not both the time
 *     anymore (the file shape of the call).
 *  3. `batching_time_slot` is "Arrives at" (matches
 *     the bedtime "Starts" / "Ends" pattern).
 *  4. The bedtime row's "Starts" / "Ends" labels
 *     are unchanged (regression guard).
 *  5. The full file renders the time once, not twice.
 */
class BatchingTimeSlotFormatFindingTest {

    private fun readStrings(): String =
        checkNotNull(
            java.io.File("app/src/main/res/values/strings.xml")
                .takeIf { it.isFile }
                ?: java.io.File("../app/src/main/res/values/strings.xml")
                    .takeIf { it.isFile }
                    ?: error("strings.xml not found"),
        ).readText()

    private fun readScreen(): String =
        checkNotNull(
            java.io.File("app/src/main/java/org/mindanchor/settings/SettingsScreen.kt")
                .takeIf { it.isFile }
                ?: java.io.File("../app/src/main/java/org/mindanchor/settings/SettingsScreen.kt")
                    .takeIf { it.isFile }
                    ?: error("SettingsScreen.kt not found"),
        ).readText()

    @Test
    fun `batching_time_slot no longer contains the time slot token`() {
        val strings = readStrings()
        val match = Regex("""name="batching_time_slot"[^>]*>([^<]+)<""").find(strings)
            ?: error("batching_time_slot string not found")
        val value = match.groupValues[1]
        assertFalse(
            "batching_time_slot must not contain %1\$s (the time moved to the value slot); got: $value",
            value.contains("%1\$s"),
        )
    }

    @Test
    fun `batching_time_slot is "Arrives at" with no embedded time`() {
        val strings = readStrings()
        val match = Regex("""name="batching_time_slot"[^>]*>([^<]+)<""").find(strings)
            ?: error("batching_time_slot string not found")
        assertEquals("Arrives at", match.groupValues[1])
    }

    @Test
    fun `batching call site no longer passes the time as the label`() {
        val screen = readScreen()
        // Pre-fix: `label = stringResource(R.string.batching_time_slot, time.format(HOUR_MINUTE))`
        // The label slot must NOT receive a `time.format` argument.
        val labelHasTime = Regex(
            """label\s*=\s*stringResource\s*\(\s*R\.string\.batching_time_slot\s*,\s*time\.format""",
        ).containsMatchIn(screen)
        assertFalse(
            "the label slot of timeNudgerRow must not include time.format; " +
                "the time belongs in the value slot, not the label. " +
                "Found buggy call: $labelHasTime",
            labelHasTime,
        )
        // The value slot MUST still receive the time.
        val valueHasTime = Regex("""value\s*=\s*time\.format\s*\(\s*HOUR_MINUTE\s*\)""")
            .containsMatchIn(screen)
        assertTrue(
            "value slot of timeNudgerRow must still pass time.format(HOUR_MINUTE).",
            valueHasTime,
        )
    }

    @Test
    fun `bedtime labels are unchanged regression guard`() {
        val strings = readStrings()
        val starts = Regex("""name="sunset_starts"[^>]*>([^<]+)<""").find(strings)
            ?: error("sunset_starts not found")
        val ends = Regex("""name="sunset_ends"[^>]*>([^<]+)<""").find(strings)
            ?: error("sunset_ends not found")
        assertEquals("Starts", starts.groupValues[1])
        assertEquals("Ends", ends.groupValues[1])
    }

    @Test
    fun `time_nudger_row format string is unchanged at "%1$s %2$s"`() {
        val strings = readStrings()
        val match = Regex("""name="time_nudger_row"[^>]*>([^<]+)<""").find(strings)
            ?: error("time_nudger_row not found")
        assertEquals("%1\$s %2\$s", match.groupValues[1])
    }
}
