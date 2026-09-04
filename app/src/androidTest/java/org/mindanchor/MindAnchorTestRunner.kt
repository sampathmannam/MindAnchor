package org.mindanchor

import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper

/**
 * Puts WorkManager into test mode for the whole instrumented suite, once,
 * before any test runs.
 *
 * [WorkManagerTestInitHelper] replaces WorkManager's already-auto-initialized
 * (via androidx.startup, in the app-under-test's own manifest) real delegate
 * with a test one backed by a [SynchronousExecutor], so
 * [org.mindanchor.continuity.CheckpointBackupWorker]/
 * [org.mindanchor.continuity.NightlySnapshotWorker] — enqueued for real by
 * any test that writes through NotesPrefs/LetterStore/JournalRepository —
 * run inline on the calling thread instead of a live background thread that
 * can still be executing after its originating test method returns. Before
 * this ran, one such leftover worker could write to the real, file-backed,
 * process-wide ContinuityPrefs/RecoveryKeyStore state a *later*,
 * unrelated test (ContinuitySettingsTest) had just reset in its own
 * @Before, corrupting it mid-run — this suite's flaky
 * ContinuitySettingsTest failures, reproducible only under the full suite
 * and never for that file run in isolation.
 */
@Suppress("unused")
class MindAnchorTestRunner : AndroidJUnitRunner() {

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(targetContext, config)
    }
}
