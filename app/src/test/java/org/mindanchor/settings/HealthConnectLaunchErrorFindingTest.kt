package org.mindanchor.settings

import org.junit.Assert.assertTrue
import org.junit.Test

import org.mindanchor.testing.TestFileUtil.fileAt
/**
 * Finding test for the v0.25.3-WP-B Health Connect launch-error
 * diagnostic surface.
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
 * The actual root-cause fix (the exact failure mode for the user's
 * specific device) is gated on an `adb logcat` capture from the
 * failing device. This test pins the diagnostic surface, not the
 * final fix.
 */
class HealthConnectLaunchErrorFindingTest {

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
        // The v0.25.3 fix wraps it in runCatching so the failure
        // reaches a state the UI can read.
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
        // and have a known handle to grep for. The v0.25.3 fix adds
        // a Log.w at the launch site and a Log.e in the onFailure
        // block.
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
        // running adb. The v0.25.3 fix sets a state on failure that
        // the UI reads to render an error string below the button.
        val stateIdx = screen.indexOf("hcLaunchError = t.javaClass.simpleName")
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
}
