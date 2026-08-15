package org.mindanchor.errors

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B7 (SOTA v2 bug-hunt, agent #5): the v0.25.5 WP-F "today's one thing"
 * card used `var draft by remember { mutableStateOf("") }` instead of
 * `rememberSaveable`. A config change (rotation, font size, locale,
 * dark-mode toggle) would lose the typed sentence. This is the same
 * BUG-002 pattern the v0.25.7 hunt found in OnboardingScreen, repeated
 * in the v0.25.5+ new card.
 *
 * v0.28.0 (BPD-strict cut): the OneThingCard Composable was removed
 * from the home surface entirely. The data model (oneThing StateFlow +
 * setOneThing method in LauncherViewModel) is preserved for the
 * export payload. The original BUG-7 rememberSaveable pin is
 * replaced with a "composable is gone" pin — a regression that
 * re-introduces the home-surface card would re-introduce both
 * the cognitive-load problem and the config-change draft-loss
 * bug the v0.28.0 cut explicitly removed.
 */
class OneThingCardDraftIsSaveableFindingTest {

    @Test
    fun `v0-28-0 BPD-strict cut — OneThingCard Composable is removed from HomeScreen`() {
        val source = java.io.File(
            "src/main/java/org/mindanchor/launcher/HomeScreen.kt",
        ).readText()
        // The v0.28.0 cut removes the OneThingCard Composable
        // from HomeScreen. A regression that re-introduces the
        // Composable would re-introduce both the BUG-7
        // config-change draft-loss pattern AND the cognitive-load
        // problem (third task-capture card overlapping with
        // OpenLoop + QuickNotes) that v0.28.0 explicitly cut.
        assertTrue(
            "OneThingCard Composable must NOT be in HomeScreen.kt (v0.28.0 " +
                "BPD-strict cut). A regression that re-introduces the " +
                "Composable would re-introduce the BUG-7 draft-loss pattern. " +
                "source=\n" + source.take(2000),
            !source.contains("private fun OneThingCard("),
        )
    }
}
