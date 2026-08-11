package org.mindanchor.letters

import org.junit.Assert.assertTrue
import org.junit.Test

class LetterLargeSizeLayoutFindingTest {

    @Test fun `LetterReader body has no maxLines cap (it wraps at Large)`() {
        val src = java.io.File(
            "../app/src/main/java/org/mindanchor/letters/LetterScreen.kt"
        ).readText()
        // The body Text in LetterReader is multi-line. Find the
        // "text = letter.body," line (the parameter line) and assert
        // the next ~500 chars (the modifier chain through to the
        // closing `)`) have no maxLines.
        val bodyText = src.indexOf("text = letter.body,")
        assertTrue("LetterReader body text call must exist", bodyText >= 0)
        val after = src.substring(bodyText, bodyText + 500)
        assertTrue(
            "LetterReader body must NOT have a maxLines cap",
            !after.contains("maxLines =")
        )
    }

    @Test fun `LetterReader body uses default wrap (no softWrap false at Large)`() {
        val src = java.io.File(
            "../app/src/main/java/org/mindanchor/letters/LetterScreen.kt"
        ).readText()
        val bodyText = src.indexOf("text = letter.body,")
        val after = src.substring(bodyText, bodyText + 500)
        assertTrue(
            "LetterReader body must not disable wrapping (no softWrap = false)",
            !after.contains("softWrap = false")
        )
    }

    @Test fun `LetterReader title has no maxLines cap (it wraps at Large)`() {
        val src = java.io.File(
            "../app/src/main/java/org/mindanchor/letters/LetterScreen.kt"
        ).readText()
        val titleText = src.indexOf("friendlyLetterDate(letter.date, LocalDate.now())")
        val after = src.substring(titleText, titleText + 500)
        assertTrue(
            "LetterReader title must NOT have a maxLines cap",
            !after.contains("maxLines =")
        )
    }

    @Test fun `LetterReader column has verticalScroll (body scrolls at Large)`() {
        val src = java.io.File(
            "../app/src/main/java/org/mindanchor/letters/LetterScreen.kt"
        ).readText()
        val readerFn = src.indexOf("private fun LetterReader(")
        val column = src.indexOf(".verticalScroll(", readerFn)
        assertTrue(
            "LetterReader's column must use verticalScroll",
            column > 0
        )
    }

    @Test fun `SingleChoiceSegmentedButtonRow is inside LetterReader's scrollable column (not pinned to bottom)`() {
        val src = java.io.File(
            "../app/src/main/java/org/mindanchor/letters/LetterScreen.kt"
        ).readText()
        // The size control lives in LetterReaderHeader, which is
        // called from LetterReader. We need the SegmentedButton to be
        // inside a column that has verticalScroll, regardless of which
        // sub-Composable declares it. Pin the order: the Column that
        // opens (in LetterReader) must come before the SegmentedButton
        // call site, and both must be after the verticalScroll call.
        val readerFn = src.indexOf("private fun LetterReader(")
        val column = src.indexOf(".verticalScroll(", readerFn)
        assertTrue("LetterReader's column must use verticalScroll", column > 0)
        // Find the Column( that owns the verticalScroll — the most
        // recent Column( before the verticalScroll position.
        val colOpen = src.lastIndexOf("Column(", column)
        assertTrue("Column( must open before verticalScroll(", colOpen > 0 && colOpen < column)
        // The SegmentedButton call site (with the opening brace, to
        // skip the import) must be inside the same scope.
        val segBtn = src.indexOf("SingleChoiceSegmentedButtonRow {", colOpen)
        assertTrue(
            "SingleChoiceSegmentedButtonRow must be inside the same Column as verticalScroll",
            segBtn > colOpen
        )
    }
}
