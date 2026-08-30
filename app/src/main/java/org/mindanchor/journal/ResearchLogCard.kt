package org.mindanchor.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import org.mindanchor.research.LedgerEventKind
import org.mindanchor.research.MAX_LEDGER_NOTE_LENGTH
import org.mindanchor.research.ResearchLedgerEvent

/**
 * One chip: the kind it records, its label, and the prompt on its dialog.
 *
 * @wording-reviewed — every string in this file reaches a person
 * recording something about their own health, including a medication
 * change. Changes are clinical-review-required.
 */
private data class LogKind(val kind: LedgerEventKind, val label: String, val prompt: String)

private val KINDS = listOf(
    LogKind(LedgerEventKind.SHIFT_SCHEDULE, "Shift or duty", "What was the shift?"),
    LogKind(LedgerEventKind.EXERCISE, "Exercise", "What did you do?"),
    LogKind(LedgerEventKind.ILLNESS, "Illness", "What's going on?"),
    LogKind(LedgerEventKind.CAFFEINE, "Caffeine", "Anything worth noting?"),
    LogKind(LedgerEventKind.MEDICATION_CHANGE, "Medication change", "What changed?"),
    LogKind(LedgerEventKind.LIFE_EVENT, "Life event", "What happened?"),
    LogKind(LedgerEventKind.ADVERSE_OR_UNINTENDED_EFFECT, "Something felt worse", "What happened?"),
)

/**
 * The research log: the things that might explain a day, recorded by the
 * person in their own words.
 *
 * Three rules this card keeps:
 *
 *  - It records; it never interprets. There is no scoring, no summary, no
 *    "you've logged this a lot lately".
 *  - A medication change is recorded and nothing else. The dialog says so
 *    in plain words, because the one thing a person might reasonably fear
 *    from an app that knows about their medication is that it will have an
 *    opinion about it.
 *  - Nothing here can be edited or deleted. The rows are append-only in
 *    the database, so offering an affordance that cannot work would be a
 *    lie; a test asserts no edit or delete control exists.
 *
 * The subtitle says nothing *interprets* what you write, which is true.
 * It deliberately does not say the notes are private: they leave the
 * device in plaintext the moment somebody exports, and the export's own
 * consent dialog is where that is spelled out.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("FunctionNaming")
fun ResearchLogCard(
    todaysEvents: List<ResearchLedgerEvent>,
    onRecord: (LedgerEventKind, String) -> Unit,
    recordError: Boolean,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf<LogKind?>(null) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("research_log_card"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "What else about today?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Things that might explain a day. Recorded for research only — " +
                    "nothing here is advice, and nothing interprets what you write.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KINDS.forEach { logKind ->
                    AssistChip(
                        onClick = { open = logKind },
                        label = { Text(logKind.label) },
                        modifier = Modifier
                            .semantics { contentDescription = "Record ${logKind.label}" }
                            .testTag("research_log_chip_${logKind.kind.name}"),
                    )
                }
            }
            RecordedToday(todaysEvents)
            if (recordError) {
                Text(
                    text = "That didn't save. Try again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("research_log_error"),
                )
            }
        }
    }

    open?.let { logKind ->
        RecordDialog(
            logKind = logKind,
            onDismiss = { open = null },
            onRecord = { note ->
                open = null
                onRecord(logKind.kind, note)
            },
        )
    }
}

/**
 * Today's rows, read-only. Deliberately no edit and no delete: the table
 * they live in rejects both, and an affordance that cannot work is worse
 * than none.
 */
@Composable
@Suppress("FunctionNaming")
private fun RecordedToday(events: List<ResearchLedgerEvent>) {
    if (events.isEmpty()) return
    Column(
        modifier = Modifier.testTag("research_log_today"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        events.sortedByDescending { it.sequence }.forEach { event ->
            val label = KINDS.firstOrNull { it.kind == event.kind }?.label ?: event.kind.name
            Text(
                text = if (event.note.isEmpty()) label else "$label — ${event.note}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("research_log_entry_${event.kind.name}"),
            )
        }
    }
}

@Composable
@Suppress("FunctionNaming")
private fun RecordDialog(logKind: LogKind, onDismiss: () -> Unit, onRecord: (String) -> Unit) {
    var note by remember(logKind) { mutableStateOf("") }
    val tooLong = note.length > MAX_LEDGER_NOTE_LENGTH

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(logKind.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (logKind.kind == LedgerEventKind.MEDICATION_CHANGE) {
                    Text(
                        text = "MindAnchor records that something changed. It does not give " +
                            "medication advice.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("research_log_medication_notice"),
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(logKind.prompt) },
                    supportingText = {
                        Text(
                            if (tooLong) {
                                "Too long by ${note.length - MAX_LEDGER_NOTE_LENGTH} characters."
                            } else {
                                "Optional. Your words, kept as you write them."
                            },
                        )
                    },
                    isError = tooLong,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("research_log_note_field"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onRecord(note) },
                enabled = !tooLong,
                modifier = Modifier.testTag("research_log_save"),
            ) {
                Text("Record")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("research_log_cancel")) {
                Text("Cancel")
            }
        },
    )
}
