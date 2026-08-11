package org.mindanchor.letters

import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsLetterControlsFindingTest {

    @Test fun `SettingsScreen has a Daily letter SectionHeading`() {
        val src = java.io.File(
            "../app/src/main/java/org/mindanchor/settings/SettingsScreen.kt"
        ).readText()
        assertTrue(
            "SettingsScreen must use letters_section as a SectionHeading",
            src.contains("R.string.letters_section")
        )
    }

    @Test fun `SettingsScreen letter toggle is always editable (no modelFits gate)`() {
        val src = java.io.File(
            "../app/src/main/java/org/mindanchor/settings/SettingsScreen.kt"
        ).readText()
        // Find the first TextButton onClick that calls setLettersEnabled.
        // The toggle's Row does NOT have `enabled = modelFits`. The
        // finding test pins the pattern: setLettersEnabled is wired
        // to toggleable without a modelFits gate.
        val setLe = src.indexOf("setLettersEnabled")
        assertTrue("setLettersEnabled must be wired", setLe >= 0)
        val before = src.lastIndexOf("Row(", setLe)
        val after = src.indexOf("}", setLe)
        val segment = src.substring(before, after)
        assertTrue(
            "Toggle Row must not gate on modelFits",
            !segment.contains("enabled = modelFits")
        )
    }

    @Test fun `Generate now is gated on modelFits + lettersEnabled + !letterRunning`() {
        val src = java.io.File(
            "../app/src/main/java/org/mindanchor/settings/SettingsScreen.kt"
        ).readText()
        // Find the Generate now TextButton. It should reference all three
        // gates: !letterRunning, lettersEnabled, modelFits.
        val gen = src.indexOf("runLetterNow")
        val button = src.lastIndexOf("TextButton(", gen)
        val end = src.indexOf(")", gen)
        val seg = src.substring(button, end)
        assertTrue("Generate now must gate on letterRunning", seg.contains("letterRunning"))
        assertTrue("Generate now must gate on lettersEnabled", seg.contains("lettersEnabled"))
        assertTrue("Generate now must gate on modelFits", seg.contains("modelFits"))
    }

    @Test fun `Open inbox is disabled when unreadCount is 0`() {
        val src = java.io.File(
            "../app/src/main/java/org/mindanchor/settings/SettingsScreen.kt"
        ).readText()
        // The Open inbox TextButton's enabled expression includes
        // unreadCount > 0.
        assertTrue(
            "Open inbox must be gated on unreadCount > 0",
            src.contains("enabled = unreadCount > 0")
        )
    }

    @Test fun `Reading section is not gated behind any modelFits check (the toggle stays usable)`() {
        val src = java.io.File(
            "../app/src/main/java/org/mindanchor/settings/SettingsScreen.kt"
        ).readText()
        // The whole Daily letter sub-section is reachable even when
        // modelFits == false. The test pins that the SectionHeading
        // for letters_section is inside a non-conditional block.
        val idx = src.indexOf("R.string.letters_section")
        val before = src.lastIndexOf("SectionHeading(", idx)
        assertTrue(before >= 0)
        // The opening brace of the surrounding if/Column should not be
        // `if (!modelFits)` (that would gate the section).
        val blockStart = src.lastIndexOf("Column(", before).coerceAtLeast(src.lastIndexOf("if (", before))
        val block = src.substring(blockStart, before)
        assertTrue(
            "Daily letter section must not be wrapped in `if (!modelFits)`",
            !block.contains("if (!modelFits")
        )
    }
}
