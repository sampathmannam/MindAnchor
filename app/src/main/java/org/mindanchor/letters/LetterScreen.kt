package org.mindanchor.letters

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import org.mindanchor.R
import org.mindanchor.reader.ReadingSize
import org.mindanchor.ui.Spacing

/**
 * The letter surface, dispatched between an inbox list and a single
 * reader. `date == null` shows the inbox; a non-null date shows the
 * reader for that date.
 *
 * v0.25.2-A: the inbox and reader Composables are filled in by
 * Tasks 3 and 4. v0.25.2-B: `size` and `onSetSize` are wired in
 * Task 15.
 *
 * `modelFits` controls the empty-state copy and the Generate-now
 * enablement, both inside the inbox; the reader doesn't need it.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
fun LetterScreen(
    letters: List<Letter>,
    modelFits: Boolean,
    date: LocalDate?,
    size: ReadingSize = ReadingSize.MEDIUM,
    onSelect: (LocalDate) -> Unit = {},
    onBack: () -> Unit = {},
    onDelete: (LocalDate) -> Unit = {},
    onSetSize: (ReadingSize) -> Unit = {},
) {
    if (date != null) {
        LetterReader(
            letter = letters.firstOrNull { it.date == date },
            size = size,
            onBack = onBack,
            onDelete = { onDelete(date) },
            onSetSize = onSetSize,
        )
    } else {
        LetterInbox(
            letters = letters,
            modelFits = modelFits,
            size = size,
            onSelect = onSelect,
            onDelete = onDelete,
            onBack = onBack,
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun LetterInbox(
    letters: List<Letter>,
    modelFits: Boolean,
    size: ReadingSize,
    onSelect: (LocalDate) -> Unit,
    onDelete: (LocalDate) -> Unit,
    onBack: () -> Unit,
) {
    // The dialog belongs to the inbox, not the row: only one
    // confirm can be open at a time, and dismissing it must clear
    // the state cleanly. The row's × button sets the pending date;
    // the dialog's confirm button calls onDelete and clears it.
    // The content Composable takes a request-callback rather than
    // touching pendingDelete directly, so the dialog host stays
    // in this function and the content stays layout-only.
    val pendingDelete = remember { mutableStateOf<LocalDate?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.Edge),
    ) {
        LetterInboxContent(
            letters = letters,
            modelFits = modelFits,
            size = size,
            onBack = onBack,
            onSelect = onSelect,
            onDeleteRequest = { pendingDelete.value = it },
        )
    }
    val pendingDeleteDate = pendingDelete.value
    if (pendingDeleteDate != null) {
        LetterDeleteDialog(
            date = pendingDeleteDate,
            onConfirm = { onDelete(pendingDeleteDate); pendingDelete.value = null },
            onDismiss = { pendingDelete.value = null },
        )
    }
}

/**
 * The visible body of [LetterInbox]: header, list (or empty
 * state), and the Generate-now action. Lifted out of [LetterInbox]
 * to keep that function under the LongMethod threshold and to
 * make the dialog-host split obvious in the file. The
 * [onDeleteRequest] callback is what links a row's × tap back
 * to the inbox's `pendingDelete` state — the content doesn't
 * know about the dialog.
 */
@Suppress("FunctionNaming")
@Composable
private fun LetterInboxContent(
    letters: List<Letter>,
    modelFits: Boolean,
    size: ReadingSize,
    onBack: () -> Unit,
    onSelect: (LocalDate) -> Unit,
    onDeleteRequest: (LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    TextButton(onClick = onBack) {
        Text(stringResource(R.string.action_back))
    }
    Text(
        text = stringResource(R.string.letters_inbox_title),
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.padding(vertical = Spacing.Comfortable),
    )
    Text(
        text = stringResource(R.string.letters_inbox_explainer),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = Spacing.Loose),
    )
    if (letters.isEmpty()) {
        val emptyRes = if (modelFits) R.string.letters_empty else R.string.letters_empty_no_model
        Text(
            text = stringResource(emptyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        // Newest first: store gives oldest first; the inbox shows
        // newest first. Group by friendly-date so "Today" sits
        // above "Yesterday" above the rest.
        val grouped = letters.reversed()
            .groupBy { friendlyLetterDate(it.date, today) }
        grouped.forEach { (label, sameDay) ->
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.Loose, bottom = Spacing.Tight),
            )
            sameDay.forEach { letter ->
                LetterRow(
                    letter = letter,
                    size = size,
                    onSelect = onSelect,
                    onDelete = onDeleteRequest,
                )
            }
        }
    }
    // Generate-now is opt-in: the user has to ask for one even
    // when the model is installed. The button is always visible
    // so a user who doesn't know about the daily alarm can still
    // request a letter.
    TextButton(
        enabled = modelFits,
        onClick = { /* wired in Task 10 */ },
        modifier = Modifier.padding(top = Spacing.Loose),
    ) {
        Text(stringResource(R.string.letters_run_now))
    }
}

/**
 * The delete-confirm surface. Lives in the same file as [LetterInbox]
 * because the call site (the inbox's row × button) is what makes the
 * dialog appear. [date] is reserved for v0.25.2-B: the dialog body
 * will eventually show "Delete the letter from {date}?". For now the
 * date is plumbed through so the call site doesn't have to restructure
 * when the body lands.
 */
@Suppress("FunctionNaming", "UnusedParameter")
@Composable
private fun LetterDeleteDialog(
    date: LocalDate,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.letters_delete_confirm)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.letters_delete_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.letters_cancel))
            }
        },
    )
}

@Suppress("FunctionNaming")
@Composable
private fun LetterRow(
    letter: Letter,
    size: ReadingSize,
    onSelect: (LocalDate) -> Unit,
    onDelete: (LocalDate) -> Unit,
) {
    val firstLine = letter.body.lineSequence().firstOrNull().orEmpty()
    val preview = if (firstLine.length > 60) {
        firstLine.take(60) + "…"
    } else {
        firstLine
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { onSelect(letter.date) }
            .padding(vertical = Spacing.Snug),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = friendlyLetterDate(letter.date, LocalDate.now()),
                style = readerTitleStyle(size),
            )
            Text(
                text = preview,
                style = readerBodyStyle(size).copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
        IconButton(onClick = { onDelete(letter.date) }) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.letters_delete),
            )
        }
    }
}

@Suppress("FunctionNaming", "UnusedParameter")
@Composable
private fun LetterReader(
    letter: Letter?,
    size: ReadingSize,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onSetSize: (ReadingSize) -> Unit,
) {
    // TODO(task-5): replace with LetterReader implementation.
}

/**
 * v0.25.2-B: reader title style scales with [size]. Tasks 14/16
 * fill in the real style matrix; for now we return the same
 * default the rest of the app uses for sub-headings. `@Composable`
 * because [MaterialTheme.typography] reads from the active theme.
 * [size] is reserved for the v0.25.2-B style matrix; the stub
 * ignores it intentionally.
 */
@Suppress("UnusedParameter")
@Composable
internal fun readerTitleStyle(size: ReadingSize): TextStyle =
    MaterialTheme.typography.titleSmall

/**
 * v0.25.2-B: reader body style scales with [size]. Tasks 14/16
 * fill in the real style matrix; for now we return the same
 * default the rest of the app uses for body text. `@Composable`
 * because [MaterialTheme.typography] reads from the active theme.
 * [size] is reserved for the v0.25.2-B style matrix; the stub
 * ignores it intentionally.
 */
@Suppress("UnusedParameter")
@Composable
internal fun readerBodyStyle(size: ReadingSize): TextStyle =
    MaterialTheme.typography.bodyMedium
