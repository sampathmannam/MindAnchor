package org.mindanchor.pulse

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.performClick
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

    /** Taps [answer] for each of the five items. */
    private fun answerAll(answer: String) {
        repeat(WhoFive.ITEM_COUNT) {
            // Each answered item keeps its buttons, so always take the first
            // still-unanswered row by index from the top.
            rule.onAllNodesWithText(answer)[it].performClick()
        }
        rule.waitForIdle()
    }

    @Test
    fun theSaveActionAppearsOnlyWhenEveryItemIsAnswered() {
        start()
        rule.onAllNodes(hasText("Save")).assertCountEquals(0)
        answerAll("3")
        rule.onNodeWithText("Save").assertIsDisplayed()
    }

    @Test
    fun aFullyPositiveAnswerScoresOneHundred() {
        start()
        answerAll("5")
        rule.onNodeWithText("Save").performClick()
        rule.waitForIdle()
        rule.onAllNodes(hasText("100", substring = true)).onFirst().assertExists()
    }

    @Test
    fun aLowScoreOffersSupportRatherThanOnlyReassurance() {
        start()
        answerAll("0")
        rule.onNodeWithText("Save").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Open support").assertIsDisplayed()
    }
}
