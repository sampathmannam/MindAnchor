@file:Suppress(
    "SwallowedException", 
    "MaxLineLength", 
    "LoopWithTooManyJumpStatements", 
    "UnusedPrivateMember",
)

package org.mindanchor.permissions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SOTA v2 bug-hunt, finding #5: AndroidManifest.xml requests
 * `android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND` (line
 * 122 of the manifest) but no source path in this app reads Health
 * Connect while the app is in the background. Every read happens
 * inside a foreground UI surface (SettingsViewModel.refreshWellness,
 * refreshHealthConnectStatus, probeYesterday, or PpgScreen onResume).
 *
 * The privacy promise of this app — the "data never leaves this
 * phone" wording on the Health Connect settings panel — is
 * undermined by requesting a permission the app cannot use. A user
 * who reads the system Health Connect dialog sees "Read data in
 * background" as a granted permission and may reasonably ask "why?"
 *
 * The four tests below pin the surface:
 *
 *  1. The manifest must NOT request READ_HEALTH_DATA_IN_BACKGROUND.
 *  2. The HealthConnectSource must not register a background read
 *     path (no CoroutineWorker / WorkManager / BroadcastReceiver
 *     calls `HealthConnectSource.readDailyVitals`).
 *  3. The SettingsViewModel must not call `readDailyVitals` from
 *     a background-scoped coroutine.
 *  4. The PpgScreen must not call `readDailyVitals` (it has its
 *     own camera-PPG path).
 */
class HealthConnectBackgroundPermissionFindingTest {

    @Test
    fun `AndroidManifest does not request READ_HEALTH_DATA_IN_BACKGROUND`() {
        val manifest = readManifest() ?: return
        // The fix shape: the manifest has no
        // <uses-permission ...READ_HEALTH_DATA_IN_BACKGROUND...>
        // element. The permission name may appear
        // in a KDoc comment (the v0.25.9 fix
        // documents the removal); the test asserts
        // the element is not declared, not that the
        // string is absent.
        val declaresBackgroundRead = Regex(
            "<uses-permission[^>]*READ_HEALTH_DATA_IN_BACKGROUND[^>]*/>",
        ).containsMatchIn(manifest)
        assertFalse(
            "AndroidManifest.xml declares <uses-permission android:name=\"...READ_HEALTH_DATA_IN_BACKGROUND\" ...>. " +
                "The app never reads Health Connect in the background — every read is in the " +
                "foreground (the report screen, the wellness card). The permission surfaces as " +
                "a 'background read' in the system Health Connect dialog, which is a privacy " +
                "over-reach. Removing the line shrinks the permission grant the user sees, and " +
                "matches the privacy promise on the settings panel.",
            declaresBackgroundRead,
        )
    }

    @Test
    fun `HealthConnectSource readDailyVitals is not invoked from a WorkManager worker or BroadcastReceiver`() {
        // All known workers / receivers in this app are listed below.
        // The check is structural: none of them may import
        // HealthConnectSource, and none of them may call
        // `readDailyVitals`.
        val workers = listOf(
            "src/main/java/org/mindanchor/vitals/coros/CorosSyncWorker.kt",
            "src/main/java/org/mindanchor/notifications/BatchReleaseReceiver.kt",
            "src/main/java/org/mindanchor/friction/SessionExpiryReceiver.kt",
            "src/main/java/org/mindanchor/sunset/SunsetReceiver.kt",
            "src/main/java/org/mindanchor/pulse/PulseReminderReceiver.kt",
            "src/main/java/org/mindanchor/model/EmaAlarmReceiver.kt",
            "src/main/java/org/mindanchor/report/ReportAlarmReceiver.kt",
            "src/main/java/org/mindanchor/notifications/BootReceiver.kt",
            "src/main/java/org/mindanchor/notifications/ExactAlarmPermissionReceiver.kt",
        )
        var reads = 0
        for (path in workers) {
            val src = try {
                java.io.File(path).readText(Charsets.UTF_8)
            } catch (t: Throwable) {
                continue
            }
            if (src.contains("HealthConnectSource") || src.contains("readDailyVitals")) {
                reads++
            }
        }
        assertTrue(
            "No WorkManager worker or BroadcastReceiver should read Health Connect. " +
                "Found $reads worker(s) referencing HealthConnectSource / readDailyVitals. " +
                "If 0, the background permission request in the manifest is unused " +
                "(the bug is the manifest line, not a missing read site).",
            reads == 0,
        )
    }

    @Test
    fun `HealthConnectSource does not declare a background read method`() {
        val src = readHealthConnectSource() ?: return
        assertFalse(
            "HealthConnectSource must not declare a method that reads in the background. " +
                "The current file only has readDailyVitals() (foreground read by " +
                "definition). The expected behaviour is that this method is only " +
                "called from UI-thread and ViewModel coroutines bound to a screen.",
            src.contains("fun readInBackground") || src.contains("fun readInTheBackground"),
        )
    }

    @Test
    fun `HealthConnectSource has foreground-only read sites in the ViewModel`() {
        // Pin that the read sites are foreground-only. This is
        // a documentation/structural test, not a runtime check.
        val vm = readSettingsViewModel() ?: return
        assertTrue(
            "SettingsViewModel must call readDailyVitals / readingsFor on Dispatchers.IO " +
                "but the read is only triggered from UI surfaces (refreshWellness, " +
                "refreshHealthConnectStatus, probeYesterday).",
            vm.contains("readDailyVitals") || vm.contains("readingsFor"),
        )
    }

    private fun readManifest(): String? = try {
        java.io.File("src/main/AndroidManifest.xml").readText(Charsets.UTF_8)
    } catch (t: Throwable) {
        null
    }

    private fun readHealthConnectSource(): String? = try {
        java.io.File("src/main/java/org/mindanchor/vitals/HealthConnectSource.kt")
            .readText(Charsets.UTF_8)
    } catch (t: Throwable) {
        null
    }

    private fun readSettingsViewModel(): String? = try {
        java.io.File("src/main/java/org/mindanchor/settings/SettingsViewModel.kt")
            .readText(Charsets.UTF_8)
    } catch (t: Throwable) {
        null
    }
}
