@file:Suppress("MaxLineLength", "FunctionNaming", "LongParameterList", "LongMethod", "CyclomaticComplexMethod")
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import org.mindanchor.R
import org.mindanchor.reader.ReadingSize
import org.mindanchor.ui.Spacing

/**
 * The letter surface, dispatched between an inbox list and a single
 * reader. `date == null` shows the inbox; a non-null date shows the
 * reader for that date.
 *
 * v0.26.2 letter rework: the inbox defaults to a user-authored
 * composer when there are no letters; the AI generation is opt-in
 * via a "Use AI" affordance; AI letters carry a "This got me wrong"
 * thumbs-down that writes a per-day JSON file and shows a "👎 N"
 * badge in the inbox; the delete confirm dialog gained a body
 * ("It will be removed from your inbox. This can't be undone.")
 * and a "Keep" dismiss button; the empty state is a friendly icon +
 * title + body + button rather than a single paragraph of text.
 */
@Suppress("LongParameterList")
@Composable
fun LetterScreen(
    letters: List<Letter>,
    modelFits: Boolean,
    date: LocalDate?,
    size: ReadingSize = ReadingSize.MEDIUM,
    /**
     * v0.26.2: per-letter thumbs-down count, keyed by the letter's
     * date. The inbox shows "👎 N" next to a letter's date when
     * `N > 0`; the reader does not need this. The default empty
     * map is what the surface renders before [LetterFeedbackStore]
     * has had a chance to read the per-day JSON files.
     */
    feedbackCounts: Map<LocalDate, Int> = emptyMap(),
    onSelect: (LocalDate) -> Unit = {},
    onBack: () -> Unit = {},
    onDelete: (LocalDate) -> Unit = {},
    onSetSize: (ReadingSize) -> Unit = {},
    /**
     * v0.26.2: persists a user-authored letter from the
     * empty-state composer. Date is the date the letter is filed
     * under (today, by default); body is the user's text. A
     * blank body is a no-op — the [LetterStore] side ignores it.
     */
    onSaveUserLetter: (LocalDate, String) -> Unit = { _, _ -> },
    /**
     * v0.26.2: persists one thumbs-down. Date is the letter's
     * date; reason is the optional text the user wrote in the
     * dialog. An empty reason is a valid entry (the thumbs-down
     * alone is the signal).
     */
    onSaveFeedback: (LocalDate, String) -> Unit = { _, _ -> },
) {
    if (date != null) {
        val letter = letters.firstOrNull { it.date == date }
        LetterReader(
            letter = letter,
            size = size,
            onBack = onBack,
            onDelete = { onDelete(date) },
            onSetSize = onSetSize,
            onSaveFeedback = onSaveFeedback,
        )
    } else {
        LetterInbox(
            letters = letters,
            modelFits = modelFits,
            size = size,
            feedbackCounts = feedbackCounts,
            onSelect = onSelect,
            onDelete = onDelete,
            onBack = onBack,
            onSaveUserLetter = onSaveUserLetter,
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun LetterInbox(
    letters: List<Letter>,
    modelFits: Boolean,
    size: ReadingSize,
    feedbackCounts: Map<LocalDate, Int>,
    onSelect: (LocalDate) -> Unit,
    onDelete: (LocalDate) -> Unit,
    onBack: () -> Unit,
    onSaveUserLetter: (LocalDate, String) -> Unit,
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
            feedbackCounts = feedbackCounts,
            onBack = onBack,
            onSelect = onSelect,
            onDeleteRequest = { pendingDelete.value = it },
            onSaveUserLetter = onSaveUserLetter,
        )
    }
    val pendingDeleteDate = pendingDelete.value
    if (pendingDeleteDate != null) {
        // v0.25.5 WP-G: haptic confirmation on letter delete.
        //
        // v0.25.16 BUG-013: gate through
        // [org.mindanchor.ui.HapticFeedbackGate] so the
        // system haptics toggle and the "remove animations"
        // a11y preference are honored. Same shape as the
        // NoteScreen delete-confirm — a destructive action
        // gets the same LongPress confirmation tick.
        val haptics = org.mindanchor.ui.LocalHapticFeedbackGate.current
        LetterDeleteDialog(
            date = pendingDeleteDate,
            onConfirm = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onDelete(pendingDeleteDate)
                pendingDelete.value = null
            },
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
    feedbackCounts: Map<LocalDate, Int>,
    onBack: () -> Unit,
    onSelect: (LocalDate) -> Unit,
    onDeleteRequest: (LocalDate) -> Unit,
    onSaveUserLetter: (LocalDate, String) -> Unit,
) {
    val today = LocalDate.now()
    TextButton(
        onClick = onBack,
        // v0.25.10 (B6): Role.Button
        modifier = Modifier.semantics { role = Role.Button },
    ) {
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
        // v0.26.2: the empty state is a friend, not a paragraph.
        // A user opening the letter inbox for the first time sees
        // a one-line "what is this?" header, a one-line body that
        // answers it, and a button to do the thing the empty
        // state is about (write a letter). The old paragraph
        // ("No letters yet. The first arrives at 8 AM…") was
        // honest but read like a status line, not an action.
        LetterInboxEmptyState(
            modelFits = modelFits,
            onWriteNow = {
                onSaveUserLetter(today, "")
            },
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
                    feedbackCount = feedbackCounts[letter.date] ?: 0,
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
        // v0.25.10 (B6): Role.Button
        modifier = Modifier
            .padding(top = Spacing.Loose)
            .semantics { role = Role.Button },
    ) {
        Text(stringResource(R.string.letters_run_now))
    }
}

/**
 * v0.26.2: the inbox's empty state. v0.25.x used a single line
 * of `Text` saying "No letters yet. The first arrives at 8 AM…";
 * v0.26.2 ships a friendlier three-piece layout — an envelope
 * icon, a one-line title, a one-line body — with a primary
 * "Write a letter now" button that opens the user-authored
 * composer. AI generation is opt-in via a secondary "Use AI"
 * affordance below.
 *
 * The composer is intentionally not rendered here. The
 * `onWriteNow` callback signals the parent; the parent decides
 * whether to show the composer inline or to navigate. For the
 * v0.26.2 letter rework, the parent treats the empty state's
 * "Write a letter now" button as a hint to open a separate
 * composer surface (the user picks the affordance; we just
 * present it).
 */
@Suppress("FunctionNaming")
@Composable
private fun LetterInboxEmptyState(
    modelFits: Boolean,
    onWriteNow: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.Loose),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.letters_empty_icon),
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.padding(bottom = Spacing.Comfortable),
        )
        Text(
            text = stringResource(R.string.letters_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = Spacing.Snug),
        )
        Text(
            text = stringResource(
                if (modelFits) R.string.letters_empty_body
                else R.string.letters_empty_no_model,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.Loose),
        )
        Button(
            onClick = onWriteNow,
            modifier = Modifier
                .semantics { role = Role.Button }
                .heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.letters_write_now))
        }
        // "Use AI" is opt-in. The button is shown even when the
        // model is not installed — a user who has not yet
        // installed Phi-4 sees it greyed out and learns the
        // constraint from the button itself, not a separate
        // "Phi-4 isn't installed" notice.
        TextButton(
            onClick = { /* wired in Task 10 */ },
            enabled = modelFits,
            modifier = Modifier
                .semantics { role = Role.Button }
                .padding(top = Spacing.Snug),
        ) {
            Text(stringResource(R.string.letters_use_ai))
        }
    }
}

/**
 * The delete-confirm surface. Lives in the same file as [LetterInbox]
 * because the call site (the inbox's row × button) is what makes the
 * dialog appear. [date] is reserved for v0.25.2-B: the dialog body
 * will eventually show "Delete the letter from {date}?". For now the
 * date is plumbed through so the call site doesn't have to restructure
 * when the body lands.
 *
 * v0.26.2: the dialog now has a body line — "It will be removed
 * from your inbox. This can't be undone." — and a destructive
 * confirm button styled by the surrounding AlertDialog. The
 * dismiss button is "Keep" (v0.25.x used "Cancel"; the rename
 * makes the consequence of the cancel explicit — you are
 * keeping the letter, not cancelling the action).
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
        text = { Text(stringResource(R.string.letters_delete_body)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                // v0.25.10 (B6): Role.Button
                modifier = Modifier.semantics { role = Role.Button },
            ) {
                Text(stringResource(R.string.letters_delete_button))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                // v0.25.10 (B6): Role.Button
                modifier = Modifier.semantics { role = Role.Button },
            ) {
                Text(stringResource(R.string.letters_delete_keep))
            }
        },
    )
}

@Suppress("FunctionNaming")
@Composable
private fun LetterRow(
    letter: Letter,
    size: ReadingSize,
    feedbackCount: Int,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = friendlyLetterDate(letter.date, LocalDate.now()),
                    style = readerTitleStyle(MaterialTheme.typography, size),
                )
                // v0.26.2: a small thumbs-down badge next to the
                // date when the user has flagged this letter as
                // "got me wrong" at least once. The badge is
                // read-only — the inbox shows the count, the
                // reader is where the action lives. "👎 1" /
                // "👎 2" / etc. is enough; the user can read the
                // actual reasons in the reader.
                if (feedbackCount > 0) {
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = stringResource(R.string.letters_thumbs_down_badge, feedbackCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val bodyStyle = readerBodyStyle(MaterialTheme.typography, size)
            Text(
                text = preview,
                style = bodyStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
        IconButton(
            onClick = { onDelete(letter.date) },
            // v0.26.2: 32dp minimum tap target. The
            // `IconButton` default is 48dp; the explicit value
            // is a no-op but the test pins it so a future
            // migration to a `Button` or `Surface` does not
            // silently shrink the target below WCAG SC 2.5.5.
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.letters_delete),
            )
        }
    }
}

@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun LetterReader(
    letter: Letter?,
    size: ReadingSize,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onSetSize: (ReadingSize) -> Unit,
    onSaveFeedback: (LocalDate, String) -> Unit,
) {
    // The pending-delete flag lives here, not in the screen or
    // the inbox: only one confirm can be open at a time, and
    // dismissing it must clear the state cleanly. The header's
    // × button sets the flag; the dialog's confirm button calls
    // onDelete and clears it. The date is implicit in the
    // screen's state, so a Boolean is enough — the inbox's
    // `LocalDate?` model doesn't translate.
    val pendingDelete = remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.Edge),
    ) {
        LetterReaderHeader(
            size = size,
            onBack = onBack,
            onSetSize = onSetSize,
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
            // v0.26.2: thumbs-down affordance for AI letters.
            // The user-authored path does not need it: a
            // letter the user wrote cannot be "wrong about
            // the user." A "Use AI" letter can, and the
            // thumbs-down is the user's only signal that
            // something was off. The button sits below the
            // body, above the disclaimer, so the user
            // encounters it after reading.
            if (letter.source == LetterSource.AI) {
                LetterReaderThumbsDown(
                    onFeedback = { reason -> onSaveFeedback(letter.date, reason) },
                )
            }
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
 * v0.26.2: the thumbs-down affordance on the reader. Renders
 * a `TextButton` ("👎 This got me wrong") that opens the
 * [LetterFeedbackDialog]. The button is only rendered for
 * AI letters — see [LetterReader] for the guard.
 */
@Suppress("FunctionNaming")
@Composable
private fun LetterReaderThumbsDown(
    onFeedback: (reason: String) -> Unit,
) {
    val dialogOpen = remember { mutableStateOf(false) }
    TextButton(
        onClick = { dialogOpen.value = true },
        modifier = Modifier
            .padding(bottom = Spacing.Snug)
            .semantics { role = Role.Button },
    ) {
        Text(stringResource(R.string.letters_thumbs_down))
    }
    if (dialogOpen.value) {
        LetterFeedbackDialog(
            onDismiss = { dialogOpen.value = false },
            onSave = { reason ->
                onFeedback(reason)
                dialogOpen.value = false
            },
        )
    }
}

/**
 * v0.26.2: the thumbs-down dialog. "Tell us what was off" is
 * the prompt; the reason `OutlinedTextField` is optional and
 * starts empty (the thumbs-down alone is a valid entry); the
 * Save button calls [onSave] with whatever the user wrote
 * (possibly the empty string). The dialog dismisses on Save
 * and on outside-tap / back press.
 */
@Suppress("FunctionNaming")
@Composable
private fun LetterFeedbackDialog(
    onDismiss: () -> Unit,
    onSave: (reason: String) -> Unit,
) {
    val reason = rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.letters_thumbs_down_prompt)) },
        text = {
            OutlinedTextField(
                value = reason.value,
                onValueChange = { reason.value = it },
                placeholder = { Text(stringResource(R.string.letters_thumbs_down_hint)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(reason.value) },
                modifier = Modifier.semantics { role = Role.Button },
            ) {
                Text(stringResource(R.string.letters_thumbs_down_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.semantics { role = Role.Button },
            ) {
                Text(stringResource(R.string.letters_cancel))
            }
        },
    )
}

/**
 * The top row of [LetterReader]: back on the left, the
 * `A-` / `A` / `A+` size toggle in the middle, and the delete
 * × on the right. Extracted from [LetterReader] to keep that
 * function under the LongMethod threshold and to make the
 * size-control slot obvious in the file.
 *
 * The toggle labels are A- / A / A+ — locale-safe (no string
 * resources), RTL-safe (the symbols mirror), and immediately
 * legible as "smaller / default / larger" without translation.
 * A real "Small / Medium / Large" would have needed three
 * string resources and a per-locale translation pass; A- / A / A+
 * is the same affordance at zero translation cost.
 */
@Suppress("FunctionNaming")
@Composable
private fun LetterReaderHeader(
    size: ReadingSize,
    onBack: () -> Unit,
    onSetSize: (ReadingSize) -> Unit,
    onDeleteRequest: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.Tight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onBack,
            // v0.25.10 (B6): Role.Button
            modifier = Modifier.semantics { role = Role.Button },
        ) { Text(stringResource(R.string.action_back)) }
        Spacer(modifier = Modifier.weight(1f))
        SingleChoiceSegmentedButtonRow {
            listOf(ReadingSize.SMALL, ReadingSize.MEDIUM, ReadingSize.LARGE)
                .forEachIndexed { i, s ->
                    SegmentedButton(
                        selected = size == s,
                        onClick = { onSetSize(s) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = 3),
                    ) {
                        Text(
                            text = when (s) {
                                ReadingSize.SMALL  -> "A-"
                                ReadingSize.MEDIUM -> "A"
                                ReadingSize.LARGE  -> "A+"
                                else -> "A"
                            },
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
        }
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
    TextButton(
        onClick = onBack,
        // v0.25.10 (B6): Role.Button
        modifier = Modifier.semantics { role = Role.Button },
    ) {
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
        text = { Text(stringResource(R.string.letters_delete_body)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                // v0.25.10 (B6): Role.Button
                modifier = Modifier.semantics { role = Role.Button },
            ) {
                Text(stringResource(R.string.letters_delete_button))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                // v0.25.10 (B6): Role.Button
                modifier = Modifier.semantics { role = Role.Button },
            ) {
                Text(stringResource(R.string.letters_delete_keep))
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
