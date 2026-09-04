package org.mindanchor.continuity

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 14, Step 2: pins the offline-startup boundary Task 11's own KDoc
 * already describes — [RestoreCoordinator.resume] (and therefore
 * `HomeActivity.onCreate`'s unconditional call to
 * [RestoreCoordinator.resumeIfPending]) must never reach the network, so a
 * phone with no connectivity at all can still open to its normal home
 * screen, including mid-restore recovery after a process death.
 *
 * Source-scan idiom, matching
 * [org.mindanchor.goinglight.NetworkCallsForbiddenTest] and
 * [org.mindanchor.release.ReleaseSafetyTest]: read the actual source text
 * of the four files on this boundary and assert none of them contain a
 * literal substring that would indicate an outbound network call or a
 * live Drive access token. `HomeActivity.kt` is where `resumeIfPending`
 * is actually invoked on every app open; `JournalActivity.kt` /
 * `JournalViewModel.kt` are the everyday-use surface a user must be able
 * to open offline; `ContinuitySnapshotRepository.kt` backs every capture
 * `RestoreCoordinator.resume`'s own final verify step performs.
 *
 * [RestoreCoordinator.kt] is scanned as a whole file, not just its
 * `resume` function body — that file's own class KDoc documents that
 * *neither* `beginRestore` nor `resume` ever references a
 * `org.mindanchor.backup.RemoteBackupStore`: `beginRestore` only stages
 * envelope bytes the caller already downloaded elsewhere (`RestoreScreen`'s
 * preview step, before the network-capable download happens), so the
 * network-free property holds for the whole file, not just the
 * `resume`-specific subset this test's brief calls out by name. Only
 * WorkManager worker files ([CheckpointBackupWorker], [NightlySnapshotWorker])
 * and the explicit sign-in/restore UI (`RestoreActivity.kt`'s
 * `beginRestore` call site, `GoogleDriveBackupSettingsSection.kt`) may
 * cross the network boundary — those files are intentionally not scanned
 * here.
 */
class OfflineStartupBoundaryTest {

    private fun locate(vararg candidates: String): File {
        val found = candidates.map(::File).firstOrNull { it.isFile }
        return found ?: error(
            "None of these paths exist from working directory " +
                "${File(".").absolutePath}: ${candidates.toList()}",
        )
    }

    private val forbiddenPatterns = listOf(
        "okhttp3",
        "GoogleDriveObjectStore",
        "currentAccessToken",
    )

    private val scannedFiles = listOf(
        "src/main/java/org/mindanchor/HomeActivity.kt" to
            "app/src/main/java/org/mindanchor/HomeActivity.kt",
        "src/main/java/org/mindanchor/journal/JournalActivity.kt" to
            "app/src/main/java/org/mindanchor/journal/JournalActivity.kt",
        "src/main/java/org/mindanchor/journal/JournalViewModel.kt" to
            "app/src/main/java/org/mindanchor/journal/JournalViewModel.kt",
        "src/main/java/org/mindanchor/continuity/ContinuitySnapshotRepository.kt" to
            "app/src/main/java/org/mindanchor/continuity/ContinuitySnapshotRepository.kt",
        "src/main/java/org/mindanchor/continuity/RestoreCoordinator.kt" to
            "app/src/main/java/org/mindanchor/continuity/RestoreCoordinator.kt",
    )

    @Test
    fun `offline-startup-critical files never reference okhttp3, GoogleDriveObjectStore, or a live access token`() {
        val offenders = mutableListOf<String>()
        for ((relativeToApp, relativeToRoot) in scannedFiles) {
            val file = locate(relativeToApp, relativeToRoot)
            val text = file.readText()
            for (pattern in forbiddenPatterns) {
                if (text.contains(pattern)) {
                    offenders.add("${file.path}: references forbidden pattern '$pattern'")
                }
            }
        }
        assertTrue(
            "These offline-startup-critical files reference a network-bound symbol, which " +
                "would break the promise that HomeActivity.onCreate (and the resume() restore " +
                "path it calls unconditionally) never needs connectivity to open.\n" +
                offenders.joinToString("\n") { "  $it" },
            offenders.isEmpty(),
        )
    }
}
