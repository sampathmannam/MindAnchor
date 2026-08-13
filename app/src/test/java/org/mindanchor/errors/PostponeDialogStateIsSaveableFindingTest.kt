package org.mindanchor.errors

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B8 (SOTA v2 bug-hunt, agent #5): the OpenLoopCard.RETURN branch's
 * `var showDialog by remember { mutableStateOf(false) }` (HomeScreen.kt:610)
 * loses its `true` value on every config change, silently closing the
 * worry-postponement dialog. Same `remember`-not-`rememberSaveable`
 * pattern as B7, in the v0.25.5 WP-A new code.
 *
 * File-shape pin: the fix PR changes `remember` to `rememberSaveable`.
 */
class PostponeDialogStateIsSaveableFindingTest {

    @Test
    fun `OpenLoopCard RETURN showDialog is rememberSaveable (regression guard for B8)`() {
        val source = java.io.File(
            "src/main/java/org/mindanchor/launcher/HomeScreen.kt",
        ).readText()
        // The pre-fix literal is `var showDialog by remember { mutableStateOf(false) }`.
        val returnBlock = source.substringAfter("LoopPhase.RETURN -> {")
            .substringBefore("LoopPhase.POSTPONED")
        assertTrue(
            "OpenLoopCard.RETURN's showDialog must be `rememberSaveable`, " +
                "not `remember` — a config change while the postpone dialog " +
                "is open closes the dialog silently.",
            returnBlock.contains("rememberSaveable { mutableStateOf(false) }"),
        )
    }
}
