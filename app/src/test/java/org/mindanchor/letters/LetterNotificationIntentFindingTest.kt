package org.mindanchor.letters

import org.junit.Assert.assertTrue
import org.junit.Test

class LetterNotificationIntentFindingTest {

    @Test fun `LetterScheduler uses HomeActivity (not the launcher intent)`() {
        val src = java.io.File(
            "src/main/java/org/mindanchor/letters/LetterScheduler.kt"
        ).readText()
        assertTrue(
            "LetterScheduler must target HomeActivity, not getLaunchIntentForPackage",
            src.contains("HomeActivity::class.java")
        )
        assertTrue(
            "LetterScheduler must NOT use getLaunchIntentForPackage for the letter intent",
            !src.contains("getLaunchIntentForPackage")
        )
    }

    @Test fun `LetterScheduler sets the OPEN_LETTER action`() {
        val src = java.io.File(
            "src/main/java/org/mindanchor/letters/LetterScheduler.kt"
        ).readText()
        assertTrue(
            "LetterScheduler must set the OPEN_LETTER action",
            src.contains("OPEN_LETTER")
        )
    }

    @Test fun `LetterScheduler adds the letter_date extra as ISO local date`() {
        val src = java.io.File(
            "src/main/java/org/mindanchor/letters/LetterScheduler.kt"
        ).readText()
        assertTrue(
            "LetterScheduler must add the letter_date extra (toString = ISO local date)",
            src.contains("putExtra(\"letter_date\"")
        )
    }

    @Test fun `LetterScheduler uses FLAG_IMMUTABLE on the letter PendingIntent`() {
        val src = java.io.File(
            "src/main/java/org/mindanchor/letters/LetterScheduler.kt"
        ).readText()
        assertTrue(
            "LetterScheduler must use FLAG_IMMUTABLE on the letter PendingIntent",
            src.contains("FLAG_IMMUTABLE")
        )
    }

    @Test fun `OPEN_LETTER action constant is in the letters package`() {
        // The action string is defined in the letters package so the
        // HomeActivity can read it without leaking into the launcher
        // package. Test pins that the constant exists in the right
        // place.
        val src = java.io.File(
            "src/main/java/org/mindanchor/letters/LetterScheduler.kt"
        ).readText()
        assertTrue(
            "OPEN_LETTER must be a const val in the letters package",
            src.contains("const val ACTION_OPEN_LETTER")
        )
    }
}
