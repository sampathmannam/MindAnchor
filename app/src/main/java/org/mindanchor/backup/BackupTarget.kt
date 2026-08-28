package org.mindanchor.backup

/**
 * The abstraction over a backup destination.
 *
 * The Google Drive path (opt-in) is the only shipped
 * implementation ([GoogleDriveBackupTarget]); the
 * interface exists so [BackupScheduler] does not
 * depend on Drive specifically.
 *
 * ## Threading
 *
 * Implementations are `suspend` so callers don't
 * need to wrap them in `withContext(Dispatchers.IO)`.
 *
 * ## What "append" means
 *
 * The contract is: take [payload] and add it to the
 * destination's record of [type]. The destination
 * decides what "add" means — for Drive, it is a
 * byte-range PUT that grows the file. The interface
 * says nothing about the format on disk; the
 * implementation owns that.
 *
 * ## What "download" means
 *
 * v0.70.7: the restore half of the contract this
 * interface's own KDoc anticipated ("a future
 * restore(type, since) method belongs here when the
 * surface grows"). Returns the destination's current
 * complete record of [type] — for Drive, the whole
 * file's bytes — or null if nothing has ever been
 * written for that type. [BackupScheduler.restoreAll]
 * splits the bytes on newline and parses each line
 * back into an entry.
 */
interface BackupTarget {
    /**
     * Appends [payload] to the destination's record
     * of [type]. The payload is already
     * transport-ready (encoded — whatever the
     * transport wants on the wire); the destination
     * does no further wrapping.
     *
     * @return [AppendResult.Ok] on a 2xx
     * round-trip, [AppendResult.AuthExpired] when
     * the user's OAuth token is no longer valid
     * and a re-prompt is needed,
     * [AppendResult.QuotaExceeded] when the
     * destination refuses the write on size
     * grounds, and [AppendResult.NetworkError]
     * wrapping the message for anything else
     * (DNS, TLS, IO).
     */
    suspend fun append(type: ContentType, payload: ByteArray): AppendResult

    /**
     * Downloads the destination's current complete
     * record of [type].
     *
     * @return the raw bytes, or null when nothing
     * has ever been written for [type] (a fresh
     * install, or this type was never backed up
     * from any device) — that is the ordinary "no
     * data yet" outcome, not a failure. A genuine
     * failure (auth, network) also returns null;
     * [BackupScheduler.restoreAll] does not
     * distinguish the two, because either way there
     * is nothing to restore right now and the next
     * nightly sync will try again.
     */
    suspend fun download(type: ContentType): ByteArray?
}

/**
 * The outcome of a [BackupTarget.append] call. The
 * shape is intentionally small: the launcher
 * surfaces a one-line message for each non-Ok
 * variant and never reads the message body in a
 * way that needs to be parsed.
 */
sealed interface AppendResult {
    /**
     * The append landed. The file on the destination
     * is now one entry larger.
     */
    data object Ok : AppendResult

    /**
     * The user's OAuth token is no longer valid.
     * The Drive surface triggers a silent-sign-in
     * and retries once; on the second failure the
     * Settings sub-section flips to "Sign in with
     * Google" so the user knows the bridge has
     * disconnected.
     */
    data object AuthExpired : AppendResult

    /**
     * The destination refused the write on size
     * grounds (e.g. the user has hit the Drive
     * storage cap). The launcher surfaces a
     * "free up space" message and disables the
     * nightly-sync toggle so the next attempt does
     * not fail in the same way.
     */
    data object QuotaExceeded : AppendResult

    /**
     * Anything else. The message is for the
     * launcher log (`Log.w("MindAnchor/Backup",
     * ...)`); the user sees a generic "couldn't
     * back up right now, will retry" surface.
     */
    data class NetworkError(val message: String) : AppendResult
}
