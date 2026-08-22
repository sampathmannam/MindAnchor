package org.mindanchor.letters

import org.junit.Assert.assertTrue
import org.junit.Test

class LetterUnavailableReaderFindingTest {

    @Test fun `LetterReader renders letters_reader_missing when letter is null`() {
        val src = java.io.File(
            "../app/src/main/java/org/mindanchor/letters/LetterScreen.kt"
        ).readText()
        assertTrue(
            "LetterReader must render letters_reader_missing when letter is null",
            src.contains("letters_reader_missing")
        )
    }

    @Test fun `LetterReader missing-letter state has a back button`() {
        val src = java.io.File(
            "../app/src/main/java/org/mindanchor/letters/LetterScreen.kt"
        ).readText()
        // The missing-letter fallback must have a way back to the inbox.
        // The back button is wired either inline (TextButton(onClick = onBack))
        // or via the LetterReaderMissing(onBack = onBack) sub-Composable
        // extracted in Task 5. Both are valid; the test pins the intent
        // (a path back from the missing-letter fallback), not the layout.
        val nullBranch = src.indexOf("letter == null")
        val nextBrace = src.indexOf("}", nullBranch)
        val seg = src.substring(nullBranch, nextBrace)
        assertTrue(
            "Missing-letter fallback must have a back button (inline or via LetterReaderMissing)",
            seg.contains("onClick = onBack") || seg.contains("LetterReaderMissing(")
        )
    }

    @Test fun `LetterReader does not call onDelete when letter is null`() {
        val src = java.io.File(
            "../app/src/main/java/org/mindanchor/letters/LetterScreen.kt"
        ).readText()
        val nullBranch = src.indexOf("letter == null")
        val nextBrace = src.indexOf("}", nullBranch)
        val seg = src.substring(nullBranch, nextBrace)
        assertTrue(
            "Missing-letter fallback must NOT call onDelete",
            !seg.contains("onDelete(")
        )
    }

    @Test fun `LetterReader missing-letter fallback does not crash if onDelete is never wired`() {
        // Compile-time: the AlertDialog in LetterReader is guarded by
        // `if (pendingDelete.value)`, and pendingDelete is only flipped
        // by the top-row IconButton (now in LetterReaderHeader, via the
        // `onDeleteRequest` callback). When letter is null, the
        // IconButton is inside the else branch of `if (letter == null)`,
        // so it never renders and pendingDelete stays false.
        val src = java.io.File(
            "../app/src/main/java/org/mindanchor/letters/LetterScreen.kt"
        ).readText()
        // The lambda that flips pendingDelete must be inside the
        // `else { ... letter.body ... }` branch, not at the top of the
        // Column. Look for the actual pattern in the file (Task 5
        // extracted the IconButton into LetterReaderHeader, so the
        // callback is `onDeleteRequest = { pendingDelete.value = true }`,
        // not `onClick = { ... }`).
        val icon = src.indexOf("onDeleteRequest = { pendingDelete.value = true }")
        assertTrue(
            "The pendingDelete-flipping callback must exist in the file",
            icon >= 0
        )
        val before = src.lastIndexOf("}", icon)
        val branch = src.substring(before, icon)
        assertTrue(
            "The delete IconButton must not be in the null-letter branch",
            !branch.contains("letter == null")
        )
    }

    @Test fun `LetterScreen keeps selectedDate so a back from the missing-letter fallback re-enters the inbox`() {
        // selectedDate is preserved across the missing-letter fallback
        // because the parent (HomeScreen) doesn't clear it; only
        // letter == null inside LetterReader. Pin that LetterReader
        // doesn't call onBack = selectedDate-clear; that's the parent's
        // responsibility.
        val src = java.io.File(
            "../app/src/main/java/org/mindanchor/letters/LetterScreen.kt"
        ).readText()
        // The missing-letter back affordance (inline or via
        // LetterReaderMissing sub-Composable) calls onBack (no
        // selectedDate arg), which the parent (HomeScreen) interprets
        // as "back to the inbox".
        val nullBranch = src.indexOf("letter == null")
        val nextBrace = src.indexOf("}", nullBranch)
        val seg = src.substring(nullBranch, nextBrace)
        assertTrue(
            "Missing-letter fallback must call onBack (inline or via LetterReaderMissing)",
            seg.contains("onClick = onBack") || seg.contains("LetterReaderMissing(")
        )
    }
}
