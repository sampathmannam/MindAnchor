package org.mindanchor.letters

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
    val today = LocalDate.now()
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
                    LetterRow(letter = letter, size = size, onSelect = onSelect, onDelete = onDelete)
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
}

@Suppress("FunctionNaming", "UnusedParameter")
@Composable
private fun LetterRow(
    letter: Letter,
    size: ReadingSize,
    onSelect: (LocalDate) -> Unit,
    onDelete: (LocalDate) -> Unit,
) {
    // TODO(task-4): replace with LetterRow implementation.
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
    // TODO(task-4): replace with LetterReader implementation.
}
