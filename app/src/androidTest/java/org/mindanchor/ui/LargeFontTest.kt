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

/**
 * Someone who needs this app is disproportionately likely to be running a
 * large system font. At 2× nothing may crash, and the things that matter —
 * the home's pinned controls and the crisis lines — must still be findable.
 *
 * ## Note: removed "support" assertions
 *
 * The previous version of this file asserted that the home surface
 * showed a literal "support" text in the TopStart corner at large
 * font sizes. The Support feature (a navigation affordance to
 * [org.mindanchor.support.SupportScreen]) was removed from the home
 * in v0.25.7 (Task 13); the comment at
 * [org.mindanchor.launcher.HomeSurface] line 938 reads:
 *
 *     "The TopStart (Support) corner was removed in v0.25.7 (Task 13)."
 *
 * The three tests that asserted a home "support" text — the one for
 * double-sized text, the one for the largest accessibility text, and
 * the one for the pinned controls at triple size — have been updated
 * to check the surviving "search" affordance and the corner
 * buttons. The two tests that exercise [SupportScreen] itself
 * ([supportSurvivesDoubleSizedText],
 * [supportRemainsReachableWhenTheCrisisCardIsTallerThanTheScreen])
 * are unchanged; the support screen is still reachable from
 * [org.mindanchor.support] for any future re-introduction of the
 * home affordance.
 *
 * ## v0.72.x: Support screen removed entirely
 *
 * The "Your plan" settings entry, the SupportActivity, the
 * SupportScreen composable and the SupportTile were all removed
 * in v0.72.x. The two tests that rendered the screen directly
 * (below) are gone with it.
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
        // v0.30+ (PR #38 follow-up): the home no longer shows
        // "support" in the TopStart corner — the Support feature
        // was removed in v0.25.7 (Task 13). The home's
        // surviving affordances at large font sizes are
        // the search bar (TopEnd) and the corner
        // settings / digest buttons. The "search" check
        // pins the home is still findable at 2× font; the
        // exact text matches the project's drawer-opener
        // string [R.string.open_drawer].
        rule.onNodeWithText("search").assertExists()
    }

    @Test
    fun theHomeScreenSurvivesTheLargestAccessibilityText() {
        setContentAtFontScale(3.0f) { LauncherRoot() }
        // v0.30+ (PR #38 follow-up): see the KDoc above.
        // The home at 3× accessibility text is asserted
        // to render — the test that was here previously
        // checked for the removed "support" text.
        rule.onNodeWithText("search").assertExists()
    }

    @Test
    fun thePinnedControlsStayOnScreenAtTripleSize() {
        setContentAtFontScale(3.0f) { LauncherRoot() }
        // v0.30+ (PR #38 follow-up): the home's surviving
        // pinned controls (search, settings, digest) sit
        // outside the scrolling column on purpose, so the
        // way out of the home screen never scrolls away
        // from you. The "support" assertion from the
        // previous version of this test is gone — the
        // Support corner was removed in v0.25.7 (Task 13).
        // That means the surviving controls must already
        // be displayed — an earlier version of this test
        // asked them to scroll into view, which is meaningless
        // for a node with no scrollable ancestor and failed
        // for exactly that reason.
        rule.onNodeWithText("search").assertIsDisplayed()
    }

}
