package org.mindanchor.continuity

import org.mindanchor.backup.RemoteBackupStore
import org.mindanchor.backup.RemoteResult
import org.mindanchor.continuity.crypto.BackupEnvelopeCodec
import org.mindanchor.continuity.crypto.RecoveryKey

/**
 * The outcome of [ContinuityBackupCoordinator.runCheckpoint]. [CheckpointBackupWorker]
 * maps each case to a [androidx.work.ListenableWorker.Result] — see that
 * file's `toWorkResult()`.
 */
sealed class CheckpointResult {
    /**
     * Uploaded, downloaded back, and verified byte-exact + content-hash
     * exact. [envelopeBytes] are the already-verified bytes — carried so
     * [NightlySnapshotWorker] can reuse them for the `LATEST` file
     * without a second capture+encrypt.
     */
    data class Verified(
        val snapshotId: String,
        val createdAt: Long,
        val contentSha256: String,
        val envelopeBytes: ByteArray,
    ) : CheckpointResult()

    /** [ContinuityPrefs.backupEnabled] is false. Not an error — the user has not opted in. */
    data object BackupDisabled : CheckpointResult()

    /** No recovery key is stored, or it has not been [org.mindanchor.continuity.crypto.RecoveryKeyStore.markVerified]. */
    data object KeyMissing : CheckpointResult()

    /** The stored Drive access token is gone/expired. */
    data object AuthExpired : CheckpointResult()

    /** A transient remote failure (network error, 429, 5xx). WorkManager should retry. */
    data class Retryable(val errorCode: ContinuityErrorCode) : CheckpointResult()

    /** A non-transient remote failure (a genuinely bad request). Retrying will not help. */
    data class PermanentFailure(val errorCode: ContinuityErrorCode) : CheckpointResult()

    /**
     * The upload round-tripped, but the downloaded bytes did not match
     * byte-for-byte, or the decrypted content hash did not match what was
     * captured. The *previous* verified checkpoint remains the repair
     * point — nothing here is acknowledged or recorded as verified.
     */
    data class VerificationFailed(val errorCode: ContinuityErrorCode) : CheckpointResult()
}

/**
 * The outcome of [ContinuityBackupCoordinator.putAndVerifyBytes] — the
 * upload-transport half of [ContinuityBackupCoordinator.runCheckpoint]'s
 * steps 5-7 (put -> get -> byte-compare), reused on its own so
 * [NightlySnapshotWorker] can re-prove its `LATEST` refresh PUT without
 * repeating the decode/decrypt/content-hash checks a prior [runCheckpoint]
 * call already proved for the same bytes.
 */
sealed class PutAndVerifyResult {
    /** Uploaded, downloaded back, and verified byte-exact. */
    data object Verified : PutAndVerifyResult()

    /** The stored Drive access token is gone/expired. */
    data object AuthExpired : PutAndVerifyResult()

    /** A transient remote failure (network error, 429, 5xx). Caller may retry. */
    data class Retryable(val errorCode: ContinuityErrorCode) : PutAndVerifyResult()

    /** A non-transient remote failure. Retrying will not help. */
    data class PermanentFailure(val errorCode: ContinuityErrorCode) : PutAndVerifyResult()

    /** The upload round-tripped, but the downloaded bytes did not match byte-for-byte. */
    data class VerificationFailed(val errorCode: ContinuityErrorCode) : PutAndVerifyResult()
}

/**
 * The Task 10 "capture → encrypt → upload → download-and-verify → acknowledge"
 * algorithm, factored out of [CheckpointBackupWorker] so it is testable as
 * plain JVM logic (see [ContinuityBackupCoordinatorTest]) — no
 * [androidx.work.CoroutineWorker], no Room, no Context.
 *
 * Every collaborator is a narrow suspend function rather than a concrete
 * class, the same seam [org.mindanchor.backup.GoogleDriveObjectStore] uses
 * for `currentAccessToken`: it is what lets this class be exercised with
 * plain fakes instead of a real [org.mindanchor.data.db.AnchorDatabase] /
 * [android.content.Context] / Robolectric.
 *
 * ## Why "upload success" is never "backup success"
 *
 * [runCheckpoint] always downloads the just-uploaded file back after [put]
 * and compares it byte-for-byte to what was uploaded, then decrypts the
 * *downloaded* envelope and compares its content hash to the snapshot
 * captured in step 3. Only when both checks pass does this class call
 * [acknowledgePending] or [recordVerified] — a network layer that reports
 * "200 OK" on a truncated or corrupted body is exactly the failure mode
 * this guards against.
 *
 * ## Which file gets verified
 *
 * [runCheckpoint]'s `targetFileName` parameter picks the Drive object name
 * this verify sequence runs against — it defaults to [ContinuityFiles.LATEST]
 * for [CheckpointBackupWorker]'s on-write checkpoint, but [NightlySnapshotWorker]
 * passes it a versioned name instead, so the SAME upload → download →
 * byte-compare → decrypt → content-hash sequence verifies the nightly
 * snapshot too, rather than a second, unverified `put()`.
 */
class ContinuityBackupCoordinator(
    private val isBackupEnabled: suspend () -> Boolean,
    private val currentVerifiedKey: suspend () -> RecoveryKey?,
    private val remoteBackupStore: RemoteBackupStore,
    private val captureSnapshot: suspend (now: Long) -> ContinuitySnapshot,
    private val acknowledgePending: suspend (snapshotId: String) -> Unit,
    private val recordError: suspend (ContinuityErrorCode) -> Unit,
    private val recordVerified: suspend (at: Long, snapshotId: String, contentHash: String) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {

    suspend fun runCheckpoint(
        targetFileName: (snapshot: ContinuitySnapshot) -> String = { ContinuityFiles.LATEST },
    ): CheckpointResult {
        if (!isBackupEnabled()) return CheckpointResult.BackupDisabled

        val key = currentVerifiedKey()
        if (key == null) {
            recordError(ContinuityErrorCode.KEY_MISSING)
            return CheckpointResult.KeyMissing
        }

        val nowMs = now()
        val snapshot = captureSnapshot(nowMs)
        val plaintext = ContinuitySnapshotCodec.encode(snapshot)
        val envelope = BackupEnvelopeCodec.encrypt(plaintext, key, nowMs)
        val envelopeBytes = BackupEnvelopeCodec.encode(envelope).encodeToByteArray()
        val fileName = targetFileName(snapshot)

        // Step 5: upload.
        when (val putResult = remoteBackupStore.put(fileName, envelopeBytes)) {
            is RemoteResult.AuthExpired -> {
                recordError(ContinuityErrorCode.AUTH)
                return CheckpointResult.AuthExpired
            }
            is RemoteResult.Retryable -> {
                recordError(ContinuityErrorCode.NETWORK)
                return CheckpointResult.Retryable(ContinuityErrorCode.NETWORK)
            }
            is RemoteResult.Permanent -> {
                recordError(ContinuityErrorCode.NETWORK)
                return CheckpointResult.PermanentFailure(ContinuityErrorCode.NETWORK)
            }
            is RemoteResult.Ok -> Unit
        }

        // Step 6: immediately download back. Upload success alone is never acknowledged.
        val downloaded = when (val getResult = remoteBackupStore.get(fileName)) {
            is RemoteResult.AuthExpired -> {
                recordError(ContinuityErrorCode.AUTH)
                return CheckpointResult.AuthExpired
            }
            is RemoteResult.Retryable -> {
                recordError(ContinuityErrorCode.NETWORK)
                return CheckpointResult.Retryable(ContinuityErrorCode.NETWORK)
            }
            is RemoteResult.Permanent -> {
                recordError(ContinuityErrorCode.NETWORK)
                return CheckpointResult.PermanentFailure(ContinuityErrorCode.NETWORK)
            }
            is RemoteResult.Ok -> getResult.value
        }

        // Step 7: byte-exact comparison. Any mismatch is VERIFY_FAILED; nothing is acknowledged.
        if (downloaded == null || !downloaded.contentEquals(envelopeBytes)) {
            recordError(ContinuityErrorCode.VERIFY_FAILED)
            return CheckpointResult.VerificationFailed(ContinuityErrorCode.VERIFY_FAILED)
        }

        // Step 8: decode + decrypt the DOWNLOADED bytes (not the local envelope) and
        // compare the decrypted content hash to what was captured in step 3.
        val downloadedEnvelope = BackupEnvelopeCodec.decode(downloaded.decodeToString())
        if (downloadedEnvelope == null) {
            recordError(ContinuityErrorCode.DECODE_FAILED)
            return CheckpointResult.VerificationFailed(ContinuityErrorCode.DECODE_FAILED)
        }
        val decrypted = BackupEnvelopeCodec.decrypt(downloadedEnvelope, key)
        val decryptedJson = (decrypted as? BackupEnvelopeCodec.DecryptResult.Success)?.plaintextJson
        if (decryptedJson == null) {
            recordError(ContinuityErrorCode.DECODE_FAILED)
            return CheckpointResult.VerificationFailed(ContinuityErrorCode.DECODE_FAILED)
        }
        val decoded = ContinuitySnapshotCodec.decode(decryptedJson)
        val decodedSnapshot = (decoded as? ContinuitySnapshotCodec.DecodeResult.Success)?.snapshot
        // Re-derived, not just compared: the stamped formatVersion has to be
        // the version the content hash was actually computed under, and this
        // is the only place the phone that wrote the file can notice if it
        // is not. The alternative is a replacement phone discovering it as a
        // VerifyMismatch, after the merge.
        val reDerived = decodedSnapshot?.let {
            runCatching { ContinuityContentHasher.hash(it.payload, it.formatVersion) }.getOrNull()
        }
        if (decodedSnapshot == null ||
            decodedSnapshot.contentSha256 != snapshot.contentSha256 ||
            reDerived != snapshot.contentSha256
        ) {
            recordError(ContinuityErrorCode.VERIFY_FAILED)
            return CheckpointResult.VerificationFailed(ContinuityErrorCode.VERIFY_FAILED)
        }

        // Step 9: only now — after 6, 7, and 8 all passed — acknowledge and record verified.
        acknowledgePending(snapshot.snapshotId)
        recordVerified(nowMs, snapshot.snapshotId, snapshot.contentSha256)

        return CheckpointResult.Verified(
            snapshotId = snapshot.snapshotId,
            createdAt = nowMs,
            contentSha256 = snapshot.contentSha256,
            envelopeBytes = envelopeBytes,
        )
    }

    /**
     * Uploads [bytes] to [fileName], then immediately downloads it back and
     * compares byte-for-byte — the same "upload success alone is never
     * enough" check [runCheckpoint] performs in its steps 5-7, on its own
     * so a caller that already holds bytes proven correct by a prior
     * [runCheckpoint] call (decode/decrypt/content-hash included) can
     * re-prove just the transport step for a genuinely separate PUT to a
     * different file name, without repeating checks that would only ever
     * re-confirm what is already known.
     *
     * Calls [recordError] on any failure, with the same [ContinuityErrorCode]
     * [runCheckpoint] would record for the equivalent step. Never calls
     * [recordVerified] — recording *this specific* PUT as a verified
     * nightly/checkpoint state is the caller's call, not this function's.
     */
    suspend fun putAndVerifyBytes(fileName: String, bytes: ByteArray): PutAndVerifyResult {
        when (val putResult = remoteBackupStore.put(fileName, bytes)) {
            is RemoteResult.AuthExpired -> {
                recordError(ContinuityErrorCode.AUTH)
                return PutAndVerifyResult.AuthExpired
            }
            is RemoteResult.Retryable -> {
                recordError(ContinuityErrorCode.NETWORK)
                return PutAndVerifyResult.Retryable(ContinuityErrorCode.NETWORK)
            }
            is RemoteResult.Permanent -> {
                recordError(ContinuityErrorCode.NETWORK)
                return PutAndVerifyResult.PermanentFailure(ContinuityErrorCode.NETWORK)
            }
            is RemoteResult.Ok -> Unit
        }

        val downloaded = when (val getResult = remoteBackupStore.get(fileName)) {
            is RemoteResult.AuthExpired -> {
                recordError(ContinuityErrorCode.AUTH)
                return PutAndVerifyResult.AuthExpired
            }
            is RemoteResult.Retryable -> {
                recordError(ContinuityErrorCode.NETWORK)
                return PutAndVerifyResult.Retryable(ContinuityErrorCode.NETWORK)
            }
            is RemoteResult.Permanent -> {
                recordError(ContinuityErrorCode.NETWORK)
                return PutAndVerifyResult.PermanentFailure(ContinuityErrorCode.NETWORK)
            }
            is RemoteResult.Ok -> getResult.value
        }

        if (downloaded == null || !downloaded.contentEquals(bytes)) {
            recordError(ContinuityErrorCode.VERIFY_FAILED)
            return PutAndVerifyResult.VerificationFailed(ContinuityErrorCode.VERIFY_FAILED)
        }

        return PutAndVerifyResult.Verified
    }
}
