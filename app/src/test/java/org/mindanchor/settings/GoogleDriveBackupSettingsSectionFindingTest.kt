package org.mindanchor.settings

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * File-shape pinning for
 * [GoogleDriveBackupSettingsSection]. v0.25.4
 * (WP-C).
 *
 * The Composable is the user-facing surface
 * for the v0.25.4 Google Drive backup. A
 * contributor who edits the file can silently
 * change the user contract (e.g. drop the
 * sign-in button, swap the toggle semantics,
 * remove the "Forget this account" path) and
 * nothing else would notice. These five tests
 * pin the shape that the SettingsScreen and
 * the WP-D scheduler depend on.
 *
 * The Composable is not directly testable in a
 * JVM-only Robolectric run (the activity
 * result launcher + the LocalContext are
 * bound to a real Activity); the file-shape
 * test is the contract the SettingsScreen
 * honors.
 */
class GoogleDriveBackupSettingsSectionFindingTest {

    private val sourcePath = "src/main/java/org/mindanchor/settings/GoogleDriveBackupSettingsSection.kt"
    private val source by lazy { File(sourcePath).readText() }

    @Test fun `file is in the settings package and is internal`() {
        assertTrue("package must be org.mindanchor.settings", source.contains("package org.mindanchor.settings"))
        // `internal` is the testable-but-not-
        // exported visibility: the SettingsScreen
        // uses it, the rest of the app does not.
        assertTrue("function must be internal", source.contains("internal fun GoogleDriveBackupSettingsSection"))
    }

    @Test fun `section uses GoogleDriveAuth + DataStore toggles from SettingsViewModel`() {
        val needs = listOf(
            "GoogleDriveAuth",
            "viewModel.autoSyncNotes",
            "viewModel.autoSyncLetters",
            "viewModel::setAutoSyncNotes",
            "viewModel::setAutoSyncLetters",
            "R.string.drive_section",
            "R.string.drive_explainer",
        )
        for (needle in needs) {
            assertTrue("must reference $needle", source.contains(needle))
        }
    }

    @Test fun `section wires the sign-in flow via ActivityResultLauncher and signInIntent`() {
        // The OAuth entry point is the
        // GoogleSignInClient.signInIntent,
        // dispatched via rememberLauncherForActivityResult.
        // A rename or signature change here would
        // break the integration with the
        // GoogleDriveAuth surface.
        assertTrue(
            "must import rememberLauncherForActivityResult",
            source.contains("rememberLauncherForActivityResult"),
        )
        assertTrue(
            "must use StartActivityForResult contract",
            source.contains("ActivityResultContracts.StartActivityForResult"),
        )
        assertTrue("must call auth.signInIntent()", source.contains("auth.signInIntent()"))
        assertTrue("must call auth.handleSignInResult", source.contains("auth.handleSignInResult"))
    }

    @Test fun `section shows the auto-sync toggles plus back-up-now plus forget-account only when signed in`() {
        // The opt-in shape: default state is
        // "Sign in with Google"; the toggles +
        // the "Back up now" + "Forget" buttons
        // are gated on signedInEmail being
        // non-null. The test pins both the
        // default-state branch and the
        // signed-in branch.
        assertTrue("must gate on signedInEmail", source.contains("signedInEmail"))
        assertTrue("must show drive_sign_in button on the default branch", source.contains("R.string.drive_sign_in"))
        assertTrue(
            "must show drive_signed_in_as with the email on the signed-in branch",
            source.contains("R.string.drive_signed_in_as"),
        )
        assertTrue("must show drive_auto_sync_notes toggle", source.contains("R.string.drive_auto_sync_notes"))
        assertTrue("must show drive_auto_sync_letters toggle", source.contains("R.string.drive_auto_sync_letters"))
        assertTrue("must show drive_backup_now button", source.contains("R.string.drive_backup_now"))
        assertTrue("must show drive_forget_account button", source.contains("R.string.drive_forget_account"))
    }

    @Test fun `section calls signOut on forget-account click`() {
        // The forget-account button is the
        // user's escape hatch from the bridge:
        // the local credentials are wiped, the
        // GoogleSignInClient.signOut is invoked,
        // and the surface flips back to
        // "Sign in with Google". The wire-up is
        // what makes the bridge revocable.
        assertTrue("must call auth.signOut", source.contains("auth.signOut()"))
        assertTrue(
            "must surface a drive_forgot message on success",
            source.contains("R.string.drive_forgot"),
        )
    }
}
