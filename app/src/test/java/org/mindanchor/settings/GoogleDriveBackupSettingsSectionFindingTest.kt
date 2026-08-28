package org.mindanchor.settings

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * File-shape pinning for
 * [GoogleDriveBackupSettingsSection]. v0.25.4
 * (WP-C); re-pinned for the v0.70.7 single-toggle,
 * backup-plus-restore rewrite.
 *
 * The Composable is the user-facing surface for
 * the Google Drive backup. A contributor who edits
 * the file can silently change the user contract
 * (e.g. drop the sign-in button, swap the toggle
 * semantics, remove the "Forget this account" or
 * the restore path) and nothing else would notice.
 * These tests pin the shape the SettingsScreen and
 * [org.mindanchor.backup.DriveNightlySync] depend on.
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

    @Test fun `section uses GoogleDriveAuth + the DataStore toggle from SettingsViewModel`() {
        val needs = listOf(
            "GoogleDriveAuth",
            "viewModel.driveNightlySyncEnabled",
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

    @Test fun `section shows the sync toggle plus back-up-now plus restore plus forget-account only when signed in`() {
        // The opt-in shape: default state is
        // "Sign in with Google"; the toggle +
        // the "Back up now" / "Restore" / "Forget"
        // buttons are gated on signedInEmail being
        // non-null. The test pins both the
        // default-state branch and the
        // signed-in branch.
        assertTrue("must gate on signedInEmail", source.contains("signedInEmail"))
        assertTrue("must show drive_sign_in button on the default branch", source.contains("R.string.drive_sign_in"))
        assertTrue(
            "must show drive_signed_in_as with the email on the signed-in branch",
            source.contains("R.string.drive_signed_in_as"),
        )
        assertTrue("must show drive_nightly_sync toggle", source.contains("R.string.drive_nightly_sync"))
        assertTrue("must show drive_backup_now button", source.contains("R.string.drive_backup_now"))
        assertTrue("must show drive_restore_now button", source.contains("R.string.drive_restore_now"))
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

    @Test fun `Back up now button dispatches BackupScheduler across all four content types`() {
        // The "Back up now" button builds one Drive target per
        // content type via buildScheduler and calls backupAll.
        // The surface is what wires the user's manual click to
        // the scheduler's delta sync.
        assertTrue("must instantiate BackupScheduler", source.contains("BackupScheduler("))
        assertTrue("must call backupAll", source.contains(".backupAll()"))
        assertTrue("must show drive_backup_uploaded on success", source.contains("R.string.drive_backup_uploaded"))
        assertTrue("must show drive_upload_failed on failure", source.contains("R.string.drive_upload_failed"))
        val targetTypes = listOf(
            "ContentType.Notes",
            "ContentType.Letters",
            "ContentType.CheckIns",
            "ContentType.WellnessReadings",
        )
        for (type in targetTypes) {
            assertTrue("must build a GoogleDriveBackupTarget for $type", source.contains(type))
        }
    }

    @Test fun `Restore from Google Drive button dispatches BackupScheduler restoreAll`() {
        // The reverse of "Back up now" — the path a new phone,
        // signed into the same account, uses to pick up whatever
        // this phone already backed up. Losing this silently
        // breaks the "when I change my mobile phone" half of the
        // feature while backupAll keeps passing every test.
        assertTrue("must call restoreAll", source.contains(".restoreAll()"))
        assertTrue("must show drive_restore_done", source.contains("R.string.drive_restore_done"))
        assertTrue("must show drive_restore_nothing", source.contains("R.string.drive_restore_nothing"))
        assertTrue("must show drive_restore_failed", source.contains("R.string.drive_restore_failed"))
    }
}
