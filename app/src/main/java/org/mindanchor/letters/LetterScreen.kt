package org.mindanchor.letters

import androidx.compose.runtime.Composable
import java.time.LocalDate
import org.mindanchor.reader.ReadingSize

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
    // TODO(task-3): replace with LetterInbox implementation.
}

@Suppress("FunctionNaming")
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
