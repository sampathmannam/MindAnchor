package org.mindanchor.backup

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bug-hunt finding tests for the worker resource lifecycle and the
 * drain concurrency model. v0.25.7+ WP-2: the test bodies were
 * rewritten to assert the *fixed* shape (close-the-client,
 * re-read-the-queue, rehydrate-on-startup) instead of the original
 * "the bug is present" assertion. The bug-hunt agent's first pass
 * pinned the bug; v0.25.7+WP-2's fix flipped the assertions.
 */
class WorkerResourceLeakFindingTest {

    @Test
    fun `BackupRetryWorker doWork closes its OkHttpClient in finally`() {
        val source = readSource("BackupRetryWorker.kt")
        assertNotNull(source)
        // v0.25.7+ WP-2 fix: the worker constructs a fresh
        // OkHttpClient inside doWork, but the finally block
        // calls client.dispatcher.executorService.shutdown() to
        // release the thread pool. The leak is closed even if
        // a future refactor removes the `val client =` line.
        val createsFresh = source!!.contains("val client = OkHttpClient()")
        val shutsDownDispatcher = source.contains(
            "client.dispatcher.executorService.shutdown()",
        )
        val hasFinally = source.contains("try {") &&
            source.contains("} finally {") &&
            source.contains("client.dispatcher.executorService")
        assertTrue(
            "BackupRetryWorker.doWork must close the OkHttp client's " +
                "Dispatcher ExecutorService in a finally block. " +
                "createsFresh=$createsFresh shutsDownDispatcher=" +
                "$shutsDownDispatcher hasFinally=$hasFinally. Each run " +
                "without the close leaks one OkHttp Dispatcher " +
                "ExecutorService until the process dies.",
            createsFresh && shutsDownDispatcher && hasFinally,
        )
    }

    @Test
    fun `BackupRetryWorker drain loop re-reads the queue inside a while loop`() {
        val source = readSource("BackupRetryWorker.kt")
        assertNotNull(source)
        // v0.25.7+ WP-2 fix: the drain loop is a `while (true)`
        // that re-reads the queue at the top of each iteration.
        // A concurrent enqueue from the on-write trigger
        // mid-drain is picked up on the next iteration. The
        // old snapshot-once pattern is gone.
        val reReadsInLoop = source!!.contains("while (true)") &&
            source.contains("backupPrefs.pendingBackups.first()") &&
            source.contains("break")
        assertTrue(
            "BackupRetryWorker.doWork must re-read the pendingBackups " +
                "queue inside a while loop, not iterate a snapshot. " +
                "reReadsInLoop=$reReadsInLoop. The old shape was a " +
                "single read at the start + a for-loop over the " +
                "snapshot; a concurrent enqueue was invisible to the " +
                "loop and the new entry sat until the user wrote " +
                "something else.",
            reReadsInLoop,
        )
    }

    @Test
    fun `HomeActivity onCreate rehydrates the pending queue on cold start`() {
        // v0.25.7+ WP-2 fix: the enqueue path in
        // BackupScheduler.encryptAndAppend is two separate
        // ops (DataStore edit then WorkManager enqueue).
        // A process death between them would leave the
        // entry stranded with no fresh worker scheduled.
        // The recovery path is HomeActivity.onCreate:
        // if the queue is non-empty at cold start, call
        // BackupRetryWorker.enqueueIfNeeded before the user
        // has to write anything.
        val scheduler = readSource("BackupScheduler.kt").orEmpty()
        val homeActivity = readSource("HomeActivity.kt").orEmpty()
        val hasOnCreateRehydration = homeActivity.contains(
            "BackupRetryWorker.enqueueIfNeeded(applicationContext)",
        ) && homeActivity.contains("pendingBackups.first()") &&
            homeActivity.contains("if (pending.isNotEmpty())")
        // Sanity check: the enqueue path is still two
        // separate ops in the scheduler (the fix is a
        // recovery path, not atomicity).
        val enqueuePathIsTwoOps = scheduler.contains(
            "backupPrefs.enqueuePending(",
        ) && scheduler.contains("BackupRetryWorker.enqueueIfNeeded(context)")
        assertTrue(
            "HomeActivity.onCreate must rehydrate the pending " +
                "queue on cold start: if pending.isNotEmpty(), call " +
                "BackupRetryWorker.enqueueIfNeeded. " +
                "hasOnCreateRehydration=$hasOnCreateRehydration " +
                "enqueuePathIsTwoOps=$enqueuePathIsTwoOps. The enqueue " +
                "path is two separate ops (DataStore edit then " +
                "WorkManager enqueue); a process death between them " +
                "would leave the entry stranded. The recovery path " +
                "on HomeActivity.onCreate closes the window.",
            hasOnCreateRehydration && enqueuePathIsTwoOps,
        )
    }

    private fun readSource(filename: String): String? = runCatching {
        val candidates = listOf(
            "app/src/main/java/org/mindanchor/backup/$filename",
            "app/src/main/java/org/mindanchor/$filename",
            "../app/src/main/java/org/mindanchor/backup/$filename",
            "../app/src/main/java/org/mindanchor/$filename",
        )
        candidates.firstNotNullOfOrNull { path ->
            val file = java.io.File(path)
            if (file.exists()) file.readText() else null
        }
    }.getOrNull()
}
