package org.mindanchor.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Duration

/**
 * The v0.27+ (Phase 2 G-10) sleep window optimizer card.
 *
 * Shown on the home surface after 14+ days of sleep
 * data, offering to set the quiet hours to the user's
 * own median bedtime (Windred 2024, SRI is a stronger
 * mortality predictor than sleep duration) with a
 * 30-min wind-down (Scullin 2018, specificity drives
 * the effect).
 *
 * The card is a one-tap offer, never a directive. The
 * user can always keep the existing quiet hours.
 */
@Composable
fun SleepWindowOptimizerCard(
    medianBedtime: Duration,
    medianWakeTime: Duration,
    daysOfData: Int,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (daysOfData < 14) return
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(PaddingValues(16.dp)),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Your sleep is steady.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "$daysOfData days of data. Your median bedtime is " +
                    "${medianBedtime.toMinutes().coerceAtLeast(0) / 60}h" +
                    " and wake is " +
                    "${medianWakeTime.toMinutes().coerceAtLeast(0) / 60}h. " +
                    "If you would like, set the quiet hours to that window " +
                    "with a 30-min wind-down.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AssistChip(
                onClick = onApply,
                label = { Text("Use my median") },
            )
            AssistChip(
                onClick = onDismiss,
                label = { Text("Not now") },
            )
        }
    }
}
