package org.mindanchor.continuity

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.continuityDataStore by preferencesDataStore(name = "continuity_prefs")

/**
 * The closed set of continuity-backup error conditions the UI can surface
 * (see [BackupHealth]). Stored on disk as [Enum.name] — the same
 * "strings on disk, not enum ordinals" convention [org.mindanchor.data.SunsetPrefs]'s
 * `Chronotype` storage uses (Task 2): an ordinal shifts silently if a case
 * is ever inserted; a name string is stable and self-describing, and an
 * unrecognised name degrades to [NONE] on read rather than crashing.
 */
enum class ContinuityErrorCode { NONE, AUTH, NETWORK, KEY_MISSING, VERIFY_FAILED, DECODE_FAILED, RESTORE_VERIFY_FAILED }

/**
 * Local flags and health state for Program 0's continuity backup
 * (Task 10). Two kinds of content live here, both intentionally narrow:
 *
 *  - **Flags**: [backupEnabled], [contextExtractionEnabled],
 *    [nightlySnapshotsEnabled] — plain opt-in switches.
 *  - **Health**: the last verified checkpoint / nightly snapshot's
 *    time+id+hash, the last restore's time+hash, [dirtySince], and the
 *    last [ContinuityErrorCode]. [CheckpointBackupWorker] and
 *    [NightlySnapshotWorker] write these; [BackupHealth] reads them to
 *    derive the UI-facing state.
 *
 * CRITICAL: every field stored here is a timestamp, an opaque id/hash
 * string, a boolean, a count of records, or a closed [ContinuityErrorCode] — never an
 * exception message, Journal text, an access token, or recovery-key
 * material. Nothing in this class ever logs a field's value either.
 */
class ContinuityPrefs(private val context: Context) {

    private val backupEnabledKey = booleanPreferencesKey("backup_enabled")
    private val contextExtractionEnabledKey = booleanPreferencesKey("context_extraction_enabled")
    private val nightlySnapshotsEnabledKey = booleanPreferencesKey("nightly_snapshots_enabled")

    private val lastCheckpointAtKey = longPreferencesKey("last_checkpoint_at")
    private val lastCheckpointIdKey = stringPreferencesKey("last_checkpoint_id")
    private val lastCheckpointHashKey = stringPreferencesKey("last_checkpoint_hash")

    private val lastNightlyAtKey = longPreferencesKey("last_nightly_at")
    private val lastNightlyIdKey = stringPreferencesKey("last_nightly_id")
    private val lastNightlyHashKey = stringPreferencesKey("last_nightly_hash")

    private val lastRestoreAtKey = longPreferencesKey("last_restore_at")
    private val lastRestoreHashKey = stringPreferencesKey("last_restore_hash")

    private val ledgerHighWaterCountKey = intPreferencesKey("ledger_high_water_count")
    private val ledgerHighWaterHeadKey = stringPreferencesKey("ledger_high_water_head")

    private val dirtySinceKey = longPreferencesKey("dirty_since")
    private val lastErrorCodeKey = stringPreferencesKey("last_error_code")

    /** Off until asked for, like every other feature in this app. */
    val backupEnabled: Flow<Boolean> = context.continuityDataStore.data.map { it[backupEnabledKey] ?: false }

    suspend fun setBackupEnabled(value: Boolean) {
        context.continuityDataStore.edit { it[backupEnabledKey] = value }
    }

    /**
     * Whether Journal entries derive structural context on save. Read by
     * a later task's gating; Task 10 only stores the flag (see this
     * class's KDoc and the plan's Task 10 brief).
     */
    val contextExtractionEnabled: Flow<Boolean> =
        context.continuityDataStore.data.map { it[contextExtractionEnabledKey] ?: true }

    suspend fun setContextExtractionEnabled(value: Boolean) {
        context.continuityDataStore.edit { it[contextExtractionEnabledKey] = value }
    }

    /**
     * The user's own nightly-snapshot preference. Defaults to true, but
     * the *effective* schedule decision is `backupEnabled && nightlySnapshotsEnabled`
     * — computed by the caller ([org.mindanchor.continuity.ContinuityWorkScheduler]),
     * not baked in here, since this flag's stored default alone does not
     * mean "nightly snapshots are running" for a user who has never
     * turned backup on at all.
     */
    val nightlySnapshotsEnabled: Flow<Boolean> =
        context.continuityDataStore.data.map { it[nightlySnapshotsEnabledKey] ?: true }

    suspend fun setNightlySnapshotsEnabled(value: Boolean) {
        context.continuityDataStore.edit { it[nightlySnapshotsEnabledKey] = value }
    }

    /** The last verified checkpoint's time/id/hash, or null fields if none has ever verified. */
    val lastCheckpoint: Flow<VerifiedRecord?> = context.continuityDataStore.data.map { prefs ->
        verifiedRecordOf(prefs[lastCheckpointAtKey], prefs[lastCheckpointIdKey], prefs[lastCheckpointHashKey])
    }

    /** The last verified nightly snapshot's time/id/hash, or null if none has ever verified. */
    val lastNightly: Flow<VerifiedRecord?> = context.continuityDataStore.data.map { prefs ->
        verifiedRecordOf(prefs[lastNightlyAtKey], prefs[lastNightlyIdKey], prefs[lastNightlyHashKey])
    }

    /** The last restore's time + the restored content's hash, or null if never restored. */
    val lastRestore: Flow<RestoreRecord?> = context.continuityDataStore.data.map { prefs ->
        val at = prefs[lastRestoreAtKey]
        val hash = prefs[lastRestoreHashKey]
        if (at == null || hash == null) null else RestoreRecord(at, hash)
    }

    /**
     * Null when there is no unconfirmed change; otherwise the epoch-millis
     * of the earliest save that no verified checkpoint has covered yet.
     */
    val dirtySince: Flow<Long?> = context.continuityDataStore.data.map { it[dirtySinceKey] }

    /**
     * Unrecognised/corrupt stored names degrade to [ContinuityErrorCode.NONE]
     * rather than crash — same fail-soft read as [org.mindanchor.data.SunsetPrefs.chronotype].
     */
    val lastErrorCode: Flow<ContinuityErrorCode> = context.continuityDataStore.data.map { prefs ->
        prefs[lastErrorCodeKey]?.let { name ->
            runCatching { ContinuityErrorCode.valueOf(name) }.getOrNull()
        } ?: ContinuityErrorCode.NONE
    }

    /** Marks [at] as the start of an unconfirmed change, unless one is already recorded. */
    suspend fun markDirtyIfNotAlready(at: Long) {
        context.continuityDataStore.edit { prefs ->
            if (prefs[dirtySinceKey] == null) prefs[dirtySinceKey] = at
        }
    }

    /** Records [code] as the last continuity-backup error. */
    suspend fun recordError(code: ContinuityErrorCode) {
        context.continuityDataStore.edit { it[lastErrorCodeKey] = code.name }
    }

    /**
     * Records a verified checkpoint: [at]/[snapshotId]/[contentHash], clears
     * [dirtySince] (this checkpoint covers every change up to and including
     * it), and resets the error code to [ContinuityErrorCode.NONE].
     */
    suspend fun recordCheckpointVerified(at: Long, snapshotId: String, contentHash: String) {
        context.continuityDataStore.edit { prefs ->
            prefs[lastCheckpointAtKey] = at
            prefs[lastCheckpointIdKey] = snapshotId
            prefs[lastCheckpointHashKey] = contentHash
            prefs[lastErrorCodeKey] = ContinuityErrorCode.NONE.name
            prefs.remove(dirtySinceKey)
        }
    }

    /** Records a verified nightly snapshot. Does not touch [dirtySince] or the checkpoint fields. */
    suspend fun recordNightlyVerified(at: Long, snapshotId: String, contentHash: String) {
        context.continuityDataStore.edit { prefs ->
            prefs[lastNightlyAtKey] = at
            prefs[lastNightlyIdKey] = snapshotId
            prefs[lastNightlyHashKey] = contentHash
        }
    }

    /**
     * The largest research ledger this device has ever held: how many
     * events, and the hash of the newest one.
     *
     * This is the part of the chain that cannot live inside the chain.
     * Removing the newest events leaves a shorter but perfectly
     * self-consistent history, so the chain alone cannot see the loss — a
     * count recorded somewhere else can.
     *
     * A **high-water mark**, deliberately, not a running mirror. It only
     * ever rises, so a write path that forgets to refresh it weakens
     * detection but can never raise a false alarm; and a replacement phone
     * starts at zero, so a restore is not mistaken for a truncation. Only
     * a ledger that has *shrunk* below the mark is evidence of loss.
     */
    val ledgerHighWater: Flow<LedgerHighWater?> = context.continuityDataStore.data.map { prefs ->
        val count = prefs[ledgerHighWaterCountKey]
        val head = prefs[ledgerHighWaterHeadKey]
        if (count == null || head == null) null else LedgerHighWater(count, head)
    }

    /** Raises the mark to [eventCount]/[headHash]. Never lowers it — see [ledgerHighWater]. */
    suspend fun raiseLedgerHighWater(eventCount: Int, headHash: String) {
        context.continuityDataStore.edit { prefs ->
            // Strictly greater: an equal count with a different head is
            // not a higher mark, and overwriting the head on equality would
            // quietly replace the recorded anchor with a rewritten one.
            if ((prefs[ledgerHighWaterCountKey] ?: 0) < eventCount) {
                prefs[ledgerHighWaterCountKey] = eventCount
                prefs[ledgerHighWaterHeadKey] = headHash
            }
        }
    }

    /** Records a restore's time and the restored content's hash. */
    suspend fun recordRestore(at: Long, contentHash: String) {
        context.continuityDataStore.edit { prefs ->
            prefs[lastRestoreAtKey] = at
            prefs[lastRestoreHashKey] = contentHash
        }
    }

    private fun verifiedRecordOf(at: Long?, id: String?, hash: String?): VerifiedRecord? =
        if (at == null || id == null || hash == null) null else VerifiedRecord(at, id, hash)

    /**
     * Test-only: clears every key in the underlying DataStore. Mirrors
     * [org.mindanchor.backup.BackupPrefs.reset] and the same-named helper
     * on several other DataStore-backed prefs classes in this codebase —
     * DataStore is a process-wide singleton keyed on the preferences name,
     * so tests in the same class (or process) share state without an
     * explicit reset. Production code never calls this.
     */
    internal suspend fun reset() {
        context.continuityDataStore.edit { it.clear() }
    }

    /** A verified checkpoint or nightly snapshot's time, snapshot id, and content hash. */
    data class VerifiedRecord(val at: Long, val snapshotId: String, val contentHash: String)

    /** A restore's time and the restored content's hash. */
    data class RestoreRecord(val at: Long, val contentHash: String)

    /** The largest ledger this device has held: [eventCount] events ending at [headHash]. */
    data class LedgerHighWater(val eventCount: Int, val headHash: String)
}
