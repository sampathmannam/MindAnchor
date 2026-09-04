package org.mindanchor.continuity

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [BackupHealth.compute] is a pure function of a [ContinuityPrefs]
 * snapshot; every combination here is exercised without DataStore,
 * Context, or Robolectric.
 */
class BackupHealthTest {

    private val verified = ContinuityPrefs.VerifiedRecord(at = 1_000L, snapshotId = "snap-1", contentHash = "hash-1")

    @Test
    fun `backup disabled is Pending regardless of everything else`() {
        val health = BackupHealth.compute(
            backupEnabled = false,
            hasVerifiedRecoveryKey = true,
            lastErrorCode = ContinuityErrorCode.NONE,
            lastVerifiedCheckpoint = verified,
        )
        assertEquals(BackupHealth.Pending, health)
    }

    @Test
    fun `backup enabled but no verified checkpoint yet is Pending`() {
        val health = BackupHealth.compute(
            backupEnabled = true,
            hasVerifiedRecoveryKey = true,
            lastErrorCode = ContinuityErrorCode.NONE,
            lastVerifiedCheckpoint = null,
        )
        assertEquals(BackupHealth.Pending, health)
    }

    @Test
    fun `no verified recovery key is RecoveryKeyRequired even with a past verified checkpoint`() {
        val health = BackupHealth.compute(
            backupEnabled = true,
            hasVerifiedRecoveryKey = false,
            lastErrorCode = ContinuityErrorCode.NONE,
            lastVerifiedCheckpoint = verified,
        )
        assertEquals(BackupHealth.RecoveryKeyRequired, health)
    }

    @Test
    fun `KEY_MISSING error code is RecoveryKeyRequired`() {
        val health = BackupHealth.compute(
            backupEnabled = true,
            hasVerifiedRecoveryKey = true,
            lastErrorCode = ContinuityErrorCode.KEY_MISSING,
            lastVerifiedCheckpoint = null,
        )
        assertEquals(BackupHealth.RecoveryKeyRequired, health)
    }

    @Test
    fun `AUTH error code is NeedsSignIn`() {
        val health = BackupHealth.compute(
            backupEnabled = true,
            hasVerifiedRecoveryKey = true,
            lastErrorCode = ContinuityErrorCode.AUTH,
            lastVerifiedCheckpoint = verified,
        )
        assertEquals(BackupHealth.NeedsSignIn, health)
    }

    @Test
    fun `VERIFY_FAILED error code is VerificationFailed`() {
        val health = BackupHealth.compute(
            backupEnabled = true,
            hasVerifiedRecoveryKey = true,
            lastErrorCode = ContinuityErrorCode.VERIFY_FAILED,
            lastVerifiedCheckpoint = verified,
        )
        assertEquals(BackupHealth.VerificationFailed, health)
    }

    @Test
    fun `RESTORE_VERIFY_FAILED error code is VerificationFailed`() {
        val health = BackupHealth.compute(
            backupEnabled = true,
            hasVerifiedRecoveryKey = true,
            lastErrorCode = ContinuityErrorCode.RESTORE_VERIFY_FAILED,
            lastVerifiedCheckpoint = verified,
        )
        assertEquals(BackupHealth.VerificationFailed, health)
    }

    @Test
    fun `DECODE_FAILED error code is VerificationFailed`() {
        val health = BackupHealth.compute(
            backupEnabled = true,
            hasVerifiedRecoveryKey = true,
            lastErrorCode = ContinuityErrorCode.DECODE_FAILED,
            lastVerifiedCheckpoint = null,
        )
        assertEquals(BackupHealth.VerificationFailed, health)
    }

    @Test
    fun `NETWORK error code falls through to the last verified checkpoint`() {
        val health = BackupHealth.compute(
            backupEnabled = true,
            hasVerifiedRecoveryKey = true,
            lastErrorCode = ContinuityErrorCode.NETWORK,
            lastVerifiedCheckpoint = verified,
        )
        assertEquals(BackupHealth.Verified(verified.at, verified.snapshotId, verified.contentHash), health)
    }

    @Test
    fun `NETWORK error code with no prior verified checkpoint is Pending`() {
        val health = BackupHealth.compute(
            backupEnabled = true,
            hasVerifiedRecoveryKey = true,
            lastErrorCode = ContinuityErrorCode.NETWORK,
            lastVerifiedCheckpoint = null,
        )
        assertEquals(BackupHealth.Pending, health)
    }

    @Test
    fun `NONE error code with a verified checkpoint is Verified with its fields`() {
        val health = BackupHealth.compute(
            backupEnabled = true,
            hasVerifiedRecoveryKey = true,
            lastErrorCode = ContinuityErrorCode.NONE,
            lastVerifiedCheckpoint = verified,
        )
        assertEquals(BackupHealth.Verified(1_000L, "snap-1", "hash-1"), health)
    }
}
