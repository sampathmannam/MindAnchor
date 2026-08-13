package org.mindanchor.workmanager

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2 Finding #2: the `BackupRetryWorker` drain loop iterates a
 * snapshot of the queue taken at the top of each `while (true)`
 * iteration. A `PendingBackup` enqueued *during* the inner
 * `for (entry in queue)` loop is picked up on the next outer
 * iteration (the v0.25.7+ WP-2 fix), so the worker is no longer
 * stranded.
 *
 * v0.25.9 FIX: the inner for-loop now filters the
 * queue against a per-run `processed: MutableSet<PendingBackup>`.
 * A concurrent enqueue that pushes the queue past
 * `BackupPrefs.MAX_PENDING = 100` and trims the oldest entries
 * (or a no-op `removePending` on a trimmed entry) no longer
 * causes the worker to re-append a payload that is already on
 * Drive. The fix shape:
 *   1. The worker keeps a `processed` set across iterations.
 *   2. On each top-of-loop read, it filters
 *      `queue.filter { it !in processed }`.
 *   3. The drain completes when the current queue minus the
 *      in-flight set is empty.
 *
 * This test now pins the **fix shape** — the inner loop's
 * `queue.filter { it !in processed }` pattern. A regression
 * that drops the filter flips the assertion red.
 */
class WorkManagerConcurrencyFindingTest {

    @Test
    fun `BackupRetryWorker inner for-loop filters by a per-run processed set (v0_25_9 fix)`() {
        val source = readSource("backup/BackupRetryWorker.kt")
        assertNotNull(source)
        // The fix shape:
        //  - a `processed` set declared before the outer while loop
        //  - the inner for-loop iterates `toProcess` (not the
        //    raw `queue` snapshot)
        //  - the set is mutated as entries are processed
        val declaresProcessed = source!!.contains("val processed = mutableSetOf<PendingBackup>()") ||
            source.contains("val processed = mutableSetOf<")
        val usesToProcess = source.contains("val toProcess = queue.filter")
        val forLoopIteratesToProcess = source.contains("for (entry in toProcess)")
        assertTrue(
            "BackupRetryWorker v0.25.9 fix: the inner for-loop must filter the queue " +
                "against a per-run `processed` set so a concurrent enqueue that trims " +
                "the oldest entries does not cause the worker to re-append a payload " +
                "that is already on Drive. declaresProcessed=$declaresProcessed " +
                "usesToProcess=$usesToProcess forLoopIteratesToProcess=$forLoopIteratesToProcess.",
            declaresProcessed && usesToProcess && forLoopIteratesToProcess,
        )
    }

    /**
     * v2 Finding #3: `BackupScheduler.startIfNeeded` (called from
     * `HomeActivity.onCreate`) creates a fresh `OkHttpClient()` and
     * never closes its `Dispatcher` ExecutorService. This is the
     * same leak shape as v1 #4/#10 — the v0.25.7+ WP-2 fix closed
     * the leak in the *worker* but not in the *on-write trigger*.
     *
     * The two surfaces are independent OkHttpClient instances:
     * the worker creates one in `doWork` and closes it in `finally`;
     * the on-write trigger creates one in `startIfNeeded` and never
     * closes it. The latter outlives the process (the
     * `appScope` is a process-singleton), so the leak is bounded
     * to one ExecutorService per process — not dozens like the
     * pre-fix worker — but the leak is still there.
     */
    @Test
    fun `BackupScheduler startIfNeeded creates an OkHttpClient that is never closed`() {
        val source = readSource("backup/BackupScheduler.kt")
        assertNotNull(source)
        val createsClientInStartIfNeeded = source!!.contains("fun startIfNeeded(context: Context)") &&
            // The pattern is `val client = OkHttpClient()` followed
            // by `val notesTarget = GoogleDriveBackupTarget(client = client, ...)`,
            // both inside the startIfNeeded function. The fix flips
            // the assertion: the client should be a process-singleton
            // (e.g. injected via a `Container` or held on the
            // companion).
            source.contains("val client = OkHttpClient()")
        // The companion `appScope` is process-singleton (a top-level
        // val in a companion object), so the OkHttpClient created
        // here outlives the activity and the process restart path
        // is the only way to release its Dispatcher.
        val isAppScopeSingleton = source.contains("private val appScope = CoroutineScope(SupervisorJob()")
        assertTrue(
            "BackupScheduler.startIfNeeded creates `val client = OkHttpClient()` and never " +
                "calls `client.dispatcher.executorService.shutdown()`. The on-write trigger's " +
                "appScope is a process-singleton (`private val appScope = CoroutineScope(...)`) " +
                "so the client outlives any activity. createsClientInStartIfNeeded=" +
                "$createsClientInStartIfNeeded isAppScopeSingleton=$isAppScopeSingleton. " +
                "The v0.25.7+ WP-2 fix closed the same leak in BackupRetryWorker.doWork but " +
                "missed the on-write-trigger path.",
            createsClientInStartIfNeeded && isAppScopeSingleton,
        )
    }

    /**
     * v2 Finding #4: the startup-rehydrate in `HomeActivity.onCreate`
     * runs on `lifecycleScope.launch { ... }`. If the activity is
     * destroyed (config change, system back, user leaves the
     * launcher) before the `pendingBackups.first()` read returns,
     * the launch is cancelled and the worker is not enqueued. The
     * pending queue is still in DataStore, so the next onCreate
     * (on next cold start) will rehydrate — but a user who briefly
     * opens the app and immediately backgrounds it before the
     * DataStore read lands loses the rehydration for the rest of
     * the session.
     *
     * The fix is to use an application-scoped coroutine, the same
     * `appScope` the scheduler itself uses, or to do the read
     * synchronously (DataStore is a hot Flow, the first read is
     * fast on warm cache). The current shape is `lifecycleScope.launch`
     * inside the activity.
     */
    @Test
    fun `HomeActivity startup-rehydrate uses lifecycleScope and can be cancelled on activity death`() {
        val source = readSource("HomeActivity.kt")
        assertNotNull(source)
        val usesLifecycleScopeForRehydrate = source!!.contains("lifecycleScope.launch") &&
            source.contains("BackupRetryWorker.enqueueIfNeeded(applicationContext)") &&
            source.contains("pendingBackups.first()")
        val isBackupSchedulerCompanionAppScope = readSource("backup/BackupScheduler.kt")
            ?.contains("private val appScope = CoroutineScope(SupervisorJob()") == true
        assertTrue(
            "HomeActivity.onCreate's rehydrate is on `lifecycleScope.launch { ... }`. If the " +
                "activity is destroyed (config change, system back) before `pendingBackups.first()` " +
                "returns, the launch is cancelled and the worker is not enqueued. The fix is " +
                "to use the BackupScheduler companion's process-singleton `appScope` " +
                "(isBackupSchedulerCompanionAppScope=$isBackupSchedulerCompanionAppScope) so " +
                "the rehydrate outlives the activity. The next onCreate will re-cover, but a " +
                "user who briefly opens and immediately backgrounds the app loses the " +
                "rehydration for the rest of the session. " +
                "usesLifecycleScopeForRehydrate=$usesLifecycleScopeForRehydrate",
            usesLifecycleScopeForRehydrate && isBackupSchedulerCompanionAppScope,
        )
    }

    /**
     * v2 Finding #5: the v0.25.7+ WP-2 fix added `.catch { e -> ...; throw e }`
     * to both on-write-trigger collectors in `BackupScheduler.start`.
     * The re-throw is intentional (per the KDoc: "a corrupt DataStore
     * is not going to recover on its own") but the side-effect is
     * that the collector is dead until the process restarts. The
     * `appScope` is a process-singleton, so the death is permanent
     * for the session.
     *
     * The bug pattern: the catch block calls `throw e`, which
     * propagates the exception to the parent coroutine and kills
     * the launch. The fix flips the assertion: a future change
     * should re-subscribe (e.g. via `retryWhen` or a supervisor
     * that re-collects on failure), or the user should at least
     * see an error in the WorkManager log.
     */
    @Test
    fun `BackupScheduler start collectors re-throw on Flow exception and are dead until process restart`() {
        val source = readSource("backup/BackupScheduler.kt")
        assertNotNull(source)
        val notesCatchReThrows = source!!.contains(".catch { e ->") &&
            // The KDoc on the .catch block is the only place the
            // pattern is described; the re-throw itself is the
            // `throw e` line inside the lambda.
            source.contains("throw e") &&
            source.contains("notes flow failed; collector is dead")
        val lettersCatchReThrows = source.contains("letters flow failed; collector is dead")
        val isAppScopeProcessSingleton = source.contains(
            "private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)",
        )
        assertTrue(
            "BackupScheduler.start notes + letters collectors re-throw on Flow exception " +
                "(notesCatchReThrows=$notesCatchReThrows lettersCatchReThrows=" +
                "$lettersCatchReThrows). The re-throw kills the launch, and the appScope is a " +
                "process-singleton (isAppScopeProcessSingleton=$isAppScopeProcessSingleton), " +
                "so the collector is dead until the process restarts. v1 #7 documented the " +
                "silent-death path; v0.25.7+ WP-2 made the death loud (logged) but the death " +
                "is still permanent. The fix flips: either re-subscribe (retryWhen / " +
                "supervisor that re-collects) or surface the failure to the user.",
            notesCatchReThrows && lettersCatchReThrows && isAppScopeProcessSingleton,
        )
    }

    /**
     * v2 Finding #6: `OpenLoop.phase` has a dead-code branch.
     * The `if (postponed) return LoopPhase.POSTPONED` at line 124
     * returns early when the user has a future postponed-at, but
     * the `when` block at line 135-141 also has `postponed -> LoopPhase.POSTPONED`.
     * The `when` branch is reachable only if the `if (postponed) return`
     * is removed, which it can't be (the early return guarantees the
     * when is skipped). The dead branch is a code-smell signal that
     * the function was refactored twice without the redundant
     * `if` being removed.
     *
     * The fix flips the assertion: the redundant `if` is removed,
     * leaving the `when` branch as the single source of truth.
     */
    @Test
    fun `OpenLoop phase has a redundant early-return for POSTPONED alongside a when branch that also handles it`() {
        val source = readSource("friction/OpenLoop.kt")
        assertNotNull(source)
        val hasEarlyReturn = source!!.contains("if (postponed) return LoopPhase.POSTPONED")
        val hasWhenBranch = source.contains("postponed -> LoopPhase.POSTPONED")
        assertTrue(
            "OpenLoop.phase has both `if (postponed) return LoopPhase.POSTPONED` (line 124) " +
                "AND `postponed -> LoopPhase.POSTPONED` inside the `when` block (line 136). " +
                "The `if`-return is unreachable once the `when` is reached: any state that " +
                "passes the `if` is caught by the `when` branch with the same answer. " +
                "hasEarlyReturn=$hasEarlyReturn hasWhenBranch=$hasWhenBranch. Not a functional " +
                "bug, but a refactor smell — a future maintainer who changes the `when` " +
                "may forget the `if` and silently drop the POSTPONED return.",
            hasEarlyReturn && hasWhenBranch,
        )
    }

    private fun readSource(relative: String): String? = runCatching {
        val candidates = listOf(
            "app/src/main/java/org/mindanchor/$relative",
            "../app/src/main/java/org/mindanchor/$relative",
        )
        candidates.firstNotNullOfOrNull { path ->
            val file = java.io.File(path)
            if (file.exists()) file.readText() else null
        }
    }.getOrNull()
}
