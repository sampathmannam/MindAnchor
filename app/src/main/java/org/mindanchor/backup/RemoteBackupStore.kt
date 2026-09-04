package org.mindanchor.backup

import java.time.Instant

/**
 * Task 9's narrow object-storage contract. Where [BackupTarget] models
 * "append a payload to a per-type stream" (the v0.25.4 legacy shape —
 * see [GoogleDriveBackupTarget]'s KDoc for why that framing is being
 * quarantined), [RemoteBackupStore] models plain named-object storage:
 * put a whole object under a name (replacing whatever was there),
 * read one back, list objects by name prefix.
 *
 * There is deliberately no `delete`. Program 0 does not remove old
 * versioned snapshots — retaining verified repair points is safer than
 * introducing cloud deletion before real-world size data exists.
 */
interface RemoteBackupStore {
    /**
     * Writes [bytes] under [name], replacing any existing object of
     * that name in full (not appending). [name] is always a filename
     * (see [org.mindanchor.continuity.ContinuityFiles]), never user
     * content; [bytes] is the only place content goes — the caller is
     * expected to have already encrypted it.
     */
    suspend fun put(name: String, bytes: ByteArray): RemoteResult<RemoteObject>

    /**
     * Reads the object named [name]. A missing object is not an
     * error — the caller may be asking for the very first backup —
     * so that case is [RemoteResult.Ok] wrapping a null value, not a
     * [RemoteResult.Retryable] or [RemoteResult.Permanent].
     */
    suspend fun get(name: String): RemoteResult<ByteArray?>

    /**
     * Lists every object whose name contains [prefix]. Used to
     * enumerate versioned snapshots (see
     * [org.mindanchor.continuity.ContinuityFiles.SNAPSHOT_PREFIX]).
     */
    suspend fun list(prefix: String): RemoteResult<List<RemoteObject>>
}

/**
 * A remote object's identity and metadata. Deliberately narrow: just
 * enough to address the object again ([id]), display it ([name],
 * [size], [modifiedTime]), and sort/prune it in a future task. Nothing
 * about the object's content lives here.
 */
data class RemoteObject(
    val id: String,
    val name: String,
    val size: Long,
    val modifiedTime: Instant,
)

/**
 * The outcome of a [RemoteBackupStore] call. [AuthExpired] and
 * [Retryable] are the two cases a caller can sensibly react to
 * automatically (re-prompt sign-in; retry later); [Permanent] is a
 * failure retrying will not fix (e.g. a malformed request).
 */
sealed interface RemoteResult<out T> {
    data class Ok<T>(val value: T) : RemoteResult<T>
    data object AuthExpired : RemoteResult<Nothing>
    data class Retryable(val code: String) : RemoteResult<Nothing>
    data class Permanent(val code: String) : RemoteResult<Nothing>
}
