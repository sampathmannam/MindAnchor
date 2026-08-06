package org.mindanchor.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.ui.MindAnchorTheme

/**
 * Onboarding decides what the app becomes for this person, so it has to
 * hand back exactly what they chose — and nothing they did not.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var completedWith: Set<Goal>? = null

    private fun start() {
        rule.setContent {
            MindAnchorTheme {
                OnboardingScreen(onDone = { completedWith = it })
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun chosenStrugglesAreCarriedThroughToThePlan() {
        start()
        rule.onNodeWithText("Welcome to MindAnchor").assertIsDisplayed()
        rule.onNodeWithText("continue").performClick()

        rule.onNodeWithText("Constant interruptions").performClick()
        rule.onNodeWithText("My phone keeps me up at night").performClick()
        rule.onNodeWithText("continue").performClick()

        // The plan names the features that match what was chosen.
        rule.onNodeWithText("Your plan — switch each on when ready:").assertIsDisplayed()
        rule.onNodeWithText("begin").performClick()
        rule.waitForIdle()

        assertEquals(setOf(Goal.INTERRUPTIONS, Goal.SLEEP), completedWith)
    }

    @Test
    fun choosingNothingStillCompletesWithoutImposingAnything() {
        start()
        rule.onNodeWithText("continue").performClick()
        rule.onNodeWithText("continue").performClick()
        rule.onNodeWithText("begin").performClick()
        rule.waitForIdle()

        assertTrue("no goals means no features forced on", completedWith?.isEmpty() == true)
    }
}
