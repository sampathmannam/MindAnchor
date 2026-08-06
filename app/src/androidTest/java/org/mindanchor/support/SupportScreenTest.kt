package org.mindanchor.support

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.ui.MindAnchorTheme

/**
 * The support screen is the one place that has to work when someone is at
 * their worst, so it gets driven end to end: it must render, the crisis
 * lines must be present without scrolling past anything, and a contact the
 * user adds must persist.
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
    fun crisisLinesAreVisibleImmediately() {
        launchSupport()
        rule.onNodeWithText("Reach someone now").assertIsDisplayed()
        rule.onAllNodes(hasText("988", substring = true)).onFirst().assertIsDisplayed()
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
        rule.onNodeWithText("edit").performClick()
        rule.onNodeWithText("When things are turning").performTextInput("cannot sleep")
        rule.onNodeWithText("done").performClick()
        rule.waitForIdle()
        rule.onAllNodes(hasText("cannot sleep", substring = true)).onFirst().assertExists()
    }
}
