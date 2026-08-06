package org.mindanchor.launcher

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
 * The launcher's own surfaces, driven on a device: the sky renders, support
 * is reachable in one tap, and the drawer both opens and comes back.
 */
@RunWith(AndroidJUnit4::class)
class LauncherUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun launchHome() {
        rule.setContent {
            MindAnchorTheme {
                LauncherRoot()
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun theHomeSurfaceRendersItsControls() {
        launchHome()
        rule.onNodeWithText("search").assertIsDisplayed()
        rule.onNodeWithText("settings").assertIsDisplayed()
        rule.onNodeWithText("digest").assertIsDisplayed()
    }

    @Test
    fun supportIsOneTapFromHome() {
        launchHome()
        // Not behind a menu, not below the fold.
        rule.onNodeWithText("support").assertIsDisplayed()
    }

    @Test
    fun theDrawerOpensAndAcceptsAQuery() {
        launchHome()
        rule.onNodeWithText("search").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Type to find an app…").assertIsDisplayed()
        rule.onNodeWithText("Type to find an app…").performTextInput("set")
        rule.waitForIdle()
    }

    @Test
    fun settingsOpensAndReturnsHome() {
        launchHome()
        rule.onNodeWithText("settings").performClick()
        rule.waitForIdle()
        rule.onAllNodes(hasText("Settings")).onFirst().assertExists()
        rule.onNodeWithText("← back").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("search").assertIsDisplayed()
    }
}
