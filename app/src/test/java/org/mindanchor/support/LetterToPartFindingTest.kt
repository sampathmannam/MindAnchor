@file:Suppress("MaxLineLength")
package org.mindanchor.support

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

import org.mindanchor.testing.TestFileUtil.fileAt
/**
 * v0.28.0: the IFS letter-to-a-part surface (Schwartz 1995).
 *
 * Pins:
 *  1. The activity exists, is wired in the manifest, non-exported.
 *  2. The screen has 3 sub-screens: PICK / TO / FROM (state enum).
 *  3. The PICK step has 5 named parts + "other" (6 total).
 *  4. The TO and FROM steps are optional free-text fields.
 *  5. State is rememberSaveable (rotation survival).
 *  6. Strings exist for all 6 part labels + title + caption + a11y.
 *  7. BPD-safe: no directive language.
 */
class LetterToPartFindingTest {

    private val manifest: String
        get() = fileAt("app/src/main/AndroidManifest.xml").readText()

    private val screen: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/support/LetterToPartScreen.kt",
        ).readText()

    private val strings: String
        get() = fileAt("app/src/main/res/values/strings.xml").readText()

    @Test
    fun `activity is registered in the manifest and exported=false`() {
        assertNotNull(screen)
        assertTrue(
            "AndroidManifest.xml must register .support.LetterToPartActivity",
            manifest.contains("android:name=\".support.LetterToPartActivity\"") &&
                manifest.contains("android:exported=\"false\""),
        )
    }

    @Test
    fun `screen has 3 sub-screens via PICK, TO, FROM state enum`() {
        assertNotNull(screen)
        assertTrue(
            "LetterToPartScreen must define a LetterScreen enum with PICK, TO, FROM",
            screen.contains("LetterScreen") &&
                screen.contains("PICK") &&
                screen.contains("TO") &&
                screen.contains("FROM"),
        )
    }

    @Test
    fun `PICK step has 5 named parts plus other (6 total)`() {
        assertNotNull(screen)
        listOf(
            "letter_to_part_pick_angry",
            "letter_to_part_pick_scared",
            "letter_to_part_pick_dislike",
            "letter_to_part_pick_critic",
            "letter_to_part_pick_protector",
            "letter_to_part_pick_other",
        ).forEach { key ->
            assertTrue(
                "LetterToPartScreen must reference R.string.$key (named IFS part)",
                screen.contains("R.string.$key"),
            )
        }
    }

    @Test
    fun `TO and FROM steps have optional free-text fields`() {
        assertNotNull(screen)
        assertTrue(
            "LetterToPartScreen must have a TO label and a TO hint",
            screen.contains("R.string.letter_to_part_to_label") &&
                screen.contains("R.string.letter_to_part_to_hint"),
        )
        assertTrue(
            "LetterToPartScreen must have a FROM label and a FROM hint",
            screen.contains("R.string.letter_to_part_from_label") &&
                screen.contains("R.string.letter_to_part_from_hint"),
        )
        assertTrue(
            "LetterToPartScreen must use OutlinedTextField for the letter drafts",
            screen.contains("OutlinedTextField"),
        )
    }

    @Test
    fun `state is rememberSaveable for rotation survival`() {
        assertNotNull(screen)
        assertTrue(
            "LetterToPartScreen must use rememberSaveable for the screen state and the drafts",
            screen.contains("rememberSaveable"),
        )
    }

    @Test
    fun `strings xml defines title, caption, pick label, 6 part labels, tofrom labels, hints, done, a11y`() {
        listOf(
            "letter_to_part_title",
            "letter_to_part_caption",
            "letter_to_part_pick_label",
            "letter_to_part_pick_angry",
            "letter_to_part_pick_scared",
            "letter_to_part_pick_dislike",
            "letter_to_part_pick_critic",
            "letter_to_part_pick_protector",
            "letter_to_part_pick_other",
            "letter_to_part_to_label",
            "letter_to_part_to_hint",
            "letter_to_part_from_label",
            "letter_to_part_from_hint",
            "letter_to_part_switch",
            "letter_to_part_done",
            "letter_to_part_a11y",
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
            "LetterToPartScreen must not contain directive phrases (BPD-safe)",
            !screen.contains("you should", ignoreCase = true) &&
                !screen.contains("you must", ignoreCase = true) &&
                !screen.contains("you need to", ignoreCase = true),
        )
    }
}
