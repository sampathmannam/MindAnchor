@file:Suppress("MaxLineLength")
package org.mindanchor.support

import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.27.0: the Linehan (1993) radical acceptance exercise surface.
 *
 * Pins:
 *  1. The activity exists, is wired in the manifest.
 *  2. The screen renders the 4 Linehan sentences from string
 *     resources.
 *  3. The Done button is present.
 *
 * The research basis is DBT Distress Tolerance Module 2
 * (Linehan 1993 ch. 8): "radical acceptance" is the skill for
 * situations that cannot be changed. The four sentences are
 * Linehan's framing: "reality is what it is", "the pain is part
 * of the pain", "accepting reduces suffering", "refusing
 * acceptance increases suffering". The implementation cycles
 * through them at 10 seconds each (40 seconds total).
 */
class RadicalAcceptanceFindingTest {

    private fun fileAt(relative: String): File {
        val candidates = listOf(relative, "../$relative", "../../$relative")
        return candidates.map(::File).firstOrNull { it.isFile }
            ?: error("$relative not found from working directory ${File(".").absolutePath}.")
    }

    private val manifest: String
        get() = fileAt("app/src/main/AndroidManifest.xml").readText()

    private val screen: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/support/RadicalAcceptanceScreen.kt",
        ).readText()

    private val strings: String
        get() = fileAt("app/src/main/res/values/strings.xml").readText()

    @Test
    fun `activity is registered in the manifest and exported=false`() {
        assertNotNull(screen)
        assertTrue(
            "AndroidManifest.xml must register .support.RadicalAcceptanceActivity",
            manifest.contains("android:name=\".support.RadicalAcceptanceActivity\"") &&
                manifest.contains("android:exported=\"false\""),
        )
    }

    @Test
    fun `screen renders the 4 Linehan sentences from string resources`() {
        assertNotNull(screen)
        listOf(
            "radical_acceptance_line_1",
            "radical_acceptance_line_2",
            "radical_acceptance_line_3",
            "radical_acceptance_line_4",
        ).forEach { key ->
            assertTrue(
                "RadicalAcceptanceScreen must call stringResource(R.string.$key) — the 4 Linehan sentences",
                screen.contains("R.string.$key"),
            )
        }
        // "It is what it is" framing — descriptive, not directive.
        assertTrue(
            "RadicalAcceptanceScreen must not contain directive phrases (BPD-safe per audit §3.4)",
            !screen.contains("you should", ignoreCase = true) &&
                !screen.contains("you must", ignoreCase = true) &&
                !screen.contains("you need to", ignoreCase = true),
        )
    }

    @Test
    fun `strings xml defines the 4 lines, the title, the caption, and the done button`() {
        listOf(
            "radical_acceptance_title",
            "radical_acceptance_caption",
            "radical_acceptance_line_1",
            "radical_acceptance_line_2",
            "radical_acceptance_line_3",
            "radical_acceptance_line_4",
            "radical_acceptance_done",
        ).forEach { key ->
            assertTrue(
                "values/strings.xml must define <string name=\"$key\">",
                strings.contains("name=\"$key\""),
            )
        }
    }
}
