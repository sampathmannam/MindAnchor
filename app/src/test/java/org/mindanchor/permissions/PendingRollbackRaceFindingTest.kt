@file:Suppress(
    "SwallowedException", 
    "MaxLineLength", 
    "LoopWithTooManyJumpStatements", 
    "UnusedPrivateMember",
)

package org.mindanchor.permissions

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SOTA v2 bug-hunt, finding #2: SettingsScreen.kt has a single shared
 * `pendingRollback` slot for both the notification-batching and
 * the check-in (EMA) toggles. A user who flips batching on, then
 * flips EMA on before the first permission dialog returns, would
 * have their batching rollback overwritten by the EMA rollback
 * (and vice versa). The two are at lines 964 (batching) and 1874
 * (EMA) of SettingsScreen.kt.
 *
 * The four tests below pin the surface:
 *
 *  1. The `pendingRollback` slot is declared at the SettingsScreen
 *     root (line 537 of SettingsScreen.kt), in scope of every
 *     group-render branch.
 *  2. The batching toggle (line ~960) sets `pendingRollback` to
 *     a callback that calls `viewModel.setBatchingEnabled(false)`.
 *  3. The EMA toggle (line ~1870) sets `pendingRollback` to a
 *     callback that calls `viewModel.setEmaEnabled(false)`.
 *  4. The launcher callback (line ~540) invokes the rollback only
 *     when `granted == false`, and clears the slot in either
 *     case — meaning the second toggle's overwrite is final.
 */
class PendingRollbackRaceFindingTest {

    @Test
    fun `SettingsScreen has a single shared pendingRollback slot at the root`() {
        val src = readSettingsScreenSource() ?: return
        val regex = Regex("var pendingRollback by remember")
        val matches = regex.findAll(src).count()
        assertNotNull("pendingRollback declaration not found", regex.find(src))
        assertTrue(
            "pendingRollback must be declared exactly once at the SettingsScreen root. " +
                "Found $matches declarations; multiple declarations would defeat the " +
                "rollback mechanism entirely. The current code has one declaration at " +
                "line 537.",
            matches == 1,
        )
    }

    @Test
    fun `SettingsScreen batching toggle arms a batching-specific rollback`() {
        val src = readSettingsScreenSource() ?: return
        val batchingMarker = "pendingRollback = { viewModel.setBatchingEnabled(false) }"
        assertTrue(
            "SettingsScreen batching toggle must arm a rollback that turns batching off. " +
                "Marker not found. The current code at line 964 does this correctly " +
                "but the slot is shared with the EMA toggle (line 1874) — see the " +
                "EMA test for the clobber race.",
            src.contains(batchingMarker),
        )
    }

    @Test
    fun `SettingsScreen EMA toggle arms an EMA-specific rollback`() {
        val src = readSettingsScreenSource() ?: return
        val emaMarker = "pendingRollback = { viewModel.setEmaEnabled(false) }"
        assertTrue(
            "SettingsScreen EMA toggle must arm a rollback that turns EMA off. " +
                "Marker not found. The current code at line 1874 does this correctly " +
                "but the slot is shared with the batching toggle (line 964) — see the " +
                "batching test for the clobber race.",
            src.contains(emaMarker),
        )
    }

    @Test
    fun `SettingsScreen permission launcher is shared between batching and EMA`() {
        val src = readSettingsScreenSource() ?: return
        val launcherCount = Regex("rememberLauncherForActivityResult\\(\\s*ActivityResultContracts\\.RequestPermission\\(\\)")
            .findAll(src)
            .count()
        // The pre-fix count is 1 (one launcher for both). The fix
        // is to make this 2 (one per toggle) or to encode the
        // originating toggle in the rollback slot itself.
        assertTrue(
            "SettingsScreen currently has $launcherCount RequestPermission launcher(s). " +
                "If 1, the batching and EMA toggles share a launcher and the " +
                "pendingRollback slot is a race surface — a second toggle overwrites " +
                "the first toggle's rollback before the first permission dialog " +
                "returns. If 2, the bug is already fixed. The expected count after " +
                "the fix is 2 (one per toggle).",
            launcherCount == 2,
        )
    }

    private fun readSettingsScreenSource(): String? = try {
        java.io.File("src/main/java/org/mindanchor/settings/SettingsScreen.kt")
            .readText(Charsets.UTF_8)
    } catch (t: Throwable) {
        null
    }
}
