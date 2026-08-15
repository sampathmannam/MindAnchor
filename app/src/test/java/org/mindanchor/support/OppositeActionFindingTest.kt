@file:Suppress("MaxLineLength")
package org.mindanchor.support

import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.28.0: the Linehan (1993, ch. 8) Opposite Action skill.
 *
 * Pins:
 *  1. The activity exists, is wired in the manifest, non-exported.
 *  2. The screen renders the 4 steps with optional free-text fields.
 *  3. The strings xml has all 4 step labels + hints + done + a11y.
 *  4. BPD-safe: no directive language.
 *
 * The research basis is DBT Distress Tolerance Module 1
 * (Linehan 1993, ch. 8): when the emotion does not fit the
 * facts, the skill is to do the opposite of the action urge.
 * For BPD the all-or-nothing pattern follows the emotion; the
 * skill is to do the opposite. Implementation is a single-screen
 * Composable, no save, no score, optional free text per step.
 */
class OppositeActionFindingTest {

    private fun fileAt(relative: String): File {
        val candidates = listOf(relative, "../$relative", "../../$relative")
        return candidates.map(::File).firstOrNull { it.isFile }
            ?: error("$relative not found from working directory ${File(".").absolutePath}.")
    }

    private val manifest: String
        get() = fileAt("app/src/main/AndroidManifest.xml").readText()

    private val screen: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/support/OppositeActionScreen.kt",
        ).readText()

    private val activity: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/support/OppositeActionActivity.kt",
        ).readText()

    private val strings: String
        get() = fileAt("app/src/main/res/values/strings.xml").readText()

    @Test
    fun `activity is registered in the manifest and exported=false`() {
        assertNotNull(activity)
        assertTrue(
            "AndroidManifest.xml must register .support.OppositeActionActivity",
            manifest.contains("android:name=\".support.OppositeActionActivity\"") &&
                manifest.contains("android:exported=\"false\""),
        )
    }

    @Test
    fun `screen renders the 4 opposite-action steps with optional free-text fields`() {
        assertNotNull(screen)
        // 4 step labels are referenced from the screen.
        listOf(
            "opposite_action_step_1_label",
            "opposite_action_step_2_label",
            "opposite_action_step_3_label",
            "opposite_action_step_4_label",
        ).forEach { key ->
            assertTrue(
                "OppositeActionScreen must call stringResource(R.string.$key) — the 4 step labels",
                screen.contains("R.string.$key"),
            )
        }
        // Optional free text per step — rememberSaveable String state.
        assertTrue(
            "OppositeActionScreen must use rememberSaveable for the 4 step drafts (rotation survival)",
            screen.contains("rememberSaveable"),
        )
        // OutlinedTextField for the optional text per step.
        assertTrue(
            "OppositeActionScreen must use OutlinedTextField for the optional free text per step",
            screen.contains("OutlinedTextField"),
        )
    }

    @Test
    fun `strings xml defines all 4 step labels, hints, title, caption, done, and a11y`() {
        listOf(
            "opposite_action_title",
            "opposite_action_caption",
            "opposite_action_step_1_label",
            "opposite_action_step_1_hint",
            "opposite_action_step_2_label",
            "opposite_action_step_2_hint",
            "opposite_action_step_3_label",
            "opposite_action_step_3_hint",
            "opposite_action_step_4_label",
            "opposite_action_step_4_hint",
            "opposite_action_done",
            "opposite_action_a11y",
        ).forEach { key ->
            assertTrue(
                "values/strings.xml must define <string name=\"$key\">",
                strings.contains("name=\"$key\""),
            )
        }
    }

    @Test
    fun `screen has no directive language (BPD-safe per audit)`() {
        assertNotNull(screen)
        assertTrue(
            "OppositeActionScreen must not contain directive phrases (BPD-safe)",
            !screen.contains("you should", ignoreCase = true) &&
                !screen.contains("you must", ignoreCase = true) &&
                !screen.contains("you need to", ignoreCase = true),
        )
    }
}
