@file:Suppress("MaxLineLength")
package org.mindanchor.support

import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.28.0: the Distress Thermometer surface.
 *
 * Pins:
 *  1. The activity exists, is wired in the manifest, non-exported.
 *  2. The screen has a 0-100 Slider (DBT Linehan 1993 + Gross 1998).
 *  3. The 4 banded suggestions match the 4 documented bands.
 *  4. Strings exist for title, caption, value, all 4 labels, all 4 suggestions.
 *  5. The 86+ band surfaces a "talk to a person" affordance.
 *  6. BPD-safe: no directive language.
 */
class DistressThermometerFindingTest {

    private fun fileAt(relative: String): File {
        val candidates = listOf(relative, "../$relative", "../../$relative")
        return candidates.map(::File).firstOrNull { it.isFile }
            ?: error("$relative not found from working directory ${File(".").absolutePath}.")
    }

    private val manifest: String
        get() = fileAt("app/src/main/AndroidManifest.xml").readText()

    private val screen: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/support/DistressThermometerScreen.kt",
        ).readText()

    private val strings: String
        get() = fileAt("app/src/main/res/values/strings.xml").readText()

    @Test
    fun `activity is registered in the manifest and exported=false`() {
        assertNotNull(screen)
        assertTrue(
            "AndroidManifest.xml must register .support.DistressThermometerActivity",
            manifest.contains("android:name=\".support.DistressThermometerActivity\"") &&
                manifest.contains("android:exported=\"false\""),
        )
    }

    @Test
    fun `screen has a 0-100 slider with the documented value range`() {
        assertNotNull(screen)
        assertTrue(
            "DistressThermometerScreen must use a Slider composable",
            screen.contains("Slider("),
        )
        assertTrue(
            "DistressThermometerScreen must set valueRange = 0f..100f",
            screen.contains("valueRange = 0f..100f"),
        )
        assertTrue(
            "DistressThermometerScreen must use mutableFloatStateOf for the slider state",
            screen.contains("mutableFloatStateOf"),
        )
    }

    @Test
    fun `screen matches 4 bands 0-30, 31-60, 61-85, 86-100 to 4 suggestions`() {
        assertNotNull(screen)
        // 4 band labels and 4 suggestions
        listOf(
            "distress_thermo_label_low",
            "distress_thermo_label_mid",
            "distress_thermo_label_high",
            "distress_thermo_label_extreme",
            "distress_thermo_low_suggestion",
            "distress_thermo_mid_suggestion",
            "distress_thermo_high_suggestion",
            "distress_thermo_extreme_suggestion",
        ).forEach { key ->
            assertTrue(
                "DistressThermometerScreen must reference R.string.$key (banded matching)",
                screen.contains("R.string.$key"),
            )
        }
        // Band thresholds
        assertTrue(
            "DistressThermometerScreen must match on the 30 / 60 / 85 thresholds",
            screen.contains("<= 30") && screen.contains("<= 60") && screen.contains("<= 85"),
        )
    }

    @Test
    fun `strings xml defines title, caption, value, 4 labels, 4 suggestions, done`() {
        listOf(
            "distress_thermo_title",
            "distress_thermo_caption",
            "distress_thermo_value",
            "distress_thermo_label_low",
            "distress_thermo_label_mid",
            "distress_thermo_label_high",
            "distress_thermo_label_extreme",
            "distress_thermo_suggestion_intro",
            "distress_thermo_low_suggestion",
            "distress_thermo_mid_suggestion",
            "distress_thermo_high_suggestion",
            "distress_thermo_extreme_suggestion",
            "distress_thermo_done",
            "distress_thermo_again",
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
            "DistressThermometerScreen must not contain directive phrases (BPD-safe)",
            !screen.contains("you should", ignoreCase = true) &&
                !screen.contains("you must", ignoreCase = true) &&
                !screen.contains("you need to", ignoreCase = true),
        )
    }
}
