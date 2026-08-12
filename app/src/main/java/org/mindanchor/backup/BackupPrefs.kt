package org.mindanchor.backup

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The per-type auto-sync toggles for the v0.25.4
 * Google Drive backup. v0.25.4 (WP-C).
 *
 * Two toggles, one per [ContentType] the v0.25.4
 * surface ships: notes and letters. Both default
 * to `false` — the launcher is "off by default;
 * opt-in" per the v0.23.0 design that the
 * v0.25.4 plan explicitly extends. A user who
 * signs in with Google but has not flipped either
 * toggle does not have anything syncing; the
 * toggle is the gate, not the sign-in.
 *
 * The on-write trigger (the WP-D scheduler) reads
 * the same DataStore; turning a toggle on is a
 * "from now on, append" decision, not a "backfill
 * everything that exists" decision. The "Back up
 * now" button in the Settings sub-section is the
 * manual full-reupload path; the toggles are the
 * streaming path.
 *
 * Distinct from [EncryptedBackupCodec] and
 * [TokenStore] (the at-rest secret stores) —
 * the toggles are not secrets, they are UI state,
 * so they live in plain DataStore.
 */
class BackupPrefs(private val context: Context) {

    private val autoSyncNotesKey = booleanPreferencesKey("auto_sync_notes")
    private val autoSyncLettersKey = booleanPreferencesKey("auto_sync_letters")

    /**
     * Whether new notes should be auto-appended
     * to `MindAnchor-Notes.txt` in the user's
     * Drive. Default `false` (opt-in).
     */
    val autoSyncNotes: Flow<Boolean> = context.backupDataStore.data
        .map { prefs -> prefs[autoSyncNotesKey] ?: false }

    /**
     * Whether new letters should be auto-appended
     * to `MindAnchor-Letters.txt` in the user's
     * Drive. Default `false` (opt-in).
     */
    val autoSyncLetters: Flow<Boolean> = context.backupDataStore.data
        .map { prefs -> prefs[autoSyncLettersKey] ?: false }

    suspend fun setAutoSyncNotes(enabled: Boolean) {
        context.backupDataStore.edit { it[autoSyncNotesKey] = enabled }
    }

    suspend fun setAutoSyncLetters(enabled: Boolean) {
        context.backupDataStore.edit { it[autoSyncLettersKey] = enabled }
    }

    // --- Pending backup queue (v0.25.5 WP-H) -------------------------------
    //
    // WorkManager offline retry for the Drive backup. The on-write
    // trigger (the WP-D scheduler) returns [AppendResult.NetworkError]
    // when the device is offline; the entry is then enqueued here
    // and re-attempted on the next [BackupRetryWorker] run, which
    // carries a NetworkType.CONNECTED constraint. The queue is
    // bounded at [MAX_PENDING] so a long offline stretch does not
    // grow the file unbounded — a trim happens on every enqueue.

    private val pendingBackupsKey = androidx.datastore.preferences.core.stringPreferencesKey(
        "pending_backups",
    )

    /** The pending backup queue, oldest first. Empty on any failure. */
    val pendingBackups: Flow<List<PendingBackup>> =
        context.backupDataStore.data.map { prefs ->
            PendingBackupLog.decode(prefs[pendingBackupsKey].orEmpty())
        }

    /**
     * Appends [entry] to the pending queue, trimming to
     * [MAX_PENDING] if the queue would grow past the bound. The
     * oldest entries are dropped first — a long offline stretch
     * keeps the most recent [MAX_PENDING] entries, which is what
     * the user cares about.
     */
    suspend fun enqueuePending(entry: PendingBackup) {
        context.backupDataStore.edit { prefs ->
            val current = PendingBackupLog.decode(prefs[pendingBackupsKey].orEmpty())
            val next = (current + entry).takeLast(MAX_PENDING)
            prefs[pendingBackupsKey] = PendingBackupLog.encode(next)
        }
    }

    /**
     * Removes a single entry from the pending queue after a
     * successful retry. The match is by (type, payload, queuedAt)
     * — the [PendingBackup] data class overrides equals for
     * payload (a ByteArray is reference-compared by default), so
     * the comparison is value-based.
     */
    suspend fun removePending(entry: PendingBackup) {
        context.backupDataStore.edit { prefs ->
            val current = PendingBackupLog.decode(prefs[pendingBackupsKey].orEmpty())
            val next = current.filterNot { it == entry }
            if (next.isEmpty()) prefs.remove(pendingBackupsKey)
            else prefs[pendingBackupsKey] = PendingBackupLog.encode(next)
        }
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

    /**
     * Test-only: drop the pending queue. Used by the round-trip
     * test's @Before to isolate tests in the same class.
     */
    internal suspend fun resetPending() {
        context.backupDataStore.edit { it.remove(pendingBackupsKey) }
    }

    companion object {
        private val Context.backupDataStore by preferencesDataStore(name = "backup_prefs")

        /**
         * The pending queue is bounded so a long offline stretch
         * does not grow the file unbounded. 100 entries is a
         * generous ceiling (the daily-active user writes maybe
         * a handful of notes and one letter); trimming the
         * oldest is the right move because the wire format is
         * sequence-of-appends — losing the oldest entries
         * means losing the earliest writes, which is the
         * cheaper direction.
         */
        const val MAX_PENDING = 100
    }
}
