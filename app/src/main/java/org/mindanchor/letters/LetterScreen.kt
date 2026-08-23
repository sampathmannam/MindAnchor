package org.mindanchor.letters

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import org.mindanchor.R
import org.mindanchor.llm.LetterError
import org.mindanchor.reader.ReadingSize
import org.mindanchor.ui.Spacing

/**
 * v0.25.7: the letter surface is now LLM-driven. The
 * shape is 4 states: [LetterWriteState.Idle] (inbox),
 * [LetterWriteState.Writing] (calm screen),
 * [LetterWriteState.Reader] (letter shown), and
 * [LetterWriteState.Error] (Try again / Open settings).
 *
 * The v0.25.2-A reader body is unchanged — same paper
 * card, same disclaimer — with a new metadata footer
 * line appended below the disclaimer for LLM-driven
 * letters. The v0.25.2-A inbox past-letters list is
 * preserved behind a pinned "Today" row that is the
 * primary affordance (Write today's letter / Open /
 * Regenerate).
 *
 * The new params (`writeState` + 5 callbacks) have
 * default values so the existing call site
 * (`HomeScreen` → `LetterScreen`) keeps compiling
 * until Task 13 wires the LLM state machine. `modelFits`
 * stays in the signature for the legacy Generate-now
 * path (not used in the LLM path).
 *
 * Haptics follow Brewster CHI 2007: `LongPress` on
 * commit actions (Write / Open / Try-again / Open
 * settings) and `TextHandleMove` on stop/replace
 * gestures (Regenerate / Cancel).
 *
 * `modelFits` is kept in the signature for the v0.25.2-A
 * Generate-now contract even though the LLM path does not
 * consume it; the call site in `HomeScreen` passes it, and
 * removing it would be a breaking change to the public API
 * until Task 13 (HomeScreen changes) consolidates the wiring.
 */
@Suppress("FunctionNaming", "LongParameterList", "UnusedParameter")
@Composable
fun LetterScreen(
    letters: List<Letter>,
    modelFits: Boolean,
    date: LocalDate?,
    size: ReadingSize = ReadingSize.MEDIUM,
    writeState: LetterWriteState = LetterWriteState.Idle,
    onSelect: (LocalDate) -> Unit = {},
    onBack: () -> Unit = {},
    onDelete: (LocalDate) -> Unit = {},
    onSetSize: (ReadingSize) -> Unit = {},
    onWriteToday: () -> Unit = {},
    onRegenerate: () -> Unit = {},
    onCancelWrite: () -> Unit = {},
    onRetryError: () -> Unit = {},
    onOpenLlmSettings: () -> Unit = {},
) {
    if (date != null) {
        // v0.25.2-A path: the user opened a specific date
        // from the past-letters list. The new path
        // (LetterWriteState.Reader) carries its letter
        // explicitly; both converge on LetterReader.
        LetterReader(
            letter = letters.firstOrNull { it.date == date },
            size = size,
            onBack = onBack,
            onDelete = { onDelete(date) },
            onSetSize = onSetSize,
        )
    } else when (writeState) {
        is LetterWriteState.Writing -> LetterWritingScreen(
            onCancel = onCancelWrite,
        )
        is LetterWriteState.Error -> LetterErrorScreen(
            error = writeState.error,
            onRetry = onRetryError,
            onOpenSettings = onOpenLlmSettings,
        )
        is LetterWriteState.Reader -> LetterReader(
            letter = writeState.letter,
            size = size,
            onBack = onBack,
            onDelete = { onDelete(writeState.letter.date) },
            onSetSize = onSetSize,
        )
        is LetterWriteState.Idle -> LetterInbox(
            letters = letters,
            size = size,
            onSelect = onSelect,
            onDelete = onDelete,
            onBack = onBack,
            onWriteToday = onWriteToday,
            onRegenerate = onRegenerate,
        )
    }
}

// -- Inbox (Idle) --

/**
 * The v0.25.7 inbox. Pinned "Today" row on top
 * (write / open / regenerate) followed by the
 * v0.25.2-A past-letters list. The Generate-now
 * button is gone — the Today row's `Write today's
 * letter` is the new primary affordance, and the
 * `Open` / `Regenerate` buttons appear once a
 * letter exists for today.
 *
 * The delete dialog lives here, not in the row:
 * only one confirm can be open at a time, and
 * dismissing it must clear the state cleanly. The
 * row's × button sets the pending date; the
 * dialog's confirm calls `onDelete` and clears it.
 *
 * `LongMethod` is suppressed: the function carries
 * the pinned Today row, the past-letters grouped
 * list, and the delete-dialog host. Splitting the
 * body into a separate `LetterInboxContent`
 * sub-Composable was the v0.25.2-A shape, but the
 * v0.25.7 wiring (haptic wrappers around
 * `onWriteToday` and `onRegenerate` so the state
 * stays one frame closer to the surface) reads
 * cleaner when the Today row, the past-letters
 * loop, and the dialog state share the same
 * `pendingDelete` slot.
 */
@Suppress("FunctionNaming", "LongMethod", "LongParameterList")
@Composable
private fun LetterInbox(
    letters: List<Letter>,
    size: ReadingSize,
    onSelect: (LocalDate) -> Unit,
    onDelete: (LocalDate) -> Unit,
    onBack: () -> Unit,
    onWriteToday: () -> Unit,
    onRegenerate: () -> Unit,
) {
    val pendingDelete = remember { mutableStateOf<LocalDate?>(null) }
    val haptics = LocalHapticFeedback.current
    val today = LocalDate.now()
    val todayLetter = letters.firstOrNull { it.date == today }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.Edge),
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.action_back))
        }
        Text(
            text = stringResource(R.string.letter_singular),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(vertical = Spacing.Comfortable),
        )
        Text(
            text = stringResource(R.string.letter_inbox_explainer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.Loose),
        )
        // Pinned Today row — primary affordance.
        TodayRow(
            today = today,
            todayLetter = todayLetter,
            onWriteToday = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onWriteToday()
            },
            onRegenerate = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onRegenerate()
            },
            onSelect = onSelect,
        )
        // Past letters: keep the v0.25.2-A shape (newest
        // first, grouped by friendly-letter-date). The
        // `letters.reversed()` pattern is preserved so the
        // existing inbox finding-test still pins the
        // ordering. The Today row is excluded because
        // it lives in its own pinned slot.
        val pastLetters = letters.reversed().filter { it.date != today }
        if (pastLetters.isEmpty()) {
            Text(
                text = stringResource(R.string.letter_cant_write_explainer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.Loose),
            )
        } else {
            val grouped = pastLetters.groupBy { friendlyLetterDate(it.date, today) }
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
                        onDelete = { pendingDelete.value = it },
                    )
                }
            }
        }
    }
    val pendingDeleteDate = pendingDelete.value
    if (pendingDeleteDate != null) {
        // v0.25.5 WP-G: haptic confirmation on letter delete.
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
 * The pinned Today row. When today's letter is absent:
 * the `letter_no_letter_today` line and a primary
 * `Write today's letter` button. When present: a
 * one-line preview (tap = open) plus a row of
 * `Open` / `Regenerate` buttons. The preview is
 * the first line of the body, truncated at 80 chars
 * (no ellipsis — the reader carries the full body).
 *
 * `today` is plumbed through even though the body does
 * not consume it yet — the slot is reserved for v0.25.7+
 * follow-ups (e.g. showing a "Posted 2h ago" line based
 * on the system clock without re-reading the clock inside
 * the Composable). The caller in [LetterInbox] already
 * has the value, so passing it is free.
 */
@Suppress(
    "FunctionNaming",
    "LongMethod",
    "LongParameterList",
    "UnusedParameter",
)
@Composable
private fun TodayRow(
    today: LocalDate,
    todayLetter: Letter?,
    onWriteToday: () -> Unit,
    onRegenerate: () -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .padding(vertical = Spacing.Snug),
    ) {
        Text(
            text = stringResource(R.string.letter_today),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(Spacing.Tight))
        if (todayLetter == null) {
            Text(
                text = stringResource(R.string.letter_no_letter_today),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = Spacing.Tight),
            )
            Button(
                onClick = onWriteToday,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.letter_write_today))
            }
        } else {
            Text(
                text = todayLetter.body.lineSequence().firstOrNull().orEmpty().take(80),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSelect(todayLetter.date)
                    }
                    .padding(bottom = Spacing.Tight),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Tight),
            ) {
                OutlinedButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSelect(todayLetter.date)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.letter_open_existing))
                }
                TextButton(
                    onClick = onRegenerate,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.letter_regenerate))
                }
            }
        }
    }
}

/**
 * The calm full-screen surface shown while the LLM
 * is generating today's letter. The `~2 seconds`
 * hint is a one-shot estimate, not a countdown —
 * we don't want the user to feel watched. The
 * Cancel button at the bottom is a `TextHandleMove`
 * haptic (Brewster 2007: stop/replace gesture).
 */
@Suppress("FunctionNaming")
@Composable
private fun LetterWritingScreen(onCancel: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(Spacing.Edge),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.letter_writing),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(Spacing.Tight))
            Text(
                text = stringResource(R.string.letter_writing_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCancel()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Spacing.Loose),
        ) {
            Text(stringResource(R.string.letters_cancel))
        }
    }
}

/**
 * The calm full-screen surface shown when letter
 * generation fails. The `userMessage` is the
 * 1-line explanation the LLM error mapping picked
 * (e.g. "API key not valid. Open settings to
 * fix."); the buttons depend on `isRetryable` —
 * `Try again` is only shown when retrying might
 * work (rate-limit, timeout, network). `Open
 * settings` is always shown so the user can fix
 * the configuration regardless of which error
 * fired.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun LetterErrorScreen(
    error: LetterError,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(Spacing.Edge),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.letter_couldnt_write),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = Spacing.Tight),
            )
            Text(
                text = error.userMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.Loose),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                if (error.isRetryable) {
                    OutlinedButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onRetry()
                        },
                    ) {
                        Text(stringResource(R.string.letter_try_again))
                    }
                }
                OutlinedButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onOpenSettings()
                    },
                ) {
                    Text(stringResource(R.string.letter_open_settings))
                }
            }
        }
    }
}

// -- Reader (unchanged from v0.25.2-A, +metadata footer for LLM letters) --

/**
 * The delete-confirm surface. Lives in the same
 * file as `LetterInbox` because the call site
 * (the inbox's row × button) is what makes the
 * dialog appear. [date] is plumbed through so the
 * call site doesn't have to restructure when the
 * dialog body eventually includes the date.
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

/**
 * The v0.25.2-A reader body. v0.25.7 adds a
 * metadata footer line below the disclaimer when
 * `letter.provider != null` (i.e. the letter was
 * written by an LLM, not the legacy on-device
 * Phi-4 model). The footer is a quiet
 * `labelSmall` showing the provider, model, token
 * counts, generation time, and the friendly date.
 *
 * The pending-delete flag lives here, not in the
 * screen or the inbox: only one confirm can be
 * open at a time, and dismissing it must clear
 * the state cleanly. The header's × button sets
 * the flag; the dialog's confirm calls `onDelete`
 * and clears it.
 *
 * `LongMethod` is suppressed for the v0.25.7
 * metadata footer (one extra branch on the body).
 * The function was already 60 lines at the
 * detekt threshold before the footer landed; the
 * footer would push it to 61. Splitting the body
 * into a separate `LetterReaderBody` sub-Composable
 * would isolate the change but at the cost of
 * reading 60 lines across two files for what is
 * one quiet metadata line. The suppression keeps
 * the v0.25.2-A shape intact.
 */
@Suppress("FunctionNaming", "LongMethod", "UnusedParameter")
@Composable
private fun LetterReader(
    letter: Letter?,
    size: ReadingSize,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onSetSize: (ReadingSize) -> Unit,
) {
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
            Text(
                text = stringResource(R.string.letters_disclaimer),
                style = readerDisclaimerStyle(MaterialTheme.typography, size),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // v0.25.7: LLM metadata footer. Renders only
            // for letters that came from an LLM
            // (`provider != null`); legacy v0.25.2-A
            // letters have all-null metadata and skip
            // this block, keeping the v0.25.2-A surface
            // byte-for-byte unchanged.
            if (letter.provider != null) {
                Text(
                    text = stringResource(
                        R.string.letter_footer_format,
                        letter.provider,
                        letter.model.orEmpty(),
                        letter.promptTokens ?: 0,
                        letter.completionTokens ?: 0,
                        (letter.durationMs ?: 0L) / 1000.0,
                        friendlyLetterDate(letter.date, LocalDate.now()),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.Loose),
                )
            }
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
 * The top row of [LetterReader]: back on the left,
 * the `A-` / `A` / `A+` size toggle in the middle,
 * and the delete × on the right. Extracted from
 * [LetterReader] to keep that function under the
 * LongMethod threshold and to make the size-control
 * slot obvious in the file.
 *
 * The toggle labels are A- / A / A+ — locale-safe
 * (no string resources), RTL-safe (the symbols
 * mirror), and immediately legible as "smaller /
 * default / larger" without translation.
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
        TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
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
 * The soft empty state for when the letter was
 * deleted while the reader was open. No retry,
 * no recovery — just a clear signpost back to the
 * inbox.
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
 * The reader-level delete confirm. Mirrors the
 * inbox's [LetterDeleteDialog] shape; separated so
 * the `pendingDelete: Boolean` state lives
 * entirely in [LetterReader] (the dialog doesn't
 * need to know what kind of state is tracking the
 * pending action).
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
 * Reader title style. `headlineMedium` shape with
 * `fontSize = size.sp * 1.75f` and `fontWeight = Light`.
 * The 1.75x ratio is deliberate: a 14sp body (SMALL)
 * needs a ~24sp title to read as a heading; 18sp body
 * needs ~32sp; 32sp body needs ~56sp. The ratio is
 * what keeps the title larger than the body at every
 * size.
 *
 * v0.25.2-B spec 2 §"3 sizes" — WCAG 2.2 SC 1.4.4
 * (Resize Text) requires that text scale up to 200%
 * without loss of content or functionality. The
 * LARGE size here (32sp body) is exactly 200% of
 * the 16sp reference body — the maximum the SC
 * explicitly permits — and the 1.75x title ratio
 * lands at 56sp, well below the screen-height line
 * where vertical scrolling would be required to see
 * the title.
 *
 * The [typography] parameter is the active
 * [androidx.compose.material3.Typography]; the call
 * site passes `MaterialTheme.typography` so the title
 * inherits font family and other shape fields from
 * the theme. The function is **not** `@Composable`
 * itself — accepting the Typography as a parameter
 * keeps the size math pure and testable from a regular
 * JUnit test, without the Compose runtime overhead of
 * `runComposeUiTest`.
 */
internal fun readerTitleStyle(typography: Typography, size: ReadingSize): TextStyle =
    typography.headlineMedium.copy(
        fontSize = (size.sp * 1.75f).sp,
        fontWeight = FontWeight.Light,
    )

/**
 * Reader body style. `bodyLarge` shape with
 * `fontSize = size.sp` (the size IS the font — that's
 * the whole point of the user-toggle) and
 * `lineHeight = size.sp * 1.45f` (the typography
 * guideline for sustained reading: ~1.4-1.5x line
 * height keeps the eye moving down the column without
 * the lines feeling cramped).
 *
 * Not `@Composable` — see [readerTitleStyle] for why
 * Typography is a parameter rather than a runtime read.
 */
internal fun readerBodyStyle(typography: Typography, size: ReadingSize): TextStyle =
    typography.bodyLarge.copy(
        fontSize = size.sp.sp,
        lineHeight = (size.sp * 1.45f).sp,
    )

/**
 * Reader disclaimer style (the "this is not a
 * substitute for…" line under every letter body).
 * `bodySmall` shape with `fontSize = size.sp * 0.85f` —
 * a hair smaller than the body, just enough to read
 * as fine print without crossing into "too small to
 * read" territory. The 0.85x ratio holds across the
 * three sizes so the disclaimer always reads as a
 * fraction quieter than the body, never as a different
 * surface.
 *
 * Not `@Composable` — see [readerTitleStyle].
 */
internal fun readerDisclaimerStyle(typography: Typography, size: ReadingSize): TextStyle =
    typography.bodySmall.copy(
        fontSize = (size.sp * 0.85f).sp,
    )
