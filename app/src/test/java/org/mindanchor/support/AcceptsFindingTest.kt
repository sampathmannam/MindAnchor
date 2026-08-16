@file:Suppress("MaxLineLength")
package org.mindanchor.support

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.testing.TestFileUtil.fileAt

/**
 * v0.28.0: the ACCEPTS self-soothing surface (DBT Linehan 1993 ch. 8).
 *
 * Pins:
 *  1. The activity exists, is wired in the manifest, non-exported.
 *  2. The screen has 7 buttons matching the 7 ACCEPTS letters.
 *  3. Each letter has a body string (the one-line sensory prompt).
 *  4. The state is rememberSaveable (rotation survival).
 *  5. Strings exist for all 7 labels + 7 bodies + title + caption + done.
 *  6. BPD-safe: no directive language.
 */
class AcceptsFindingTest {

    private val manifest: String
        get() = fileAt("app/src/main/AndroidManifest.xml").readText()

    private val screen: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/support/AcceptsScreen.kt",
        ).readText()

    private val strings: String
        get() = fileAt("app/src/main/res/values/strings.xml").readText()

    @Test
    fun `activity is registered in the manifest and exported=false`() {
        assertNotNull(screen)
        assertTrue(
            "AndroidManifest.xml must register .support.AcceptsActivity",
            manifest.contains("android:name=\".support.AcceptsActivity\"") &&
                manifest.contains("android:exported=\"false\""),
        )
    }

    @Test
    fun `screen has 7 ACCEPTS letters in the documented enum`() {
        assertNotNull(screen)
        assertTrue(
            "AcceptsScreen must define an AcceptsLetter enum with 7 values",
            screen.contains("AcceptsLetter") &&
                screen.contains("ACTIVITIES") &&
                screen.contains("CONTRIBUTING") &&
                screen.contains("COMPARISONS") &&
                screen.contains("EMOTIONS") &&
                screen.contains("PUSHAWAY") &&
                screen.contains("THOUGHTS") &&
                screen.contains("SENSATIONS"),
        )
    }

    @Test
    fun `each ACCEPTS letter has a body string key referenced from the screen`() {
        assertNotNull(screen)
        listOf(
            "accepts_activities_body",
            "accepts_contributing_body",
            "accepts_comparisons_body",
            "accepts_emotions_body",
            "accepts_pushaway_body",
            "accepts_thoughts_body",
            "accepts_sensations_body",
        ).forEach { key ->
            assertTrue(
                "AcceptsScreen must reference R.string.$key (body of the ACCEPTS letter)",
                screen.contains("R.string.$key"),
            )
        }
    }

    @Test
    fun `state is rememberSaveable for the open letter`() {
        assertNotNull(screen)
        assertTrue(
            "AcceptsScreen must use rememberSaveable for the open letter state",
            screen.contains("rememberSaveable"),
        )
    }

    @Test
    fun `strings xml defines title, caption, 7 labels, 7 bodies, done`() {
        listOf(
            "accepts_title",
            "accepts_caption",
            "accepts_activities",
            "accepts_contributing",
            "accepts_comparisons",
            "accepts_emotions",
            "accepts_pushaway",
            "accepts_thoughts",
            "accepts_sensations",
            "accepts_activities_body",
            "accepts_contributing_body",
            "accepts_comparisons_body",
            "accepts_emotions_body",
            "accepts_pushaway_body",
            "accepts_thoughts_body",
            "accepts_sensations_body",
            "accepts_done",
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
            "AcceptsScreen must not contain directive phrases (BPD-safe)",
            !screen.contains("you should", ignoreCase = true) &&
                !screen.contains("you must", ignoreCase = true) &&
                !screen.contains("you need to", ignoreCase = true),
        )
    }
}
