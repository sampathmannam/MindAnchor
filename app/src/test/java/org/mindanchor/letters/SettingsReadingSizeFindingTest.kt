package org.mindanchor.letters

import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsReadingSizeFindingTest {

    @Test fun `SettingsScreen has a Reading size SectionHeading`() {
        val src = java.io.File(
            "../app/src/main/java/org/mindanchor/settings/SettingsScreen.kt"
        ).readText()
        assertTrue(
            "SettingsScreen must use reading_size_section as a SectionHeading",
            src.contains("R.string.reading_size_section")
        )
    }

    @Test fun `SettingsScreen Reading size uses SingleChoiceSegmentedButtonRow`() {
        val src = java.io.File(
            "../app/src/main/java/org/mindanchor/settings/SettingsScreen.kt"
        ).readText()
        // Search for the call site (with the opening brace, on the
        // same line OR a different line) to skip the import statement
        // at the top of the file.
        assertTrue(
            "Reading size must use SingleChoiceSegmentedButtonRow",
            src.contains("SingleChoiceSegmentedButtonRow")
        )
    }

    @Test fun `SettingsScreen Reading size onClick calls setLetterSize(s)`() {
        val src = java.io.File(
            "../app/src/main/java/org/mindanchor/settings/SettingsScreen.kt"
        ).readText()
        assertTrue(
            "Reading size SegmentedButton must call setLetterSize(s)",
            src.contains("setLetterSize(s)")
        )
    }

    @Test fun `SettingsScreen Reading size explainer uses bodySmall + onSurfaceVariant`() {
        val src = java.io.File(
            "../app/src/main/java/org/mindanchor/settings/SettingsScreen.kt"
        ).readText()
        val idx = src.indexOf("R.string.reading_size_explainer")
        val after = src.substring(idx, idx + 400)
        assertTrue(
            "Reading size explainer must use bodySmall + onSurfaceVariant",
            after.contains("bodySmall") && after.contains("onSurfaceVariant")
        )
    }

    @Test fun `SettingsScreen Reading size is reachable without modelFits (the toggle works pre-model)`() {
        val src = java.io.File(
            "../app/src/main/java/org/mindanchor/settings/SettingsScreen.kt"
        ).readText()
        val idx = src.indexOf("R.string.reading_size_section")
        val before = src.lastIndexOf("SectionHeading(", idx)
        val blockStart = src.lastIndexOf("Column(", before).coerceAtLeast(src.lastIndexOf("if (", before))
        val block = src.substring(blockStart, before)
        assertTrue(
            "Reading size must NOT be wrapped in `if (modelFits)` or `if (!modelFits)`",
            !block.contains("modelFits")
        )
    }
}
