package org.mindanchor.continuity

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import org.mindanchor.backup.BackupRepository
import org.mindanchor.backup.GoogleDriveAuth
import org.mindanchor.backup.GoogleDriveObjectStore
import org.mindanchor.continuity.crypto.RecoveryKeyStore
import org.mindanchor.data.FrictionPrefs
import org.mindanchor.data.NotesPrefs
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.journal.DeviceIdentityStore
import org.mindanchor.letters.LetterStore

/**
 * The on-write checkpoint worker. `doWork()` is a thin wrapper: it
 * constructs the real dependencies ([AnchorDatabase], [ContinuityPrefs],
 * [RecoveryKeyStore], [GoogleDriveObjectStore] via [GoogleDriveAuth]'s
 * token, [ContinuitySnapshotRepository]) and delegates the actual
 * capture → encrypt → upload → verify → acknowledge algorithm to
 * [ContinuityBackupCoordinator] — see that class for the algorithm itself
 * and why it lives apart from this `CoroutineWorker` boilerplate.
 *
 * Scheduled/cancelled exclusively through [ContinuityWorkScheduler]; this
 * class has no `ensureScheduled`/`cancel` companion of its own (unlike
 * [org.mindanchor.friction.BanditResetWorker]) because [ContinuityWorkScheduler]
 * owns scheduling for both continuity workers in one place — see that
 * object's KDoc.
 */
class CheckpointBackupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val database = AnchorDatabase.get(ctx)
        val coordinator = buildCoordinator(ctx, database)
        return coordinator.runCheckpoint().toWorkResult()
    }

    companion object {
        /**
         * Builds the [ContinuityBackupCoordinator] with real
         * dependencies. Extracted so [NightlySnapshotWorker] (which runs
         * the same checkpoint algorithm, then additionally versions the
         * result) can build an identical coordinator without duplicating
         * this wiring.
         */
        internal fun buildCoordinator(
            ctx: Context,
            database: AnchorDatabase,
            isBackupEnabled: suspend (ContinuityPrefs) -> Boolean = { it.backupEnabled.first() },
        ): ContinuityBackupCoordinator {
            val continuityPrefs = ContinuityPrefs(ctx)
            val recoveryKeyStore = RecoveryKeyStore.create(ctx)
            val auth = GoogleDriveAuth(ctx)
            val remoteBackupStore = GoogleDriveObjectStore(currentAccessToken = auth::currentAccessToken)
            val snapshotRepository = ContinuitySnapshotRepository(
                context = ctx,
                database = database,
                notesPrefs = NotesPrefs(ctx),
                letterStore = LetterStore(ctx),
                frictionPrefs = FrictionPrefs(ctx),
                deviceIdentity = DeviceIdentityStore(ctx),
                backupRepository = BackupRepository(ctx),
            )
            val dao = database.journal()

            return ContinuityBackupCoordinator(
                isBackupEnabled = { isBackupEnabled(continuityPrefs) },
                currentVerifiedKey = {
                    recoveryKeyStore.current()?.takeIf { recoveryKeyStore.isVerified() }
                },
                remoteBackupStore = remoteBackupStore,
                captureSnapshot = { now -> snapshotRepository.capture(now) },
                acknowledgePending = { snapshotId -> dao.acknowledgePending(snapshotId) },
                recordError = { code -> continuityPrefs.recordError(code) },
                recordVerified = { at, id, hash -> continuityPrefs.recordCheckpointVerified(at, id, hash) },
            )
        }
    }
}

/**
 * Maps a [CheckpointResult] to the [androidx.work.ListenableWorker.Result]
 * WorkManager acts on.
 *
 *  - [CheckpointResult.Verified] / [CheckpointResult.BackupDisabled] /
 *    [CheckpointResult.KeyMissing] / [CheckpointResult.AuthExpired] /
 *    [CheckpointResult.PermanentFailure] all map to [androidx.work.ListenableWorker.Result.success] —
 *    none of these is a transient condition WorkManager's retry/backoff
 *    can fix; the health state recorded by the coordinator is what
 *    surfaces the problem to the user.
 *  - [CheckpointResult.Retryable] maps to [androidx.work.ListenableWorker.Result.retry] —
 *    a network-class failure, exactly the case WorkManager's exponential
 *    backoff exists for.
 *  - [CheckpointResult.VerificationFailed] maps to [androidx.work.ListenableWorker.Result.success]
 *    (the safer default per the plan: do not retry forever on a
 *    corruption condition that is unlikely to self-resolve; the previous
 *    verified checkpoint stays the repair point and `VERIFY_FAILED`
 *    health state is visible).
 */
internal fun CheckpointResult.toWorkResult(): androidx.work.ListenableWorker.Result = when (this) {
    is CheckpointResult.Verified,
    is CheckpointResult.BackupDisabled,
    is CheckpointResult.KeyMissing,
    is CheckpointResult.AuthExpired,
    is CheckpointResult.PermanentFailure,
    is CheckpointResult.VerificationFailed,
    -> androidx.work.ListenableWorker.Result.success()

    is CheckpointResult.Retryable -> androidx.work.ListenableWorker.Result.retry()
}
