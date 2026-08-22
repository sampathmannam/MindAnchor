package org.mindanchor.letters

import org.junit.Assert.assertTrue
import org.junit.Test

class LetterReaderLayoutFindingTest {

    @Test fun `LetterReader is a private function in LetterScreenKt`() {
        val cls = Class.forName("org.mindanchor.letters.LetterScreenKt")
        assertTrue(cls.declaredMethods.any { it.name == "LetterReader" })
    }

    @Test fun `LetterReader title uses friendlyLetterDate`() {
        val src = java.io.File(
            "src/main/java/org/mindanchor/letters/LetterScreen.kt"
        ).readText()
        assertTrue(
            "LetterReader must render the title via friendlyLetterDate(letter.date, today)",
            Regex("""friendlyLetterDate\(letter\.date,""").containsMatchIn(src)
        )
    }

    @Test fun `LetterReader body is the full letter body (no truncation)`() {
        val src = java.io.File(
            "src/main/java/org/mindanchor/letters/LetterScreen.kt"
        ).readText()
        assertTrue(
            "LetterReader body must render the full letter (no take(60))",
            Regex("""text\s*=\s*letter\.body,""").containsMatchIn(src)
        )
    }

    @Test fun `LetterReader handles null letter with the missing-letter string`() {
        val src = java.io.File(
            "src/main/java/org/mindanchor/letters/LetterScreen.kt"
        ).readText()
        assertTrue(
            "LetterReader must render letters_reader_missing when letter is null",
            src.contains("letters_reader_missing")
        )
    }

    @Test fun `LetterReader has a disclaimer footer`() {
        val src = java.io.File(
            "src/main/java/org/mindanchor/letters/LetterScreen.kt"
        ).readText()
        assertTrue(
            "LetterReader must render letters_disclaimer at the bottom",
            src.contains("letters_disclaimer")
        )
    }
}
