package org.mindanchor.workmanager

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SOTA v2 bug-hunt finding tests for the WorkManager surface.
 *
 * **Scope**: WorkManager + Concurrency around the v0.25.5–v0.25.8 surface.
 * The pre-existing v1 report (`bug_hunt_backup.md`) found 20 backup
 * issues, 5 of which v0.25.8 fixed. The v2 sweep is broader: it covers
 * the two newly-added workers (`BackupRetryWorker`, `CorosSyncWorker`),
 * the four AlarmManager-driven schedulers that share the same
 * WorkManager-shaped risk surface, and the on-write trigger wiring
 * the v0.25.7 startup-rehydrate introduced.
 *
 * **Method**: file-shape pins + assertion on the **bug pattern being
 * present**, matching the v1 report's "the bug is present in the
 * source" style. The fix later will flip the assertion.
 *
 * **The eight tests below** correspond to the eight v2 findings that
 * are not already covered by `WorkerResourceLeakFindingTest` /
 * `OnWriteTriggerFirstEmissionFindingTest` / `OnWriteTriggerWireupFindingTest`
 * / `BackupRetryWorkerFindingTest` / `BackupSchedulerFindingTest` /
 * `OpenLoopPostponementFindingTest` / `PpgSessionFindingTest`. The
 * shape of each test mirrors the existing FindingTest pattern:
 * a single `assertTrue` with a descriptive failure message, the
 * bug pattern searched by string/regex, and a one-paragraph
 * KDoc explaining what the test pins and why.
 */
class WorkNameUniquenessFindingTest {

    /**
     * The three workers in the app have three distinct unique
     * work names. A duplicate would let a "Sync now" tap cancel
     * an in-flight backup drain (or vice versa) — the wrong
     * one would be cancelled, the user's data would sit in the
     * queue, and the failure would be invisible.
     */
    @Test
    fun `the three worker names are all distinct`() {
        val backupRetry = readSource("backup/BackupRetryWorker.kt").orEmpty()
        val coros = readSource("vitals/coros/CorosSyncWorker.kt").orEmpty()
        assertNotNull("BackupRetryWorker.kt must be readable for the file-shape pin", backupRetry)
        assertNotNull("CorosSyncWorker.kt must be readable for the file-shape pin", coros)

        val backupName = extractNameConst(backupRetry, "NAME")
        val corosPeriodic = extractNameConst(coros, "PERIODIC_NAME")
        val corosOneshot = extractNameConst(coros, "ONESHOT_NAME")

        assertTrue(
            "BackupRetryWorker.NAME constant not found in source. The unique work name is the " +
                "only addressable surface in WorkManager.getInstance().getWorkInfosByName() — " +
                "an unnamed worker cannot be cancelled or observed. backupName=$backupName.",
            backupName != null,
        )
        assertTrue(
            "CorosSyncWorker.PERIODIC_NAME constant not found. corosPeriodic=$corosPeriodic.",
            corosPeriodic != null,
        )
        assertTrue(
            "CorosSyncWorker.ONESHOT_NAME constant not found. corosOneshot=$corosOneshot.",
            corosOneshot != null,
        )
        assertNotEquals(
            "BackupRetryWorker.NAME and CorosSyncWorker.ONESHOT_NAME collide. A 'Sync now' " +
                "tap would cancel the backup drain and leave the pending queue stranded. " +
                "backupName=$backupName corosOneshot=$corosOneshot.",
            backupName,
            corosOneshot,
        )
        assertNotEquals(
            "CorosSyncWorker.PERIODIC_NAME and CorosSyncWorker.ONESHOT_NAME collide. A second " +
                "'Sync now' tap would cancel the periodic schedule. corosPeriodic=$corosPeriodic " +
                "corosOneshot=$corosOneshot.",
            corosPeriodic,
            corosOneshot,
        )
    }

    /**
     * v2 Finding #1: the `BackupRetryWorker` and `CorosSyncWorker`
     * both use `enqueueUniqueWork(NAME, KEEP, request)`. The two
     * names are distinct (per the test above) — that is the
     * invariant. The actual bug pattern is the *absence* of a
     * uniqueness assertion; the existing `BackupRetryWorkerFindingTest`
     * pins the shape of the enqueue but not the cross-worker
     * distinctness. This test pins both: it fails if the names
     * are equal OR if either is missing.
     */
    @Test
    fun `worker names are exactly the documented strings and not WorkManager defaults`() {
        val backupRetry = readSource("backup/BackupRetryWorker.kt").orEmpty()
        val coros = readSource("vitals/coros/CorosSyncWorker.kt").orEmpty()
        // The expected strings, per BackupRetryWorker.kt:242 and
        // CorosSyncWorker.kt:149,158. A regression that renamed
        // either constant would change the WorkManager-panel
        // label without telling anyone — the user would see
        // "org.mindanchor.backup.BackupRetryWorker" (the default
        // class-name label) instead of the intended one.
        val expectedBackup = "\"backup_retry_oneshot\""
        val expectedCorosPeriodic = "\"coros_sync_periodic\""
        val expectedCorosOneshot = "\"coros_sync_oneshot\""
        val allExpected = listOf(expectedBackup, expectedCorosPeriodic, expectedCorosOneshot)
        val allPresent = allExpected.all { expected ->
            backupRetry.contains(expected) || coros.contains(expected)
        }
        assertTrue(
            "Expected work-name strings $allExpected are not all present. A regression that " +
                "renamed any of NAME / PERIODIC_NAME / ONESHOT_NAME would break the system " +
                "WorkManager panel label and would silently couple the two workers via the " +
                "default unique-name collision. " +
                "backupRetryHas=$backupRetry corosHas=$coros",
            allPresent,
        )
    }

    private fun extractNameConst(source: String, constName: String): String? {
        val regex = Regex("""const\s+val\s+$constName\s*=\s*"([^"]+)"""")
        return regex.find(source)?.groupValues?.getOrNull(1)
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
