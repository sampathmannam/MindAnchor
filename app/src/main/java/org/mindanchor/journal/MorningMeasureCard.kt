package org.mindanchor.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mindanchor.research.MorningMeasure

/**
 * The under-30-second morning check-in: five 1-5 ratings, one Save button.
 *
 * This is a personal research measure, not a diagnosis or clinical score —
 * the card only ever collects and shows the five raw values. No threshold,
 * red/green interpretation, or derived total is computed anywhere here.
 *
 * Placed in Journal Today (Task 6 wires it up); this file only needs to be
 * independently host-able and testable via Compose test.
 */
private data class MeasureDimension(
    val key: String,
    val label: String,
    val lowEndpoint: String,
    val highEndpoint: String,
)

private val DIMENSIONS = listOf(
    MeasureDimension(key = "mood", label = "Mood", lowEndpoint = "Low", highEndpoint = "High"),
    MeasureDimension(key = "anxiety", label = "Anxiety", lowEndpoint = "Calm", highEndpoint = "Tense"),
    MeasureDimension(
        key = "angerUrge",
        label = "Anger or urge to react",
        lowEndpoint = "Steady",
        highEndpoint = "Reactive",
    ),
    MeasureDimension(
        key = "energyFunction",
        label = "Energy / ability to function",
        lowEndpoint = "Depleted",
        highEndpoint = "Energized",
    ),
    MeasureDimension(key = "sleepQuality", label = "Sleep quality", lowEndpoint = "Poor", highEndpoint = "Great"),
)

@Composable
fun MorningMeasureCard(
    existing: MorningMeasure?,
    onSave: (mood: Int, anxiety: Int, angerUrge: Int, energyFunction: Int, sleepQuality: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember(existing) { mutableIntStateOf(if (existing == null) 1 else 0) }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Morning check-in",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "A personal research measure, not a diagnosis or clinical score.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (existing != null && editing == 0) {
                val values = listOf(
                    existing.mood,
                    existing.anxiety,
                    existing.angerUrge,
                    existing.energyFunction,
                    existing.sleepQuality,
                )
                DIMENSIONS.zip(values).forEach { (dimension, value) ->
                    Text(text = "${dimension.label}: $value / 5", style = MaterialTheme.typography.bodyMedium)
                }
                AssistChip(onClick = { editing = 1 }, label = { Text("Edit") })
            } else {
                var mood by remember(existing) { mutableIntStateOf(existing?.mood ?: 0) }
                var anxiety by remember(existing) { mutableIntStateOf(existing?.anxiety ?: 0) }
                var angerUrge by remember(existing) { mutableIntStateOf(existing?.angerUrge ?: 0) }
                var energyFunction by remember(existing) { mutableIntStateOf(existing?.energyFunction ?: 0) }
                var sleepQuality by remember(existing) { mutableIntStateOf(existing?.sleepQuality ?: 0) }

                SegmentedRow(DIMENSIONS[0], mood) { mood = it }
                SegmentedRow(DIMENSIONS[1], anxiety) { anxiety = it }
                SegmentedRow(DIMENSIONS[2], angerUrge) { angerUrge = it }
                SegmentedRow(DIMENSIONS[3], energyFunction) { energyFunction = it }
                SegmentedRow(DIMENSIONS[4], sleepQuality) { sleepQuality = it }

                val canSave = mood in 1..5 && anxiety in 1..5 && angerUrge in 1..5 &&
                    energyFunction in 1..5 && sleepQuality in 1..5
                Button(
                    onClick = { onSave(mood, anxiety, angerUrge, energyFunction, sleepQuality) },
                    enabled = canSave,
                ) {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun SegmentedRow(dimension: MeasureDimension, selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = dimension.label, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..5).forEach { n ->
                FilterChip(
                    selected = selected == n,
                    onClick = { onSelect(n) },
                    label = { Text("$n") },
                    modifier = Modifier.testTag("${dimension.key}_$n"),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = dimension.lowEndpoint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = dimension.highEndpoint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
