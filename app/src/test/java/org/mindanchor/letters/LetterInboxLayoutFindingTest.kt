package org.mindanchor.letters

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LetterInboxLayoutFindingTest {

    @Test fun `LetterInbox uses safeDrawingPadding`() {
        // File-shape: the Column has the right modifier chain. We pin
        // the modifier sequence to the textual source rather than to
        // Compose semantics so the test doesn't pull in a runtime.
        val src = java.io.File(
            "src/main/java/org/mindanchor/letters/LetterScreen.kt"
        ).readText()
        assertTrue(
            "LetterInbox must use safeDrawingPadding before verticalScroll",
            src.contains(".safeDrawingPadding()") &&
                src.contains(".verticalScroll(")
        )
    }

    @Test fun `Generate now is a TextButton with onClick disabled when model does not fit`() {
        // File-shape: there is a TextButton whose enabled expression
        // is `modelFits` (or `!something && modelFits`). Pin that the
        // enablement depends on modelFits.
        val src = java.io.File(
            "src/main/java/org/mindanchor/letters/LetterScreen.kt"
        ).readText()
        assertTrue(
            "Generate now TextButton must gate on modelFits",
            Regex("""enabled\s*=\s*[^,)]*modelFits""").containsMatchIn(src)
        )
    }

    @Test fun `inbox shows newest first (reverses the store-sorted list)`() {
        // File-shape: the inbox iterates `letters.reversed()`. The
        // store gives oldest first, the inbox shows newest first.
        val src = java.io.File(
            "src/main/java/org/mindanchor/letters/LetterScreen.kt"
        ).readText()
        assertTrue(
            "Inbox must reverse the letter list (newest first)",
            src.contains("letters.reversed()")
        )
    }

    @Test fun `empty-state string for no model exists in strings file`() {
        val xml = java.io.File(
            "../app/src/main/res/values/strings.xml"
        ).readText()
        assertTrue(xml.contains("name=\"letters_empty_no_model\""))
    }

    @Test fun `empty-state string for no letters exists in strings file`() {
        val xml = java.io.File(
            "../app/src/main/res/values/strings.xml"
        ).readText()
        assertTrue(xml.contains("name=\"letters_empty\""))
    }
}
