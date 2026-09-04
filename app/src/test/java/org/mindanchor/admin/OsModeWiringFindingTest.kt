package org.mindanchor.admin

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Finding tests pinning the OS Mode wiring (master plan T-1.1/T-1.2),
 * in the same style as [org.mindanchor.settings.SettingsBackFindingTest].
 *
 * The crash-safety contract of OS Mode lives in four wiring points, and
 * each one failing silently would strand apps on the wrong side of the
 * window — the exact class of bug (the missing-alarm bug, see Alarms.kt)
 * this repo treats as worst-shape. Source-pinning is how the repo keeps
 * such wiring from drifting away quietly.
 */
class OsModeWiringFindingTest {

    private fun readMain(relativePath: String): String =
        checkNotNull(
            java.io.File("app/src/main/java/org/mindanchor/$relativePath")
                .takeIf { it.isFile }
                ?: java.io.File("../app/src/main/java/org/mindanchor/$relativePath")
                    .takeIf { it.isFile }
                    ?: error("$relativePath not found"),
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
    fun `boot re-arm hub re-derives OS Mode suspension`() {
        val alarms = readMain("Alarms.kt")
        assertTrue(
            "Alarms.ensureAll must call OsMode.sync so a reboot or " +
                "process restart re-derives suspension from the window " +
                "(crash-safe re-entry, master plan T-1.2).",
            alarms.contains("OsMode.sync"),
        )
    }

    @Test
    fun `sunset alarms drive the OS Mode layer on both window edges`() {
        val controller = readMain("sunset/SunsetController.kt")
        assertTrue(
            "SunsetController.handleAlarm must call OsMode.sync on every " +
                "alarm so SUNSET_START applies and SUNSET_END lifts.",
            controller.contains("OsMode.sync"),
        )
    }

    @Test
    fun `the typed dwell escape hatch lifts the feed layer`() {
        val home = readMain("launcher/HomeScreen.kt")
        assertTrue(
            "The Sleep Lock unlock path must call OsMode.onEarlyUnlock " +
                "(master plan T-1.2: the 30s typed dwell unlocks early).",
            home.contains("OsMode.onEarlyUnlock"),
        )
    }

    @Test
    fun `settings renders the guided OS Mode surface`() {
        val settings = readMain("settings/SettingsScreen.kt")
        assertTrue(
            "SettingsScreen must render OsModeSection with the " +
                "permissionEpoch idiom (master plan T-1.1).",
            settings.contains("OsModeSection(permissionEpoch = permissionEpoch)"),
        )
    }

    @Test
    fun `every OS Mode string exists for the guided surface`() {
        val strings = readStrings()
        listOf(
            "osmode_section",
            "osmode_explainer",
            "osmode_grants",
            "osmode_leaving",
            "osmode_not_provisioned",
            "osmode_toggle",
            "osmode_armed_note",
            "osmode_available_note",
        ).forEach { key ->
            assertTrue(
                "strings.xml must define $key — OsModeSection renders it.",
                strings.contains("name=\"$key\""),
            )
        }
    }

    @Test
    fun `the OS Mode switch reports its new state back, not only to prefs`() {
        // A Compose Switch is fully controlled: it renders whatever
        // `checked` says. The first version wrote the new value to
        // OsModePrefs but never updated the state driving `checked`, so
        // after one tap the control and the store disagreed -- every
        // further tap re-sent the stale value and the switch could not be
        // turned back on without leaving and re-entering Settings.
        // Reproduced on device: three taps, switch stuck at checked=true,
        // stored value stuck at false.
        val source = File("src/main/java/org/mindanchor/settings/OsModeSection.kt")
            .readText(Charsets.UTF_8)
        val row = source.substringAfter("private fun OsModeArmedRow")
        assertTrue(
            "OsModeArmedRow must hoist the new value back to its caller",
            row.contains("onEnabledChange"),
        )
        val handler = row.substringAfter("onCheckedChange = { checked ->").substringBefore("}")
        assertTrue(
            "onCheckedChange must report the new state, or the switch cannot move twice",
            handler.contains("onEnabledChange(checked)"),
        )
    }
}
