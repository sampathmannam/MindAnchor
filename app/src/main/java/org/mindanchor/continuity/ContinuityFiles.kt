package org.mindanchor.continuity

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The naming scheme for the two kinds of object
 * [org.mindanchor.backup.GoogleDriveObjectStore] writes: one mutable
 * "latest" object, and an ever-growing set of immutable, timestamped
 * snapshots. Pure filename/date-formatting logic — no network, no I/O.
 *
 * Program 0 does not delete old versioned snapshots; there is no
 * `prune`/`delete` helper here on purpose.
 */
object ContinuityFiles {
    const val LATEST = ContinuityContract.LATEST_FILE_NAME
    const val SNAPSHOT_PREFIX = "MindAnchor-Continuity-Snapshot-"

    /**
     * The Drive object name for a single versioned snapshot: the
     * [SNAPSHOT_PREFIX], an RFC3339-ish UTC timestamp
     * (`yyyyMMdd'T'HHmmss'Z'`) derived from [createdAt], and
     * [snapshotId] (see [ContinuitySnapshot.snapshotId]) so two
     * snapshots created in the same second never collide.
     */
    fun versioned(createdAt: Instant, snapshotId: String): String =
        "$SNAPSHOT_PREFIX${
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                .withZone(ZoneOffset.UTC).format(createdAt)
        }-$snapshotId.mab"
}
