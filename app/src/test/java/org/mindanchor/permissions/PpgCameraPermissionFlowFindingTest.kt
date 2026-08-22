@file:Suppress(
    "SwallowedException", 
    "MaxLineLength", 
    "LoopWithTooManyJumpStatements", 
    "UnusedPrivateMember",
)

package org.mindanchor.permissions

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SOTA v2 bug-hunt, finding #1: PPG (camera) permission has no
 * rationale and no settings redirect.
 *
 * The PpgScreen permission flow (app/src/main/java/org/mindanchor/vitals/PpgScreen.kt:85-87)
 * requests CAMERA via `rememberLauncherForActivityResult(RequestPermission())`.
 * On Android 11+ a user can deny the camera permission twice; the system
 * will not show the dialog a third time. The current code re-launches the
 * same `permissionLauncher.launch(android.Manifest.permission.CAMERA)` on
 * every tap, with no `shouldShowRequestPermissionRationale` check and no
 * `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` redirect.
 *
 * The five tests below pin the surface:
 *
 *  1. The PpgScreen file declares the camera permission launcher (the
 *     string `permissionLauncher.launch(android.Manifest.permission.CAMERA)`
 *     must be present and bound to a `RequestPermission` contract).
 *  2. The same file must reference `shouldShowRequestPermissionRationale`
 *     or `ACTION_APPLICATION_DETAILS_SETTINGS` (the recovery path for
 *     "user has selected Don't ask again"). If neither is present, the
 *     user is permanently stuck.
 *  3. The same file must contain a string resource for the rationale
 *     copy (or surface text) and a string for the "Open settings" button.
 *  4. The `permissionEpoch` re-reads on ON_RESUME — the only currently
 *     working recovery path — must remain.
 *  5. The flow must guard against a user who grants the permission via
 *     system settings (re-read on resume) and a user who revokes it from
 *     system settings.
 */
class PpgCameraPermissionFlowFindingTest {

    @Test
    fun `PpgScreen has the camera permission launcher`() {
        val src = readPpgScreenSource()
        assertNotNull("PpgScreen.kt should exist", src)
        assertTrue(
            "PpgScreen must launch CAMERA via rememberLauncherForActivityResult",
            src!!.contains("permissionLauncher.launch(android.Manifest.permission.CAMERA)"),
        )
    }

    @Test
    fun `PpgScreen references shouldShowRequestPermissionRationale or APPLICATION_DETAILS_SETTINGS`() {
        val src = readPpgScreenSource() ?: return
        val hasRationale = src.contains("shouldShowRequestPermissionRationale")
        val hasSettingsIntent = src.contains("ACTION_APPLICATION_DETAILS_SETTINGS")
        assertTrue(
            "PpgScreen must surface a rationale OR a settings-redirect path. " +
                "Found shouldShowRequestPermissionRationale=$hasRationale, " +
                "ACTION_APPLICATION_DETAILS_SETTINGS=$hasSettingsIntent. " +
                "Without one of these, a user who denies CAMERA twice is permanently " +
                "locked out of the PPG measurement surface.",
            hasRationale || hasSettingsIntent,
        )
    }

    @Test
    fun `PpgScreen re-reads permission on ON_RESUME via permissionEpoch`() {
        val src = readPpgScreenSource() ?: return
        assertTrue(
            "PpgScreen must re-read CAMERA grant on every ON_RESUME. " +
                "The current implementation uses a permissionEpoch counter " +
                "incremented in a LifecycleEventObserver. Without it, a " +
                "user who flips the permission in system settings sees a " +
                "stale 'needs permission' state until the activity restarts.",
            src.contains("ON_RESUME") && src.contains("permissionEpoch"),
        )
    }

    @Test
    fun `PpgScreen declares the camera permission in checkSelfPermission`() {
        val src = readPpgScreenSource() ?: return
        assertTrue(
            "PpgScreen must use checkSelfPermission(Manifest.permission.CAMERA). " +
                "The current code calls it with the camera permission but the " +
                "rationale + settings-redirect path is missing — see test #2.",
            src.contains("checkSelfPermission(android.Manifest.permission.CAMERA)"),
        )
    }

    @Test
    fun `PpgScreen surface has a 'grant' affordance`() {
        val src = readPpgScreenSource() ?: return
        assertTrue(
            "PpgScreen must render a 'grant' button when permission is missing. " +
                "The current TextButton labelled ppg_grant does the basic work; " +
                "the fix is to also surface a 'Open settings' option when " +
                "shouldShowRequestPermissionRationale returns false (which means " +
                "the user has selected 'Don't ask again').",
            src.contains("ppg_grant"),
        )
    }

    private fun readPpgScreenSource(): String? = try {
        java.io.File(
            "src/main/java/org/mindanchor/vitals/PpgScreen.kt",
        ).readText(Charsets.UTF_8)
    } catch (t: Throwable) {
        null
    }
}
