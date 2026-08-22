@file:Suppress("MagicNumber", "MaxLineLength", "FunctionNaming", "LongMethod")
package org.mindanchor.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.mindanchor.R
import java.time.LocalDate

/**
 * v0.28.0: the DBT diary card screen.
 *
 * Five fields (urge / emotion / intensity / skill / outcome) +
 * a Save button. The "this week" view is a list, not a chart
 * (per the v0.26.6 audit §2.3 BPD-safety rule).
 *
 * ## What this is and is not
 *
 * The card is the DBT standard mood-tracking surface (Linehan
 * 1993 ch. 11; Dimeff et al. 2011). It replaces the v0.27 EMA +
 * CheckIn shape. The card is *one* per day, not multiple. There
 * is no streak, no score, no chart. The intensity is 0–10
 * (DBT diary card convention). The skill field is optional.
 *
 * State is rememberSaveable so a config change (rotation)
 * keeps the draft. The save is fire-and-forget on the
 * composition scope (one card per day is the right cardinality
 * for this app's check-in pattern).
 */
@Composable
fun DiaryCardScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { DiaryCardPrefs(context.applicationContext) }

    var urge by rememberSaveable { mutableStateOf("") }
    var emotion by rememberSaveable { mutableStateOf("") }
    var intensity by rememberSaveable { mutableFloatStateOf(5f) }
    var skill by rememberSaveable { mutableStateOf("") }
    var outcome by rememberSaveable { mutableStateOf("") }
    var saved by rememberSaveable { mutableStateOf(false) }
    // v0.28.1: use mutableStateListOf so the list is a real
    // SnapshotStateList. The previous code declared a List<>
    // and cast it to SnapshotStateList inside LaunchedEffect,
    // which crashed the activity on the first save (ClassCastException
    // on an empty immutable list). The cast is gone; the list is
    // already the right type, and .clear() / .addAll() are the
    // public API.
    val week = remember {
        mutableStateListOf<Pair<LocalDate, DiaryCardEntry>>()
    }

    LaunchedEffect(saved) {
        // Refresh the "this week" surface after a save.
        if (saved) {
            week.clear()
            week.addAll(prefs.lastWeek())
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "DBT diary card. Five fields: urge, emotion, intensity, skill, outcome. " +
                    "Save at the bottom."
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.diary_card_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.diary_card_caption),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DiaryField(
                labelRes = R.string.diary_card_urge_label,
                hintRes = R.string.diary_card_urge_hint,
                value = urge,
                onValueChange = { urge = it },
            )
            DiaryField(
                labelRes = R.string.diary_card_emotion_label,
                hintRes = R.string.diary_card_emotion_hint,
                value = emotion,
                onValueChange = { emotion = it },
            )
            Text(
                text = stringResource(R.string.diary_card_intensity_label),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.diary_card_intensity_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = intensity.toInt().toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.CenterEnd)
                        .padding(end = 8.dp),
                )
                Slider(
                    value = intensity,
                    onValueChange = { intensity = it },
                    valueRange = 0f..10f,
                    steps = 9,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            DiaryField(
                labelRes = R.string.diary_card_skill_label,
                hintRes = R.string.diary_card_skill_hint,
                value = skill,
                onValueChange = { skill = it },
            )
            DiaryField(
                labelRes = R.string.diary_card_outcome_label,
                hintRes = R.string.diary_card_outcome_hint,
                value = outcome,
                onValueChange = { outcome = it },
            )
            TextButton(
                onClick = {
                    scope.launch {
                        prefs.save(
                            LocalDate.now(),
                            DiaryCardEntry(
                                urge = urge.ifBlank { null },
                                emotion = emotion.ifBlank { null },
                                intensity = intensity.toInt(),
                                skill = skill.ifBlank { null },
                                outcome = outcome.ifBlank { null },
                            ),
                        )
                        saved = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { role = Role.Button },
            ) { Text(stringResource(R.string.diary_card_save)) }
            if (saved) {
                Text(
                    text = stringResource(R.string.diary_card_saved),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // v0.28.0: history surface. List-shaped, not chart-shaped
            // (audit §2.3 — chart implies interpretation the project
            // is not allowed to make).
            Text(
                text = stringResource(R.string.diary_card_history),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp).semantics { heading() },
            )
            if (week.isEmpty() && !saved) {
                Text(
                    text = stringResource(R.string.diary_card_history_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                week.forEach { (date, entry) ->
                    Text(
                        text = "$date — ${entry.emotion ?: ""} (${entry.intensity ?: 0}/10)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            TextButton(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { role = Role.Button },
            ) { Text(stringResource(R.string.action_back)) }
        }
    }
}

@Composable
private fun DiaryField(
    labelRes: Int,
    hintRes: Int,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelRes)) },
        placeholder = { Text(stringResource(hintRes)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        minLines = 2,
    )
}
