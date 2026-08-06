package org.mindanchor.pulse

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.ui.MindAnchorTheme

/**
 * The wellbeing pulse, answered the way a person answers it. A low score
 * must lead somewhere — offering support, not just sympathy.
 */
@RunWith(AndroidJUnit4::class)
class PulseFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun start() {
        rule.setContent {
            MindAnchorTheme {
                PulseScreen(onClose = {})
            }
        }
        rule.waitForIdle()
    }

    /**
     * Taps [answer] for each of the five items. Five questions plus a scale
     * do not fit one screen, so every target is scrolled into view first —
     * exactly what a person does with their thumb.
     */
    private fun answerAll(answer: String) {
        repeat(WhoFive.ITEM_COUNT) {
            // Each answered item keeps its buttons, so always take the first
            // still-unanswered row by index from the top.
            rule.onAllNodesWithText(answer)[it].performScrollTo().performClick()
        }
        rule.waitForIdle()
    }

    /** Every item answered, so the save action must exist. */
    private fun save() {
        rule.onNodeWithText("Save").performScrollTo().performClick()
        rule.waitForIdle()
    }

    @Test
    fun theSaveActionAppearsOnlyWhenEveryItemIsAnswered() {
        start()
        rule.onAllNodes(hasText("Save")).assertCountEquals(0)
        answerAll("3")
        rule.onNodeWithText("Save").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theMostPositiveAnswerIsReachableAndScoresOneHundred() {
        start()
        // "5" is the last button on the scale — the one a too-wide row
        // clips off the edge of a narrow screen.
        answerAll("5")
        save()
        rule.onAllNodes(hasText("100", substring = true)).onFirst().assertExists()
    }

    @Test
    fun theBleakestAnswerIsReachableAndOffersSupport() {
        start()
        // "0" across the board is the person most in need of the next step,
        // so this path must stay walkable end to end.
        answerAll("0")
        save()
        rule.onNodeWithText("Open support").performScrollTo().assertIsDisplayed()
    }
}
