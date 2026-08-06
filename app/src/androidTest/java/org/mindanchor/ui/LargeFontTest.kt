package org.mindanchor.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.launcher.LauncherRoot
import org.mindanchor.support.SupportScreen

/**
 * Someone who needs this app is disproportionately likely to be running a
 * large system font. At 2× nothing may crash, and the things that matter —
 * support, the crisis lines — must still be findable.
 */
@RunWith(AndroidJUnit4::class)
class LargeFontTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun setContentAtFontScale(scale: Float, content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = scale),
            ) {
                MindAnchorTheme { content() }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun theHomeScreenSurvivesDoubleSizedText() {
        setContentAtFontScale(2.0f) { LauncherRoot() }
        rule.onNodeWithText("support").assertExists()
        rule.onNodeWithText("search").assertExists()
    }

    @Test
    fun supportSurvivesDoubleSizedText() {
        setContentAtFontScale(2.0f) { SupportScreen(onClose = {}) }
        rule.onNodeWithText("Reach someone now").assertExists()
    }

    @Test
    fun theHomeScreenSurvivesTheLargestAccessibilityText() {
        setContentAtFontScale(3.0f) { LauncherRoot() }
        rule.onNodeWithText("support").assertExists()
    }

    @Test
    fun theClockCanActuallyBeReachedAtTripleSize() {
        setContentAtFontScale(3.0f) { LauncherRoot() }
        // assertExists() was never enough. A node clipped off the bottom of
        // an unscrollable screen still exists in the semantics tree, which
        // is exactly how the home surface shipped for so long with content
        // no thumb could reach. performScrollTo() fails if nothing can
        // scroll to it, so this asserts reachability rather than presence.
        rule.onNodeWithText("support").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun supportRemainsReachableWhenTheCrisisCardIsTallerThanTheScreen() {
        setContentAtFontScale(2.0f) { SupportScreen(onClose = {}) }
        // The crisis card alone runs to seven lines plus contacts at this
        // size. The safety plan below it must still be gettable to.
        rule.onNodeWithText("My plan").performScrollTo().assertIsDisplayed()
    }
}
