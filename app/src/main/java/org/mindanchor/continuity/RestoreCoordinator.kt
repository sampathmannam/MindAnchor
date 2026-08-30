package org.mindanchor.continuity

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mindanchor.backup.BackupRepository
import org.mindanchor.continuity.crypto.BackupEnvelopeCodec
import org.mindanchor.continuity.crypto.RecoveryKey
import org.mindanchor.continuity.crypto.RecoveryKeyStore
import org.mindanchor.data.FrictionPrefs
import org.mindanchor.data.LauncherPrefs
import org.mindanchor.data.NotesPrefs
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.data.db.ContinuityChangeEntity
import org.mindanchor.data.db.JournalContextEntity
import org.mindanchor.data.db.JournalEntryEntity
import org.mindanchor.data.db.MorningMeasureEntity
import org.mindanchor.data.db.PassiveBaselineSegmentEntity
import org.mindanchor.data.db.PassiveDailyRevisionEntity
import org.mindanchor.data.db.PassiveObservationDecisionEntity
import org.mindanchor.data.db.PassivePipelineRunEntity
import org.mindanchor.data.db.PassiveRawProvenanceEntity
import org.mindanchor.data.db.PassiveSourceLagEntity
import org.mindanchor.data.db.PassiveSourceReadEntity
import org.mindanchor.data.db.PassiveWindowRevisionEntity
import org.mindanchor.data.db.ResearchLedgerEventEntity
import org.mindanchor.data.db.StudyPhaseEntity
import org.mindanchor.data.mergeRestored
import org.mindanchor.data.replaceAlwaysOpen
import org.mindanchor.data.replaceFlagged
import org.mindanchor.journal.DeviceIdentityStore
import org.mindanchor.letters.Letter
import org.mindanchor.letters.LetterStore
import org.mindanchor.letters.mergeRestored
import org.mindanchor.model.Note
import org.mindanchor.model.NoteType

/**
 * The outcome of [RestoreCoordinator.beginRestore] / [RestoreCoordinator.resume].
 * Deliberately distinguishes every failure mode the Task 11 brief calls
 * out by name, so [RestoreScreen] never has to guess *why* a restore
 * stopped.
 */
sealed class RestoreResult {
    /** Reached [RestoreStage.VERIFIED] just now: the content hash matched and the staged file was deleted. */
    data class Verified(val contentHash: String) : RestoreResult()

    /** [RestoreCoordinator.resume] was called when the persisted stage was already [RestoreStage.VERIFIED]. A fast no-op. */
    data object AlreadyVerified : RestoreResult()

    /** [RestoreCoordinator.resume] was called when the persisted stage was [RestoreStage.NONE]. Nothing to resume. */
    data object NothingStaged : RestoreResult()

    /** [RestoreCoordinator.beginRestore] refused to start: local data is not empty (see that function's KDoc). */
    data object PreflightBlocked : RestoreResult()

    /** No verified recovery key is stored on this device. */
    data object KeyMissing : RestoreResult()

    /** The stored recovery key does not match the staged envelope's key id. Nothing was mutated. */
    data object WrongRecoveryKey : RestoreResult()

    /** The staged envelope failed to decode, failed GCM authentication, or failed its own plaintext hash check. Nothing was mutated. */
    data object StagedFileCorrupt : RestoreResult()

    /**
     * The re-captured local content hash did not match [expectedHash]
     * after every merge phase completed. The merged data is **not** rolled
     * back — see [RestoreCoordinator]'s KDoc — and the staged file is
     * deliberately kept (not deleted) so a retry or diagnostic export
     * remains possible.
     */
    data class VerifyMismatch(val recomputedHash: String, val expectedHash: String?) : RestoreResult()

    /**
     * The persisted stage pointed past [RestoreStage.NONE] but the staged
     * encrypted file was missing on disk — a corrupted-local-state edge
     * case. State was reset to [RestoreStage.NONE]; nothing was merged or
     * mutated on this call.
     */
    data object LocalStateReset : RestoreResult()
}

/**
 * The Task 11 "stage before mutating, merge idempotently, verify before
 * claiming success" restore algorithm.
 *
 * Every collaborator is a narrow suspend function — the same seam
 * [ContinuityBackupCoordinator] uses — so `RestoreCoordinatorTest` can
 * fake the Room/DataStore layers as plain in-memory lambdas with zero
 * Room/Context/Robolectric. [Companion.build] wires the real production
 * dependencies (Room via `withTransaction`, [org.mindanchor.data.NotesPrefs],
 * [org.mindanchor.letters.LetterStore], [org.mindanchor.data.FrictionPrefs],
 * [org.mindanchor.backup.BackupRepository], [ContinuitySnapshotRepository],
 * [ContinuityPrefs], [RestoreStateStore]) the same way
 * [CheckpointBackupWorker.buildCoordinator] wires [ContinuityBackupCoordinator].
 *
 * ## Two entry points, one reason
 *
 * [beginRestore] is the *only* place a fresh restore (persisted stage
 * [RestoreStage.NONE]) may start, and it is the only function in this
 * class that ever needs already-downloaded envelope bytes handed to it —
 * by design, it never itself calls a [org.mindanchor.backup.RemoteBackupStore].
 * The actual network download happens once, in [RestoreCandidateSelector.select],
 * during [RestoreScreen]'s preview step (before the user has even tapped
 * "Restore"); [beginRestore] just stages those already-verified bytes and
 * then delegates to [resume] for every subsequent stage.
 *
 * [resume] is the sole entry point that continues an *already-staged*
 * restore ([RestoreStage.DOWNLOADED] through [RestoreStage.VERIFIED]) using
 * only the staged encrypted file and the already-stored recovery key —
 * it never references a [org.mindanchor.backup.RemoteBackupStore] at all,
 * which is what keeps [resumeIfPending] (called unconditionally from
 * `HomeActivity.onCreate`) offline by construction, not merely by
 * discipline: there is no network-capable dependency anywhere in this
 * class for [resume] to accidentally call.
 *
 * ## Idempotency, stage by stage
 *
 *  - **DOWNLOADED → DECRYPTED**: decrypting is a pure function of the
 *    staged (unmutated) ciphertext and the stored key. Running it twice
 *    produces the same [ContinuityPayload] both times; nothing is written
 *    except the persisted stage and the snapshot's own `contentSha256`.
 *  - **DECRYPTED → ROOM_MERGED**: [mergeRoom] upserts by primary key
 *    (`OnConflictStrategy.REPLACE` for entries/context/measures,
 *    `IGNORE` for continuity-change rows) inside one Room transaction — a
 *    second run with the same rows is a no-op-equivalent overwrite with
 *    identical data, not a duplicate.
 *  - **ROOM_MERGED → DATASTORES_MERGED**: [mergeDataStores] calls
 *    [org.mindanchor.backup.BackupRepository.import] (already idempotent —
 *    see Task 7) plus `NotesPrefs.mergeRestored` /
 *    `LetterStore.mergeRestored` / `FrictionPrefs.replaceFlagged` /
 *    `FrictionPrefs.replaceAlwaysOpen`, all confirmed idempotent by Task 7
 *    (dedup-by-id-and-`updatedAt`, additive-by-date, or a plain `replace`
 *    of a set with itself).
 *  - **DATASTORES_MERGED → VERIFIED**: [recapture] + [ContinuityContentHasher.hash]
 *    is a pure read; running it twice reads the same (already-merged) data
 *    and produces the same hash both times.
 *
 * Because every one of those four transitions is independently idempotent,
 * re-running the *whole* sequence from [RestoreStage.DOWNLOADED] after an
 * interruption at any point converges to the exact same end state as one
 * clean, uninterrupted run — this is what `RestoreCoordinatorTest` and
 * `RestoreResumeTest` both prove, one interruption point at a time.
 *
 * ## Never delete local data to force a match
 *
 * A [RestoreStage.VERIFIED] hash mismatch ([RestoreResult.VerifyMismatch])
 * never rolls back, wipes, or re-merges-from-scratch the data already
 * written by [mergeRoom]/[mergeDataStores] in this or an earlier call —
 * that data came from individually-idempotent, individually-safe merge
 * operations, and a hash mismatch at the final check is a signal (e.g. a
 * concurrent local write racing the restore), not proof the merge itself
 * was wrong. The staged file is kept (not deleted) so the operation can be
 * retried or diagnosed; [RestoreStage.VERIFIED] is simply never persisted,
 * so a later [resume] call retries the same final check rather than
 * silently claiming success.
 */
@Suppress("LongParameterList")
class RestoreCoordinator(
    private val currentStageInfo: suspend () -> RestoreStageInfo,
    private val persistDownloaded: suspend (
        remoteName: String,
        envelopeSha256: String,
        expectedContentHash: String,
        expectedFormatVersion: Int,
    ) -> Unit,
    private val persistDecrypted: suspend (expectedContentHash: String, expectedFormatVersion: Int) -> Unit,
    private val persistRoomMerged: suspend () -> Unit,
    private val persistDataStoresMerged: suspend () -> Unit,
    private val persistVerified: suspend () -> Unit,
    private val resetState: suspend () -> Unit,
    private val readStagedBytes: suspend () -> ByteArray?,
    private val writeStagedBytesAtomically: suspend (ByteArray) -> Unit,
    private val deleteStagedFile: suspend () -> Unit,
    private val currentVerifiedKey: suspend () -> RecoveryKey?,
    private val preflightIsLocalDataEmpty: suspend () -> Boolean,
    private val mergeRoom: suspend (ContinuityPayload) -> Unit,
    private val mergeDataStores: suspend (ContinuityPayload) -> Unit,
    private val recapture: suspend () -> ContinuitySnapshot,
    private val recordRestoreVerified: suspend (at: Long, contentHash: String) -> Unit,
    private val recordVerifyFailed: suspend () -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /**
     * Starts a brand-new restore from [RestoreStage.NONE]. [remoteName]/
     * [envelopeBytes] come from a [RestoreCandidate] the caller already
     * selected and verified via [RestoreCandidateSelector.select]
     * (typically shown to the user as a preview before they confirmed);
     * [expectedContentHash] is that same candidate's decrypted
     * `ContinuitySnapshot.contentSha256`.
     *
     * If a restore is already in progress or already completed (persisted
     * stage is not [RestoreStage.NONE]), this call never re-stages or
     * re-runs the local-data preflight — it just delegates to [resume],
     * so an accidental double-tap of "Restore" can never overwrite an
     * in-flight or already-verified restore with a different candidate.
     *
     * The local-data preflight (Journal, context, morning measures, Notes,
     * Letters, safety plan/contacts/pulses, launcher favorites/hidden/
     * renames all empty) only runs here, and only when the stage is
     * genuinely [RestoreStage.NONE] — see this class's KDoc and the Task
     * 11 brief's "Local-data preflight" section for why a resumed restore
     * must never re-run it.
     */
    suspend fun beginRestore(
        remoteName: String,
        envelopeBytes: ByteArray,
        expectedContentHash: String,
        expectedFormatVersion: Int,
        onStageCompleted: (RestoreStage) -> Unit = {},
    ): RestoreResult {
        val info = currentStageInfo()
        if (info.stage != RestoreStage.NONE) {
            return resume(onStageCompleted)
        }
        if (!preflightIsLocalDataEmpty()) {
            return RestoreResult.PreflightBlocked
        }
        writeStagedBytesAtomically(envelopeBytes)
        persistDownloaded(remoteName, sha256Hex(envelopeBytes), expectedContentHash, expectedFormatVersion)
        onStageCompleted(RestoreStage.DOWNLOADED)
        return resume(onStageCompleted)
    }

    /**
     * Continues an already-staged restore from whatever stage is currently
     * persisted, all the way to [RestoreStage.VERIFIED] (or a failure).
     * Never downloads anything — see this class's KDoc.
     *
     * Safe to call repeatedly from any stage, including
     * [RestoreStage.NONE] (no-op) and [RestoreStage.VERIFIED] (fast
     * no-op) — see this class's KDoc for why each individual transition is
     * idempotent.
     *
     * ## Verified against the staged snapshot's own format version
     *
     * A Program 0 checkpoint's content hash covers ten payload fields;
     * digesting today's twelve against it would fail every restore of
     * every backup written before Program 1, *after* the data had already
     * been merged. `expectedFormatVersion` therefore comes from the staged
     * snapshot, not from the current constant. When the persisted value is
     * absent the fallback is Program 0's version, which is sound rather
     * than merely convenient: the constant stayed at 1 until the payload
     * actually gained its fields, so a restore staged by any earlier build
     * can only be a version-1 snapshot.
     *
     * [onStageCompleted] is a purely cosmetic progress callback — invoked
     * once, in order, immediately after each stage is durably persisted —
     * so `RestoreScreen` can render a live DOWNLOADED→DECRYPTED→
     * ROOM_MERGED→DATASTORES_MERGED→VERIFIED stepper. It carries no
     * decision logic and its absence (the default no-op) changes nothing
     * about the algorithm itself.
     */
    suspend fun resume(onStageCompleted: (RestoreStage) -> Unit = {}): RestoreResult {
        val info = currentStageInfo()
        if (info.stage == RestoreStage.NONE) return RestoreResult.NothingStaged
        if (info.stage == RestoreStage.VERIFIED) return RestoreResult.AlreadyVerified

        val key = currentVerifiedKey() ?: return RestoreResult.KeyMissing

        var stage = info.stage
        var expectedContentHash = info.expectedContentHash
        var expectedFormatVersion =
            info.expectedFormatVersion ?: ContinuityContract.PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION
        var payload: ContinuityPayload? = null

        if (stage == RestoreStage.DOWNLOADED) {
            val decrypted = decryptStaged(key) ?: return handleMissingStagedFile()
            when (decrypted) {
                is DecryptOutcome.WrongKey -> return RestoreResult.WrongRecoveryKey
                is DecryptOutcome.Corrupt -> return RestoreResult.StagedFileCorrupt
                is DecryptOutcome.Success -> {
                    expectedContentHash = decrypted.snapshot.contentSha256
                    expectedFormatVersion = decrypted.snapshot.formatVersion
                    persistDecrypted(expectedContentHash, expectedFormatVersion)
                    stage = RestoreStage.DECRYPTED
                    payload = decrypted.snapshot.payload
                    onStageCompleted(RestoreStage.DECRYPTED)
                }
            }
        }

        if (stage == RestoreStage.DECRYPTED) {
            val resolvedPayload = payload ?: when (val decrypted = decryptStaged(key) ?: return handleMissingStagedFile()) {
                is DecryptOutcome.WrongKey -> return RestoreResult.WrongRecoveryKey
                is DecryptOutcome.Corrupt -> return RestoreResult.StagedFileCorrupt
                // Read from the file rather than from the persisted pref
                // whenever it is open anyway: the snapshot is the authority
                // on its own format version.
                is DecryptOutcome.Success -> {
                    expectedFormatVersion = decrypted.snapshot.formatVersion
                    decrypted.snapshot.payload
                }
            }
            mergeRoom(resolvedPayload)
            persistRoomMerged()
            stage = RestoreStage.ROOM_MERGED
            payload = resolvedPayload
            onStageCompleted(RestoreStage.ROOM_MERGED)
        }

        if (stage == RestoreStage.ROOM_MERGED) {
            val resolvedPayload = payload ?: when (val decrypted = decryptStaged(key) ?: return handleMissingStagedFile()) {
                is DecryptOutcome.WrongKey -> return RestoreResult.WrongRecoveryKey
                is DecryptOutcome.Corrupt -> return RestoreResult.StagedFileCorrupt
                is DecryptOutcome.Success -> {
                    expectedFormatVersion = decrypted.snapshot.formatVersion
                    decrypted.snapshot.payload
                }
            }
            mergeDataStores(resolvedPayload)
            persistDataStoresMerged()
            stage = RestoreStage.DATASTORES_MERGED
            onStageCompleted(RestoreStage.DATASTORES_MERGED)
        }

        check(stage == RestoreStage.DATASTORES_MERGED) { "unreachable restore stage after merge phases: $stage" }
        return verifyAndFinish(expectedContentHash, expectedFormatVersion, onStageCompleted)
    }

    /**
     * The final check. Re-captures what this device now holds, hashes it
     * against the staged snapshot's own format version, and only then
     * persists [RestoreStage.VERIFIED] and drops the staged file.
     */
    private suspend fun verifyAndFinish(
        expectedContentHash: String?,
        expectedFormatVersion: Int,
        onStageCompleted: (RestoreStage) -> Unit,
    ): RestoreResult {
        val recaptured = recapture()
        val recomputedHash = ContinuityContentHasher.hash(recaptured.payload, expectedFormatVersion)
        if (expectedContentHash == null || recomputedHash != expectedContentHash) {
            recordVerifyFailed()
            return RestoreResult.VerifyMismatch(recomputedHash, expectedContentHash)
        }

        persistVerified()
        recordRestoreVerified(now(), recomputedHash)
        deleteStagedFile()
        onStageCompleted(RestoreStage.VERIFIED)
        return RestoreResult.Verified(recomputedHash)
    }

    private suspend fun handleMissingStagedFile(): RestoreResult {
        resetState()
        return RestoreResult.LocalStateReset
    }

    private suspend fun decryptStaged(key: RecoveryKey): DecryptOutcome? {
        val bytes = readStagedBytes() ?: return null
        val envelope = BackupEnvelopeCodec.decode(bytes.decodeToString()) ?: return DecryptOutcome.Corrupt
        return when (val result = BackupEnvelopeCodec.decrypt(envelope, key)) {
            is BackupEnvelopeCodec.DecryptResult.Success -> {
                when (val decoded = ContinuitySnapshotCodec.decode(result.plaintextJson)) {
                    is ContinuitySnapshotCodec.DecodeResult.Success -> DecryptOutcome.Success(decoded.snapshot)
                    is ContinuitySnapshotCodec.DecodeResult.UnsupportedVersion -> DecryptOutcome.Corrupt
                    is ContinuitySnapshotCodec.DecodeResult.Corrupt -> DecryptOutcome.Corrupt
                }
            }
            is BackupEnvelopeCodec.DecryptResult.WrongKey -> DecryptOutcome.WrongKey
            is BackupEnvelopeCodec.DecryptResult.Corrupt -> DecryptOutcome.Corrupt
            is BackupEnvelopeCodec.DecryptResult.UnsupportedVersion -> DecryptOutcome.Corrupt
        }
    }

    private sealed class DecryptOutcome {
        data class Success(val snapshot: ContinuitySnapshot) : DecryptOutcome()
        data object WrongKey : DecryptOutcome()
        data object Corrupt : DecryptOutcome()
    }

    companion object {
        private fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        /**
         * Checks whether the persisted restore stage is anything other
         * than [RestoreStage.NONE] and, if so, continues it locally — the
         * self-repair path for "the process died mid-restore" on the very
         * next ordinary app open. Fire-and-forget: launched in its own
         * coroutine so `HomeActivity.onCreate` never blocks on it, and
         * makes zero network/Drive calls (see [RestoreCoordinator]'s
         * KDoc) — the recovery key is already on this device, so local-
         * phase resumption needs no user prompt either.
         */
        fun resumeIfPending(context: Context) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                val stateStore = RestoreStateStore(context)
                if (stateStore.currentInfo().stage == RestoreStage.NONE) return@launch
                // Nothing here may throw. This is a root coroutine on the
                // app's own start-up path: an uncaught exception is a crash
                // on every cold start, and the state that caused it is on
                // disk, so it would happen again every time. Corrupt
                // persisted state, an unreadable staged file, a format
                // version this build does not know — all of them end as a
                // recorded error the backup-health card can show, never as
                // a launcher that will not open.
                runCatching { build(context).resume() }.onFailure { thrown ->
                    Log.w("RestoreCoordinator", "a pending restore could not be resumed", thrown)
                    ContinuityPrefs(context).recordError(ContinuityErrorCode.RESTORE_VERIFY_FAILED)
                }
            }
        }

        /**
         * Wires a [RestoreCoordinator] with real production dependencies —
         * the same shape [CheckpointBackupWorker.buildCoordinator] uses
         * for [ContinuityBackupCoordinator]. Used by [resumeIfPending] and
         * by `RestoreActivity`'s explicit user-initiated flow.
         */
        fun build(context: Context): RestoreCoordinator {
            val app = context.applicationContext
            val database = AnchorDatabase.get(app)
            val dao = database.journal()
            val passive = database.passive()
            val notesPrefs = NotesPrefs(app)
            val letterStore = LetterStore(app)
            val frictionPrefs = FrictionPrefs(app)
            val launcherPrefs = LauncherPrefs(app)
            val backupRepository = BackupRepository(app)
            val continuityPrefs = ContinuityPrefs(app)
            val restoreStateStore = RestoreStateStore(app)
            val recoveryKeyStore = RecoveryKeyStore.create(app)
            val snapshotRepository = ContinuitySnapshotRepository(
                context = app,
                database = database,
                notesPrefs = notesPrefs,
                letterStore = letterStore,
                frictionPrefs = frictionPrefs,
                deviceIdentity = DeviceIdentityStore(app),
                backupRepository = backupRepository,
            )

            val stagingDir = File(app.filesDir, "continuity")
            val stagingFile = File(stagingDir, "restore-staged.mab")
            val stagingTmpFile = File(stagingDir, "restore-staged.mab.tmp")

            return RestoreCoordinator(
                currentStageInfo = restoreStateStore::currentInfo,
                persistDownloaded = restoreStateStore::markDownloaded,
                persistDecrypted = restoreStateStore::markDecrypted,
                persistRoomMerged = restoreStateStore::markRoomMerged,
                persistDataStoresMerged = restoreStateStore::markDataStoresMerged,
                persistVerified = restoreStateStore::markVerified,
                resetState = restoreStateStore::reset,
                readStagedBytes = {
                    withContext(Dispatchers.IO) {
                        if (stagingFile.exists()) stagingFile.readBytes() else null
                    }
                },
                writeStagedBytesAtomically = { bytes ->
                    withContext(Dispatchers.IO) {
                        stagingDir.mkdirs()
                        stagingTmpFile.outputStream().use { out ->
                            out.write(bytes)
                            out.flush()
                        }
                        // renameTo does not overwrite on every filesystem — delete any
                        // stale staged file first, mirroring ModelStore's own atomic
                        // replace precedent.
                        if (stagingFile.exists()) stagingFile.delete()
                        stagingTmpFile.renameTo(stagingFile)
                    }
                },
                deleteStagedFile = { withContext(Dispatchers.IO) { stagingFile.delete() } },
                currentVerifiedKey = { recoveryKeyStore.current()?.takeIf { recoveryKeyStore.isVerified() } },
                preflightIsLocalDataEmpty = {
                    dao.entriesNow().isEmpty() &&
                        dao.allContext().isEmpty() &&
                        dao.morningMeasuresNow().isEmpty() &&
                        notesPrefs.notes.first().notes.isEmpty() &&
                        letterStore.letters.first().isEmpty() &&
                        database.safety().plan().first().let { it == null || it.isEmpty } &&
                        database.safety().contactsNow().isEmpty() &&
                        database.pulses().history().first().isEmpty() &&
                        launcherPrefs.favorites.first().isEmpty() &&
                        launcherPrefs.hidden.first().isEmpty() &&
                        launcherPrefs.renames.first().isEmpty() &&
                        // A replacement phone must have an empty ledger
                        // before it restores: merging a backup's chain
                        // into a chain this phone already started would
                        // fork it, and append-only tables cannot be
                        // un-forked.
                        database.research().ledgerEventCount() == 0 &&
                        database.research().studyPhaseCount() == 0 &&
                        passive.rawProvenanceNow().isEmpty() &&
                        passive.sourceReadsNow().isEmpty() &&
                        passive.sourceLagsNow().isEmpty() &&
                        passive.baselineSegmentsNow().isEmpty() &&
                        passive.pipelineRunsNow().isEmpty() &&
                        passive.windowRevisionsNow().isEmpty() &&
                        passive.dailyRevisionsNow().isEmpty() &&
                        passive.observationDecisionsNow().isEmpty()
                },
                mergeRoom = { payload ->
                    database.withTransaction {
                        dao.upsertEntries(payload.journalEntries.map { it.toEntity() })
                        dao.upsertContext(payload.contextRows.map { it.toEntity() })
                        dao.upsertMorningMeasures(payload.morningMeasures.map { it.toEntity() })
                        payload.continuityChanges.forEach { dao.insertChange(it.toEntity()) }
                        // INSERT OR IGNORE on content-addressed ids: a
                        // second merge of the same events inserts nothing,
                        // so resume is duplicate-free by construction
                        // rather than by a de-duplication pass.
                        mergeResearchRows(database, payload)
                        mergePassiveRows(database, payload)
                    }
                },
                mergeDataStores = { payload ->
                    backupRepository.import(payload.legacyBackupJson, System.currentTimeMillis())
                    notesPrefs.mergeRestored(payload.notes.map { it.toDomain() })
                    letterStore.mergeRestored(
                        payload.letters.mapNotNull { it.toDomain() },
                        payload.readLetterDates.mapNotNull { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() }.toSet(),
                    )
                    frictionPrefs.replaceFlagged(payload.frictionedApps.toSet())
                    frictionPrefs.replaceAlwaysOpen(payload.alwaysOpenApps.toSet())
                },
                recapture = { snapshotRepository.capture(System.currentTimeMillis()) },
                recordRestoreVerified = { at, hash -> continuityPrefs.recordRestore(at, hash) },
                recordVerifyFailed = { continuityPrefs.recordError(ContinuityErrorCode.RESTORE_VERIFY_FAILED) },
            )
        }
    }
}

/**
 * Merges the research ledger and study phases into [database].
 *
 * Extracted from [RestoreCoordinator.build]'s `mergeRoom` so a test can run
 * the production code twice; a hand-rolled copy in a test proves nothing
 * about the lambda a real restore takes.
 *
 * **Idempotent, and that is load-bearing.** `mergeRoom` commits, and only
 * then is `ROOM_MERGED` persisted; anything that interrupts between those
 * two durability events leaves the rows written and the stage still at
 * `DECRYPTED`, so the next resume re-runs this. Checking each
 * `INSERT OR IGNORE`'s row id would therefore throw on every resumed
 * restore — the rows are already there — and, because `resumeIfPending`
 * runs on app start, would crash the launcher on every cold start with no
 * way out but clearing app data.
 *
 * So the post-condition is what is checked instead: every row the payload
 * carries must be *present* afterwards, however it got there. That is true
 * on a first merge, true on a re-merge, and false exactly when a row was
 * genuinely dropped — which is the case worth failing for, because a
 * research row lost to a restore is history nobody would notice going.
 */
internal suspend fun mergeResearchRows(database: AnchorDatabase, payload: ContinuityPayload) {
    val research = database.research()
    research.insertLedgerEvents(payload.researchLedgerEvents.map { it.toEntity() })
    payload.studyPhases.forEach { research.insertStudyPhase(it.toEntity()) }

    val storedEventIds = research.ledgerEventsNow().map { it.id }.toSet()
    val missingEvents = payload.researchLedgerEvents.map { it.id }.filterNot { it in storedEventIds }
    check(missingEvents.isEmpty()) {
        "${missingEvents.size} restored ledger events are not in the table; the ledger would be incomplete"
    }

    val storedPhaseIds = research.studyPhasesNow().map { it.id }.toSet()
    val missingPhases = payload.studyPhases.map { it.id }.filterNot { it in storedPhaseIds }
    check(missingPhases.isEmpty()) {
        "${missingPhases.size} restored study phases are not in the table; their events would point at nothing"
    }
}

internal suspend fun mergePassiveRows(database: AnchorDatabase, payload: ContinuityPayload) {
    val dao = database.passive()
    dao.insertRawProvenance(payload.passiveRawProvenance.map { it.toEntity() })
    dao.insertSourceReads(payload.passiveSourceReads.map { it.toEntity() })
    dao.insertSourceLags(payload.passiveSourceLags.map { it.toEntity() })
    payload.passiveBaselineSegments.forEach { dao.insertBaselineSegment(it.toEntity()) }
    payload.passivePipelineRuns.forEach { dao.insertPipelineRun(it.toEntity()) }
    dao.insertWindowRevisions(payload.passiveWindowRevisions.map { it.toEntity() })
    dao.insertDailyRevisions(payload.passiveDailyRevisions.map { it.toEntity() })
    dao.insertObservationDecisions(payload.passiveObservationDecisions.map { it.toEntity() })

    check(dao.rawProvenanceNow().map { it.id }.containsAll(payload.passiveRawProvenance.map { it.id }))
    check(dao.windowRevisionsNow().map { it.id }.containsAll(payload.passiveWindowRevisions.map { it.id }))
    check(dao.dailyRevisionsNow().map { it.id }.containsAll(payload.passiveDailyRevisions.map { it.id }))
    check(
        dao.observationDecisionsNow().map { it.id }
            .containsAll(payload.passiveObservationDecisions.map { it.id }),
    )
}

// --- DTO -> Room entity / domain mapping, back-direction of ContinuitySnapshot.kt's toDto() ---

internal fun JournalEntryDto.toEntity(): JournalEntryEntity = JournalEntryEntity(
    id = id,
    createdAt = createdAt,
    updatedAt = updatedAt,
    localDate = localDate,
    title = title,
    body = body,
    kind = kind,
    sourceDeviceId = sourceDeviceId,
    deletedAt = deletedAt,
)

internal fun JournalContextDto.toEntity(): JournalContextEntity = JournalContextEntity(
    id = id,
    entryId = entryId,
    recordType = recordType,
    key = key,
    value = value,
    sourceStart = sourceStart,
    sourceEnd = sourceEnd,
    confidence = confidence,
    extractorVersion = extractorVersion,
    createdAt = createdAt,
)

internal fun MorningMeasureDto.toEntity(): MorningMeasureEntity = MorningMeasureEntity(
    id = id,
    localDate = localDate,
    createdAt = createdAt,
    updatedAt = updatedAt,
    mood = mood,
    anxiety = anxiety,
    angerUrge = angerUrge,
    energyFunction = energyFunction,
    sleepQuality = sleepQuality,
    instrumentVersion = instrumentVersion,
    sourceDeviceId = sourceDeviceId,
)

internal fun ResearchLedgerEventDto.toEntity(): ResearchLedgerEventEntity = ResearchLedgerEventEntity(
    id = id,
    sequence = sequence,
    kind = kind,
    occurredAt = occurredAt,
    recordedAt = recordedAt,
    localDate = localDate,
    studyPhaseId = studyPhaseId,
    sourceDeviceId = sourceDeviceId,
    note = note,
    payloadJson = payloadJson,
    previousEventHash = previousEventHash,
    eventHash = eventHash,
)

internal fun StudyPhaseDto.toEntity(): StudyPhaseEntity = StudyPhaseEntity(
    id = id,
    ordinal = ordinal,
    startedAt = startedAt,
    reason = reason,
    appVersionCode = appVersionCode,
    appVersionName = appVersionName,
    protocolCatalogSha256 = protocolCatalogSha256,
    ruleSetVersion = ruleSetVersion,
    modelSetVersion = modelSetVersion,
    transformationSetVersion = transformationSetVersion,
    missingDataPolicyVersion = missingDataPolicyVersion,
    instrumentVersion = instrumentVersion,
    dictionaryVersion = dictionaryVersion,
    sourceDeviceId = sourceDeviceId,
)

internal fun PassiveRawProvenanceDto.toEntity(): PassiveRawProvenanceEntity = PassiveRawProvenanceEntity(
    id = id,
    sourceFamily = sourceFamily,
    recordKind = recordKind,
    eventStart = eventStart,
    eventEnd = eventEnd,
    unit = unit,
    dataOriginPackage = dataOriginPackage,
    deviceManufacturer = deviceManufacturer,
    deviceModel = deviceModel,
    deviceType = deviceType,
    sourceUpdatedTime = sourceUpdatedTime,
    ingestedAt = ingestedAt,
    zoneId = zoneId,
    zoneOffsetSeconds = zoneOffsetSeconds,
    recordId = recordId,
    recordVersion = recordVersion,
)

internal fun PassiveSourceReadDto.toEntity(): PassiveSourceReadEntity = PassiveSourceReadEntity(
    id = id,
    runId = runId,
    sourceFamily = sourceFamily,
    state = state,
    rangeStart = rangeStart,
    rangeEnd = rangeEnd,
    zoneId = zoneId,
    attemptedAt = attemptedAt,
    recordCount = recordCount,
    errorCode = errorCode,
)

internal fun PassiveSourceLagDto.toEntity(): PassiveSourceLagEntity = PassiveSourceLagEntity(
    id = id,
    sourceFamily = sourceFamily,
    eventEnd = eventEnd,
    observedUpdatedAt = observedUpdatedAt,
    ingestedAt = ingestedAt,
    lagMillis = lagMillis,
    usedIngestedAtFallback = usedIngestedAtFallback,
    observedAt = observedAt,
)

internal fun PassiveBaselineSegmentDto.toEntity(): PassiveBaselineSegmentEntity = PassiveBaselineSegmentEntity(
    id = id,
    openedAt = openedAt,
    fingerprintsJson = fingerprintsJson,
    windowTransformationVersion = windowTransformationVersion,
    dailyTransformationVersion = dailyTransformationVersion,
)

internal fun PassivePipelineRunDto.toEntity(): PassivePipelineRunEntity = PassivePipelineRunEntity(
    id = id,
    startedAt = startedAt,
    completedAt = completedAt,
    scanStart = scanStart,
    scanEnd = scanEnd,
    zoneId = zoneId,
    historyPermissionGranted = historyPermissionGranted,
    firstSuccessfulPermissionedRun = firstSuccessfulPermissionedRun,
    result = result,
    sourceStatesJson = sourceStatesJson,
)

internal fun PassiveWindowRevisionDto.toEntity(): PassiveWindowRevisionEntity = PassiveWindowRevisionEntity(
    id = id,
    windowStart = windowStart,
    windowEnd = windowEnd,
    asOfTime = asOfTime,
    zoneId = zoneId,
    zoneOffsetSeconds = zoneOffsetSeconds,
    wakeRelativeMinute = wakeRelativeMinute,
    baselineSegment = baselineSegment,
    featureRowsJson = featureRowsJson,
    heartRateCoverage = heartRateCoverage,
    physiologyEligible = physiologyEligible,
    exerciseOverlapMillis = exerciseOverlapMillis,
    provenanceRecordIdsJson = provenanceRecordIdsJson,
    missingnessJson = missingnessJson,
    exclusionsJson = exclusionsJson,
    transformationVersion = transformationVersion,
    sourceUpdatedTime = sourceUpdatedTime,
    ingestedAt = ingestedAt,
    final = final,
    revisionReason = revisionReason,
    contentHash = contentHash,
)

internal fun PassiveDailyRevisionDto.toEntity(): PassiveDailyRevisionEntity = PassiveDailyRevisionEntity(
    id = id,
    localDate = localDate,
    asOfTime = asOfTime,
    dataStatus = dataStatus,
    featuresJson = featuresJson,
    excludedFeaturesJson = excludedFeaturesJson,
    baselineSegment = baselineSegment,
    sourceUpdatedTime = sourceUpdatedTime,
    ingestedAt = ingestedAt,
    sourceReadStatesJson = sourceReadStatesJson,
    coverageJson = coverageJson,
    missingnessJson = missingnessJson,
    exclusionsJson = exclusionsJson,
    provenanceJson = provenanceJson,
    windowTransformationVersion = windowTransformationVersion,
    dailyTransformationVersion = dailyTransformationVersion,
    watermark = watermark,
    revisionReason = revisionReason,
    contentHash = contentHash,
)

internal fun PassiveObservationDecisionDto.toEntity(): PassiveObservationDecisionEntity =
    PassiveObservationDecisionEntity(
        id = id,
        localDate = localDate,
        asOfTime = asOfTime,
        dataStatus = dataStatus,
        observationState = observationState,
        baselineSegment = baselineSegment,
        calibrationSeed = calibrationSeed,
        frozenBaselineAsOfTime = frozenBaselineAsOfTime,
        frozenBaselineThroughDay = frozenBaselineThroughDay,
        decisionJson = decisionJson,
        revisionReason = revisionReason,
        contentHash = contentHash,
    )

internal fun ContinuityChangeDto.toEntity(): ContinuityChangeEntity = ContinuityChangeEntity(
    id = id,
    entityType = entityType,
    entityId = entityId,
    operation = operation,
    occurredAt = occurredAt,
    acknowledgedSnapshotId = acknowledgedSnapshotId,
)

internal fun NoteDto.toDomain(): Note = Note(
    id = id,
    body = body,
    createdAt = createdAt,
    updatedAt = updatedAt,
    pinned = pinned,
    type = type?.let { name -> runCatching { NoteType.valueOf(name) }.getOrNull() },
)

/** Null when [LetterDto.date] fails to parse — the caller drops that one letter rather than aborting the whole restore. */
internal fun LetterDto.toDomain(): Letter? {
    val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
    return Letter(
        date = parsedDate,
        body = body,
        provider = provider,
        model = model,
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        durationMs = durationMs,
    )
}
