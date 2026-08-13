package org.mindanchor.backup

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * v0.25.6: the WorkManager-driven drain of the v0.25.5
 * [PendingBackup] queue. The data layer that v0.25.5
 * shipped (the queue + [BackupPrefs.enqueuePending] +
 * [BackupPrefs.removePending]) is the contract that
 * matters for the on-write path; this class is the
 * half that was deliberately split out of v0.25.5-WP-H
 * so the data layer could be tested and shipped without
 * a Worker class. v0.25.6 closes the split.
 *
 * ## Why a Worker, not a flow collector
 *
 * The on-write trigger ([BackupScheduler.start]) runs
 * for the lifetime of the caller's scope — typically
 * the application scope, which dies with the process.
 * A retry that outlasts the process needs a system-
 * driven scheduler; WorkManager is the only
 * [androidx.work.NetworkType.CONNECTED]-constrained
 * scheduler the launcher's existing dependency set
 * ships.
 *
 * ## Lifecycle
 *
 *  - The on-write trigger enqueues a [PendingBackup]
 *    on every non-[AppendResult.Ok]. The same trigger
 *    then calls [enqueueIfNeeded] to schedule this
 *    worker.
 *  - WorkManager holds the request until the device
 *    is on a CONNECTED network, then runs [doWork].
 *  - [doWork] reads the queue, dispatches each entry
 *    against the right [BackupTarget], and removes
 *    successful entries. A non-Ok result stops the
 *    drain and returns [Result.retry] — the failed
 *    entry stays in the queue for the next run.
 *
 * ## Idempotence
 *
 * Each call to [enqueueIfNeeded] uses
 * [ExistingWorkPolicy.KEEP]. A second enqueue while
 * the worker is already running is a no-op (the first
 * run will drain whatever was queued at the moment it
 * read the flow). A second enqueue after the worker
 * has finished its run *replaces* the finished record
 * with a fresh one — which is fine, because the worker
 * re-reads the queue from disk on every run, so a
 * "replacement" that fires the same doWork path is
 * just an extra drain. (Use REPLACE if the future
 * "AuthExpired → notification" path needs to suppress
 * a not-yet-shown notification; KEEP is the right
 * choice for the v0.25.6 scope.)
 *
 * ## Why a one-shot, not a periodic worker
 *
 * The periodic-worker pattern (every-6h, like
 * [org.mindanchor.vitals.coros.CorosSyncWorker]) is
 * the wrong shape here: a periodic run that finds an
 * empty queue wastes the user's battery on a no-op
 * round-trip. The one-shot pattern is event-driven —
 * the on-write trigger kicks it on every enqueue, and
 * WorkManager's backoff table handles the "still
 * offline" case naturally.
 *
 * @wording-reviewed — the worker's name is the only
 * surface the user sees in the system WorkManager
 * settings panel. The label is "MindAnchor backup
 * retry" by convention; the system shows the
 * fully-qualified class name when the label is
 * missing. See [NAME] for the unique work name.
 */
class BackupRetryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    /**
     * Reads the pending queue from [BackupPrefs],
     * dispatches each entry against the right
     * [BackupTarget] (a fresh [GoogleDriveBackupTarget]
     * per [ContentType], built from the same
     * [GoogleDriveAuth] the rest of the v0.25.4
     * surface uses), and removes successful entries
     * from the queue.
     *
     * A non-[AppendResult.Ok] result stops the drain
     * and returns [Result.retry] so WorkManager picks
     * it up on the next backoff tick. The failed entry
     * stays in the queue — the [PendingBackup] is
     * the source of truth, and the worker does not
     * have the standing to drop it.
     *
     * The drain is fail-soft: a single bad entry is
     * wrapped in [runCatching] so a malformed payload
     * (or any other per-entry exception) does not take
     * the whole run down. The [PendingBackupLog.decode]
     * already drops corrupt lines, so the worker
     * should never see one — defense in depth.
     */
    @Suppress("detekt.SwallowedException", "ReturnCount", "LongMethod")
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val backupPrefs = BackupPrefs(ctx)
        // v0.25.7+ WP-2: re-read the queue inside the
        // loop instead of snapshotting it at the start.
        // The snapshot-once pattern was a durability
        // hole: a concurrent enqueue that happened
        // after the snapshot was taken (and while the
        // worker was running) would use
        // ExistingWorkPolicy.KEEP on its own
        // schedule — WorkManager saw the in-flight
        // worker and dropped the new enqueue. The
        // concurrent entry then sat in the queue
        // until the user wrote something else, which
        // triggered a fresh enqueue that drained
        // both. On a quiet day the entry waited
        // indefinitely. Re-reading inside the loop
        // picks up anything enqueued while we were
        // draining the previous batch.
        val auth = GoogleDriveAuth(ctx)
        // v0.25.7+ WP-2: reuse a single OkHttpClient
        // for the entire run. The original code
        // constructed a fresh client inside the
        // function; OkHttpClient owns a Dispatcher
        // with a non-daemon ExecutorService that is
        // never garbage-collected (the Kotlin
        // reference goes out of scope, the thread
        // pool doesn't). With the worker firing on
        // backoff, this was a slow thread-pool leak
        // over the process lifetime. A single client
        // per run is the smallest correct shape;
        // a process-singleton is a v0.25.7+ follow-up.
        val client = OkHttpClient()
        val notesTarget = GoogleDriveBackupTarget(
            client = client,
            auth = auth,
            type = ContentType.Notes,
        )
        val lettersTarget = GoogleDriveBackupTarget(
            client = client,
            auth = auth,
            type = ContentType.Letters,
        )
        var totalDrained = 0
        try {
            while (true) {
                val queue = runCatching {
                    backupPrefs.pendingBackups.first()
                }.getOrElse {
                    Log.w(LOG_TAG, "doWork: failed to read pendingBackups", it)
                    return Result.retry()
                }
                if (queue.isEmpty()) break
                for (entry in queue) {
                    val target = when (entry.type) {
                        ContentType.Notes -> notesTarget
                        ContentType.Letters -> lettersTarget
                    }
                    val result = runCatching { target.append(entry.type, entry.payload) }
                        .getOrElse {
                            Log.w(
                                LOG_TAG,
                                "doWork: append threw for ${entry.type.fileName}",
                                it,
                            )
                            return Result.retry()
                        }
                    if (result is AppendResult.Ok) {
                        // Best-effort: a remove failure is not a reason
                        // to mark the whole run as failed. The next run
                        // will re-attempt an already-appended entry; the
                        // Drive target's append is "add to the file",
                        // which produces a duplicate entry on disk. The
                        // duplicate is acceptable for a journal-style
                        // append-only log (a restore can dedup by
                        // timestamp + body hash), and the cost of a
                        // duplicate is much smaller than the cost of
                        // losing a backup.
                        runCatching { backupPrefs.removePending(entry) }
                            .onFailure { Log.w(LOG_TAG, "doWork: removePending failed", it) }
                    } else {
                        Log.w(
                            LOG_TAG,
                            "doWork: non-Ok result for ${entry.type.fileName} — " +
                                "stopping drain, will retry",
                        )
                        return Result.retry()
                    }
                }
                totalDrained += queue.size
                // Re-read the queue at the top of the loop to pick
                // up concurrent enqueues. A `while (true)` that
                // never sees an empty queue would loop forever;
                // the `break` on the empty read is the exit
                // condition. A concurrent enqueue between the
                // inner loop's last removePending and the next
                // .first() will be picked up on the next iteration.
            }
        } finally {
            // v0.25.7+ WP-2: shut down the client's
            // dispatcher ExecutorService so the
            // thread pool is not leaked. shutdown()
            // is non-blocking; awaitTermination
            // bounds the wait so a stuck network
            // thread doesn't hang the worker.
            client.dispatcher.executorService.shutdown()
            runCatching {
                client.dispatcher.executorService.awaitTermination(
                    SHUTDOWN_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS,
                )
            }
        }
        Log.i(LOG_TAG, "doWork: drained $totalDrained entries")
        return Result.success()
    }

    companion object {
        /**
         * The unique work name. Distinct from
         * [org.mindanchor.vitals.coros.CorosSyncWorker.PERIODIC_NAME]
         * and `coros_sync_oneshot` so the three workers
         * never share a queue — each is a separately-
         * scheduled stream. A user with all three
         * side-channels enabled (COROS, Google Drive
         * auto-backup) sees three independent rows in
         * the system WorkManager panel, not one.
         */
        const val NAME = "backup_retry_oneshot"

        /**
         * v0.25.7+ WP-2: how long doWork's
         * `finally` block waits for the OkHttp
         * client's thread pool to terminate
         * before giving up. 5s is a balance: a
         * stuck network thread should not hang
         * the worker forever, but a normal
         * request + response round-trip needs
         * time to wind down.
         */
        private const val SHUTDOWN_TIMEOUT_SECONDS: Long = 5

        /**
         * Schedules the worker. The work is constrained
         * to [NetworkType.CONNECTED] — the worker
         * drains [PendingBackup]s that failed because
         * the device was offline, so a CONNECTED
         * constraint is the right shape. A periodic
         * worker would be a wasted run when the queue
         * is empty; the on-write trigger enqueues on
         * demand, and WorkManager's backoff table
         * handles the "still offline" case.
         *
         * The [ExistingWorkPolicy.KEEP] policy means
         * a second enqueue while the worker is
         * already running is a no-op — the in-flight
         * run will see whatever the queue held at the
         * moment it read. A second enqueue after a
         * completed run replaces the finished record
         * with a fresh one; the next run picks up
         * the new state of the queue.
         */
        fun enqueueIfNeeded(context: Context) {
            val request = OneTimeWorkRequestBuilder<BackupRetryWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        private const val LOG_TAG = "MindAnchor/BackupRetry"
    }
}
