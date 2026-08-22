package org.mindanchor.errors

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B2 (SOTA v2 bug-hunt, agent #5): GoingLightVpnService.start() calls
 * runBlocking on the main thread. A slow DataStore read or a corrupt
 * Keystore blocks the main thread until the system shows an ANR.
 *
 * File-shape pin: the fix PR replaces start() with a suspend fun and
 * moves the VpnService Builder.establish() to withContext(Dispatchers.Main).
 * The asserts below are the regression guard.
 */
class VpnServiceStartIsNotBlockingFindingTest {

    @Test
    fun `GoingLightVpnService start is a suspend fun (regression guard for B2)`() {
        val source = java.io.File(
            "src/main/java/org/mindanchor/goinglight/GoingLightVpnService.kt",
        ).readText()
        // The fix shape: `suspend fun start(): Boolean`. The pre-fix
        // shape is `fun start(): Boolean`. The literal `suspend fun start`
        // is the regression guard.
        assertTrue(
            "GoingLightVpnService.start must be a suspend fun — the pre-fix " +
                "shape was `fun start(): Boolean` and called runBlocking on the main thread.",
            source.contains("suspend fun start"),
        )
    }

    @Test
    fun `GoingLightVpnService does not call runBlocking (regression guard for B2)`() {
        val source = java.io.File(
            "src/main/java/org/mindanchor/goinglight/GoingLightVpnService.kt",
        ).readText()
        // After the fix the import can stay (it is used by tests) but the
        // call site must not exist. A direct `runBlocking {` call inside
        // the VpnService is the regression shape.
        assertFalse(
            "GoingLightVpnService.kt must not call runBlocking on the main thread " +
                "(the v0.25.5+ project-wide move away from blocking IO on the main dispatcher).",
            source.contains("runBlocking {"),
        )
    }
}
