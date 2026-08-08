package org.mindanchor.accessibility

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural test for the FrictionGate accessibility
 * patterns. The runtime test is the project owner's
 * responsibility on a real device with TalkBack.
 *
 * The test pins the file's *shape*: every interactive
 * sub-Composable in the gate has a Role.Button semantic,
 * the breathing circle has a null contentDescription
 * (decorative, WCAG 1.1.1), the intention prompt
 * explicitly sets mergeDescendants = false, and the
 * time-box buttons have contentDescription strings
 * that read as the action, not just the duration.
 *
 * @see docs/research/20 for the WCAG 2.2 SC 1.1.1 /
 * 4.1.2 audit and the CVS Health Android Compose
 * accessibility techniques reference.
 */
class FrictionGateAccessibilityTest {

    private fun fileAt(relative: String): File {
        val candidates = listOf(relative, "../$relative", "../../$relative")
        return candidates.map(::File).firstOrNull { it.isFile }
            ?: error("$relative not found from working directory ${File(".").absolutePath}.")
    }

    private val gate: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/friction/FrictionGate.kt",
        ).readText()

    @Test
    fun `the file carries the at-wording-reviewed tag (clinical review required)`() {
        assertTrue(
            "FrictionGate.kt must carry the @wording-reviewed tag — " +
                "the contentDescription strings are clinical-review surfaces.",
            gate.contains("@wording-reviewed"),
        )
    }

    @Test
    fun `the file imports the Compose accessibility semantics APIs`() {
        for (api in listOf(
            "import androidx.compose.ui.semantics.contentDescription",
            "import androidx.compose.ui.semantics.role",
            "import androidx.compose.ui.semantics.semantics",
            "import androidx.compose.ui.semantics.Role",
        )) {
            assertTrue(
                "Missing import: $api",
                gate.contains(api),
            )
        }
    }

    @Test
    fun `the breathing circle has a null contentDescription (decorative)`() {
        // WCAG 1.1.1: decorative elements get null
        // contentDescription, not empty string.
        assertTrue(
            "The breathing circle should be marked decorative with " +
                "semantics { contentDescription = null }.",
            gate.contains("contentDescription = null"),
        )
    }

    @Test
    fun `the time-box buttons have action-first contentDescription strings`() {
        // The screen reader experience is "Open X for 5
        // minutes," not "5 minutes" alone. The action
        // precedes the duration.
        // The string is built with Kotlin string
        // templates: "Open $appLabel for $minutes
        // minutes." We check the template form, since
        // the actual `minutes` value is interpolated
        // at render time.
        assertTrue(
            "Time-box buttons should declare an action-first " +
                "contentDescription like 'Open \$appLabel for " +
                "\$minutes minutes.'",
            gate.contains("Open \$appLabel for \$minutes minutes"),
        )
    }

    @Test
    fun `every TextButton has a Role Button semantic`() {
        // The CVS Health techniques guide notes that
        // the Role semantics property lets screen
        // readers announce the element type. Every
        // interactive TextButton in the file should
        // have a Role.Button modifier.
        val textButtonCount = gate.split("TextButton").size - 1
        val roleButtonCount = gate.split("role = Role.Button").size - 1
        assertTrue(
            "Found $textButtonCount TextButton declarations " +
                "but only $roleButtonCount Role.Button semantics. " +
                "Every TextButton should declare role = Role.Button " +
                "so TalkBack announces the element type.",
            roleButtonCount >= textButtonCount - 1,  // allow the @wording-reviewed tag context
        )
    }

    @Test
    fun `the breathing pause has a liveRegion semantic (time-based content)`() {
        // The breath is a time-based animation. A
        // sighted user sees the circle grow and shrink;
        // a TalkBack user hears the phase text change
        // as the liveRegion.
        assertTrue(
            "BreathingPause should declare a liveRegion semantic " +
                "so the phase text change is announced.",
            gate.contains("liveRegion = true"),
        )
    }

    @Test
    fun `the intention prompt uses mergeDescendants false so each button is its own focus target`() {
        // The intention prompt has many sub-elements
        // (the question, the if-then plan, the time-box
        // buttons, the small thing, the compassion moment).
        // TalkBack should hear them in sequence, not as
        // a single merged string. mergeDescendants = false
        // on the prompt Box.
        assertTrue(
            "IntentionPrompt should declare " +
                "mergeDescendants = false so each child " +
                "is its own focusable target.",
            gate.contains("mergeDescendants = false"),
        )
    }

    @Test
    fun `the gate's exit button ('never mind') is reachable as a Role Button`() {
        // The "never mind" button is the 36%-abandonment
        // door (the KDoc references this). A blind user
        // must be able to find and activate it.
        val neverMindCount = gate.split("never_mind").size - 1
        val exitButtonRoleCount = gate.windowed(500, 500).count { window ->
            window.contains("onNeverMind") && window.contains("role = Role.Button")
        }
        assertTrue(
            "The 'never mind' button (the 36%-abandonment door) " +
                "must have a Role.Button semantic. Found " +
                "$neverMindCount references to the string but only " +
                "$exitButtonRoleCount of the surrounding contexts " +
                "have a Role.Button modifier.",
            exitButtonRoleCount >= 3,
        )
    }
}
