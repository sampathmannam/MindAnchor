package org.mindanchor.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mindanchor.friction.PerAppSessionLength

/**
 * The v0.27+ (Phase 2 G-16) per-app session-length row.
 *
 * Shown on the home surface's app-actions sheet when the
 * user opens a flagged app and the launcher has a stored
 * per-app length. The row is the `5/10/20` button bar
 * (the documented minimum time-box row, Adhikari 2023
 * PNAS) with a "Like last time — N min" affordance when
 * the user has previously picked one, and a "Learn this
 * for next time" toggle that persists the choice into
 * [PerAppSessionLength] via the gate's existing
 * onTimeBoxChosen callback.
 *
 * ## Why a card rather than a row of buttons
 *
 * The four chips are the foreground. The subtitle and
 * the toggle are the
 * validate-then-suggest family the rest of the
 * protective layer uses — "Like last time" is an
 * offer, "Learn this" is opt-in. A card carries both
 * affordances without crowding the gate's existing
 * primary action.
 */
@Composable
fun PerAppSessionLengthRow(
    appLabel: String,
    currentLength: PerAppSessionLength,
    learnThisTimeDefault: Boolean,
    onPick: (minutes: Long?) -> Unit,
    onToggleLearn: (Boolean) -> Unit,
    onForget: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                text = "Time-box for $appLabel",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Pick a time. The launcher will nudge you when it's up.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(5L, 10L, 20L).forEach { minutes ->
                    AssistChip(
                        onClick = { onPick(minutes) },
                        label = { Text("$minutes min") },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Learn this for next time",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Off by default. On persists the time-box.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = learnThisTimeDefault,
                    onCheckedChange = onToggleLearn,
                )
            }
            if (currentLength != PerAppSessionLength()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    AssistChip(
                        onClick = onForget,
                        label = { Text("Forget this default") },
                    )
                }
            }
        }
    }
}
