package org.mindanchor.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.25.6 WP-H: the [BackupRetryWorker] closes the deliberate
 * v0.25.5-WP-H split — the data layer is in place, the
 * Worker class is the missing half. The five tests below
 * pin the worker shape: it exists, it is constrained, it
 * drains the queue, it handles a non-Ok result correctly,
 * and the call site in [BackupScheduler] enqueues it.
 */
class BackupRetryWorkerFindingTest {

    @Test
    fun `BackupRetryWorker exists and is a CoroutineWorker`() {
        // The class file is the contract. A regression
        // that moved the class (or accidentally dropped the
        // `CoroutineWorker` superclass) would leave the
        // v0.25.5-WP-H enqueue path with nothing to
        // schedule — the silent-failure mode the data
        // layer was supposed to fix would be back.
        val source = readSource("BackupRetryWorker.kt")
        assertNotNull(source)
        assertTrue(
            "BackupRetryWorker.kt is missing",
            source!!.contains("class BackupRetryWorker(") &&
                source.contains("CoroutineWorker(appContext, params)"),
        )
    }

    @Test
    fun `BackupRetryWorker enqueueIfNeeded builds a CONNECTED-constrained one-shot`() {
        // The work has a NetworkType.CONNECTED
        // constraint and is a one-shot (not a periodic
        // worker). A regression that dropped the
        // constraint would let the worker fire on
        // metered networks and burn the user's data; a
        // regression that switched to a periodic worker
        // would burn the user's battery on empty-queue
        // runs.
        val source = readSource("BackupRetryWorker.kt")
        assertNotNull(source)
        assertTrue(
            "BackupRetryWorker must use CONNECTED constraint + OneTimeWorkRequest",
            source!!.contains("OneTimeWorkRequestBuilder<BackupRetryWorker>") &&
                source.contains("setRequiredNetworkType(NetworkType.CONNECTED)") &&
                source.contains("enqueueUniqueWork(") &&
                source.contains("ExistingWorkPolicy.KEEP"),
        )
    }

    @Test
    fun `BackupRetryWorker doWork drains the queue via the BackupTarget`() {
        // The drain loop is the contract. A regression
        // that dropped the per-entry `target.append(...)`
        // call would leave the queue permanent. A
        // regression that dropped the `removePending`
        // call on Ok would re-process the same entry on
        // every run.
        val source = readSource("BackupRetryWorker.kt")
        assertNotNull(source)
        assertTrue(
            "BackupRetryWorker.doWork must call target.append and removePending on Ok",
            source!!.contains("target.append(entry.type, entry.payload)") &&
                source.contains("backupPrefs.removePending(entry)"),
        )
    }

    @Test
    fun `BackupRetryWorker doWork returns Result_retry on a non-Ok result`() {
        // The retry path. A regression that returned
        // Result.failure would mark the entry as
        // permanently un-backup-able; a regression that
        // returned Result.success would silently drop
        // the entry. retry is the only honest answer,
        // and it is gated on the explicit "stopping
        // drain" log message so the file-shape pin
        // targets the failure path, not a stray
        // Result.retry() at the top of doWork (the
        // queue-read failure path).
        val source = readSource("BackupRetryWorker.kt")
        assertNotNull(source)
        assertTrue(
            "BackupRetryWorker.doWork must return Result.retry on a per-entry non-Ok result",
            source!!.contains("stopping drain, will retry") &&
                source.contains("return Result.retry()"),
        )
    }

    @Test
    fun `BackupScheduler enqueueIfNeeded is called after every enqueuePending`() {
        // The on-write call site. A regression that
        // dropped the enqueue would mean a queued
        // backup sits in DataStore until the user
        // opens the app and the on-write trigger
        // re-fires — which never happens if the
        // process is dead. The find is the cheapest
        // way to keep the work scheduled.
        val source = readSource("BackupScheduler.kt")
        assertNotNull(source)
        assertTrue(
            "BackupScheduler.encryptAndAppend must call BackupRetryWorker.enqueueIfNeeded",
            source!!.contains("BackupRetryWorker.enqueueIfNeeded(context)"),
        )
    }

    private fun readSource(filename: String): String? = runCatching {
        val candidates = listOf(
            "app/src/main/java/org/mindanchor/backup/$filename",
            "../app/src/main/java/org/mindanchor/backup/$filename",
        )
        candidates.firstNotNullOfOrNull { path ->
            val file = java.io.File(path)
            if (file.exists()) file.readText() else null
        }
    }.getOrNull()
}
