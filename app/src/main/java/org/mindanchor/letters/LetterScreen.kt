package org.mindanchor.letters

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                style = readerTitleStyle(MaterialTheme.typography, size),
            )
            val bodyStyle = readerBodyStyle(MaterialTheme.typography, size)
            Text(
                text = preview,
                style = bodyStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
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
    // The pending-delete flag lives here, not in the screen or
    // the inbox: only one confirm can be open at a time, and
    // dismissing it must clear the state cleanly. The header's
    // × button sets the flag; the dialog's confirm button calls
    // onDelete and clears it. The date is implicit in the
    // screen's state, so a Boolean is enough — the inbox's
    // `LocalDate?` model doesn't translate. `onSetSize` is
    // unused for now: Task 18 wires it into the size-control
    // slot in [LetterReaderHeader].
    val pendingDelete = remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.Edge),
    ) {
        LetterReaderHeader(
            onBack = onBack,
            onDeleteRequest = { pendingDelete.value = true },
        )
        if (letter == null) {
            LetterReaderMissing(onBack = onBack)
        } else {
            Text(
                text = friendlyLetterDate(letter.date, LocalDate.now()),
                style = readerTitleStyle(MaterialTheme.typography, size),
                modifier = Modifier.padding(vertical = Spacing.Comfortable),
            )
            Text(
                text = letter.body,
                style = readerBodyStyle(MaterialTheme.typography, size),
                modifier = Modifier.padding(bottom = Spacing.Loose),
            )
            Text(
                text = stringResource(R.string.letters_disclaimer),
                style = readerDisclaimerStyle(MaterialTheme.typography, size),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (pendingDelete.value) {
        LetterReaderDeleteDialog(
            onConfirm = {
                pendingDelete.value = false
                onDelete()
            },
            onDismiss = { pendingDelete.value = false },
        )
    }
}

/**
 * The top row of [LetterReader]: back on the left, a placeholder
 * slot in the middle for the v0.25.2-B size control (Task 18),
 * and the delete × on the right. Extracted from [LetterReader]
 * to keep that function under the LongMethod threshold and to
 * make the size-control slot obvious in the file.
 */
@Suppress("FunctionNaming")
@Composable
private fun LetterReaderHeader(
    onBack: () -> Unit,
    onDeleteRequest: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.Tight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
        Spacer(modifier = Modifier.weight(1f))
        // Size control slot (filled by Task 18).
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onDeleteRequest) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.letters_delete),
            )
        }
    }
}

/**
 * The soft empty state for when the letter was deleted while the
 * reader was open. No retry, no recovery — just a clear signpost
 * back to the inbox.
 */
@Suppress("FunctionNaming")
@Composable
private fun LetterReaderMissing(onBack: () -> Unit) {
    Text(
        text = stringResource(R.string.letters_reader_missing),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(onClick = onBack) {
        Text(stringResource(R.string.action_back))
    }
}

/**
 * The reader-level delete confirm. Mirrors the inbox's
 * [LetterDeleteDialog] shape; separated so the
 * `pendingDelete: Boolean` state lives entirely in [LetterReader]
 * (the dialog doesn't need to know what kind of state is
 * tracking the pending action).
 */
@Suppress("FunctionNaming")
@Composable
private fun LetterReaderDeleteDialog(
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

/**
 * Reader title style. `headlineMedium` shape with `fontSize = size.sp * 1.75f`
 * and `fontWeight = Light`. The `headlineMedium` baseline is the same shape
 * the launcher already uses for top-of-screen headings, so a person who has
 * the reader open at MEDIUM sees a title that's about 32sp tall — comfortably
 * larger than the body's 18sp without overwhelming the page.
 *
 * The 1.75x ratio is deliberate: a 14sp body (SMALL) needs a ~24sp title to
 * read as a heading; 18sp body needs ~32sp; 32sp body needs ~56sp. The ratio
 * is what keeps the title larger than the body at every size.
 *
 * v0.25.2-B spec 2 §"3 sizes" — WCAG 2.2 SC 1.4.4 (Resize Text) requires
 * that text scale up to 200% without loss of content or functionality. The
 * LARGE size here (32sp body) is exactly 200% of the 16sp reference body —
 * the maximum the SC explicitly permits — and the 1.75x title ratio lands
 * at 56sp, well below the screen-height line where vertical scrolling would
 * be required to see the title.
 *
 * The [typography] parameter is the active [androidx.compose.material3.Typography];
 * the call site (always inside a `@Composable` function) passes
 * `MaterialTheme.typography` so the title inherits font family and other
 * shape fields from the theme. The function is **not** `@Composable` itself
 * — accepting the Typography as a parameter keeps the size math pure and
 * testable from a regular JUnit test, without the Compose runtime overhead
 * of `runComposeUiTest`.
 */
internal fun readerTitleStyle(typography: Typography, size: ReadingSize): TextStyle =
    typography.headlineMedium.copy(
        fontSize = (size.sp * 1.75f).sp,
        fontWeight = FontWeight.Light,
    )

/**
 * Reader body style. `bodyLarge` shape with `fontSize = size.sp` (the size
 * IS the font — that's the whole point of the user-toggle) and
 * `lineHeight = size.sp * 1.45f` (the typography guideline for sustained
 * reading: ~1.4-1.5x line height keeps the eye moving down the column
 * without the lines feeling cramped).
 *
 * Not `@Composable` — see [readerTitleStyle] for why Typography is a
 * parameter rather than a runtime read.
 */
internal fun readerBodyStyle(typography: Typography, size: ReadingSize): TextStyle =
    typography.bodyLarge.copy(
        fontSize = size.sp.sp,
        lineHeight = (size.sp * 1.45f).sp,
    )

/**
 * Reader disclaimer style (the "this is not a substitute for…" line under
 * every letter body). `bodySmall` shape with `fontSize = size.sp * 0.85f` —
 * a hair smaller than the body, just enough to read as fine print without
 * crossing into "too small to read" territory. The 0.85x ratio holds across
 * the three sizes so the disclaimer always reads as a fraction quieter than
 * the body, never as a different surface.
 *
 * Not `@Composable` — see [readerTitleStyle].
 */
internal fun readerDisclaimerStyle(typography: Typography, size: ReadingSize): TextStyle =
    typography.bodySmall.copy(
        fontSize = (size.sp * 0.85f).sp,
    )
