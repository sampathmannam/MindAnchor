package org.mindanchor.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mindanchor.friction.IfThenPlan

/**
 * The v0.27+ (Phase 2 G-17) per-app if-then-plan builder.
 *
 * A 3-field form: cue, action, default minutes. The user
 * pre-commits the moment of decision in advance, which
 * Gollwitzer 1999 (d=0.65, 94 studies, >8000 participants)
 * found the highest-yield form of the technique.
 *
 * The specificity nudge fires on a vague cue: a
 * placeholder text "if I'm about to open X" becomes
 * the affordance's label, and the form is invalid
 * (the gate does not pre-fill) when the cue is empty.
 *
 * ## Why a 3-field card, not a single textarea
 *
 * Three fields mirror the Linehan 1993 DBT-DESC
 * structure: Describe, Express, Specify. The user's
 * if-then is a structured tool, not a free-form
 * journal entry; three fields keep the structure
 * visible.
 */
@Composable
fun IfThenBuilderCard(
    appLabel: String,
    initial: IfThenPlan,
    onSave: (IfThenPlan) -> Unit,
    modifier: Modifier = Modifier,
) {
    var cue by remember { mutableStateOf(initial.cue) }
    var action by remember { mutableStateOf(initial.action) }
    var defaultMinutes by remember { mutableStateOf(initial.defaultMinutes?.toString() ?: "") }
    val canSave = cue.isNotBlank() && action.isNotBlank()
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "If-then for $appLabel",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Pre-commit the moment. A cue, a plan, a time-box.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = cue,
                onValueChange = { cue = it },
                label = { Text("If I am about to open…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = action,
                onValueChange = { action = it },
                label = { Text("Then I will…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = defaultMinutes,
                onValueChange = { defaultMinutes = it.filter { c -> c.isDigit() }.take(3) },
                label = { Text("Default minutes (blank = untimed)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                AssistChip(
                    onClick = {
                        onSave(
                            IfThenPlan(
                                cue = cue.trim(),
                                action = action.trim(),
                                defaultMinutes = defaultMinutes.toLongOrNull(),
                            ),
                        )
                    },
                    enabled = canSave,
                    label = { Text("Save plan") },
                )
            }
        }
    }
}
