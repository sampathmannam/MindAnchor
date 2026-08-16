@file:Suppress("MaxLineLength")
package org.mindanchor.launcher

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

import org.mindanchor.testing.TestFileUtil.fileAt
/**
 * v0.26.4: the BPD "Right now" entry points on the home surface.
 *
 * The §3.4 spec chain capture, IFS picker, and data export are
 * built (in v0.26.1 and v0.26.3) but unreachable without a
 * home-surface wire. v0.26.4 wires the three affordances into a
 * single "Right now" section on the home, with one tap from the
 * home to the activity.
 *
 * The FindingTest pins:
 *   1. The home surface renders the "Right now" section header
 *      and the three button labels.
 *   2. Each button dispatches an Intent to the corresponding
 *      activity class.
 *   3. The three activities are non-exported (only this same
 *      app launches them — the runCatching + same-app
 *      startActivity pattern is the security model).
 *
 * A future refactor that drops the entry points, re-orders the
 * section, or changes the Intent pattern flips one of the
 * assertions red.
 */
class BpdEntryPointsFindingTest {

    private val homeScreen: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/launcher/HomeScreen.kt",
        ).readText()

    private val strings: String
        get() = fileAt(
            "app/src/main/res/values/strings.xml",
        ).readText()

    @Test
    fun `HomeSurface renders the Right now section header and caption`() {
        assertTrue(
            "HomeScreen must call stringResource(R.string.right_now_section) — the " +
                "BPD entry-point section header",
            homeScreen.contains("stringResource(R.string.right_now_section)"),
        )
        assertTrue(
            "HomeScreen must call stringResource(R.string.right_now_caption) — the " +
                "one-line caption below the header",
            homeScreen.contains("stringResource(R.string.right_now_caption)"),
        )
    }

    @Test
    fun `HomeSurface renders the three BPD button labels`() {
        // v0.26.4: the three button labels — "What just happened?",
        // "Which part is loud?", and "Export for my therapist".
        assertTrue(
            "HomeScreen must call stringResource(R.string.right_now_chain) for the " +
                "chain-capture button",
            homeScreen.contains("stringResource(R.string.right_now_chain)"),
        )
        assertTrue(
            "HomeScreen must call stringResource(R.string.right_now_ifs) for the " +
                "IFS-picker button",
            homeScreen.contains("stringResource(R.string.right_now_ifs)"),
        )
        assertTrue(
            "HomeScreen must call stringResource(R.string.right_now_export) for the " +
                "data-export button",
            homeScreen.contains("stringResource(R.string.right_now_export)"),
        )
    }

    @Test
    fun `HomeSurface dispatches Intents to the three BPD activities`() {
        // v0.26.4: each button starts the corresponding activity.
        // The class names are .chain.ChainCaptureActivity,
        // .ifs.IfsPickerActivity, and .export.ExportActivity.
        // The runCatching pattern is the same as onOpenNotes +
        // onOpenCheckInHistory (defensive against a misconfigured
        // manifest). The Intent constructor spans multiple
        // lines (val intent = android.content.Intent(\n context, ...))
        // so the regex tolerates whitespace + newlines.
        val chainPattern = Regex(
            """runCatching\s*\{[\s\S]*?Intent\([\s\S]*?context[\s\S]*?org\.mindanchor\.chain\.ChainCaptureActivity::class\.java[\s\S]*?\)""",
        )
        assertTrue(
            "HomeScreen must dispatch an Intent to org.mindanchor.chain.ChainCaptureActivity " +
                "from the 'What just happened?' button (runCatching + startActivity)",
            chainPattern.containsMatchIn(homeScreen),
        )
        val ifsPattern = Regex(
            """runCatching\s*\{[\s\S]*?Intent\([\s\S]*?context[\s\S]*?org\.mindanchor\.ifs\.IfsPickerActivity::class\.java[\s\S]*?\)""",
        )
        assertTrue(
            "HomeScreen must dispatch an Intent to org.mindanchor.ifs.IfsPickerActivity " +
                "from the 'Which part is loud?' button (runCatching + startActivity)",
            ifsPattern.containsMatchIn(homeScreen),
        )
        val exportPattern = Regex(
            """runCatching\s*\{[\s\S]*?Intent\([\s\S]*?context[\s\S]*?org\.mindanchor\.export\.ExportActivity::class\.java[\s\S]*?\)""",
        )
        assertTrue(
            "HomeScreen must dispatch an Intent to org.mindanchor.export.ExportActivity " +
                "from the 'Export for my therapist' button (runCatching + startActivity)",
            exportPattern.containsMatchIn(homeScreen),
        )
    }

    @Test
    fun `strings xml defines the five Right now keys`() {
        // The five keys are: right_now_section, right_now_caption,
        // right_now_chain, right_now_ifs, right_now_export.
        listOf(
            "right_now_section",
            "right_now_caption",
            "right_now_chain",
            "right_now_ifs",
            "right_now_export",
        ).forEach { key ->
            assertTrue(
                "values/strings.xml must define <string name=\"$key\">",
                strings.contains("name=\"$key\""),
            )
        }
    }

    @Test
    fun `values-ta strings xml has Tamil Right now keys (placeholder English OK)`() {
        // v0.26.4: the Tamil file must declare the five
        // right_now_* keys. The values may still be English
        // placeholders (Stream 4 left the file as English copy
        // with the letter Tamil strings from Stream 3); a
        // translator is a v0.26.5+ follow-up.
        val ta = fileAt(
            "app/src/main/res/values-ta/strings.xml",
        ).readText()
        assertNotNull(ta)
        listOf(
            "right_now_section",
            "right_now_caption",
            "right_now_chain",
            "right_now_ifs",
            "right_now_export",
        ).forEach { key ->
            assertTrue(
                "values-ta/strings.xml must declare <string name=\"$key\"> " +
                    "(value may be English placeholder pending translation)",
                ta.contains("name=\"$key\""),
            )
        }
    }
}
