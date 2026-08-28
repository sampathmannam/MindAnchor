package org.mindanchor.continuity

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Local WorkManager scheduling for Task 10 continuity backup —
 * [CheckpointBackupWorker] (on-write, unique one-time work, replaced on
 * every new save) and [NightlySnapshotWorker] (nightly, self-rescheduling
 * one-time work). Same shape as [org.mindanchor.vitals.coros.CorosSyncWorker]'s
 * `syncNow`/`ensureScheduled`/`cancel` companion, pulled into a standalone
 * object because both workers share it.
 *
 * Every function here is pure local scheduling: it enqueues work with
 * WorkManager and returns. Nothing in this file constructs a sign-in
 * token or otherwise touches the network — the actual upload happens
 * later, inside the worker, once WorkManager's own `CONNECTED` constraint
 * is satisfied. This is what keeps [org.mindanchor.HomeActivity]'s
 * cold-start scheduling calls offline (see [ContinuityWorkSchedulerTest]'s
 * network-boundary test, which pins this file — by name search, so it
 * catches even a doc-comment mention — as never referencing the Drive
 * auth surface).
 */
object ContinuityWorkScheduler {

    /**
     * The unique work name for the on-write checkpoint. [ExistingWorkPolicy.REPLACE]
     * is load-bearing: if a new save happens while a checkpoint upload is
     * in flight, the in-flight worker is cancelled and a fresh one
     * captures the complete *current* state, rather than uploading a
     * checkpoint that is already stale by the time it finishes.
     */
    internal const val CHECKPOINT_WORK_NAME = "continuity_checkpoint_backup"

    /**
     * The unique work name for the nightly versioned snapshot. Also
     * [ExistingWorkPolicy.REPLACE] — [ensureNightlyScheduled] is called on
     * every cold start (idempotent self-repair, see [NightlySnapshotWorker]'s
     * KDoc) and a redundant call must not stack a second nightly job next
     * to the one already scheduled.
     */
    internal const val NIGHTLY_WORK_NAME = "continuity_nightly_snapshot"

    /** The local time-of-day the nightly snapshot targets. Android may defer the actual run. */
    val NIGHTLY_TARGET_TIME: LocalTime = LocalTime.of(2, 0)

    /**
     * Enqueues (or replaces) the checkpoint worker. Called after every
     * write to a store [org.mindanchor.continuity.ContinuitySnapshot]
     * captures — see the Task 10 brief's Journal/measure/Notes/Letters
     * call sites.
     */
    fun requestCheckpoint(context: Context) {
        markDirtyIfNotAlready(context)
        workManagerOrNull(context)?.enqueueUniqueWork(
            CHECKPOINT_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            buildCheckpointRequest(),
        )
    }

    /**
     * Arms (or re-arms) the nightly snapshot for the next local 02:00.
     * Called unconditionally from [org.mindanchor.HomeActivity.onCreate]
     * on every cold start, and again by [NightlySnapshotWorker] itself at
     * the end of a successful run — so a process death between "upload
     * verified" and "reschedule the next night" self-repairs on the next
     * app open, per the plan's continuity guarantees.
     */
    fun ensureNightlyScheduled(context: Context, now: ZonedDateTime = ZonedDateTime.now()) {
        workManagerOrNull(context)?.enqueueUniqueWork(
            NIGHTLY_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            buildNightlyRequest(now),
        )
    }

    /** Cancels both unique works. Called when the user disables continuity backup. */
    fun cancelAll(context: Context) {
        val workManager = workManagerOrNull(context) ?: return
        workManager.cancelUniqueWork(CHECKPOINT_WORK_NAME)
        workManager.cancelUniqueWork(NIGHTLY_WORK_NAME)
    }

    /**
     * [WorkManager.getInstance] throws [IllegalStateException] when
     * WorkManager has not been initialized for [context] — the ordinary
     * case in production (the manifest's `WorkManagerInitializer`
     * `ContentProvider` always runs first) but not guaranteed in every
     * JVM test's minimal Robolectric harness, including ones that
     * predate Task 10 and exercise [org.mindanchor.data.NotesPrefs] /
     * [org.mindanchor.letters.LetterStore] writes directly. Scheduling a
     * checkpoint is best-effort, local convenience — never something a
     * save should fail over — so this degrades to a silent no-op rather
     * than propagate the exception into every write-path caller.
     */
    private fun workManagerOrNull(context: Context): WorkManager? =
        runCatching { WorkManager.getInstance(context) }.getOrNull()

    /**
     * The checkpoint's constraints: connected-network only, no battery
     * constraint (an on-write checkpoint should not wait for
     * "not low battery" — the user is actively using the app right now).
     * `internal` so [ContinuityWorkSchedulerTest] can assert on this
     * directly ([androidx.work.Constraints] is a plain public API class
     * with public getters, unlike the built [OneTimeWorkRequest]'s
     * internal `WorkSpec`, which is not a stable inspection surface).
     */
    internal fun checkpointConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** The nightly snapshot's constraints: connected-network + battery-not-low. */
    internal fun nightlyConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    /** Builds the checkpoint [OneTimeWorkRequest] — see [checkpointConstraints]. */
    internal fun buildCheckpointRequest(): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<CheckpointBackupWorker>()
            .setConstraints(checkpointConstraints())
            .build()

    /**
     * Builds the nightly [OneTimeWorkRequest] — see [nightlyConstraints] —
     * with an initial delay computed by [nextNightlyDelayMillis].
     */
    internal fun buildNightlyRequest(now: ZonedDateTime): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<NightlySnapshotWorker>()
            .setInitialDelay(nextNightlyDelayMillis(now), TimeUnit.MILLISECONDS)
            .setConstraints(nightlyConstraints())
            .build()

    /**
     * The delay, in milliseconds, from [now] to the next local occurrence
     * of [NIGHTLY_TARGET_TIME]. If [now] is already at or past today's
     * target time, the delay lands on tomorrow's occurrence instead.
     *
     * ## Why this is DST-safe
     *
     * A naive `now + 24 * 60 * 60 * 1000` is wrong on any day the local
     * UTC offset changes: the wall-clock gap between "now" and "the next
     * 02:00" is 23h on a spring-forward day and 25h on a fall-back day,
     * not always 24h. This function never adds a fixed millisecond
     * offset. It builds the *target* as a [ZonedDateTime] — `today's (or
     * tomorrow's) date, at 02:00, in [now]'s time zone` — and then asks
     * [Duration.between] for the actual elapsed wall-clock time between
     * two zoned instants. [Duration.between] resolves each
     * [ZonedDateTime] to its real UTC instant first (using the zone's
     * offset *at that specific date*), so the returned duration already
     * accounts for any DST transition that falls between [now] and the
     * target — 23h, 24h, or 25h, whichever is actually correct for that
     * calendar boundary. [coerceAtLeast] is a final defensive floor, not
     * the mechanism that makes this correct: the `isAfter` check above it
     * already guarantees a strictly-future target, so [Duration.between]
     * should never be negative.
     */
    internal fun nextNightlyDelayMillis(now: ZonedDateTime, targetTime: LocalTime = NIGHTLY_TARGET_TIME): Long {
        var next = now.toLocalDate().atTime(targetTime).atZone(now.zone)
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        return Duration.between(now, next).toMillis().coerceAtLeast(0L)
    }

    /**
     * Fire-and-forget: marks [ContinuityPrefs.dirtySince] with the current
     * time if no unconfirmed change is already recorded. Same
     * "sync entry point launches its own coroutine to write a suspend
     * DataStore field" shape as [org.mindanchor.goinglight.GoingLightScheduler.enable].
     * A DataStore write is best-effort informational state for the UI; it
     * must never block or fail the (synchronous) WorkManager enqueue this
     * function is called from.
     */
    private fun markDirtyIfNotAlready(context: Context) {
        val prefs = ContinuityPrefs(context)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            prefs.markDirtyIfNotAlready(System.currentTimeMillis())
        }
    }
}
