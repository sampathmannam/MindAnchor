package org.mindanchor.settings

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Finding test for the POST_NOTIFICATIONS permission flow.
 *
 * v0.22.0 shipped with two P2 UX bugs in SettingsScreen.kt:
 *
 *  1. The "Batch notifications" toggle in the Quiet group asks for
 *     POST_NOTIFICATIONS when switched on, but the launcher's
 *     callback was an empty `{}` — a denied permission left the
 *     toggle ON with no notifications actually delivered and no
 *     feedback to the user that something was wrong.
 *
 *  2. The "Ask me how I am" toggle in the Measuring group had the
 *     same shape — same empty callback, same silent denial.
 *
 * The fix introduced a `pendingRollback` state variable that
 * captures a ViewModel setter to invoke if the launcher returns
 * `granted = false`. This test pins that shape so a future refactor
 * that re-introduces the empty `{}` callback is caught at build
 * time, not when a user toggles the switch and is silently stranded.
 *
 * What this test checks:
 *  1. The launcher callback is not the no-op `{}` shape — it must
 *     inspect the `granted` argument and act on a denial.
 *  2. There is a `pendingRollback` state variable that the two
 *     toggles write to before launching and the callback reads from.
 *  3. The batching toggle wires a rollback that calls
 *     `viewModel.setBatchingEnabled(false)`.
 *  4. The EMA toggle wires a rollback that calls
 *     `viewModel.setEmaEnabled(false)`.
 */
class PostNotificationsRollbackTest {

    private fun fileAt(relative: String): File {
        val candidates = listOf(relative, "../$relative", "../../$relative")
        return candidates.map(::File).firstOrNull { it.isFile }
            ?: error("$relative not found from working directory ${File(".").absolutePath}.")
    }

    private val screen: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/settings/SettingsScreen.kt",
        ).readText()

    @Test
    fun `the POST_NOTIFICATIONS launcher is not a no-op`() {
        // The pre-fix shape was `val permissionLauncher = ... { }` — an
        // empty lambda that swallowed the grant result. A denial therefore
        // left the in-app toggle ON with no notifications ever delivered.
        // The fix is a callback that looks at `granted` and rolls back the
        // optimistic update.
        val launcherBlock = Regex(
            """ActivityResultContracts\.RequestPermission\(\),\s*\)\s*\{[^}]*\}""",
        ).find(screen)?.value ?: error(
            "Could not locate the RequestPermission launcher block in SettingsScreen.kt",
        )
        assertTrue(
            "The RequestPermission launcher callback must inspect the " +
                "`granted` argument and act on a denial — the pre-fix " +
                "shape was an empty `{}` that left toggles stuck ON. " +
                "Block: $launcherBlock",
            launcherBlock.contains("granted") &&
                launcherBlock.contains("pendingRollback"),
        )
    }

    @Test
    fun `a pendingRollback state variable is declared at the screen level`() {
        // The callback needs somewhere to read the rollback from — the
        // toggle's onCheckedChange writes it before launching, the
        // callback reads it on return. If the variable is missing, a
        // compiler change that loses track of the rollback will compile
        // but the toggles will be stuck ON again.
        assertTrue(
            "SettingsScreen.kt must declare `var pendingRollback by " +
                "remember { mutableStateOf<(() -> Unit)?>(null) }` so " +
                "the toggle's onCheckedChange can register a rollback " +
                "before the launcher is called.",
            screen.contains("var pendingRollback by remember") &&
                screen.contains("mutableStateOf<(() -> Unit)?>(null)"),
        )
    }

    @Test
    fun `the batching toggle registers a rollback that calls setBatchingEnabled(false)`() {
        // The toggles' onCheckedChange must write a closure into
        // pendingRollback that flips the ViewModel back to false. If
        // this wiring is removed, the launcher callback becomes a no-op
        // and the bug returns.
        val batchingBlock = Regex(
            """toggleable\(value = batchingEnabled[\s\S]*?pendingRollback\s*=\s*\{\s*viewModel\.setBatchingEnabled\(false\)""",
        ).find(screen)?.value ?: error(
            "Could not locate the batchingEnabled toggle's pendingRollback assignment",
        )
        assertTrue(
            "The batching toggle must register a rollback that calls " +
                "viewModel.setBatchingEnabled(false) before launching the " +
                "permission request. " +
                "Block: $batchingBlock",
            batchingBlock.isNotEmpty(),
        )
    }

    @Test
    fun `the EMA toggle registers a rollback that calls setEmaEnabled(false)`() {
        val emaBlock = Regex(
            """toggleable\(value = emaEnabled[\s\S]*?pendingRollback\s*=\s*\{\s*viewModel\.setEmaEnabled\(false\)""",
        ).find(screen)?.value ?: error(
            "Could not locate the emaEnabled toggle's pendingRollback assignment",
        )
        assertTrue(
            "The EMA (Ask-me-how-I-am) toggle must register a rollback " +
                "that calls viewModel.setEmaEnabled(false) before " +
                "launching the permission request. Without it, the same " +
                "silent-stuck-ON bug as batching returns. " +
                "Block: $emaBlock",
            emaBlock.isNotEmpty(),
        )
    }

    @Test
    fun `the launcher callback invokes pendingRollback on a denial and clears it`() {
        // The two halves of the rollback contract: the callback must
        // call the rollback on !granted, and clear the variable on
        // either path so the next toggle tap doesn't accidentally fire
        // a stale rollback from a previous request.
        val launcherBlock = Regex(
            """ActivityResultContracts\.RequestPermission\(\),\s*\)\s*\{[^}]*\}""",
        ).find(screen)?.value ?: error(
            "Could not locate the RequestPermission launcher block",
        )
        assertTrue(
            "The launcher callback must (1) invoke pendingRollback when " +
                "granted is false, and (2) reset pendingRollback to null " +
                "so the next toggle tap doesn't replay a stale rollback. " +
                "Block: $launcherBlock",
            launcherBlock.contains("if (!granted) pendingRollback?.invoke()") &&
                launcherBlock.contains("pendingRollback = null"),
        )
    }
}
