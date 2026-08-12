package org.mindanchor.backup

/**
 * The v0.25.4 abstraction over a backup destination.
 *
 * The launcher has two outbound channels that can
 * hold user data: a local file picker
 * ([LocalBackupTarget], the default and the one the
 * first-run experience is wired to) and the Google
 * Drive path (opt-in, v0.25.4+). The interface is
 * what [BackupScheduler] dispatches against; the
 * concrete [GoogleDriveBackupTarget] is the v0.25.4
 * implementation that lives next to it.
 *
 * The contract is intentionally narrow: one
 * operation (append), one result type. Restore is
 * a separate concern that the v0.25.4 surface does
 * not yet ship — the on-write trigger is the only
 * MVP. A future `restore(type, since)` method
 * belongs here when the surface grows.
 *
 * ## Threading
 *
 * Implementations are `suspend` so callers don't
 * need to wrap them in `withContext(Dispatchers.IO)`.
 * A long-running upload is rare (a few KB at most
 * per entry) but the suspend contract makes it
 * possible for a future implementation to chunk
 * large payloads.
 *
 * ## What "append" means
 *
 * The contract is: take [payload] (already
 * encrypted by [EncryptedBackupCodec] if the
 * transport requires it) and add it to the
 * destination's record of [type]. The destination
 * decides what "add" means — for Drive, it is a
 * byte-range PUT that grows the file. For a future
 * local-network target, it might be an `append`
 * mode filesystem call. The interface says nothing
 * about the format on disk; the implementation
 * owns that.
 */
interface BackupTarget {
    /**
     * Appends [payload] to the destination's record
     * of [type]. The payload is already
     * transport-ready (encrypted, encoded — whatever
     * the transport wants on the wire); the
     * destination does no further wrapping.
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
     * storage cap, or a per-file size cap on a
     * future local-network target). The launcher
     * surfaces a "free up space" message and
     * disables the auto-backup toggle so the
     * next attempt does not fail in the same
     * way.
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
