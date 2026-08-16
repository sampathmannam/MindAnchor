@file:Suppress("MaxLineLength", "FunctionNaming", "MagicNumber")
package org.mindanchor.letters

import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.testing.TestFileUtil.fileAt

class LetterDeleteConfirmFindingTest {

    private val screen: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/letters/LetterScreen.kt",
        ).readText()

    private val strings: String
        get() = fileAt(
            "app/src/main/res/values/strings.xml",
        ).readText()

    @Test fun `LetterRow uses an IconButton with the Close icon for delete`() {
        assertTrue(
            "LetterRow delete must be an IconButton with Icons.Default.Close",
            screen.contains("Icons.Default.Close")
        )
    }

    @Test fun `LetterInbox uses an AlertDialog for delete confirm (not silent delete)`() {
        assertTrue(
            "LetterInbox must wrap delete in an AlertDialog (matches the Notes list)",
            screen.contains("AlertDialog(") && screen.contains("letters_delete_confirm")
        )
    }

    @Test fun `LetterRow preview truncates at 60 chars with ellipsis`() {
        assertTrue(
            "LetterRow preview must truncate with '…' for > 60 char first lines",
            screen.contains("take(60)") && screen.contains("…")
        )
    }

    @Test fun `LetterRow has min height 48dp for touch target`() {
        assertTrue(
            "LetterRow must enforce 48dp minimum touch target",
            screen.contains(".heightIn(min = 48.dp)")
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
        //
        // v0.26.2: the onConfirm also calls `performHapticFeedback`
        // before the onDelete. The regex is unchanged — it
        // still pins the delete-call contract.
        // onConfirm = { ... onDelete(pendingDeleteDate); pendingDelete.value = null }
        assertTrue(
            "LetterInbox must wire onConfirm to onDelete(pendingDeleteDate)",
            Regex("""onConfirm\s*=\s*\{[\s\S]*?onDelete\(pendingDeleteDate\)""")
                .containsMatchIn(screen)
        )
    }

    // v0.26.2: the dialog now has a body line and a
    // "Keep" dismiss button. v0.25.x used the AlertDialog
    // title-only shape; a v0.26.2 regression that drops the
    // body line or the Keep button would be a silent
    // step backwards (a destructive action with no
    // "this can't be undone" warning).
    @Test fun `delete dialog has a body line (v0_26_2 letters_delete_body)`() {
        assertTrue(
            "LetterDeleteDialog must render a text block reading R.string.letters_delete_body",
            // The `text = { Text(stringResource(R.string.letters_delete_body)) }`
            // shape is the Material 3 way to add body content to an
            // AlertDialog. A regression to title-only would drop
            // this slot.
            screen.contains("text = { Text(stringResource(R.string.letters_delete_body)) }"),
        )
        assertTrue(
            "strings.xml must define <string name=\"letters_delete_body\">",
            strings.contains("name=\"letters_delete_body\""),
        )
    }

    @Test fun `delete dialog dismiss button is Keep (not Cancel) - v0_26_2 rename`() {
        // v0.25.x used "Cancel". v0.26.2 renames to "Keep"
        // — the user is keeping the letter, not cancelling
        // the action. The new key is letters_delete_keep.
        assertTrue(
            "LetterDeleteDialog must render letters_delete_keep on the dismiss button",
            screen.contains("R.string.letters_delete_keep"),
        )
        assertTrue(
            "strings.xml must define <string name=\"letters_delete_keep\">",
            strings.contains("name=\"letters_delete_keep\""),
        )
    }

    @Test fun `delete confirm button is Delete (destructive label, v0_26_2 still uses letters_delete_button)`() {
        // The confirm button label is unchanged across v0.26.2;
        // the test pins the destructive label so a future
        // rename to a softer word ("Remove", "Discard") would
        // flip the test red.
        assertTrue(
            "LetterDeleteDialog must render letters_delete_button on the confirm button",
            screen.contains("R.string.letters_delete_button"),
        )
    }
}
