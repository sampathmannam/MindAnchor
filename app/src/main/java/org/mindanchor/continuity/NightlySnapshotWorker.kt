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
 * capture → encrypt → upload → download-and-verify → acknowledge algorithm
 * as [CheckpointBackupWorker] (via [CheckpointBackupWorker.buildCoordinator]
 * and [ContinuityBackupCoordinator.runCheckpoint]), gated additionally on
 * [ContinuityPrefs.nightlySnapshotsEnabled] — the effective nightly-schedule
 * decision is `backupEnabled && nightlySnapshotsEnabled`, computed here
 * rather than baked into either flag's own stored default (see
 * [ContinuityPrefs.nightlySnapshotsEnabled]'s KDoc).
 *
 * Unlike the on-write checkpoint (which verifies against `LATEST`), this
 * worker points [ContinuityBackupCoordinator.runCheckpoint] at tonight's
 * [ContinuityFiles.versioned] name — the versioned file is the one that is
 * actually upload → download → byte-compare → decrypt → content-hash
 * verified, exactly like [CheckpointBackupWorker]'s checkpoint. Only when
 * that run verifies successfully does this worker refresh `LATEST` with the
 * SAME already-verified envelope bytes (a plain re-upload, not a second
 * independent download-verify pass — those bytes are already proven
 * correct) and record the nightly-specific health fields in
 * [ContinuityPrefs]. Only a fully successful night reschedules the next one
 * via [ContinuityWorkScheduler.ensureNightlyScheduled]; every other outcome
 * (backup off, no key, a genuine failure at either step) leaves the next
 * schedule to [org.mindanchor.HomeActivity.onCreate]'s unconditional re-arm
 * on the next cold start, which is the self-repair path for "process died
 * between a successful upload and rescheduling" too.
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

        // Step 1: capture, encrypt, upload the VERSIONED file, and
        // read-verify it — the same upload -> download -> byte-compare ->
        // decrypt -> content-hash sequence the on-write checkpoint runs
        // against LATEST, just targeted at tonight's versioned name.
        val checkpointResult = coordinator.runCheckpoint(
            targetFileName = { snapshot -> ContinuityFiles.versioned(Instant.ofEpochMilli(snapshot.createdAt), snapshot.snapshotId) },
        )
        val verified = checkpointResult as? CheckpointResult.Verified
            ?: return checkpointResult.toWorkResult()

        // Step 2: the versioned file is now proven correct byte-for-byte and
        // by content hash. Refresh LATEST with the SAME already-verified
        // bytes — a plain re-upload, not a second independent download-verify
        // pass.
        val auth = GoogleDriveAuth(ctx)
        val remoteBackupStore = GoogleDriveObjectStore(currentAccessToken = auth::currentAccessToken)

        return when (val putResult = remoteBackupStore.put(ContinuityFiles.LATEST, verified.envelopeBytes)) {
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
