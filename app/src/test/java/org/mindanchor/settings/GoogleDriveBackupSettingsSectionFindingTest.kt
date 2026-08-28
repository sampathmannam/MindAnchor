package org.mindanchor.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * File-shape pinning for [GoogleDriveBackupSettingsSection]. Task 12
 * (Program 0) — rewrites the v0.25.4 Google Drive per-type Notes/Letters
 * toggle surface into the continuity backup health/recovery-key/kill-switch
 * surface.
 *
 * The Composable is not directly testable in a JVM-only Robolectric run
 * (the activity result launchers + LocalContext are bound to a real
 * Activity) — same constraint the pre-Task-12 file's KDoc documented. The
 * file-shape test is the contract; the real interaction tests live in the
 * instrumented `ContinuitySettingsTest`.
 */
class GoogleDriveBackupSettingsSectionFindingTest {

    private val sourcePath = "src/main/java/org/mindanchor/settings/GoogleDriveBackupSettingsSection.kt"
    private val source by lazy { File(sourcePath).readText() }

    @Test fun `file is in the settings package and is internal`() {
        assertTrue("package must be org.mindanchor.settings", source.contains("package org.mindanchor.settings"))
        assertTrue(
            "function must be internal",
            source.contains("internal fun GoogleDriveBackupSettingsSection"),
        )
    }

    /**
     * The Task 12 investigation finding: the old per-type Notes/Letters
     * auto-sync toggles were wired to literal no-op `onCheckedChange`
     * lambdas, and a full-codebase search found no consumer of
     * `BackupPrefs.autoSyncNotes`/`autoSyncLetters` other than the old
     * ViewModel exposure and that dead toggle UI. Program 0's
     * `ContinuityWorkScheduler` now checkpoints Notes/Letters
     * unconditionally once continuity backup is on, so the fix is
     * deletion, not rewiring: this test proves the new file carries no
     * trace of the old toggle, the old scheduler-based "Back up now"
     * path, or the SettingsViewModel dependency that only existed to
     * expose those two dead flows.
     */
    @Test fun `old Notes-Letters auto-sync toggle is gone, not merely rewired`() {
        val removedNeedles = listOf(
            "autoSyncNotes",
            "autoSyncLetters",
            "R.string.drive_auto_sync_notes",
            "R.string.drive_auto_sync_letters",
            "viewModel: SettingsViewModel",
            "BackupScheduler(",
            "scheduler.backupAll()",
            "OkHttpClient()",
            "R.string.drive_backup_uploaded",
        )
        for (needle in removedNeedles) {
            assertFalse("must NOT reference $needle (the dead per-type toggle path)", source.contains(needle))
        }
    }

    @Test fun `section still reuses the existing GoogleDriveAuth sign-in machinery unchanged`() {
        val needs = listOf(
            "GoogleDriveAuth",
            "auth.signInIntent()",
            "auth.handleSignInResult",
            "auth.signOut()",
            "rememberLauncherForActivityResult",
            "ActivityResultContracts.StartActivityForResult",
            "R.string.drive_sign_in",
            "R.string.drive_signed_in_as",
            "R.string.drive_forget_account",
            "R.string.drive_forgot",
        )
        for (needle in needs) {
            assertTrue("must reference $needle", source.contains(needle))
        }
    }

    @Test fun `recovery key generate-and-verify flow requires full re-entry, never a checkbox`() {
        val needs = listOf(
            "RecoveryKeyStore",
            "RecoveryKeyCodec.generate",
            "RecoveryKeyCodec.format",
            "RecoveryKeyCodec.decode",
            "recoveryKeyStore.save(",
            "recoveryKeyStore.markVerified()",
            "recoveryKeyStore.isVerified()",
            "NOT_CREATED",
            "NEEDS_VERIFICATION",
            "VERIFIED",
        )
        for (needle in needs) {
            assertTrue("must reference $needle", source.contains(needle))
        }
        // The verify path must compare a *decoded, retyped* key, never a
        // bare acknowledgement flag/checkbox.
        assertFalse("must not accept a bare acknowledgement flag as verification", source.contains("iSavedItChecked"))
    }

    @Test fun `backup switch is gated on sign-in and a verified key, disabling never deletes data`() {
        val needs = listOf(
            "canEnableBackup",
            "continuityPrefs.setBackupEnabled",
            "requestCheckpoint(context)",
            "ensureNightlyScheduled(context)",
            "cancelAllWork(context)",
            "ContinuityWorkScheduler::requestCheckpoint",
            "ContinuityWorkScheduler::ensureNightlyScheduled",
            "ContinuityWorkScheduler::cancelAll",
        )
        for (needle in needs) {
            assertTrue("must reference $needle", source.contains(needle))
        }
        // No delete-style call anywhere in the file.
        val deleteNeedles = listOf(".delete(", "DELETE FROM", "clear()")
        for (needle in deleteNeedles) {
            assertFalse("must never call a delete-style operation ($needle)", source.contains(needle))
        }
    }

    @Test fun `context extraction kill switch is wired to ContinuityPrefs only`() {
        assertTrue(
            "must call continuityPrefs.setContextExtractionEnabled",
            source.contains("continuityPrefs.setContextExtractionEnabled"),
        )
        // Task 12 deliberately does not wire the gate into Journal
        // writing — see this file's setContextExtractionEnabled KDoc and
        // the task report.
        assertFalse("must not reference JournalRepository", source.contains("JournalRepository"))
        assertFalse("must not reference StructuralContextExtractor", source.contains("StructuralContextExtractor"))
    }

    @Test fun `back-up-now schedules a checkpoint and never performs direct network IO`() {
        assertTrue("must call requestCheckpoint on click", source.contains("fun backUpNow()"))
        assertTrue(
            "must reference ContinuityMessage.CheckpointRequested",
            source.contains("ContinuityMessage.CheckpointRequested"),
        )
        assertFalse("must not construct an OkHttpClient directly", source.contains("OkHttpClient"))
    }

    @Test fun `restore button targets RestoreActivity`() {
        assertTrue(
            "must launch an Intent targeting RestoreActivity",
            source.contains("Intent(context, RestoreActivity::class.java)"),
        )
    }

    @Test fun `save-encrypted-copy uses the continuity envelope codec, not the legacy plaintext export`() {
        val needs = listOf(
            "BackupEnvelopeCodec.encrypt",
            "BackupEnvelopeCodec.encode",
            "ContinuitySnapshotCodec.encode",
            "BackupRepository.write",
            "ActivityResultContracts.CreateDocument(\"application/octet-stream\")",
        )
        for (needle in needs) {
            assertTrue("must reference $needle", source.contains(needle))
        }
    }

    @Test fun `research export uses the shared ResearchExportBuilder and warns before writing`() {
        val needs = listOf(
            "ResearchExportBuilder.export",
            "ResearchExportBuilder.fileName()",
            "ActivityResultContracts.CreateDocument(\"application/json\")",
            "showResearchPrivacyDialog",
            "R.string.continuity_export_research_privacy_body",
        )
        for (needle in needs) {
            assertTrue("must reference $needle", source.contains(needle))
        }
    }

    @Test fun `health section maps every BackupHealth variant to its own exact string resource`() {
        val needs = mapOf(
            "BackupHealth.Verified" to "R.string.continuity_health_verified",
            "BackupHealth.Pending" to "R.string.continuity_health_pending",
            "BackupHealth.NeedsSignIn" to "R.string.continuity_health_needs_sign_in",
            "BackupHealth.RecoveryKeyRequired" to "R.string.continuity_health_recovery_key_required",
            "BackupHealth.VerificationFailed" to "R.string.continuity_health_verification_failed",
        )
        for ((variant, stringRes) in needs) {
            assertTrue("must reference $variant", source.contains(variant))
            assertTrue("must reference $stringRes", source.contains(stringRes))
        }
        assertTrue("must call BackupHealth.compute", source.contains("BackupHealth.compute("))
        assertTrue(
            "must surface the last verified nightly time",
            source.contains("R.string.continuity_health_last_nightly"),
        )
        assertTrue("must surface the last restore time", source.contains("R.string.continuity_health_last_restore"))
    }

    /**
     * The hard product-honesty rule: no string this file renders ever
     * says an upload alone was "backed up" — only BackupHealth.Verified's
     * own copy may claim a verified backup. Scans the Kotlin source
     * itself (not just the strings.xml resource *values*, which a
     * separate check below also covers) so a hardcoded literal would be
     * caught too.
     */
    @Test fun `no string anywhere conflates an upload with a verified backup`() {
        val lower = source.lowercase()
        val occurrences = Regex("backed up").findAll(lower).toList()
        assertTrue(
            "the phrase 'backed up' must never appear in this file " +
                "(found ${occurrences.size} occurrence(s)) — see continuity_checkpoint_requested / " +
                "continuity_health_verified for the only sanctioned phrasing",
            occurrences.isEmpty(),
        )
    }

    @Test fun `forget account still calls signOut`() {
        assertTrue("must call auth.signOut", source.contains("auth.signOut()"))
        assertTrue(
            "must surface a drive_forgot message on success",
            source.contains("ContinuityMessage.Forgotten"),
        )
    }
}
