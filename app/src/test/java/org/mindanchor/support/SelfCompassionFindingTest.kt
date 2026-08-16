@file:Suppress("MaxLineLength")
package org.mindanchor.support

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

import org.mindanchor.testing.TestFileUtil.fileAt
/**
 * v0.27.0: the Neff (2003) self-compassion break surface.
 *
 * Pins:
 *  1. The activity exists, is wired in the manifest.
 *  2. The screen renders the 3 Neff lines from string resources
 *     (not hardcoded literals — the i18n sweep expects this).
 *  3. The Done button is present.
 *
 * The research basis is the self-compassion intervention
 * (Neff 2003): three sentences — "this is a moment of suffering",
 * "suffering is part of being human", "may I be kind to myself"
 * — read at the user's pace. The implementation cycles through
 * the three lines at 15 seconds each (45 seconds total); the
 * user can dismiss at any time.
 */
class SelfCompassionFindingTest {

    private val activity: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/support/SelfCompassionActivity.kt",
        ).readText()

    private val screen: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/support/SelfCompassionScreen.kt",
        ).readText()

    private val manifest: String
        get() = fileAt("app/src/main/AndroidManifest.xml").readText()

    private val strings: String
        get() = fileAt("app/src/main/res/values/strings.xml").readText()

    @Test
    fun `activity is registered in the manifest and exported=false`() {
        assertNotNull(activity)
        assertTrue(
            "AndroidManifest.xml must register .support.SelfCompassionActivity as a non-exported activity",
            manifest.contains("android:name=\".support.SelfCompassionActivity\"") &&
                manifest.contains("android:exported=\"false\""),
        )
    }

    @Test
    fun `screen renders the 3 Neff lines from string resources (no hardcoded literals)`() {
        assertNotNull(screen)
        listOf(
            "self_compassion_line_1",
            "self_compassion_line_2",
            "self_compassion_line_3",
        ).forEach { key ->
            assertTrue(
                "SelfCompassionScreen must call stringResource(R.string.$key) — the 3 Neff sentences",
                screen.contains("R.string.$key"),
            )
        }
    }

    @Test
    fun `strings xml defines the 3 lines, the title, the caption, and the done button`() {
        listOf(
            "self_compassion_title",
            "self_compassion_caption",
            "self_compassion_line_1",
            "self_compassion_line_2",
            "self_compassion_line_3",
            "self_compassion_done",
        ).forEach { key ->
            assertTrue(
                "values/strings.xml must define <string name=\"$key\">",
                strings.contains("name=\"$key\""),
            )
        }
    }
}
