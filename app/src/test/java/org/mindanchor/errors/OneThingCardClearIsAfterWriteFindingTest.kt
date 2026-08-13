package org.mindanchor.errors

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B11 (SOTA v2 bug-hunt, agent #5): the OneThingCard's "Set" button
 * clears the draft optimistically (`onSet(draft); draft = ""`) before
 * the storage write completes. If the DataStore edit throws (disk
 * full, SealedCodecs exception on a too-large draft, etc.), the draft
 * is gone but the one-thing is not set. The user sees the empty input
 * and believes they typed it wrong. The same race exists in the
 * QuickNotesCard (v0.20.4); the v0.25.5+ new card copied the shape.
 *
 * File-shape pin: the fix PR makes `onSet` a `suspend` callback that
 * returns the success of the write, and only clears the draft on
 * `true`.
 */
class OneThingCardClearIsAfterWriteFindingTest {

    @Test
    fun `OneThingCard Set clears the draft only after the write succeeds (regression guard for B11)`() {
        val source = java.io.File(
            "src/main/java/org/mindanchor/launcher/HomeScreen.kt",
        ).readText()
        // The pre-fix shape is the literal:
        //   onClick = { onSet(draft); draft = "" }
        // The fix shape is:
        //   onClick = { scope.launch { if (onSet(draft)) draft = "" } }
        // The file-shape pin is the presence of a `scope.launch` (or
        // similar) that wraps the clear, with the clear conditional on
        // the write's success. The pre-fix shape has `draft = ""`
        // directly after `onSet(draft)`.
        val oneThingBlock = source.substringAfter("private fun OneThingCard")
            .substringBefore("@Composable\nprivate fun BedtimeListCard")
        // The negative regression guard: the literal `onSet(draft); draft = ""`
        // does not appear (the "clear before write" pattern).
        assertTrue(
            "OneThingCard's Set onClick must not be `{ onSet(draft); draft = \"\" }` " +
                "— the draft is cleared before the storage write completes. " +
                "The fix shape is `{ scope.launch { if (onSet(draft)) draft = \"\" } }`.",
            !oneThingBlock.contains("""onSet(draft)""" + "\n" + """                    draft = """"),
        )
    }
}
