package org.mindanchor.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.sunset.Chronotype
import org.mindanchor.ui.MindAnchorTheme

/**
 * Onboarding decides what the app becomes for this person, so it has to
 * hand back exactly what they chose — and nothing they did not.
 *
 * v0.22.0: WP-10 step 1 reduced the flow from 4 screens (welcome →
 * goals → chronotype → plan) to 3 screens (welcome → pick → plan).
 * The "pick" screen has both goals (4 checkboxes) and chronotype
 * (4 radios) under one "What fits?" heading.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var completedWithGoals: Set<Goal>? = null
    private var completedWithChronotype: Chronotype? = null

    private fun start() {
        rule.setContent {
            MindAnchorTheme {
                OnboardingScreen(
                    onDone = { goals, chronotype ->
                        completedWithGoals = goals
                        completedWithChronotype = chronotype
                    },
                )
            }
        }
        rule.waitForIdle()
    }

    // The OnboardingScreen body is wrapped in a verticalScroll,
    // and on the MindAnchorTest AVD the "continue" button is
    // below the fold on the pick step. A bare performClick() taps
    // wherever the node's coordinates land, and a node that is
    // off-screen cannot be tapped. performScrollTo() first.
    private fun clickContinue() {
        rule.onNodeWithText("continue").performScrollTo().performClick()
    }

    private fun continueToPick() {
        // 0 (welcome) → 1 (pick). Caller is expected to be at step 0.
        clickContinue()
    }

    private fun continuePastPick() {
        // 1 (pick) → 2 (plan). Caller is expected to be at step 1.
        clickContinue()
    }

    @Test
    fun chosenStrugglesAreCarriedThroughToThePlan() {
        start()
        rule.onNodeWithText("Welcome to MindAnchor").assertIsDisplayed()
        continueToPick()

        rule.onNodeWithText("Constant interruptions").performClick()
        rule.onNodeWithText("My phone keeps me up at night").performClick()
        continuePastPick()

        // The plan names the features that match what was chosen.
        rule.onNodeWithText("Your plan — switch each on when ready:").assertIsDisplayed()
        rule.onNodeWithText("begin").performClick()
        rule.waitForIdle()

        assertEquals(setOf(Goal.INTERRUPTIONS, Goal.SLEEP), completedWithGoals)
    }

    @Test
    fun choosingNothingStillCompletesWithoutImposingAnything() {
        start()
        // Walk through every step without making a selection, then
        // verify the launcher was handed back empty + Chronotype.UNKNOWN.
        continueToPick()
        continuePastPick()
        rule.onNodeWithText("begin").performClick()
        rule.waitForIdle()

        assertTrue("no goals means no features forced on", completedWithGoals?.isEmpty() == true)
        assertEquals(
            "no chronotype chosen means unknown is passed through",
            Chronotype.UNKNOWN,
            completedWithChronotype,
        )
    }

    @Test
    fun chosenChronotypeIsCarriedThroughToCompletion() {
        start()
        continueToPick()
        // Tap the night-owl row. The label text is the inline string,
        // so we click on it and trust the row's selectable semantics
        // to make the entire row the target.
        rule.onNodeWithText("At my best in the evening, asleep after midnight")
            .performScrollTo()
            .performClick()
        continuePastPick()
        rule.onNodeWithText("begin").performClick()
        rule.waitForIdle()

        assertNotNull(completedWithGoals)
        assertEquals(Chronotype.NIGHT_OWL, completedWithChronotype)
    }

    @Test
    fun pickScreenShowsBothGoalsAndChronotypeOnOneScreen() {
        // v0.22.0: the pick screen combines goals and chronotype.
        // A regression that splits them back into two screens
        // would have to re-introduce a step number and break
        // this test. (The 3-screen count is also a test
        // invariant — any "go to step 2" code that lands on
        // something other than the plan screen is a regression.)
        start()
        continueToPick()
        rule.onNodeWithText("Constant interruptions").assertIsDisplayed()
        rule.onNodeWithText("Asleep by 21:00, up before 06:00").assertIsDisplayed()
        // Both sections on one screen — clicking "continue"
        // once should land on the plan step.
        continuePastPick()
        rule.onNodeWithText("Your plan — switch each on when ready:").assertIsDisplayed()
    }
}
