package org.mindanchor

import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import androidx.work.testing.WorkManagerTestInitHelper

/**
 * Puts WorkManager into test mode for the whole instrumented suite, once,
 * before any test runs.
 *
 * [WorkManagerTestInitHelper.initializeTestWorkManager] (no custom
 * [androidx.work.Configuration]) replaces WorkManager's already-auto-
 * initialized real delegate with one backed by its `TestScheduler`: work
 * enqueued with [androidx.work.WorkManager] is recorded but never actually
 * runs unless a test explicitly drives it via
 * [WorkManagerTestInitHelper.getTestDriver] — which nothing in this suite
 * does.
 *
 * This matters because [org.mindanchor.continuity.ContinuityWorkScheduler]
 * .requestCheckpoint is called for real, unmocked, by NotesPrefs/
 * LetterStore/JournalRepository on every write — so almost any test in
 * this suite incidentally enqueues a [org.mindanchor.continuity.CheckpointBackupWorker].
 * Before this ran, WorkManager's default (real) executor let that worker
 * actually execute on a real background thread, sometimes still running
 * after its originating test method returned; since
 * [org.mindanchor.continuity.ContinuityPrefs]/
 * [org.mindanchor.continuity.crypto.RecoveryKeyStore] are real,
 * file-backed, process-wide singletons, that write could land on the
 * exact on-disk state a *later*, unrelated test (ContinuitySettingsTest)
 * had just reset in its own @Before, corrupting it mid-run.
 *
 * An earlier version of this fix used a `SynchronousExecutor` instead,
 * which made the problem worse, not better: it guaranteed every
 * incidental checkpoint enqueue actually completed, synchronously, inline
 * with whatever unrelated test triggered it — turning a probabilistic
 * race into a deterministic one. Leaving work un-driven, as this does,
 * means an incidental enqueue is inert: recorded, but never touches the
 * real ContinuityPrefs/RecoveryKeyStore files at all.
 */
@Suppress("unused")
class MindAnchorTestRunner : AndroidJUnitRunner() {

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        WorkManagerTestInitHelper.initializeTestWorkManager(targetContext)
    }
}
