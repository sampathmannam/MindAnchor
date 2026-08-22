package org.mindanchor.errors

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B11 (SOTA v2 bug-hunt, agent #5): the OneThingCard's "Set" button
 * cleared the draft optimistically (`onSet(draft); draft = ""`) before
 * the storage write completes. If the DataStore edit throws (disk
 * full, SealedCodecs exception on a too-large draft, etc.), the draft
 * is gone but the one-thing is not set. The user sees the empty input
 * and believes they typed it wrong. The same race existed in the
 * QuickNotesCard (v0.20.4); the v0.25.5+ new card copied the shape.
 *
 * v0.28.0 (BPD-strict cut): the OneThingCard Composable was removed
 * from the home surface entirely. The data model (oneThing StateFlow +
 * setOneThing method in LauncherViewModel) is preserved for the
 * export payload. The original BUG-11 clear-after-write pin is
 * replaced with a "composable is gone" pin — a regression that
 * re-introduces the home-surface card would re-introduce the
 * clear-before-write race the v0.28.0 cut explicitly removed.
 */
class OneThingCardClearIsAfterWriteFindingTest {

    @Test
    fun `v0-28-0 BPD-strict cut — OneThingCard Composable is removed from HomeScreen`() {
        val source = java.io.File(
            "src/main/java/org/mindanchor/launcher/HomeScreen.kt",
        ).readText()
        // The v0.28.0 cut removes the OneThingCard Composable
        // from HomeScreen. A regression that re-introduces the
        // Composable would re-introduce both the BUG-11
        // clear-before-write race AND the cognitive-load
        // problem (third task-capture card) that v0.28.0
        // explicitly cut.
        assertTrue(
            "OneThingCard Composable must NOT be in HomeScreen.kt (v0.28.0 " +
                "BPD-strict cut). A regression that re-introduces the " +
                "Composable would re-introduce the BUG-11 clear-before-write " +
                "race. source=\n" + source.take(2000),
            !source.contains("private fun OneThingCard("),
        )
    }
}
