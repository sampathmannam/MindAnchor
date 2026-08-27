package org.mindanchor.anchorcore

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.ui.MindAnchorTheme

/**
 * v0.70.0 AnchorCore wiring, driven on a real device:
 * Settings → Measuring → AnchorCore section.
 */
@RunWith(AndroidJUnit4::class)
class AnchorCoreUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun launchHome() {
        rule.setContent {
            MindAnchorTheme {
                org.mindanchor.launcher.LauncherRoot()
            }
        }
        rule.waitForIdle()
    }

    private fun openAnchorCoreSection() {
        launchHome()
        rule.onNodeWithText("settings").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Measuring").performClick()
        rule.waitForIdle()
        rule.onNodeWithText(
            "A quiet weekly picture from your own patterns. Off until you ask."
        ).performScrollTo()
        rule.waitForIdle()
    }

    @Test
    fun anchorCoreSectionRendersTheRightCopy() {
        openAnchorCoreSection()
        rule.onNodeWithText(
            "A quiet weekly picture from your own patterns. Off until you ask."
        ).assertIsDisplayed()
    }

    @Test
    fun clickingMasterSubtitleTogglesTheState() {
        openAnchorCoreSection()
        val subtitle = rule.onNodeWithText(
            "A quiet weekly picture from your own patterns. Off until you ask."
        )
        // DEBUG: dump all text nodes we can see in the AnchorCore section.
        val allText = rule.onAllNodesWithText(".*", substring = false).fetchSemanticsNodes()
            .mapNotNull { node ->
                val text = (node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)
                    ?.joinToString("") { it.text })
                if (text.isNullOrBlank()) null else text
            }
        val matches = allText.filter { "Letter" in it || "knows" in it || "Gentler" in it || "Wind" in it }
        check(matches.isEmpty()) {
            "AnchorCore hook rows unexpectedly visible at master=OFF: $matches"
        }
        // Capture whether the hook rows are visible BEFORE the click.
        val beforeLetter = rule.onAllNodesWithText("Letter knows the week")
            .fetchSemanticsNodes().isNotEmpty()
        subtitle.performClick()
        rule.waitForIdle()
        val afterLetter = rule.onAllNodesWithText("Letter knows the week")
            .fetchSemanticsNodes().isNotEmpty()
        // The state must have flipped.
        check(beforeLetter != afterLetter) {
            "Clicking the master subtitle did not flip the hook-row visibility (before=$beforeLetter, after=$afterLetter)"
        }
    }

    @Test
    fun overrideRevokeRowHidesWhenNoOverrideIsActive() {
        openAnchorCoreSection()
        val revokeText = rule.onAllNodesWithText(
            "Wind-down is 30 minutes earlier until "
        )
        check(revokeText.fetchSemanticsNodes().isEmpty()) {
            "Override revoke row should not render without a Hook C accept"
        }
    }
}
