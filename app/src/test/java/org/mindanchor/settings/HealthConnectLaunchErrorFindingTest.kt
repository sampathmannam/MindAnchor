package org.mindanchor.settings

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Finding test for the v0.25.3-WP-B + v0.26+ Health Connect
 * launch-error diagnostic surface.
 *
 * Background: one user reported the "Connect to your watch" button
 * silently doing nothing on their physical device ("error / exception
 * / no response"). The pre-v0.25.3 launcher wrapped the `launch(...)`
 * call in nothing — if the system Health Connect activity could not
 * be dispatched (ActivityNotFoundException on a custom ROM, a
 * SecurityException, or a send-cancel), the failure was swallowed
 * and the screen looked identical to "denied".
 *
 * v0.25.3-WP-B:
 *  1. The launch is wrapped in `runCatching`. On failure, the
 *     exception class name is stored in a state and a visible error
 *     text is shown below the button.
 *  2. Both the launch and the result handler log to the
 *     `MindAnchor/HealthConnect` logcat tag, so the user can capture
 *     diagnostic info with `adb logcat` and we have something to
 *     root-cause from.
 *
 * v0.26+ (3-step fallback) — a single user reported the
 * button still doing nothing on 2026-08-24. The
 * permission-contract path was failing silently on their
 * device (Health Connect reported SDK_AVAILABLE but the
 * permission activity did not surface). The fix extends
 * the v0.25.3 surface to a 3-step fallback:
 *   1. SDK launcher (the canonical path; unchanged)
 *   2. Direct intent to the Health Connect app's main
 *      activity, so the user can grant permissions from
 *      the app's own UI when the SDK contract is wedged
 *   3. Play Store listing for Health Connect, as a last
 *      resort when the app is missing entirely
 * All three are wrapped in runCatching; if every path
 * throws, the last error is logged and stored in
 * [hcLaunchError] for the visible error text. The
 * "Why isn\'t this working?" expand-and-explain section
 * was added in the same pass so a user with a stuck
 * device has a self-service diagnosis path.
 */
class HealthConnectLaunchErrorFindingTest {

    private fun fileAt(relative: String): File {
        val candidates = listOf(relative, "../$relative", "../../$relative")
        return candidates.map(::File).firstOrNull { it.isFile }
            ?: error("$relative not found from working directory ${File(".").absolutePath}.")
    }

    private val screen: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/settings/SettingsScreen.kt",
        ).readText()

    @Test
    fun `the watch-connect launch is wrapped in runCatching (not a bare launcher-launch)`() {
        // The pre-v0.25.3 shape was:
        //   onClick = { healthConnectPermissionLauncher.launch(perms) }
        // A bare launch swallows the dispatch failure (the system
        // Health Connect activity could not be resolved, the user's
        // phone had a custom ROM with a different package, etc.).
        // v0.25.3 wrapped the SDK launcher in runCatching.
        // v0.26+ wraps the same launcher plus the two
        // fallback launches in runCatching. The pattern
        // check is the same: the launcher.launch() call
        // must be inside a runCatching block.
        val runCatchingIdx = screen.indexOf(
            "runCatching {\n" +
                "                            healthConnectPermissionLauncher.launch(",
        )
        assertTrue(
            "The onClick for the 'Connect to your watch' button must wrap " +
                "the launcher.launch(...) call in runCatching { ... } so a " +
                "dispatch failure (ActivityNotFoundException, " +
                "SecurityException, send-cancel) is captured. " +
                "A bare launch() is the pre-v0.25.3 silent-failure shape. " +
                "runCatchingIdx=$runCatchingIdx",
            runCatchingIdx >= 0,
        )
    }

    @Test
    fun `the launch is logcat-tagged (MindAnchor slash HealthConnect) for adb logcat capture`() {
        // The user (or a support contact) can capture a failing
        // device's logcat with `adb logcat -s MindAnchor/HealthConnect`
        // and have a known handle to grep for. v0.25.3 added a Log.w
        // at the launch site and a Log.e in the onFailure block.
        // v0.26+ extends the Log.e to the "all three launches
        // failed" path. The pattern check is the same: both tag
        // strings must appear.
        val launchLogIdx = screen.indexOf("Log.w(\"MindAnchor/HealthConnect\"")
        val failureLogIdx = screen.indexOf("Log.e(\"MindAnchor/HealthConnect\"")
        assertTrue(
            "The launch site must call Log.w with the " +
                "MindAnchor/HealthConnect tag so the launch attempt is " +
                "visible in adb logcat. " +
                "launchLogIdx=$launchLogIdx",
            launchLogIdx >= 0,
        )
        assertTrue(
            "The onFailure block must call Log.e with the " +
                "MindAnchor/HealthConnect tag so a dispatch failure " +
                "is visible in adb logcat. " +
                "failureLogIdx=$failureLogIdx",
            failureLogIdx >= 0,
        )
    }

    @Test
    fun `a failed launch surfaces a visible error text (not just a log line)`() {
        // A logcat-only error is invisible to a user who is not
        // running adb. v0.25.3 set a state on failure that the UI
        // reads to render an error string below the button. v0.26+
        // uses a [failure] local rather than [t] because the
        // 3-step fallback folds the three exceptions into one
        // for the assignment; the test pattern is updated to match.
        val stateIdx = screen.indexOf("hcLaunchError =")
        assertTrue(
            "The onFailure block must record the failure in a state " +
                "the UI can read. " +
                "stateIdx=$stateIdx",
            stateIdx >= 0,
        )
        // And the UI must render that state. The render site is the
        // visible-error Text below the button.
        val renderIdx = screen.indexOf("R.string.health_connect_launch_failed")
        assertTrue(
            "The visible error must use the health_connect_launch_failed " +
                "string resource. " +
                "renderIdx=$renderIdx",
            renderIdx >= 0,
        )
    }

    @Test
    fun `the launcher result handler clears the launch error (a real result means the dialog opened)`() {
        // When the system dialog actually opens and the user makes a
        // choice, the result callback fires with a non-null granted
        // set. At that point the visible error must clear, because
        // the previous failure (if any) is no longer the relevant
        // state. The handler must clear hcLaunchError, not leave the
        // stale error from a previous attempt visible.
        val clearIdx = screen.indexOf("hcLaunchError = null")
        assertTrue(
            "The launcher result handler must clear hcLaunchError so a " +
                "successful dialog return does not leave a stale error " +
                "from a previous failed attempt. " +
                "clearIdx=$clearIdx",
            clearIdx >= 0,
        )
    }

    @Test
    fun `v0_26+ 3-step fallback opens Health Connect main activity when the SDK launcher fails`() {
        // v0.26+ (3-step fallback) — the user reported the
        // button doing nothing on 2026-08-24. The fix
        // extends v0.25.3 to: try SDK launcher; if it
        // fails, try a direct intent to the Health Connect
        // app's main activity; if that fails, open the
        // Play Store listing. The fallback launch must
        // be wrapped in runCatching so a wedged provider
        // never takes down the launcher Activity.
        assertTrue(
            "The button onClick must try a direct intent to the " +
                "Health Connect app's main activity as a fallback when " +
                "the SDK launcher fails. Look for a second runCatching " +
                "block that calls context.startActivity(...) on a " +
                "Health Connect main intent. " +
                "fallbackOpenIdx=${screen.indexOf("fallbackOpen")}",
            screen.indexOf("fallbackOpen") >= 0,
        )
        assertTrue(
            "The fallback must target the Health Connect package " +
                "constants, not a hardcoded action string. Look for " +
                "HEALTH_CONNECT_PACKAGE / HEALTH_CONNECT_MAIN_ACTION.",
            screen.indexOf("HEALTH_CONNECT_PACKAGE") >= 0 &&
                screen.indexOf("HEALTH_CONNECT_MAIN_ACTION") >= 0,
        )
    }

    @Test
    fun `v0_26+ 'Why isn' 't this working' expandable section lists the three most common causes`() {
        // The v0.26+ "Why isn\'t this working?" section is
        // the self-service diagnosis path for the user who
        // hit the silent-failure on 2026-08-24. It must
        // list at least three causes (disabled, not
        // installed, outdated) and the strings must be
        // present in the resource table.
        val file = fileAt("app/src/main/res/values/strings.xml").readText()
        assertTrue(
            "The 'Why isn\\'t this working?' strings must be present. " +
                "Missing health_connect_why_header: ${
                    if (file.indexOf("health_connect_why_header") < 0) "yes" else "no"
                }",
            file.indexOf("health_connect_why_header") >= 0,
        )
        assertTrue(
            "The disabled-cause string must be present.",
            file.indexOf("health_connect_why_disabled") >= 0,
        )
        assertTrue(
            "The not-installed-cause string must be present.",
            file.indexOf("health_connect_why_installed") >= 0,
        )
        assertTrue(
            "The outdated-cause string must be present.",
            file.indexOf("health_connect_why_outdated") >= 0,
        )
    }
}
