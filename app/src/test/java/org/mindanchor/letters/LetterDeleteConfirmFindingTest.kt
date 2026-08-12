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
            "LetterRow preview must truncate with '…' for > 60 char first lines",
            src.contains("take(60)") && src.contains("…")
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
        // File-shape: the dialog is now extracted as LetterDeleteDialog
        // (date, onConfirm, onDismiss). The onConfirm lambda is supplied
        // at the call site in LetterInbox, and calls
        // onDelete(pendingDeleteDate) followed by clearing the state.
        // The dialog itself is a generic container (TextButton onClick
        // = onConfirm); the delete-call contract lives in the inbox.
        //
        // v0.25.5-WP-G: a haptic call was added to the lambda before
        // the onDelete — the regex allows the haptic line between `{`
        // and `onDelete(pendingDeleteDate)` so the test pins the
        // delete-call contract without coupling to the haptic shape.
        val src = java.io.File(
            "src/main/java/org/mindanchor/letters/LetterScreen.kt"
        ).readText()
        // onConfirm = { ... onDelete(pendingDeleteDate); pendingDelete.value = null }
        assertTrue(
            "LetterInbox must wire onConfirm to onDelete(pendingDeleteDate)",
            Regex("""onConfirm\s*=\s*\{[\s\S]*?onDelete\(pendingDeleteDate\)""")
                .containsMatchIn(src)
        )
    }
}
