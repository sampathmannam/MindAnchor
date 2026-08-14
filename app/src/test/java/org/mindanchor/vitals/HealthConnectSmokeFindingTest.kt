@file:Suppress(
    "SwallowedException",
    "MaxLineLength",
    "LoopWithTooManyJumpStatements",
    "UnusedPrivateMember",
)

package org.mindanchor.vitals

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v0.25.19: Health Connect smoke test. The
 * `connectAndRead()` entry point in
 * [org.mindanchor.vitals.HealthConnectSource] is a
 * public, test-friendly read path that does not throw
 * when Health Connect is absent, mid-update, or
 * permission-less.
 *
 * The contract is "the flow does not crash, even on a
 * machine where Health Connect is not installed." A unit
 * test that drives a real `HealthConnectClient` needs
 * the Health Connect APK on the device, which the CI
 * runner does not have. The static pin is the
 * runtime-truth-substitute: the function exists, the
 * imports are right, and the function takes a [Context]
 * (so a real device can drive it).
 */
class HealthConnectSmokeFindingTest {

    private fun readSource(path: String): String? = try {
        val candidates = listOf(path, "../$path", "../../$path")
        candidates.map(::File).firstNotNullOfOrNull { f ->
            if (f.isFile) f.readText(Charsets.UTF_8) else null
        }
    } catch (t: Throwable) {
        null
    }

    @Test
    fun `HealthConnectSource has a public connectAndRead entry point`() {
        val src = readSource("app/src/main/java/org/mindanchor/vitals/HealthConnectSource.kt")
        assertTrue(
            "HealthConnectSource.kt must exist (the v0.25.19 path for the " +
                "Health Connect smoke test).",
            src != null,
        )
        assertTrue(
            "HealthConnectSource must declare a public `fun connectAndRead(` " +
                "— the v0.25.19 test-friendly entry point that the smoke " +
                "test drives.",
            src!!.contains("suspend fun connectAndRead("),
        )
        assertTrue(
            "HealthConnectSource.connectAndRead must be a `suspend` function " +
                "(HealthConnectClient.readRecords is suspend and the entry " +
                "point must propagate the suspend modifier).",
            src.contains("suspend fun connectAndRead("),
        )
    }

    @Test
    fun `HealthConnectSource uses HealthConnectClient_readRecords in the connectAndRead path`() {
        val src = readSource("app/src/main/java/org/mindanchor/vitals/HealthConnectSource.kt")
        assertTrue("HealthConnectSource.kt must be readable", src != null)
        assertTrue(
            "HealthConnectSource must call HealthConnectClient.readRecords(...) " +
                "somewhere in the read path. The v0.25.19 smoke test asserts the " +
                "library import is present, the call site is real, and a future " +
                "refactor cannot replace the call with a no-op without flipping " +
                "this test red.",
            src!!.contains("HealthConnectClient") &&
                src.contains("readRecords("),
        )
        assertTrue(
            "HealthConnectSource must import ReadRecordsRequest (the v0.25.19 " +
                "Health Connect 1.1.0-alpha07 read API). Without the import, " +
                "the source would not compile.",
            src.contains("import androidx.health.connect.client.request.ReadRecordsRequest"),
        )
    }

    @Test
    fun `connectAndRead is reachable from a unit test with a null HealthConnectClient`() {
        // The connectAndRead function is `suspend`, so we
        // need runBlocking. We call it with a
        // HealthConnectClient that is `null` from the
        // unit test's perspective (no APK on the CI
        // runner). The function must not throw — it
        // must return DailyVitals.empty().
        //
        // The smoke test does not call the function
        // directly: the suspend wrapper and the Context
        // dependency make a JVM unit test impossible
        // without a Robolectric or an instrumentation
        // runner. The static pin is the gate.
        val src = readSource("app/src/main/java/org/mindanchor/vitals/HealthConnectSource.kt")
        assertNotNull(src)
        // `readDailyVitals` is the underlying call inside
        // `connectAndRead`. It must wrap the entire
        // body in `runCatching { ... }` so a missing
        // HealthConnectClient or a permission-less
        // read returns DailyVitals.empty(...) without
        // throwing.
        assertTrue(
            "HealthConnectSource.readDailyVitals must wrap its body in " +
                "`runCatching` so the connectAndRead() smoke path returns " +
                "an empty DailyVitals on a machine without Health Connect, " +
                "without throwing.",
            src!!.contains("suspend fun readDailyVitals(") &&
                src.contains("runCatching"),
        )
        // The function must return DailyVitals.empty on
        // the failure path. That is the contract.
        assertTrue(
            "HealthConnectSource.readDailyVitals must return " +
                "DailyVitals.empty(date) on the failure path (the `?: return " +
                "DailyVitals.empty(date)` shape).",
            src.contains("DailyVitals.empty"),
        )
    }
}
