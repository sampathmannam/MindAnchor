package org.mindanchor.errors

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B10 (SOTA v2 bug-hunt, agent #5): the v0.25.5 WP-F OneThingCard's
 * "Set" TextButton has no `enabled = draft.isNotBlank()` gate. A user
 * can tap "Set" with an empty draft, the prefs layer trims and removes
 * the key, and the input is cleared. The QuickNotesCard (the v0.20.4
 * sibling) has the gate; the v0.25.5+ new card does not.
 *
 * File-shape pin: the fix PR adds `enabled = draft.isNotBlank()` to
 * the OneThingCard's "Set" button.
 */
class OneThingCardSetButtonIsGatedFindingTest {

    @Test
    fun `OneThingCard Set button is enabled-gated on draft non-blank (regression guard for B10)`() {
        val source = java.io.File(
            "src/main/java/org/mindanchor/launcher/HomeScreen.kt",
        ).readText()
        // The OneThingCard's "Set" TextButton should have
        // `enabled = draft.isNotBlank()` matching the QuickNotesCard
        // sibling. The literal is the regression guard.
        val oneThingBlock = source.substringAfter("private fun OneThingCard")
            .substringBefore("@Composable\nprivate fun BedtimeListCard")
        assertTrue(
            "OneThingCard's Set button must be `enabled = draft.isNotBlank()` — " +
                "the v0.25.5 WP-F new card regressed the `enabled`-gate " +
                "discipline the QuickNotesCard (v0.20.4) had.",
            oneThingBlock.contains("enabled = draft.isNotBlank()"),
        )
    }
}
