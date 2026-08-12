package org.mindanchor.report

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Finding test for v0.25.3-WP-D: the report screen's
 * letter-reader-mode header (A- / A / A+ segmented control).
 *
 * Background: the letter feature shipped in v0.25.2 includes a
 * 3-state reading-size control. The report screen renders
 * long-form copy too, but until WP-D it always used the platform
 * default — a person who picked "A+" for the letter would see a
 * normal-sized report. WP-D reuses [org.mindanchor.reader.ReaderPrefs]
 * (the same DataStore) so the choice carries between surfaces.
 *
 * What this test pins:
 *  1. The public `ReportScreen` overload reads `ReaderPrefs.size`
 *     and calls `setSize` on the writer scope — both ends of the
 *     wire-through are in place.
 *  2. The testable `ReportScreen` overload accepts `size` and
 *     `onSetSize` parameters with the same A- / A / A+ segmented
 *     control that [org.mindanchor.letters.LetterReader] uses.
 *  3. The control iterates all three sizes and uses the locale-safe
 *     A- / A / A+ labels (no string resources).
 */
class ReportScreenReadingSizeFindingTest {

    private fun fileAt(relative: String): File {
        val candidates = listOf(relative, "../$relative", "../../$relative")
        return candidates.map(::File).firstOrNull { it.isFile }
            ?: error("$relative not found from working directory ${File(".").absolutePath}.")
    }

    private val screen: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/report/ReportScreen.kt",
        ).readText()

    @Test
    fun `the public ReportScreen reads ReaderPrefs and wires setSize`() {
        // The public overload must read from the same DataStore the
        // letter feature uses, and must call setSize when the user
        // picks a new size. Otherwise the report reverts to the
        // default every launch and the letter-size choice is one-way.
        assertTrue(
            "The public ReportScreen overload must read ReaderPrefs.size " +
                "and pass onSetSize to the testable overload. " +
                "Otherwise the report is a static default and the " +
                "v0.25.2 reader-mode choice is one-way.",
            screen.contains("ReaderPrefs(") &&
                screen.contains("readerPrefs.size.collectAsState") &&
                screen.contains("onSetSize = { newSize ->") &&
                screen.contains("readerPrefs.setSize(newSize)"),
        )
    }

    @Test
    fun `the testable ReportScreen accepts size and onSetSize parameters`() {
        // The testable overload (the one without the Context read) must
        // accept a `size: ReadingSize` and an `onSetSize: (ReadingSize)
        // -> Unit` so a screenshot test or another Composable can pass
        // the values directly. The defaults make a caller that omits
        // both still render correctly (the existing screenshot harness
        // does not need to be updated).
        assertTrue(
            "The testable ReportScreen overload must accept `size: ReadingSize` " +
                "and `onSetSize: (ReadingSize) -> Unit` parameters (both " +
                "with defaults so older callers / screenshot tests still " +
                "compile).",
            screen.contains("size: ReadingSize = ReadingSize.MEDIUM") &&
                screen.contains("onSetSize: (ReadingSize) -> Unit = {}"),
        )
    }

    @Test
    fun `the report header uses SingleChoiceSegmentedButtonRow with the A- A A+ labels`() {
        // The control must be the same shape as the letter reader's:
        // SingleChoiceSegmentedButtonRow + SegmentedButton, iterating
        // the three sizes, with the locale-safe A- / A / A+ labels.
        // A future maintainer reading just this file should be able to
        // tell that the size toggle is intentional, not a stray widget.
        assertTrue(
            "ReportScreen must use SingleChoiceSegmentedButtonRow for " +
                "the size toggle (the same control the letter reader " +
                "uses, not a fresh one-off).",
            screen.contains("SingleChoiceSegmentedButtonRow"),
        )
        assertTrue(
            "The segmented button must iterate all three ReadingSize " +
                "values (SMALL, MEDIUM, LARGE) so the picker is " +
                "exhaustive.",
            screen.contains("ReadingSize.SMALL") &&
                screen.contains("ReadingSize.MEDIUM") &&
                screen.contains("ReadingSize.LARGE"),
        )
        assertTrue(
            "The segmented button labels must be A- / A / A+ " +
                "(locale-safe, no string resources, RTL-safe).",
            screen.contains("\"A-\"") &&
                screen.contains("\"A\"") &&
                screen.contains("\"A+\""),
        )
    }
}
