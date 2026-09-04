package org.mindanchor.support

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.ui.MindAnchorTheme

/**
 * The support screen is the one place that has to work when someone is at
 * their worst, so it gets driven end to end: it must render, the crisis
 * card must be present without scrolling past anything, and the contact
 * form must reject an unreachable person.
 */
@RunWith(AndroidJUnit4::class)
class SupportScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun launchSupport() {
        rule.setContent {
            MindAnchorTheme {
                SupportScreen(onClose = {})
            }
        }
    }

    @Test
    fun theWayToReachSomeoneIsVisibleImmediately() {
        launchSupport()
        // The card is now entirely the person's own chosen people. No
        // hotline numbers appear anywhere in the app by design, so this
        // asserts the card itself is present and above the fold rather
        // than asserting any particular number.
        rule.onNodeWithText("Reach someone now").assertIsDisplayed()
    }

    @Test
    fun aContactCannotBeSavedWithoutANumberToCall() {
        launchSupport()
        rule.onNodeWithText("edit").performScrollTo().performClick()
        rule.waitForIdle()
        // A name alone is not a way to reach anyone. Saving it would put a
        // button on the crisis card that does nothing when tapped.
        rule.onNodeWithText("Name").performScrollTo().performTextInput("Sam")
        rule.waitForIdle()
        rule.onNodeWithText("Add person").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("Phone").performScrollTo().performTextInput("5551234567")
        rule.waitForIdle()
        rule.onNodeWithText("Add person").performScrollTo().assertIsEnabled()
    }

    @Test
    fun theScreenIsNavigableByHeading() {
        launchSupport()
        // Support is the longest screen in the app. A TalkBack user who
        // cannot jump by heading has to hear every crisis number, every
        // skill and every plan field in order before reaching the part
        // they came for — which is the opposite of what this screen is
        // for. Four headings, one per section.
        rule.onAllNodes(isHeading()).assertCountEquals(4)
    }

    @Test
    fun distressSkillsArePresent() {
        launchSupport()
        rule.onNodeWithText("STOP").assertExists()
        rule.onNodeWithText("TIPP").assertExists()
        rule.onNodeWithText("5-4-3-2-1").assertExists()
    }

    @Test
    fun aSafetyPlanCanBeWrittenAndReadBack() {
        launchSupport()
        // The plan sits below the crisis card and the skills, so every
        // target has to be scrolled into view before it can be touched.
        rule.onNodeWithText("edit").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("When things are turning")
            .performScrollTo()
            .performTextInput("cannot sleep")
        rule.onNodeWithText("done").performScrollTo().performClick()
        rule.waitUntil(timeoutMillis = 10_000L) {
            rule.onAllNodes(hasText("edit")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodes(hasText("cannot sleep", substring = true)).onFirst().assertExists()
    }
}
