package org.mindanchor.friction

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
 * The gate must always end somewhere the user chose — including out.
 *
 * This also pins the breath to a finite animation: an endless one leaves
 * the UI permanently non-idle, which hangs every test that touches it and
 * quietly burns frames on a real phone.
 */
@RunWith(AndroidJUnit4::class)
class FrictionGateTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var openedFor: Long? = null
    private var openCalls = 0
    private var walkedAway = false

    private fun start() {
        rule.setContent {
            MindAnchorTheme {
                FrictionGate(
                    appLabel = "Instagram",
                    onOpen = { openedFor = it; openCalls++ },
                    onNeverMind = { walkedAway = true },
                )
            }
        }
    }

    private fun awaitIntentionPrompt() {
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("What are you here to do in Instagram?")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun theBreathEndsAndTheIntentionPromptArrives() {
        start()
        awaitIntentionPrompt()
        rule.onNodeWithText("Open without a timer").assertExists()
    }

    @Test
    fun walkingAwayIsAlwaysOneTap() {
        start()
        awaitIntentionPrompt()
        rule.onNodeWithText("never mind").performClick()
        rule.waitForIdle()
        assertTrue("never mind must leave without opening", walkedAway)
        assertEquals("the app must not have been opened", 0, openCalls)
    }

    @Test
    fun choosingATimeBoxPassesTheChosenMinutes() {
        start()
        awaitIntentionPrompt()
        rule.onNodeWithText("10 min").performClick()
        rule.waitForIdle()
        assertEquals(10L, openedFor)
    }

    @Test
    fun openingWithoutATimerPassesNoSession() {
        start()
        awaitIntentionPrompt()
        rule.onNodeWithText("Open without a timer").performClick()
        rule.waitForIdle()
        assertEquals(1, openCalls)
        assertEquals(null, openedFor)
    }
}
