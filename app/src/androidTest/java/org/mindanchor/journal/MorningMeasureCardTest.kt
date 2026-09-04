package org.mindanchor.journal

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.research.MorningMeasure
import org.mindanchor.ui.MindAnchorTheme

/**
 * v0.70.0+ Task 5 morning check-in card, driven on a real device.
 */
@RunWith(AndroidJUnit4::class)
class MorningMeasureCardTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /**
     * Waits for [tag] to actually appear before the caller acts on it.
     *
     * On a slow/CPU-constrained CI runner, [rule]'s own waitForIdle() —
     * which settles Compose's recomposition clock — is not always enough
     * time for the very first frame after a `setContent`/state-driven
     * recomposition to have laid out every node. A handful of tests here
     * that click several chips in quick succession right after
     * `setContent` saw the click silently miss (the onSave callback never
     * fired) rather than a wrong value, which points at the target node
     * not existing yet rather than a logic bug in the card itself.
     */
    private fun waitForNode(tag: String, timeoutMillis: Long = 15_000) {
        rule.waitUntil(timeoutMillis) {
            rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun savingSelectedValuesInvokesCallbackWithExactValues() {
        var captured: List<Int>? = null
        rule.setContent {
            MindAnchorTheme {
                MorningMeasureCard(
                    existing = null,
                    onSave = { mood, anxiety, angerUrge, energyFunction, sleepQuality ->
                        captured = listOf(mood, anxiety, angerUrge, energyFunction, sleepQuality)
                    },
                )
            }
        }
        rule.waitForIdle()
        waitForNode("mood_2")

        rule.onNodeWithTag("mood_2").performClick()
        rule.onNodeWithTag("anxiety_4").performClick()
        rule.onNodeWithTag("angerUrge_1").performClick()
        rule.onNodeWithTag("energyFunction_5").performClick()
        rule.onNodeWithTag("sleepQuality_3").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Save").performClick()
        rule.waitForIdle()

        assertEquals(listOf(2, 4, 1, 5, 3), captured)
    }

    @Test
    fun researchMeasureCopyIsShown() {
        rule.setContent {
            MindAnchorTheme {
                MorningMeasureCard(existing = null, onSave = { _, _, _, _, _ -> })
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText("A personal research measure, not a diagnosis or clinical score.")
            .assertIsDisplayed()
    }

    @Test
    fun existingRecordShowsSavedValuesReadOnlyWithAnEditAction() {
        val existing = MorningMeasure.create(
            localDate = LocalDate.of(2026, 8, 28),
            now = 1_000L,
            mood = 2,
            anxiety = 4,
            angerUrge = 1,
            energyFunction = 5,
            sleepQuality = 3,
            sourceDeviceId = "d1",
        )
        rule.setContent {
            MindAnchorTheme {
                MorningMeasureCard(existing = existing, onSave = { _, _, _, _, _ -> })
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText("Mood: 2 / 5").assertIsDisplayed()
        rule.onNodeWithText("Sleep quality: 3 / 5").assertIsDisplayed()
        rule.onNodeWithText("Edit").assertIsDisplayed()

        // The editable selectors are not shown until Edit is pressed.
        rule.onNodeWithTag("mood_2").assertDoesNotExist()
    }

    @Test
    fun clickingEditSwitchesToPrefilledEditableSelectors() {
        val existing = MorningMeasure.create(
            localDate = LocalDate.of(2026, 8, 28),
            now = 1_000L,
            mood = 2,
            anxiety = 4,
            angerUrge = 1,
            energyFunction = 5,
            sleepQuality = 3,
            sourceDeviceId = "d1",
        )
        var captured: List<Int>? = null
        rule.setContent {
            MindAnchorTheme {
                MorningMeasureCard(
                    existing = existing,
                    onSave = { mood, anxiety, angerUrge, energyFunction, sleepQuality ->
                        captured = listOf(mood, anxiety, angerUrge, energyFunction, sleepQuality)
                    },
                )
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText("Edit").performClick()
        rule.waitForIdle()
        waitForNode("mood_5")

        // Pre-filled with the existing values: only change one dimension, then save.
        rule.onNodeWithTag("mood_5").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Save").performClick()
        rule.waitForIdle()

        assertEquals(listOf(5, 4, 1, 5, 3), captured)
    }
}
