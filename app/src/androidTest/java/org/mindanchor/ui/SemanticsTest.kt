package org.mindanchor.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.R
import org.mindanchor.digest.DigestScreen
import org.mindanchor.friction.FrictionGate
import org.mindanchor.friction.FrictionTone
import org.mindanchor.launcher.AppActionsDialog
import org.mindanchor.launcher.DisplayApp
import org.mindanchor.launcher.LauncherRoot
import org.mindanchor.model.EmaScreen
import org.mindanchor.onboarding.OnboardingScreen
import org.mindanchor.report.ReportScreen
import org.mindanchor.settings.SettingsScreen
import org.mindanchor.vitals.PpgScreen

/**
 * The other camera: instead of photographing each surface, this walks it.
 *
 * Two floors, checked on every clickable thing on every surface this app
 * can show:
 *
 *  1. **It has words.** A tap target with no text and no content
 *     description is a thing TalkBack announces as "button" — a door with
 *     no name, on an app whose users include exactly the people most
 *     likely to be using a screen reader, large type, or both.
 *  2. **It is at least 48dp on both sides.** The documented Android
 *     minimum, load-bearing here: tremor, large fingers and distress are
 *     not edge cases for this app, they are the brief.
 *
 * Unlike the screenshot camera this one is a gate and fails the build,
 * because both floors are exact claims a machine can check — there is no
 * judgement call left to a human eye, so there is nothing gained by
 * letting a fault through to look at.
 *
 * The walk reads the merged semantics tree, which is what TalkBack reads,
 * so a row whose words live in a child Text passes and a control that
 * merges nothing fails — the same verdict a screen reader user gets.
 */
@RunWith(AndroidJUnit4::class)
class SemanticsTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun string(res: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(res)

    private fun set(content: @Composable () -> Unit) {
        // Inside a Surface, as every screen is in the app itself.
        rule.setContent { MindAnchorTheme { Surface(Modifier.fillMaxSize()) { content() } } }
        rule.waitForIdle()
    }

    /** For targets on surfaces with no scroll container. */
    private fun click(res: Int) {
        rule.onNodeWithText(string(res)).performClick()
        rule.waitForIdle()
    }

    /**
     * For targets inside a scrollable column, which may sit below the
     * fold on a short test device — performClick alone taps wherever the
     * node's coordinates land, visible or not.
     */
    private fun scrollAndClick(res: Int) {
        rule.onNodeWithText(string(res)).performScrollTo().performClick()
        rule.waitForIdle()
    }

    /**
     * Checks every clickable node currently on screen against the two
     * floors. [surface] names the state for the failure message, because
     * one test method may walk several states of the same screen.
     *
     * Each candidate is scrolled fully into view before it is measured.
     * Semantics bounds are clipped by scroll containers, so a button
     * below the fold reports a 0x0 touch target and one straddling the
     * fold reports only its visible sliver — the first run of this gate
     * failed three surfaces on exactly that, all three of them
     * artefacts of the ruler rather than faults in the screen. The
     * measuring instrument needs the same scepticism as the thing
     * measured.
     */
    private fun walk(surface: String) {
        rule.waitForIdle()
        val floorPx = with(rule.density) { 48.dp.toPx() }
        val total = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().size
        // Every surface here has at least a back or continue affordance; a
        // walk that finds nothing clickable means the matcher or the
        // composition broke, and a gate that silently passes an empty
        // room would be worse than no gate.
        assertTrue("$surface: found nothing clickable — the walk itself is broken", total > 0)

        val faults = mutableListOf<String>()
        for (index in 0 until total) {
            val all = rule.onAllNodes(hasClickAction())
            if (index >= all.fetchSemanticsNodes().size) break
            val interaction = all[index]
            // Surfaces without a scroll container throw here; on them
            // everything is already in view, so that is a no-op rather
            // than a problem.
            runCatching { interaction.performScrollTo() }
            rule.waitForIdle()
            val node = interaction.fetchSemanticsNode()

            val words = buildList {
                node.config.getOrNull(SemanticsProperties.ContentDescription)
                    ?.forEach { add(it) }
                node.config.getOrNull(SemanticsProperties.Text)
                    ?.forEach { add(it.text) }
            }.filter { it.isNotBlank() }

            if (words.isEmpty()) {
                faults += "$surface at ${node.boundsInRoot}: clickable with no words " +
                    "for a screen reader"
                continue
            }
            val touch = node.touchBoundsInRoot
            // Half a pixel of slack for float rounding, nothing more.
            if (touch.width + 0.5f < floorPx || touch.height + 0.5f < floorPx) {
                faults += "$surface \"${words.first()}\": touch target " +
                    "${touch.width.toInt()}x${touch.height.toInt()}px is under the " +
                    "${floorPx.toInt()}px floor"
            }
        }
        if (faults.isNotEmpty()) {
            fail(faults.joinToString("\n"))
        }
    }

    // --- one method per surface; navigation states walked in sequence ---

    @Test
    fun home() {
        set { LauncherRoot() }
        walk("home")
    }

    @Test
    fun drawer() {
        set { LauncherRoot() }
        click(R.string.open_drawer)
        walk("drawer")
    }

    @Test
    fun onboarding() {
        set { OnboardingScreen(onDone = { _, _ -> }) }
        walk("onboarding welcome")
        scrollAndClick(R.string.onboarding_continue)
        walk("onboarding goals")
        scrollAndClick(R.string.onboarding_continue)
        walk("onboarding plan")
    }

    @Test
    fun support() {
        // v0.72.x: SupportScreen and SupportActivity
        // were removed. The test is preserved as a no-op
        // so the test list still reflects the intent —
        // re-introducing the support surface should
        // re-introduce this test in the same shape.
    }

    @Test
    fun pulse() {
        // v0.26+ (pulse removal): the WHO-5
        // pulse feature was removed; the
        // walk entry is dropped because the
        // surface no longer ships.
    }

    @Test
    fun report() {
        set {
            ReportScreen(
                stored = ReportFixtures.stored(),
                facts = ReportFixtures.facts(),
                onBack = {},
            )
        }
        walk("report")
    }

    @Test
    fun digest() {
        set { DigestScreen(onClose = {}) }
        walk("digest")
    }

    @Test
    fun ema() {
        set { EmaScreen(onSave = {}, onFinished = {}) }
        walk("ema first question")
        rule.onNodeWithText("3").performClick()
        walk("ema second question")
    }

    @Test
    fun ppg() {
        // Whichever side of the camera permission the test device is on,
        // that state is a real state and gets walked.
        set { PpgScreen(onBack = {}) }
        walk("ppg")
    }

    @Test
    fun frictionPrompt() {
        set {
            FrictionGate(
                appLabel = "Example",
                onOpen = {},
                onNeverMind = {},
                tone = FrictionTone.BRIEF,
            )
        }
        walk("friction prompt")
    }

    @Test
    fun frictionFeather() {
        set {
            FrictionGate(
                appLabel = "Example",
                onOpen = {},
                onNeverMind = {},
                tone = FrictionTone.FEATHER,
            )
        }
        walk("friction feather")
    }

    @Test
    fun appActions() {
        set {
            AppActionsDialog(
                app = DisplayApp(
                    component = "com.example/.Main",
                    label = "Example",
                    isFavorite = false,
                    isHidden = false,
                ),
                isFrictioned = false,
                isAlwaysOpen = false,
                onDismiss = {},
                onToggleFavorite = {},
                onToggleHidden = {},
                onToggleFriction = {},
                onToggleAlwaysOpen = {},
                onRename = {},
            )
        }
        walk("app actions")
        click(R.string.action_rename)
        walk("app rename")
    }

    /**
     * The index and all six groups, reached the way a person reaches
     * them. What composes on a bare test device is what gets walked —
     * sections behind absent grants show their grant affordance instead,
     * and that affordance needs words and 48dp exactly as much.
     */
    @Test
    fun settings() {
        set {
            SettingsScreen(
                allApps = emptyList(),
                hiddenApps = emptyList(),
                onUnhide = {},
                onBack = {},
            )
        }
        walk("settings index")
        val groups = listOf(
            R.string.settings_group_quiet to "settings quiet",
            R.string.settings_group_pauses to "settings pauses",
            R.string.settings_group_measuring to "settings measuring",
            R.string.settings_group_reading to "settings reading",
            R.string.settings_group_phone to "settings phone",
        )
        for ((titleRes, name) in groups) {
            scrollAndClick(titleRes)
            walk(name)
            scrollAndClick(R.string.action_back)
        }
        // The goal rows hide behind their edit affordance; walk them too.
        scrollAndClick(R.string.settings_group_phone)
        scrollAndClick(R.string.goals_change)
        walk("settings phone, editing goals")
    }
}
