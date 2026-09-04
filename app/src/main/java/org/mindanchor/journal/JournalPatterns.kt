package org.mindanchor.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mindanchor.research.MorningMeasure

// The exact key set StructuralContextExtractor produces (Task 3). Program 0
// shows the fact *keys* only — never a value pulled from private text, and
// never anything beyond what StructuralContextExtractor actually records.
private val STRUCTURAL_FACT_KEYS = listOf("entry_kind", "local_date", "word_count", "user_title")

/**
 * The Patterns destination: transparent counts only — days written, words
 * written, the morning-measure history, and the structural fact keys that
 * exist. Never the raw journal body, never a context row's value rendered
 * as if it were the person's own writing, and no streaks, rewards, or
 * distress colouring anywhere.
 *
 * Renders exactly the two headings and the exact "no inferences" copy the
 * Task 6 brief specifies, so nothing here can silently start implying an
 * inference Program 0 does not make.
 */
@Composable
fun JournalPatterns(
    entries: List<JournalEntry>,
    morningMeasureHistory: List<MorningMeasure>,
    modifier: Modifier = Modifier,
) {
    val daysWritten = entries.map { it.localDate }.distinct().size
    val wordsWritten = entries.sumOf { entry ->
        entry.body.trim().split(Regex("\\s+")).count { it.isNotBlank() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "From your writing",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(text = "Days written: $daysWritten")
        Text(text = "Words written: $wordsWritten")

        Text(text = "Morning check-in history", style = MaterialTheme.typography.titleMedium)
        if (morningMeasureHistory.isEmpty()) {
            Text(text = "Nothing recorded yet.", style = MaterialTheme.typography.bodySmall)
        } else {
            morningMeasureHistory.forEach { measure ->
                Text(
                    text = "${measure.localDate} — mood ${measure.mood}/5, anxiety ${measure.anxiety}/5, " +
                        "anger/urge ${measure.angerUrge}/5, energy ${measure.energyFunction}/5, " +
                        "sleep ${measure.sleepQuality}/5",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Text(text = "Structural facts recorded", style = MaterialTheme.typography.titleMedium)
        STRUCTURAL_FACT_KEYS.forEach { key -> Text(text = key, style = MaterialTheme.typography.bodySmall) }

        Text(
            text = "Inferences",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.testTag("journal_patterns_inferences_heading"),
        )
        Text(text = "No inferences are created in Program 0.")
    }
}
