package org.mindanchor.letters

import org.junit.Assert.assertTrue
import org.junit.Test

class LetterReaderSizeToggleFindingTest {

    @Test fun `LetterReader header uses SingleChoiceSegmentedButtonRow`() {
        val src = java.io.File(
            "../app/src/main/java/org/mindanchor/letters/LetterScreen.kt"
        ).readText()
        assertTrue(
            "LetterReader header must use SingleChoiceSegmentedButtonRow",
            src.contains("SingleChoiceSegmentedButtonRow")
        )
    }

    @Test fun `SegmentedButton has 3 options (SMALL, MEDIUM, LARGE)`() {
        val src = java.io.File(
            "../app/src/main/java/org/mindanchor/letters/LetterScreen.kt"
        ).readText()
        assertTrue(
            "SegmentedButton must reference all three ReadingSize values",
            src.contains("ReadingSize.SMALL") && src.contains("ReadingSize.MEDIUM") && src.contains("ReadingSize.LARGE")
        )
    }

    @Test fun `SegmentedButton labels are A- A A+ (locale-safe)`() {
        val src = java.io.File(
            "../app/src/main/java/org/mindanchor/letters/LetterScreen.kt"
        ).readText()
        assertTrue(
            "SegmentedButton labels must be A- / A / A+",
            src.contains("\"A-\"") && src.contains("\"A\"") && src.contains("\"A+\"")
        )
    }

    @Test fun `SegmentedButton onClick calls onSetSize with the chosen value`() {
        val src = java.io.File(
            "../app/src/main/java/org/mindanchor/letters/LetterScreen.kt"
        ).readText()
        assertTrue(
            "SegmentedButton onClick must call onSetSize(s)",
            src.contains("onClick = { onSetSize(s) }")
        )
    }

    @Test fun `SegmentedButton is inside the scrollable Column (not pinned to bottom)`() {
        val src = java.io.File(
            "../app/src/main/java/org/mindanchor/letters/LetterScreen.kt"
        ).readText()
        // The size control must be inside the Column that has
        // verticalScroll(...). Pin the order: the Column opens
        // first, the Row (with the size control) is inside the
        // Column, the Column has verticalScroll.
        //
        // The test searches for the *call site* (with the opening
        // brace), not the import line — `indexOf("SingleChoiceSegmentedButtonRow")`
        // would match the import statement at the top of the file
        // (which is above every Column) and fail the assertion.
        val column = src.indexOf("verticalScroll(")
        val segBtn = src.indexOf("SingleChoiceSegmentedButtonRow {")
        val columnStart = src.lastIndexOf("Column(", column)
        assertTrue(
            "SegmentedButton call site must be inside a Column with verticalScroll",
            segBtn > columnStart && segBtn > column
        )
    }
}
