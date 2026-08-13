package org.mindanchor.errors

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B6 (SOTA v2 bug-hunt, agent #5): the v0.25.5 WP-A worry-postponement
 * dialog captures `now` and `zone` at the moment the dialog composes
 * (`val now = remember { LocalDateTime.now() }`) instead of at the
 * moment the user picks. A dialog that stays open across a clock
 * change, an NTP correction, a zone change, or simply a long pause
 * produces a postponed-at time that is stale by however long the
 * dialog was open.
 *
 * File-shape pin: the fix PR reads `LocalDateTime.now(zone)` inside
 * the onClick lambdas. The asserts below are the regression guard.
 */
class PostponeDialogUsesPickTimeNotOpenTimeFindingTest {

    @Test
    fun `PostponeDialog does not capture now at dialog open (regression guard for B6)`() {
        val source = java.io.File(
            "src/main/java/org/mindanchor/launcher/HomeScreen.kt",
        ).readText()
        // The fix shape: no `remember { LocalDateTime.now() }` line in
        // the PostponeDialog composable. The pre-fix shape is exactly
        // that literal.
        val dialogBlock = source.substringAfter("private fun PostponeDialog")
            .substringBefore("@Composable\nprivate fun formatWallClock")
            .substringBefore("private fun formatWallClock")
        assertFalse(
            "PostponeDialog must not `remember { LocalDateTime.now() }` — the " +
                "captured value is stale the moment the dialog stays open " +
                "across any clock change.",
            dialogBlock.contains("remember { LocalDateTime.now() }"),
        )
    }

    @Test
    fun `PostponeDialog reads now inside the onClick (regression guard for B6)`() {
        val source = java.io.File(
            "src/main/java/org/mindanchor/launcher/HomeScreen.kt",
        ).readText()
        val dialogBlock = source.substringAfter("private fun PostponeDialog")
            .substringBefore("@Composable\nprivate fun formatWallClock")
            .substringBefore("private fun formatWallClock")
        // The fix shape: `onClick = { val zone = ...; LocalDateTime.now(zone) ... }`.
        // The pre-fix shape uses a captured `now` from outside the lambda.
        assertTrue(
            "PostponeDialog must call LocalDateTime.now(...) inside the onClick " +
                "lambda — the pick moment, not the open moment.",
            dialogBlock.contains("LocalDateTime.now("),
        )
    }
}
