package org.mindanchor.errors

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B10 (SOTA v2 bug-hunt, agent #5): the v0.25.5 WP-F "today's one
 * thing" card has a "Set" TextButton that fires on a blank draft. The
 * launcher state receives an empty string. The BUG-005 fix migrates
 * the draft to rememberSaveable; the BUG-10 fix gates the Set
 * affordance on a non-blank draft.
 *
 * v0.28.0 (BPD-strict cut): the OneThingCard Composable was removed
 * from the home surface entirely. The data model (oneThing StateFlow +
 * setOneThing method in LauncherViewModel) is preserved for the
 * export payload. The original BUG-10 Set-gate pin is replaced with
 * a "composable is gone" pin — a regression that re-introduces the
 * home-surface card would re-introduce both the Set-without-gate bug
 * AND the cognitive-load problem that v0.28.0 explicitly cut.
 */
class OneThingCardSetButtonIsGatedFindingTest {

    @Test
    fun `v0-28-0 BPD-strict cut — OneThingCard Composable is removed from HomeScreen`() {
        val source = java.io.File(
            "src/main/java/org/mindanchor/launcher/HomeScreen.kt",
        ).readText()
        // The v0.28.0 cut removes the OneThingCard Composable
        // from HomeScreen. A regression that re-introduces the
        // Composable would re-introduce both the BUG-10
        // Set-without-gate pattern AND the cognitive-load
        // problem that v0.28.0 explicitly removed.
        assertTrue(
            "OneThingCard Composable must NOT be in HomeScreen.kt (v0.28.0 " +
                "BPD-strict cut). A regression that re-introduces the " +
                "Composable would re-introduce the BUG-10 Set-without-gate " +
                "pattern. source=\n" + source.take(2000),
            !source.contains("private fun OneThingCard("),
        )
    }
}
