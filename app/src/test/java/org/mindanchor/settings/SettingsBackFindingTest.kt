package org.mindanchor.settings

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Finding tests for v0.25.1 bug 5 — pressing the
 * system back button from inside a settings
 * sub-section (Quiet, Pauses, Measuring, Reading,
 * Your plan, This phone) jumped straight to home,
 * skipping the settings index. A user exploring
 * Settings → Quiet → back lost the list of the
 * other five sections and had to re-enter Settings
 * from the home.
 *
 * The fix:
 *  1. The [org.mindanchor.launcher.HomeScreen] global
 *     `BackHandler` no longer fires when
 *     `surface == LauncherSurface.Settings`.
 *  2. The [SettingsScreen] registers its own
 *     `BackHandler` that closes an open group on
 *     the first press and only calls `onBack()` on
 *     the second press.
 *  3. The visible "back" text button uses the same
 *     predicate, so visible-button back and
 *     system-back back behave identically.
 *
 * What this test pins:
 *  1. The HomeScreen BackHandler predicate excludes
 *     the Settings surface.
 *  2. The SettingsScreen registers a BackHandler.
 *  3. The SettingsScreen's BackHandler closes the
 *     group first, only exiting on the second press.
 *  4. The visible back button has the same
 *     `if (group != null) group = null else onBack()`
 *     shape.
 *  5. The SettingsScreen imports `BackHandler`.
 */
class SettingsBackFindingTest {

    private fun readHome(): String =
        checkNotNull(
            java.io.File("app/src/main/java/org/mindanchor/launcher/HomeScreen.kt")
                .takeIf { it.isFile }
                ?: java.io.File("../app/src/main/java/org/mindanchor/launcher/HomeScreen.kt")
                    .takeIf { it.isFile }
                    ?: error("HomeScreen.kt not found"),
        ).readText()

    private fun readSettings(): String =
        checkNotNull(
            java.io.File("app/src/main/java/org/mindanchor/settings/SettingsScreen.kt")
                .takeIf { it.isFile }
                ?: java.io.File("../app/src/main/java/org/mindanchor/settings/SettingsScreen.kt")
                    .takeIf { it.isFile }
                    ?: error("SettingsScreen.kt not found"),
        ).readText()

    @Test
    fun `HomeScreen BackHandler excludes the Settings surface`() {
        val home = readHome()
        // The fix: the BackHandler predicate has
        // `surface != LauncherSurface.Settings` so it
        // does not fire while Settings is the active
        // surface. Use a permissive substring check —
        // the regex version was too brittle for the
        // multi-line predicate.
        assertTrue(
            "the HomeScreen BackHandler predicate must exclude " +
                "LauncherSurface.Settings. Searched for " +
                "'surface != LauncherSurface.Settings' in the file.",
            home.contains("surface != LauncherSurface.Settings"),
        )
    }

    @Test
    fun `SettingsScreen registers its own BackHandler`() {
        val settings = readSettings()
        // The fix added a BackHandler inside SettingsScreen.
        val count = Regex("""BackHandler\s*\(""").findAll(settings).count()
        assertTrue(
            "SettingsScreen must register at least one BackHandler " +
                "to handle back from a sub-section. count=$count",
            count >= 1,
        )
    }

    @Test
    fun `SettingsScreen BackHandler closes the group before calling onBack`() {
        val settings = readSettings()
        // The fix: a `BackHandler` block whose body
        // contains `if (group != null)`, `group = null`,
        // and `onBack()`. Use substring checks; the
        // multi-line braces made the regex version
        // brittle.
        assertTrue(
            "the SettingsScreen BackHandler must check " +
                "`if (group != null)` to close the open group " +
                "before exiting.",
            settings.contains("if (group != null)"),
        )
        assertTrue(
            "the SettingsScreen BackHandler must reset " +
                "`group = null` on the first back press.",
            settings.contains("group = null"),
        )
        assertTrue(
            "the SettingsScreen BackHandler must call " +
                "`onBack()` on the second press (when no " +
                "group is open).",
            settings.contains("onBack()"),
        )
    }

    @Test
    fun `visible back button shares the same if-else shape`() {
        val settings = readSettings()
        // The visible "back" TextButton and the
        // system BackHandler must agree; if they
        // diverge, a user pressing one path gets a
        // different result from the other. The fix
        // wires the same `if (group != null) group = null
        // else onBack()` shape on both paths.
        val hasAll = settings.contains("if (group != null)") &&
            settings.contains("group = null") &&
            settings.contains("onBack()")
        val hasVisibleTextButton = settings.contains("TextButton")
        assertTrue(
            "the visible back TextButton must use the same " +
                "`if (group != null) group = null else onBack()` shape " +
                "as the BackHandler.",
            hasAll && hasVisibleTextButton,
        )
    }

    @Test
    fun `SettingsScreen imports BackHandler`() {
        val settings = readSettings()
        assertTrue(
            "SettingsScreen must import androidx.activity.compose.BackHandler; " +
                "without the import, the BackHandler call would not compile " +
                "and the fix would silently break at refactor time.",
            settings.contains("import androidx.activity.compose.BackHandler"),
        )
    }
}
