package org.mindanchor.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import org.mindanchor.report.Label
import org.mindanchor.report.Pattern
import org.mindanchor.report.Signal

/**
 * The v0.28+ (Phase 3 G-25) n-of-1 weekly pattern
 * discovery card.
 *
 * Shown on the home surface once a week (Sunday
 * evening, via WorkManager cron) when the launcher
 * has 14+ days of data and at least 3 data points
 * for a Signal/Label pair.
 *
 * The composer is a single sentence in the form
 * "this week, your X was higher when Y; your Z was
 * deepest on days W." The sentence uses the
 * project's `direction-bands` framing — never
 * "good" or "bad", never a causal claim, never
 * per-population. The user reads the fact, the
 * user is the only one who decides what the fact
 * means.
 *
 * ## Why one sentence
 *
 * A multi-sentence n-of-1 report reads as advice.
 * A single sentence reads as a fact. The
 * literature on personal-pattern displays
 * (Kahneman 2011 *Thinking Fast and Slow*, ch. 24;
 * Yan 2016; the project's n-of-1 framing) is
 * consistent: one sentence, direction bands,
 * "when" and "on days" — never "because".
 */
@Composable
fun NOfOnePatternsCard(
    patterns: List<Pattern>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (patterns.isEmpty()) return
    val sentence = composePatternSentence(patterns)
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
                text = "Your week, in one line",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = sentence,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "From your own data, in your own window. Never anyone else's.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Thanks")
            }
        }
    }
}

/**
 * The pure-function sentence composer. The
 * composer's output is deterministic for the same
 * input list, so the card's text is stable across
 * recompositions and across re-opens of the home
 * surface.
 */
private fun composePatternSentence(patterns: List<Pattern>): String {
    if (patterns.isEmpty()) return "Not enough data this week to say anything."
    val top = patterns.maxByOrNull { it.similarDays } ?: return ""
    val direction = if (top.medianWhenLikeToday > top.medianOverall) {
        "higher"
    } else if (top.medianWhenLikeToday < top.medianOverall) {
        "lower"
    } else {
        "about the same as"
    }
    val signal = top.signal.name.lowercase().replace('_', ' ')
    val label = top.label.name.lowercase()
    return "This week, your $label was $direction on days your $signal was ${"%.0f".format(top.medianWhenLikeToday)}, " +
        "vs ${"%.0f".format(top.medianOverall)} overall."
}

/**
 * The v0.28+ (Phase 3 G-26) wind-down mode Composable.
 *
 * Shown on the home surface after the user's
 * configured wind-down time (default 21:00,
 * overridable in Settings). The card tells the
 * user the time, and offers a one-tap "Begin"
 * affordance. The launcher is responsible for
 * actually applying the wind-down (warmer colour
 * temperature, optional grayscale, lower
 * notification volume).
 *
 * ## Why a Composable surface rather than an
 *   automatic transition
 *
 * The wind-down is opt-in. A user who is in the
 * middle of a 10pm game does not need the launcher
 * to dim the screen on a schedule. The card is
 * the same validate-then-suggest family the rest
 * of the protective layer uses: "if you would
 * like, the wind-down is here."
 */
@Composable
fun WindDownCard(
    onBegin: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                text = "If you would like, the wind-down is here.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Warmer colour, lower volume, fewer interruptions. " +
                    "You can dismiss and the home surface stays as it is.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onBegin,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Begin")
            }
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Not now")
            }
        }
    }
}

/**
 * The v0.28+ (Phase 3 G-29) gratitude card. Shown on
 * the home surface when the user is journaling or
 * before the bedtime list. The 1-tap text field
 * saves to the Letters store as a regular letter;
 * the long-press expands to a full Letter editor.
 *
 * ## Evidence
 *
 * Seligman 2005 (3 good things, weekly gratitude
 * letter, RCT): the active-constructive response
 * (writing, not just listing) was the highest-yield
 * form. The card is the active-constructive form.
 *
 * The card is a 1-tap field, not a multi-paragraph
 * textarea. The longer-form expansion is the
 * existing Letter editor — same pipeline, same
 * storage. The card is the entry point, not the
 * destination.
 */
@Composable
fun GratitudeCard(
    onSave: (String) -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    val canSave = text.isNotBlank()
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
                text = "What was the best moment today?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "One or two sentences. Seligman 2005: the active-constructive " +
                    "response — writing, not listing — is the highest-yield form.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = false,
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            AssistChip(
                onClick = { onSave(text); text = "" },
                enabled = canSave,
                label = { Text("Save as a Letter") },
            )
            AssistChip(
                onClick = onExpand,
                label = { Text("Open full editor") },
            )
        }
    }
}
