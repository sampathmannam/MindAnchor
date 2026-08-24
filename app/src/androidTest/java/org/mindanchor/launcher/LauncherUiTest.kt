package org.mindanchor.launcher

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.ui.MindAnchorTheme

/**
 * The launcher's own surfaces, driven on a device: the sky renders
 * and the drawer both opens and comes back.
 *
 * ## Note: removed `supportIsOneTapFromHome`
 *
 * The previous version of this file had a
 * `supportIsOneTapFromHome` test that asserted the home surface
 * showed a literal "support" text in the TopStart corner. The
 * Support feature (a navigation affordance to the support screen)
 * was removed in v0.25.7 (Task 13); the comment at
 * [org.mindanchor.launcher.HomeSurface] line 938 reads:
 *
 *     "The TopStart (Support) corner was removed in v0.25.7 (Task 13)."
 *
 * The removal of the feature did not delete the test, so the
 * `supportIsOneTapFromHome` case had been failing on CI ever
 * since. v0.30+ removes the test. The KDoc on
 * [org.mindanchor.launcher.HomeSurface] still names the four
 * surviving corner buttons (Notes, digest, settings, search)
 * and the support screen remains reachable from
 * [org.mindanchor.support] for any future re-introduction.
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
        // Anchor on an index row rather than the screen title, because the
        // title string is lowercase "settings" like the button that opened
        // it. This used to anchor on "Notification batching", which sat on
        // the first screen when settings was one long scroll; batching now
        // lives a level down under Quiet, and this test failing was how
        // that change announced itself.
        rule.onNodeWithText("Measuring").assertExists()
        rule.onNodeWithText("← back").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("search").assertIsDisplayed()
    }

    /**
     * Settings is two levels deep now, and back has to mean "up one"
     * rather than "all the way out".
     *
     * Getting this wrong is not a crash, it is worse: a person who opens
     * Measuring, reads a line, and taps back would be thrown to the home
     * screen and have to start again. That is the kind of small, repeated
     * friction nobody reports as a bug and everybody quietly resents.
     */
    @Test
    fun settingsGroupOpensAndBackReturnsToTheIndex() {
        launchHome()
        rule.onNodeWithText("settings").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Measuring").performClick()
        rule.waitForIdle()
        // A section that only exists inside Measuring.
        rule.onNodeWithText("Heart rhythm").assertExists()

        rule.onNodeWithText("← back").performClick()
        rule.waitForIdle()
        // Back to the index, not out to the launcher.
        rule.onNodeWithText("Reading").assertExists()
        rule.onNodeWithText("Heart rhythm").assertDoesNotExist()

        rule.onNodeWithText("← back").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("search").assertIsDisplayed()
    }
}
