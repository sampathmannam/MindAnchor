package org.mindanchor.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.mindanchor.research.MorningMeasure

private val DATE_HEADER_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())

/**
 * The Today destination: a calm date header and a writing card (title +
 * body), the Task 5 morning check-in card, and Save. Apple Journal-inspired
 * means calm/uncluttered layout only — no reused assets, icons, or
 * wordmarks.
 */
@Composable
fun JournalToday(
    today: LocalDate,
    title: String,
    body: String,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onSave: () -> Unit,
    savedConfirmation: Boolean,
    saveError: Boolean,
    morningMeasure: MorningMeasure?,
    onSaveMorningMeasure: (mood: Int, anxiety: Int, angerUrge: Int, energyFunction: Int, sleepQuality: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = today.format(DATE_HEADER_FORMATTER),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.testTag("journal_date_header"),
        )
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Title") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("journal_title_field"),
        )
        OutlinedTextField(
            value = body,
            onValueChange = onBodyChange,
            label = { Text("What's on your mind?") },
            minLines = 6,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("journal_body_field"),
        )
        Button(
            onClick = onSave,
            enabled = body.isNotBlank(),
            modifier = Modifier.testTag("journal_save_button"),
        ) {
            Text("Save")
        }
        if (savedConfirmation) {
            Text(
                text = "Context prepared",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("journal_saved_confirmation"),
            )
        }
        if (saveError) {
            Text(
                text = "That didn't save. Your words are still here — try again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("journal_save_error"),
            )
        }
        MorningMeasureCard(existing = morningMeasure, onSave = onSaveMorningMeasure)
    }
}
