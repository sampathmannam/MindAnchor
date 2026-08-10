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

    // The OnboardingScreen body is wrapped in a verticalScroll
    // (see OnboardingScreen.kt), and on the MindAnchorTest AVD the
    // "continue" button is below the fold on the chronotype step
    // (the step's body text pushes the button off-screen). A bare
    // ``performClick()`` taps wherever the node's coordinates land --
    // visible or not -- and the test fails with "Failed to inject
    // touch input" / no node found. The SemanticsTest onboarding
    // walk already handles this with ``performScrollTo().performClick()``;
    // we mirror it here.
    private fun clickContinue() {
        rule.onNodeWithText("continue").performScrollTo().performClick()
    }

    private fun continueToGoals() {
        // 0 (welcome) -> 1 (goals). Caller is expected to be at step 0.
        clickContinue()
    }

    private fun continuePastGoals() {
        // 1 (goals) -> 2 (chronotype). Caller is expected to be at
        // step 1 -- ``continueToGoals()`` is the entry to step 1.
        // (The previous version of these helpers was cumulative from
        // step 0 -- ``continuePastGoals`` clicked twice, ``continuePastChronotype``
        // three times -- which overran the plan step on every
        // chained call and surfaced as "Failed to inject touch input
        // / could not find 'continue'" on the chronotype step. The
        // plan step has "begin", not "continue", so any extra click
        // after step 3 lands on a node that does not exist.)
        clickContinue()
    }

    private fun continuePastChronotype() {
        // 2 (chronotype) -> 3 (plan). Caller is expected to be at
        // step 2 -- ``continuePastGoals()`` is the entry to step 2.
        clickContinue()
    }

    @Test
    fun chosenStrugglesAreCarriedThroughToThePlan() {
        start()
        rule.onNodeWithText("Welcome to MindAnchor").assertIsDisplayed()
        continueToGoals()

        rule.onNodeWithText("Constant interruptions").performClick()
        rule.onNodeWithText("My phone keeps me up at night").performClick()
        continuePastGoals()
        continuePastChronotype()

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
        // The helpers are sequential (each does 1 click) so the
        // test must call them in order from step 0 -> step 3.
        continueToGoals()
        continuePastGoals()
        continuePastChronotype()
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
        // Walk to the chronotype step, pick NIGHT_OWL, then carry on.
        continueToGoals()
        continuePastGoals()
        // Tap the night-owl row. The label text is the inline string,
        // so we click on it and trust the row's selectable semantics
        // to make the entire row the target.
        rule.onNodeWithText("At my best in the evening, asleep after midnight")
            .performScrollTo()
            .performClick()
        continuePastChronotype()
        rule.onNodeWithText("begin").performClick()
        rule.waitForIdle()

        assertNotNull(completedWithGoals)
        assertEquals(Chronotype.NIGHT_OWL, completedWithChronotype)
    }
}
