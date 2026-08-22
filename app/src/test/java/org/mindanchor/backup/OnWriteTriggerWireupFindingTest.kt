package org.mindanchor.backup

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.25.6+ WP-1: the on-write Drive backup trigger
 * was unwired before this fix. The data layer
 * (v0.25.5-WP-H: [PendingBackup] queue + the
 * retry worker) was in place; the [BackupScheduler]'s
 * [start] method was defined but never called. The
 * settings sub-section's auto-sync toggles were
 * no-ops (`onCheckedChange = { /* no-op comment */ }`),
 * so even if [start] had been called, the trigger's
 * gate would have been the default `false` for
 * every user.
 *
 * The five tests below pin the wire-up:
 *  1. [BackupScheduler.startIfNeeded] exists and is
 *     idempotent (a `started` flag + double-checked
 *     lock).
 *  2. [BackupScheduler.start]'s collectors read the
 *     auto-sync toggle as the gate.
 *  3. [HomeActivity.onCreate] calls
 *     [BackupScheduler.startIfNeeded].
 *  4. [GoogleDriveBackupSettingsSection] writes the
 *     notes auto-sync toggle to the ViewModel.
 *  5. [GoogleDriveBackupSettingsSection] writes the
 *     letters auto-sync toggle to the ViewModel.
 *
 * A regression that left the trigger unwired — or
 * that re-introduced the no-op `onCheckedChange` —
 * would surface as a silent-failure mode: new
 * notes are written but never appear in the
 * user's Drive. The finding tests are the cheapest
 * way to keep the wire-up honest.
 */
class OnWriteTriggerWireupFindingTest {

    @Test
    fun `BackupScheduler startIfNeeded exists and uses an idempotent started flag`() {
        // The wire-up entry point. Before v0.25.6+ WP-1
        // the only way to start the trigger was to
        // construct a scheduler and call start manually,
        // and no production code did. The static
        // startIfNeeded is the production entry point,
        // and the `started` flag is the guard against
        // double-start (the activity is recreated on
        // every config change).
        val source = readSource("BackupScheduler.kt")
        assertNotNull(source)
        assertTrue(
            "BackupScheduler has a static startIfNeeded(context)",
            source!!.contains("fun startIfNeeded(context: Context)"),
        )
        assertTrue(
            "BackupScheduler has a started guard",
            source.contains("private var started") && source.contains("synchronized(this)"),
        )
    }

    @Test
    fun `BackupScheduler start reads the auto-sync toggle as the gate`() {
        // The collectors must check the toggle
        // before calling encryptAndAppend. Before
        // v0.25.6+ WP-1 the collectors always
        // appended, so a user who had not opted
        // in would have their notes auto-backed-up
        // (and the auth-failed entries enqueued).
        val source = readSource("BackupScheduler.kt")
        assertNotNull(source)
        assertTrue(
            "BackupScheduler.start reads autoSyncNotes as a gate",
            source!!.contains("autoSyncNotes.first()") &&
                source.contains("if (!autoSyncOn) return@collect"),
        )
        assertTrue(
            "BackupScheduler.start reads autoSyncLetters as a gate",
            source.contains("autoSyncLetters.first()"),
        )
    }

    @Test
    fun `HomeActivity onCreate calls BackupScheduler startIfNeeded`() {
        // The wire-up site. A regression that
        // dropped this call would leave the
        // trigger silent — the user would
        // see no error, the data would just
        // never reach Drive.
        val source = readSource("HomeActivity.kt")
        assertNotNull(source)
        assertTrue(
            "HomeActivity.onCreate calls BackupScheduler.startIfNeeded",
            source!!.contains("BackupScheduler.startIfNeeded(applicationContext)"),
        )
    }

    @Test
    fun `GoogleDriveBackupSettingsSection writes the notes auto-sync toggle`() {
        // The Settings sub-section's
        // onCheckedChange was a no-op
        // before v0.25.6+ WP-1 — the user
        // could flip the Switch and the
        // preference was never written. A
        // regression that re-introduced the
        // no-op would re-create the
        // silent-failure mode.
        val source = readSource("GoogleDriveBackupSettingsSection.kt")
        assertNotNull(source)
        assertTrue(
            "Settings onCheckedChange writes autoSyncNotes via viewModel",
            source!!.contains("viewModel.setAutoSyncNotes(enabled)"),
        )
    }

    @Test
    fun `GoogleDriveBackupSettingsSection writes the letters auto-sync toggle`() {
        val source = readSource("GoogleDriveBackupSettingsSection.kt")
        assertNotNull(source)
        assertTrue(
            "Settings onCheckedChange writes autoSyncLetters via viewModel",
            source!!.contains("viewModel.setAutoSyncLetters(enabled)"),
        )
    }

    private fun readSource(filename: String): String? = runCatching {
        val candidates = listOf(
            "app/src/main/java/org/mindanchor/backup/$filename",
            "app/src/main/java/org/mindanchor/settings/$filename",
            "app/src/main/java/org/mindanchor/$filename",
            "../app/src/main/java/org/mindanchor/backup/$filename",
            "../app/src/main/java/org/mindanchor/settings/$filename",
            "../app/src/main/java/org/mindanchor/$filename",
        )
        candidates.firstNotNullOfOrNull { path ->
            val file = java.io.File(path)
            if (file.exists()) file.readText() else null
        }
    }.getOrNull()
}
