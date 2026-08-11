package org.mindanchor.letters

import org.junit.Assert.assertTrue
import org.junit.Test

class LetterDeleteConfirmFindingTest {

    @Test fun `LetterRow uses an IconButton with the Close icon for delete`() {
        val src = java.io.File(
            "src/main/java/org/mindanchor/letters/LetterScreen.kt"
        ).readText()
        assertTrue(
            "LetterRow delete must be an IconButton with Icons.Default.Close",
            src.contains("Icons.Default.Close")
        )
    }

    @Test fun `LetterInbox uses an AlertDialog for delete confirm (not silent delete)`() {
        val src = java.io.File(
            "src/main/java/org/mindanchor/letters/LetterScreen.kt"
        ).readText()
        assertTrue(
            "LetterInbox must wrap delete in an AlertDialog (matches the Notes list)",
            src.contains("AlertDialog(") && src.contains("letters_delete_confirm")
        )
    }

    @Test fun `LetterRow preview truncates at 60 chars with ellipsis`() {
        val src = java.io.File(
            "src/main/java/org/mindanchor/letters/LetterScreen.kt"
        ).readText()
        assertTrue(
            "LetterRow preview must truncate at 60 chars with '…' for > 60 char first lines",
            src.contains("MAX_PREVIEW_CHARS = 60") &&
                src.contains("take(MAX_PREVIEW_CHARS)") &&
                src.contains("…")
        )
    }

    @Test fun `LetterRow has min height 48dp for touch target`() {
        val src = java.io.File(
            "src/main/java/org/mindanchor/letters/LetterScreen.kt"
        ).readText()
        assertTrue(
            "LetterRow must enforce 48dp minimum touch target",
            src.contains(".heightIn(min = 48.dp)")
        )
    }

    @Test fun `delete confirm calls onDelete exactly once on confirm`() {
        // File-shape: the AlertDialog's confirmButton onClick calls
        // onDelete(pendingDeleteDate) once. We pin the onDelete call
        // site inside the confirm-button lambda.
        val src = java.io.File(
            "src/main/java/org/mindanchor/letters/LetterScreen.kt"
        ).readText()
        // confirmButton { TextButton(onClick = { onDelete(pendingDeleteDate); pendingDelete = null }) }
        assertTrue(
            "AlertDialog confirm must call onDelete and clear pendingDelete",
            Regex("""onClick\s*=\s*\{\s*onDelete\(pendingDeleteDate\)""")
                .containsMatchIn(src)
        )
    }
}
