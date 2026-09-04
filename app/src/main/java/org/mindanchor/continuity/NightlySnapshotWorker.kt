package org.mindanchor.continuity

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.Instant
import kotlinx.coroutines.flow.first
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
 * SAME already-verified envelope bytes, via
 * [ContinuityBackupCoordinator.putAndVerifyBytes] — a real upload →
 * download → byte-compare re-check of THIS specific PUT (a different file
 * name is a genuinely separate network operation that can fail or corrupt
 * independently of the versioned upload), just without a second
 * decode/decrypt/content-hash pass, since those already proved correct for
 * these exact bytes. Only when *that* also verifies does this worker record
 * the nightly-specific health fields in [ContinuityPrefs] — a corrupted
 * `LATEST` refresh records [ContinuityErrorCode.VERIFY_FAILED] instead
 * (surfacing as [BackupHealth.VerificationFailed]) and skips
 * [ContinuityPrefs.recordNightlyVerified], but does not fail the whole
 * night: the versioned archive is the durable repair point (Program 0 never
 * deletes old versioned snapshots), and [CheckpointBackupWorker] will
 * re-verify and refresh `LATEST` from scratch on the very next ordinary
 * Journal/Notes/Letters save regardless. Only a fully successful night
 * (both the versioned upload AND the `LATEST` refresh verifying)
 * reschedules the next one via [ContinuityWorkScheduler.ensureNightlyScheduled];
 * every other outcome (backup off, no key, a genuine failure at either
 * step) leaves the next schedule to [org.mindanchor.HomeActivity.onCreate]'s
 * unconditional re-arm on the next cold start, which is the self-repair
 * path for "process died between a successful upload and rescheduling"
 * too.
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
        // bytes, but still re-prove THIS PUT with a real download +
        // byte-compare — a different file name is a separate network
        // operation that can fail/corrupt independently of the versioned
        // upload, so a bare put() here would be exactly the "trust a 200
        // OK" gap runCheckpoint's own verify pipeline exists to close.
        return when (val refreshResult = coordinator.putAndVerifyBytes(ContinuityFiles.LATEST, verified.envelopeBytes)) {
            is PutAndVerifyResult.Verified -> {
                continuityPrefs.recordNightlyVerified(verified.createdAt, verified.snapshotId, verified.contentSha256)
                ContinuityWorkScheduler.ensureNightlyScheduled(ctx)
                Result.success()
            }
            // The versioned archive already verified and is the durable
            // repair point; recordNightlyVerified() is deliberately not
            // called for any of these — an honest failed/error signal
            // (recorded by putAndVerifyBytes itself) beats a false
            // "nightly verified", and CheckpointBackupWorker will refresh
            // LATEST again on the next ordinary save regardless.
            is PutAndVerifyResult.AuthExpired,
            is PutAndVerifyResult.PermanentFailure,
            is PutAndVerifyResult.VerificationFailed,
            -> Result.success()
            is PutAndVerifyResult.Retryable -> Result.retry()
        }
    }
}
