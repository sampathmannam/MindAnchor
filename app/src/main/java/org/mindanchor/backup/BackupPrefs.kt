package org.mindanchor.backup

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The single toggle gating the Google Drive backup.
 *
 * v0.70.7: replaces the v0.25.4 per-type
 * `autoSyncNotes`/`autoSyncLetters` pair. Those two
 * toggles gated a streaming on-write trigger that was
 * built but never started from production code — the
 * Settings switches for them were themselves wired to
 * no-op callbacks, so the whole path was dead in both
 * directions. The backup now runs as one nightly full
 * resync ([org.mindanchor.backup.DriveNightlySync])
 * covering every [ContentType], so one toggle is the
 * complete gate: off means nothing syncs, on means
 * everything does, once a night, plus on demand via
 * the "Back up now" button.
 *
 * Off by default, matching this app's opt-in-everywhere
 * convention. A user who signs in with Google but has
 * not flipped this toggle has nothing syncing — the
 * toggle is the gate, not the sign-in.
 *
 * Distinct from [TokenStore] (the at-rest secret
 * store) — this toggle is not a secret, it is UI
 * state, so it lives in plain DataStore.
 */
class BackupPrefs(private val context: Context) {

    private val driveNightlySyncEnabledKey = booleanPreferencesKey("drive_nightly_sync_enabled")

    /** Whether the nightly Google Drive backup is on. Default `false` (opt-in). */
    val driveNightlySyncEnabled: Flow<Boolean> = context.backupDataStore.data
        .map { prefs -> prefs[driveNightlySyncEnabledKey] ?: false }

    suspend fun setDriveNightlySyncEnabled(enabled: Boolean) {
        context.backupDataStore.edit { it[driveNightlySyncEnabledKey] = enabled }
    }

    private val lastSyncDayKey = stringPreferencesKey("drive_last_sync_day")

    /**
     * The calendar day [DriveNightlySync] last actually ran
     * [BackupScheduler.backupAll] to completion. Compared against
     * today by [DriveSyncSchedule.decide] the same way
     * [org.mindanchor.report.ReportStore.generatedDay] gates the
     * nightly report — once tonight's backup has run, a later
     * alarm within the same retry window does nothing further
     * rather than re-attempting a sync that already happened.
     */
    val lastSyncDay: Flow<String?> = context.backupDataStore.data
        .map { prefs -> prefs[lastSyncDayKey] }

    suspend fun setLastSyncDay(day: String) {
        context.backupDataStore.edit { it[lastSyncDayKey] = day }
    }

    /**
     * Clears every key in the underlying DataStore.
     * Test-only — used by the round-trip test's
     * `@Before` to isolate tests in the same class
     * (DataStore is a process-wide singleton
     * keyed on the preferences name, so two tests
     * in the same class share state without an
     * explicit reset). `internal` so the test can
     * call it; production code never clears the
     * store.
     */
    internal suspend fun reset() {
        context.backupDataStore.edit { it.clear() }
    }

    companion object {
        private val Context.backupDataStore by preferencesDataStore(name = "backup_prefs")
    }
}
