package org.mindanchor.errors

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B7 (SOTA v2 bug-hunt, agent #5): the v0.25.5 WP-F "today's one thing"
 * card uses `var draft by remember { mutableStateOf("") }` instead of
 * `rememberSaveable`. A config change (rotation, font size, locale,
 * dark-mode toggle) loses the typed sentence. This is the same
 * BUG-002 pattern the v0.25.7 hunt found in OnboardingScreen, repeated
 * in the v0.25.5+ new card.
 *
 * File-shape pin: the fix PR adds `import androidx.compose.runtime.saveable.rememberSaveable`
 * and changes the `remember` to `rememberSaveable`. The assert below
 * is the regression guard.
 */
class OneThingCardDraftIsSaveableFindingTest {

    @Test
    fun `OneThingCard draft is rememberSaveable (regression guard for B7)`() {
        val source = java.io.File(
            "src/main/java/org/mindanchor/launcher/HomeScreen.kt",
        ).readText()
        // The pre-fix shape: `var draft by remember { mutableStateOf("") }`
        // inside the `if (text == null) { ... }` branch of OneThingCard.
        val oneThingBlock = source.substringAfter("private fun OneThingCard")
            .substringBefore("@Composable\nprivate fun BedtimeListCard")
        assertTrue(
            "OneThingCard.draft must be `rememberSaveable`, not `remember` — " +
                "the v0.25.5+ WP-F new card repeats the v0.25.7 BUG-002 pattern " +
                "and loses typed input on every config change.",
            oneThingBlock.contains("rememberSaveable { mutableStateOf(\"\") }"),
        )
    }
}
