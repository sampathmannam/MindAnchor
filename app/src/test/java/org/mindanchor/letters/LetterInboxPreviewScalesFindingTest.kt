package org.mindanchor.letters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LetterInboxPreviewScalesFindingTest {

    @Test fun `LetterRow date label uses readerTitleStyle (scaled)`() {
        val src = java.io.File(
            "../app/src/main/java/org/mindanchor/letters/LetterScreen.kt"
        ).readText()
        assertTrue(
            "LetterRow date label must use readerTitleStyle",
            src.contains("readerTitleStyle(MaterialTheme.typography, size)")
        )
    }

    @Test fun `LetterRow preview uses readerBodyStyle (scaled)`() {
        val src = java.io.File(
            "../app/src/main/java/org/mindanchor/letters/LetterScreen.kt"
        ).readText()
        assertTrue(
            "LetterRow preview must use readerBodyStyle",
            src.contains("readerBodyStyle(MaterialTheme.typography, size)")
        )
    }

    @Test fun `friendly-date group headers do NOT use the scaled style (they group, not read)`() {
        val src = java.io.File(
            "../app/src/main/java/org/mindanchor/letters/LetterScreen.kt"
        ).readText()
        // The group-separator "Today" / "Yesterday" / "Monday" / "Earlier"
        // headers use titleSmall with onSurfaceVariant, not the scaled
        // style. We pin that there are exactly two readerTitleStyle
        // call sites: one in LetterRow (date label) and one in
        // LetterReader (letter title). The inbox's group header uses
        // MaterialTheme.typography.titleSmall directly, NOT
        // readerTitleStyle. Heuristic: count the explicit
        // readerTitleStyle(MaterialTheme.typography, size) call sites.
        val count = src.split("readerTitleStyle(MaterialTheme.typography, size)").size - 1
        assertEquals(
            "Expected exactly 2 readerTitleStyle(MaterialTheme.typography, size) call sites (LetterRow + LetterReader)",
            2,
            count
        )
    }

    @Test fun `LetterRow enforces 48dp min height for touch target at all sizes`() {
        val src = java.io.File(
            "../app/src/main/java/org/mindanchor/letters/LetterScreen.kt"
        ).readText()
        assertTrue(
            "LetterRow must keep heightIn(min = 48.dp) regardless of the chosen size",
            src.contains(".heightIn(min = 48.dp)")
        )
    }

    @Test fun `readerBodyStyle is exposed as a top-level function (so the test can call it)`() {
        val cls = Class.forName("org.mindanchor.letters.LetterScreenKt")
        val m = cls.declaredMethods.first { it.name == "readerBodyStyle" }
        assertTrue(
            "readerBodyStyle must be a static (top-level) function",
            java.lang.reflect.Modifier.isStatic(m.modifiers)
        )
    }
}
