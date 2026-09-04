package org.mindanchor.continuity

import org.mindanchor.backup.RemoteBackupStore
import org.mindanchor.backup.RemoteResult
import org.mindanchor.continuity.crypto.BackupEnvelopeCodec
import org.mindanchor.continuity.crypto.RecoveryKey

/**
 * A remote backup that has already been downloaded, decrypted, and
 * verified — everything [RestoreScreen] needs to preview safely, and
 * everything [RestoreCoordinator.beginRestore] needs to stage it, without a
 * second download.
 *
 * [envelopeBytes] is the still-encrypted `.mab` file's raw bytes (what
 * actually gets written to the staging file); [snapshot] is the already-
 * decrypted, already-verified snapshot this envelope decrypts to. The UI
 * reads [snapshot] only for its counts/metadata — [snapshot].payload's
 * Journal/Notes/Letters entries carry body/title text that must never be
 * rendered on the restore confirmation screen (see [RestoreScreen]).
 */
data class RestoreCandidate(
    val remoteName: String,
    val envelopeBytes: ByteArray,
    val snapshot: ContinuitySnapshot,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RestoreCandidate) return false
        return remoteName == other.remoteName &&
            envelopeBytes.contentEquals(other.envelopeBytes) &&
            snapshot == other.snapshot
    }

    override fun hashCode(): Int =
        31 * (31 * remoteName.hashCode() + envelopeBytes.contentHashCode()) + snapshot.hashCode()
}

/** The outcome of [RestoreCandidateSelector.select]. */
sealed class CandidateSelectionResult {
    /**
     * A verified, decryptable candidate was found. [usedFallbackFrom] is
     * non-null when [ContinuityFiles.LATEST] itself was unusable (missing
     * or corrupt) and this candidate is the newest decryptable versioned
     * snapshot instead — the UI uses this to show "Using backup from ...
     * (Latest was unreadable)".
     */
    data class Found(val candidate: RestoreCandidate, val usedFallbackFrom: String?) : CandidateSelectionResult()

    /**
     * The entered recovery key does not match the key id recorded in a
     * candidate's envelope. This is a *distinct* failure mode from
     * corruption (see [BackupEnvelopeCodec.DecryptResult.WrongKey]'s KDoc)
     * and selection stops immediately on the first one encountered — a
     * wrong key would fail identically against every other candidate too,
     * and retrying across candidates would only obscure "you typed the key
     * wrong" as "no valid backup exists".
     */
    data object WrongRecoveryKey : CandidateSelectionResult()

    /** Every candidate that was tried failed to decode/decrypt/verify (and none was a wrong-key failure). */
    data object NoneAvailable : CandidateSelectionResult()

    /** [ContinuityFiles.LATEST] itself could not even be listed/downloaded due to a remote/auth problem. */
    data class RemoteError(val code: String) : CandidateSelectionResult()
}

/**
 * Step 3 of the Task 11 brief: picks a restore candidate safely.
 *
 * Candidate order: (1) [ContinuityFiles.LATEST]; (2) versioned snapshots
 * ([ContinuityFiles.SNAPSHOT_PREFIX]) newest to oldest, ordered by a
 * descending string sort on the filename — [ContinuityFiles.versioned]'s
 * `yyyyMMdd'T'HHmmss'Z'` timestamp segment sorts correctly as a plain
 * string, so this is simpler and just as correct as re-parsing each name's
 * embedded timestamp.
 *
 * For each candidate, in order: download, decode the envelope, and decrypt
 * with [key] — full verification (envelope decode, GCM auth via
 * [BackupEnvelopeCodec.decrypt], and that codec's own
 * `plaintextSha256` check) all happen before this function ever returns a
 * [CandidateSelectionResult.Found]. A corrupt [ContinuityFiles.LATEST]
 * automatically falls through to the newest decryptable versioned
 * snapshot. A wrong key stops the whole search immediately (see
 * [CandidateSelectionResult.WrongRecoveryKey]).
 */
object RestoreCandidateSelector {

    suspend fun select(remoteBackupStore: RemoteBackupStore, key: RecoveryKey): CandidateSelectionResult {
        when (val latestOutcome = tryCandidate(remoteBackupStore, ContinuityFiles.LATEST, key)) {
            is CandidateOutcome.Success ->
                return CandidateSelectionResult.Found(latestOutcome.candidate, usedFallbackFrom = null)
            is CandidateOutcome.WrongKey -> return CandidateSelectionResult.WrongRecoveryKey
            is CandidateOutcome.RemoteError -> return CandidateSelectionResult.RemoteError(latestOutcome.code)
            is CandidateOutcome.Unusable -> Unit // fall through to versioned snapshots
        }

        val versionedNames = when (val listed = remoteBackupStore.list(ContinuityFiles.SNAPSHOT_PREFIX)) {
            is RemoteResult.Ok -> listed.value.map { it.name }.distinct().sortedDescending()
            is RemoteResult.AuthExpired -> return CandidateSelectionResult.RemoteError("auth_expired")
            is RemoteResult.Retryable -> return CandidateSelectionResult.RemoteError(listed.code)
            is RemoteResult.Permanent -> return CandidateSelectionResult.RemoteError(listed.code)
        }

        for (name in versionedNames) {
            when (val outcome = tryCandidate(remoteBackupStore, name, key)) {
                is CandidateOutcome.Success ->
                    return CandidateSelectionResult.Found(outcome.candidate, usedFallbackFrom = ContinuityFiles.LATEST)
                is CandidateOutcome.WrongKey -> return CandidateSelectionResult.WrongRecoveryKey
                is CandidateOutcome.RemoteError, is CandidateOutcome.Unusable -> Unit // try the next candidate
            }
        }

        return CandidateSelectionResult.NoneAvailable
    }

    /** The per-candidate result of a single download+decode+decrypt attempt. */
    private sealed class CandidateOutcome {
        data class Success(val candidate: RestoreCandidate) : CandidateOutcome()
        data object WrongKey : CandidateOutcome()
        data class RemoteError(val code: String) : CandidateOutcome()

        /** Missing, corrupt, or an unsupported format version — anything that should fall through to the next candidate. */
        data object Unusable : CandidateOutcome()
    }

    private suspend fun tryCandidate(remoteBackupStore: RemoteBackupStore, name: String, key: RecoveryKey): CandidateOutcome {
        val bytes = when (val got = remoteBackupStore.get(name)) {
            is RemoteResult.Ok -> got.value ?: return CandidateOutcome.Unusable
            is RemoteResult.AuthExpired -> return CandidateOutcome.RemoteError("auth_expired")
            is RemoteResult.Retryable -> return CandidateOutcome.RemoteError(got.code)
            is RemoteResult.Permanent -> return CandidateOutcome.RemoteError(got.code)
        }
        val envelope = BackupEnvelopeCodec.decode(bytes.decodeToString()) ?: return CandidateOutcome.Unusable
        val decrypted = when (val result = BackupEnvelopeCodec.decrypt(envelope, key)) {
            is BackupEnvelopeCodec.DecryptResult.Success -> result.plaintextJson
            is BackupEnvelopeCodec.DecryptResult.WrongKey -> return CandidateOutcome.WrongKey
            is BackupEnvelopeCodec.DecryptResult.Corrupt -> return CandidateOutcome.Unusable
            is BackupEnvelopeCodec.DecryptResult.UnsupportedVersion -> return CandidateOutcome.Unusable
        }
        val snapshot = when (val decoded = ContinuitySnapshotCodec.decode(decrypted)) {
            is ContinuitySnapshotCodec.DecodeResult.Success -> decoded.snapshot
            is ContinuitySnapshotCodec.DecodeResult.UnsupportedVersion -> return CandidateOutcome.Unusable
            is ContinuitySnapshotCodec.DecodeResult.Corrupt -> return CandidateOutcome.Unusable
        }
        return CandidateOutcome.Success(RestoreCandidate(remoteName = name, envelopeBytes = bytes, snapshot = snapshot))
    }
}
