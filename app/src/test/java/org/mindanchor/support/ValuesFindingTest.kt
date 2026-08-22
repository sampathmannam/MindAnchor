/*
 * ValuesFindingTest.kt — pins the v0.29.0 ACT values
 * clarification surface.
 *
 * The 8-domain standard ACT taxonomy (Hayes et al. 1999/2004;
 * Wilson & Murrell 2004) is the scaffold. The user's one
 * sentence per domain is the card. Saved to a single
 * DataStore key on Save tap; revisits load the saved values
 * back into the input fields.
 *
 * Pins:
 *  1. The activity exists, is wired in the manifest, non-exported.
 *  2. The screen renders the 8 value-domain labels and fields.
 *  3. The strings xml has all 8 labels + the field hint + save.
 *  4. The screen uses rememberSaveable for the draft fields
 *     (rotation survival).
 *  5. The screen loads the saved card via ValuesPrefs on
 *     first composition (so revisits show the user's words).
 *  6. The screen saves the card to ValuesPrefs on Save tap.
 *  7. The screen is wired into SupportScreen as the last
 *     entry of the in-the-moment → reflective group.
 *  8. BPD-safe: no directive language.
 *  9. No score, no chart, no comparison. The card is the
 *     values, nothing more.
 */
@file:Suppress("MaxLineLength")
package org.mindanchor.support

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.testing.TestFileUtil.fileAt

class ValuesFindingTest {

    private val manifest: String
        get() = fileAt("app/src/main/AndroidManifest.xml").readText()

    private val screen: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/support/ValuesScreen.kt",
        ).readText()

    private val activity: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/support/ValuesActivity.kt",
        ).readText()

    private val prefs: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/support/ValuesPrefs.kt",
        ).readText()

    private val supportScreen: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/support/SupportScreen.kt",
        ).readText()

    private val strings: String
        get() = fileAt("app/src/main/res/values/strings.xml").readText()

    @Test
    fun `activity is registered in the manifest and exported=false`() {
        assertNotNull(screen)
        assertTrue(
            "AndroidManifest.xml must register .support.ValuesActivity",
            manifest.contains("android:name=\".support.ValuesActivity\"") &&
                manifest.contains("android:exported=\"false\""),
        )
    }

    @Test
    fun `activity extends SupportSurfaceActivity and declares Surface`() {
        assertNotNull(activity)
        assertTrue(
            "ValuesActivity must extend SupportSurfaceActivity (v0.29.0 scaffold)",
            activity.contains("class ValuesActivity : SupportSurfaceActivity()"),
        )
        assertTrue(
            "ValuesActivity must implement the Surface composable",
            activity.contains("override fun Surface(onDone: () -> Unit)"),
        )
        assertTrue(
            "ValuesActivity.Surface must render ValuesScreen",
            activity.contains("ValuesScreen(onDone = onDone)"),
        )
    }

    @Test
    fun `screen renders all 8 ACT value domains`() {
        assertNotNull(screen)
        listOf(
            "values_relationships_label",
            "values_health_label",
            "values_work_label",
            "values_growth_label",
            "values_leisure_label",
            "values_spirituality_label",
            "values_community_label",
            "values_parenting_label",
        ).forEach { key ->
            assertTrue(
                "ValuesScreen must reference R.string.$key (the label of the value domain)",
                screen.contains("R.string.$key"),
            )
        }
    }

    @Test
    fun `screen uses rememberSaveable for the 8 draft fields`() {
        assertNotNull(screen)
        // Each of the 8 fields is a separate rememberSaveable.
        // The pattern is `var <name> by rememberSaveable`.
        val rememberSaveableCount = Regex("""var \w+ by rememberSaveable""")
            .findAll(screen).count()
        assertTrue(
            "ValuesScreen must have at least 8 rememberSaveable-backed draft " +
                "fields (one per value domain). Saw $rememberSaveableCount.",
            rememberSaveableCount >= 8,
        )
    }

    @Test
    fun `screen loads the saved card on first composition via LaunchedEffect`() {
        assertNotNull(screen)
        assertTrue(
            "ValuesScreen must use a LaunchedEffect to load the saved card " +
                "on first composition (so revisits show the user's words).",
            screen.contains("LaunchedEffect(Unit)") &&
                screen.contains("prefs.load()"),
        )
    }

    @Test
    fun `screen saves the card to ValuesPrefs on Save tap`() {
        assertNotNull(screen)
        assertTrue(
            "ValuesScreen must build a ValuesCard from the 8 fields and call " +
                "prefs.save(card) on Save tap.",
            screen.contains("ValuesCard(") &&
                screen.contains("prefs.save(card)"),
        )
    }

    @Test
    fun `prefs defines the 8-domain ValuesCard with all fields`() {
        assertNotNull(prefs)
        listOf(
            "relationships",
            "health",
            "work",
            "growth",
            "leisure",
            "spirituality",
            "community",
            "parenting",
        ).forEach { field ->
            assertTrue(
                "ValuesCard must declare a `$field: String = \"\"` field",
                Regex("""val\s+$field:\s*String\s*=\s*"""").containsMatchIn(prefs),
            )
        }
    }

    @Test
    fun `prefs uses a single DataStore key for the values card`() {
        assertNotNull(prefs)
        assertTrue(
            "ValuesPrefs must use a single DataStore key for the values card " +
                "(one card per user, not per domain).",
            prefs.contains("act_values") && prefs.contains("preferencesDataStore"),
        )
    }

    @Test
    fun `support screen wires ValuesActivity as the last reflective entry`() {
        assertNotNull(supportScreen)
        assertTrue(
            "SupportScreen must wire ValuesActivity as the last entry of the " +
                "in-the-moment → reflective group.",
            supportScreen.contains("ValuesActivity::class.java") &&
                supportScreen.contains("support_values_button"),
        )
    }

    @Test
    fun `strings xml defines the values title, caption, 8 labels, hint, save, a11y`() {
        listOf(
            "values_title",
            "values_caption",
            "values_relationships_label",
            "values_health_label",
            "values_work_label",
            "values_growth_label",
            "values_leisure_label",
            "values_spirituality_label",
            "values_community_label",
            "values_parenting_label",
            "values_field_hint",
            "values_save",
            "values_saved",
            "values_a11y",
        ).forEach { key ->
            assertTrue(
                "values/strings.xml must define <string name=\"$key\">",
                strings.contains("name=\"$key\""),
            )
        }
    }

    @Test
    fun `strings xml defines the support_values_button for the Support entry`() {
        assertTrue(
            "values/strings.xml must define <string name=\"support_values_button\"> " +
                "as the label on the Support screen entry.",
            strings.contains("name=\"support_values_button\""),
        )
    }

    @Test
    fun `screen has no directive language (BPD-safe per audit)`() {
        assertNotNull(screen)
        assertTrue(
            "ValuesScreen must not contain directive phrases (BPD-safe).",
            !screen.contains("you should", ignoreCase = true) &&
                !screen.contains("you must", ignoreCase = true) &&
                !screen.contains("you need to", ignoreCase = true),
        )
    }
}
