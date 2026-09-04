package org.mindanchor.continuity

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.restoreDataStore by preferencesDataStore(name = "continuity_restore_prefs")

/**
 * The staged-restore state machine's five nonterminal-or-terminal stages
 * (Task 11 brief, verbatim). Stored on disk as [Enum.name] — the same
 * "strings on disk, not enum ordinals" convention [ContinuityErrorCode]
 * uses, for the same reason (a name is stable across a case being
 * inserted; an ordinal silently shifts).
 */
enum class RestoreStage { NONE, DOWNLOADED, DECRYPTED, ROOM_MERGED, DATASTORES_MERGED, VERIFIED }

/**
 * A point-in-time read of the persisted restore state: which [stage] the
 * in-progress (or completed, or never-started) restore is at, plus the
 * fields [RestoreCoordinator] needs to resume from that stage without
 * re-prompting the user or re-decrypting anything it doesn't have to.
 *
 * [remoteName] and [envelopeSha256] are recorded at [RestoreStage.DOWNLOADED]
 * (the remote object's name and the staged ciphertext's own hash, for
 * corruption detection on resume). [expectedContentHash] is the decrypted
 * snapshot's own `contentSha256` — known as soon as decrypt first succeeds
 * (see [RestoreCoordinator]'s KDoc for why it is written no earlier than
 * that, even though the field lives on the same [RestoreStageInfo]
 * accessible from [RestoreStage.DOWNLOADED] onward).
 *
 * CRITICAL: none of these fields, nor anything else [RestoreStateStore]
 * persists, is ever decrypted Journal text or any other plaintext payload
 * content — only opaque names/hashes and the stage enum itself.
 */
data class RestoreStageInfo(
    val stage: RestoreStage,
    val remoteName: String?,
    val envelopeSha256: String?,
    val expectedContentHash: String?,
    /**
     * The staged snapshot's own `formatVersion`. The final verify hashes
     * the re-captured payload against *this*, not against the current
     * version — a Program 0 checkpoint's content hash covers ten fields,
     * and hashing twelve against it would fail every restore of every
     * backup written before Program 1.
     */
    val expectedFormatVersion: Int?,
)

/**
 * The at-rest surface for Task 11's staged-restore progress. A dedicated
 * DataStore (`continuity_restore_prefs`), separate from [ContinuityPrefs],
 * because this state describes an in-progress *operation* (or its absence)
 * rather than the ongoing backup-health flags/records [ContinuityPrefs]
 * owns — the two are cleared and read independently.
 *
 * Every function here is a plain suspend read/write with no decision logic
 * of its own; [RestoreCoordinator] is what interprets [RestoreStageInfo]
 * and decides what to do next. This split is what lets
 * `RestoreCoordinatorTest` fake the state store as narrow lambdas (see that
 * class) while this concrete implementation is only exercised by the
 * androidTest suite, the same seam [ContinuityPrefs] and
 * [ContinuityBackupCoordinator] already use.
 */
class RestoreStateStore(private val context: Context) {

    private val stageKey = stringPreferencesKey("restore_stage")
    private val remoteNameKey = stringPreferencesKey("restore_remote_name")
    private val envelopeSha256Key = stringPreferencesKey("restore_envelope_sha256")
    private val expectedContentHashKey = stringPreferencesKey("restore_expected_content_hash")
    private val expectedFormatVersionKey = intPreferencesKey("restore_expected_format_version")

    /** A one-shot read of the currently persisted restore state. */
    suspend fun currentInfo(): RestoreStageInfo {
        val prefs = context.restoreDataStore.data.first()
        val stage = prefs[stageKey]?.let { name ->
            runCatching { RestoreStage.valueOf(name) }.getOrNull()
        } ?: RestoreStage.NONE
        return RestoreStageInfo(
            stage = stage,
            remoteName = prefs[remoteNameKey],
            envelopeSha256 = prefs[envelopeSha256Key],
            expectedContentHash = prefs[expectedContentHashKey],
            expectedFormatVersion = prefs[expectedFormatVersionKey],
        )
    }

    /**
     * Begins a new staged restore: persists [RestoreStage.DOWNLOADED] plus
     * the staged file's own identity ([remoteName], [envelopeSha256]) and
     * — already known by this point, since the candidate that was staged
     * was already decrypted once during selection/preview — the
     * [expectedContentHash] the eventual [RestoreStage.VERIFIED] check
     * will compare against.
     */
    suspend fun markDownloaded(
        remoteName: String,
        envelopeSha256: String,
        expectedContentHash: String,
        expectedFormatVersion: Int,
    ) {
        context.restoreDataStore.edit { prefs ->
            prefs[stageKey] = RestoreStage.DOWNLOADED.name
            prefs[remoteNameKey] = remoteName
            prefs[envelopeSha256Key] = envelopeSha256
            prefs[expectedContentHashKey] = expectedContentHash
            prefs[expectedFormatVersionKey] = expectedFormatVersion
        }
    }

    /** Advances to [RestoreStage.DECRYPTED]. [expectedContentHash] is re-written defensively (see [markDownloaded]). */
    suspend fun markDecrypted(expectedContentHash: String, expectedFormatVersion: Int) {
        context.restoreDataStore.edit { prefs ->
            prefs[stageKey] = RestoreStage.DECRYPTED.name
            prefs[expectedContentHashKey] = expectedContentHash
            prefs[expectedFormatVersionKey] = expectedFormatVersion
        }
    }

    /** Advances to [RestoreStage.ROOM_MERGED]. Called only after the Room transaction has committed. */
    suspend fun markRoomMerged() {
        context.restoreDataStore.edit { prefs -> prefs[stageKey] = RestoreStage.ROOM_MERGED.name }
    }

    /** Advances to [RestoreStage.DATASTORES_MERGED]. Called only after every DataStore merge has completed. */
    suspend fun markDataStoresMerged() {
        context.restoreDataStore.edit { prefs -> prefs[stageKey] = RestoreStage.DATASTORES_MERGED.name }
    }

    /** Advances to [RestoreStage.VERIFIED]. Called only after the re-captured content hash matches. */
    suspend fun markVerified() {
        context.restoreDataStore.edit { prefs -> prefs[stageKey] = RestoreStage.VERIFIED.name }
    }

    /**
     * Clears every field, returning to [RestoreStage.NONE]. Used
     * defensively when [RestoreCoordinator] finds the staged file missing
     * while the persisted stage says otherwise — a corrupted-local-state
     * edge case (see that class). [RestoreStage.VERIFIED] is *not* reset
     * to [RestoreStage.NONE] on success: it is a legitimate terminal state
     * that stays persisted, so a later [RestoreCoordinator.resume] call
     * (e.g. from [RestoreCoordinator.resumeIfPending]) reads it and
     * correctly treats the restore as already-done rather than as
     * "nothing ever started".
     */
    suspend fun reset() {
        context.restoreDataStore.edit { it.clear() }
    }
}
