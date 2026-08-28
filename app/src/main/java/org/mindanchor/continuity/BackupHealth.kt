package org.mindanchor.continuity

/**
 * The UI-facing continuity-backup health state (Task 12 owns the actual
 * surface; Task 10 defines the domain type here because
 * [CheckpointBackupWorker] / [NightlySnapshotWorker] are what update the
 * [ContinuityPrefs] state this is derived from).
 */
sealed class BackupHealth {
    /** The last checkpoint or nightly snapshot that was uploaded AND verified. */
    data class Verified(val at: Long, val snapshotId: String, val contentHash: String) : BackupHealth()

    /** Backup is off, or on but has never completed a verified checkpoint yet. */
    data object Pending : BackupHealth()

    /** The last attempt failed because the stored Drive access token expired. */
    data object NeedsSignIn : BackupHealth()

    /** No verified recovery key is on this device — nothing can be encrypted yet. */
    data object RecoveryKeyRequired : BackupHealth()

    /** The last attempt uploaded, but download/decrypt verification did not match. */
    data object VerificationFailed : BackupHealth()

    companion object {
        /**
         * Derives the current [BackupHealth] from a snapshot of
         * [ContinuityPrefs]'s stored state. Pure function — no DataStore,
         * no Context — so [BackupHealthTest] can exercise every
         * combination directly.
         *
         * Precedence: an unrecoverable local condition (backup off, no
         * verified key) wins over a stale error code; a `NONE`/`NETWORK`
         * error code (network trouble is transient and WorkManager is
         * already retrying it) falls through to whatever the last
         * verified checkpoint says, so a temporary network hiccup does
         * not flip a previously-healthy "Verified" state to something
         * alarming.
         */
        fun compute(
            backupEnabled: Boolean,
            hasVerifiedRecoveryKey: Boolean,
            lastErrorCode: ContinuityErrorCode,
            lastVerifiedCheckpoint: ContinuityPrefs.VerifiedRecord?,
        ): BackupHealth {
            if (!backupEnabled) return Pending
            if (!hasVerifiedRecoveryKey) return RecoveryKeyRequired
            when (lastErrorCode) {
                ContinuityErrorCode.AUTH -> return NeedsSignIn
                ContinuityErrorCode.VERIFY_FAILED, ContinuityErrorCode.DECODE_FAILED -> return VerificationFailed
                ContinuityErrorCode.KEY_MISSING -> return RecoveryKeyRequired
                ContinuityErrorCode.NETWORK, ContinuityErrorCode.NONE -> Unit
            }
            return lastVerifiedCheckpoint?.let { Verified(it.at, it.snapshotId, it.contentHash) } ?: Pending
        }
    }
}
