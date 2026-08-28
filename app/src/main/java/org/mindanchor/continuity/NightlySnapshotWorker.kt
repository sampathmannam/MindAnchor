package org.mindanchor.continuity

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.Instant
import kotlinx.coroutines.flow.first
import org.mindanchor.backup.GoogleDriveAuth
import org.mindanchor.backup.GoogleDriveObjectStore
import org.mindanchor.backup.RemoteResult
import org.mindanchor.data.db.AnchorDatabase

/**
 * The nightly versioned-snapshot worker. Runs the same
 * capture → encrypt → upload `LATEST` → verify → acknowledge algorithm as
 * [CheckpointBackupWorker] (via [CheckpointBackupWorker.buildCoordinator]),
 * gated additionally on [ContinuityPrefs.nightlySnapshotsEnabled] — the
 * effective nightly-schedule decision is `backupEnabled && nightlySnapshotsEnabled`,
 * computed here rather than baked into either flag's own stored default
 * (see [ContinuityPrefs.nightlySnapshotsEnabled]'s KDoc).
 *
 * When (and only when) that run verifies successfully, this worker
 * *additionally* uploads the exact same already-verified envelope bytes
 * under [ContinuityFiles.versioned] — a second name for the same content,
 * not a second capture — and records the nightly-specific health fields
 * in [ContinuityPrefs]. Only a fully successful night reschedules the next
 * one via [ContinuityWorkScheduler.ensureNightlyScheduled]; every other
 * outcome (backup off, no key, a genuine failure) leaves the next
 * schedule to [org.mindanchor.HomeActivity.onCreate]'s unconditional
 * re-arm on the next cold start, which is the self-repair path for
 * "process died between a successful upload and rescheduling" too.
 */
class NightlySnapshotWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val database = AnchorDatabase.get(ctx)
        val continuityPrefs = ContinuityPrefs(ctx)
        val coordinator = CheckpointBackupWorker.buildCoordinator(
            ctx = ctx,
            database = database,
            isBackupEnabled = { it.backupEnabled.first() && it.nightlySnapshotsEnabled.first() },
        )

        val checkpointResult = coordinator.runCheckpoint()
        val verified = checkpointResult as? CheckpointResult.Verified
            ?: return checkpointResult.toWorkResult()

        val auth = GoogleDriveAuth(ctx)
        val remoteBackupStore = GoogleDriveObjectStore(currentAccessToken = auth::currentAccessToken)
        val versionedName = ContinuityFiles.versioned(Instant.ofEpochMilli(verified.createdAt), verified.snapshotId)

        return when (val putResult = remoteBackupStore.put(versionedName, verified.envelopeBytes)) {
            is RemoteResult.Ok -> {
                continuityPrefs.recordNightlyVerified(verified.createdAt, verified.snapshotId, verified.contentSha256)
                ContinuityWorkScheduler.ensureNightlyScheduled(ctx)
                Result.success()
            }
            is RemoteResult.AuthExpired -> {
                continuityPrefs.recordError(ContinuityErrorCode.AUTH)
                Result.success()
            }
            is RemoteResult.Permanent -> {
                continuityPrefs.recordError(ContinuityErrorCode.NETWORK)
                Result.success()
            }
            is RemoteResult.Retryable -> {
                continuityPrefs.recordError(ContinuityErrorCode.NETWORK)
                Result.retry()
            }
        }
    }
}
