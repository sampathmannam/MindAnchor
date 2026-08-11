package org.mindanchor.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Finding tests for v0.25.1 bug 3 — the
 * "Allow exact timing" link in the Quiet section
 * read as plain text. The pre-fix TextButton had
 * the same colour as section headers and no chevron
 * or fill, so a first-time user did not realise it
 * was interactive.
 *
 * The fix adds a chevron (`→`) to the rendered text
 * and a small `vertical = 4.dp` padding to lift the
 * button out of the surrounding flow. The chevron
 * is added in code (not in the string resource) so
 * the resource stays screen-reader-friendly and
 * locale-independent.
 *
 * What this test pins:
 *  1. The string resource `exact_alarms_grant` is
 *     "Allow exact timing" — no embedded chevron
 *     (would conflict with right-to-left locales
 *     and screen readers).
 *  2. The call site appends "  →" to the label.
 *  3. The TextButton has a vertical padding.
 *  4. The button still routes to the system exact-
 *     alarm settings (no regression on the action).
 *  5. The intent still uses
 *     `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM`
 *     and the app's own package.
 */
class ExactTimingAffordanceFindingTest {

    private fun readScreen(): String =
        checkNotNull(
            java.io.File("app/src/main/java/org/mindanchor/settings/SettingsScreen.kt")
                .takeIf { it.isFile }
                ?: java.io.File("../app/src/main/java/org/mindanchor/settings/SettingsScreen.kt")
                    .takeIf { it.isFile }
                    ?: error("SettingsScreen.kt not found"),
        ).readText()

    private fun readStrings(): String =
        checkNotNull(
            java.io.File("app/src/main/res/values/strings.xml")
                .takeIf { it.isFile }
                ?: java.io.File("../app/src/main/res/values/strings.xml")
                    .takeIf { it.isFile }
                    ?: error("strings.xml not found"),
        ).readText()

    @Test
    fun `exact_alarms_grant string resource has no embedded chevron`() {
        val strings = readStrings()
        val match = Regex("""name="exact_alarms_grant"[^>]*>([^<]+)<""").find(strings)
            ?: error("exact_alarms_grant not found")
        val value = match.groupValues[1]
        // The chevron is appended in code, not in the
        // string resource. This keeps the string
        // screen-reader-friendly and locale-safe.
        assertEquals("Allow exact timing", value)
        assertTrue(
            "the chevron must be appended in code, not the string; " +
                "an embedded '→' in the resource would be read aloud " +
                "as 'rightwards arrow' and break in RTL locales.",
            !value.contains("→"),
        )
    }

    @Test
    fun `call site appends a chevron glyph to the label`() {
        val screen = readScreen()
        // The fix appends "  →" to the resource.
        assertTrue(
            "the call site must append a chevron to the exact_alarms_grant label; " +
                "the user needs the visual cue to know the row is interactive.",
            screen.contains("exact_alarms_grant) + \"  →\""),
        )
    }

    @Test
    fun `TextButton has vertical padding to lift the affordance`() {
        val screen = readScreen()
        // The exact-alarms TextButton must have a
        // vertical padding modifier to separate it
        // from the explainer text above.
        assertTrue(
            "the exact-alarms TextButton must carry a vertical padding " +
                "to lift it from the explainer text.",
            screen.contains(".padding(vertical = 4.dp)"),
        )
    }

    @Test
    fun `intent routes to system exact-alarm settings for the app's own package`() {
        val screen = readScreen()
        assertTrue(
            "the exact-alarms launch must still call " +
                "Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM with the app's " +
                "own package name.",
            screen.contains("Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM"),
        )
        assertTrue(
            "the exact-alarms launch must still pass the app's own package name.",
            screen.contains("Uri.fromParts(\"package\", context.packageName, null)"),
        )
    }

    @Test
    fun `exact-alarms button is still gated to API S+`() {
        val screen = readScreen()
        // Android S = API 31. The check protects older
        // devices that don't have the explicit-permission
        // flow.
        assertTrue(
            "the exact-alarms TextButton must still be gated to API S+.",
            screen.contains("Build.VERSION_CODES.S"),
        )
    }
}
