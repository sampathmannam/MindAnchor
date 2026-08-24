package org.mindanchor.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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

/**
 * The v0.27+ (Phase 2 G-24) TIPP crisis-survival card.
 *
 * TIPP is the DBT distress-tolerance crisis-survival
 * protocol (Temperature, Intense exercise, Paced
 * breathing, Paired muscle relaxation). Linehan 1993
 * DBT Skills Training Manual, 2nd ed., Week 12 of
 * the DBT-SG schedule (PMC12344970).
 *
 * Shown on the home surface when the user has logged
 * a low WHO-5 score for 3+ consecutive days, or has
 * opened PreHome 5+ times in an hour (the
 * "self-checking" stress signal). The card is
 * validate-then-suggest, never directive.
 *
 * ## The four contraindications
 *
 * Before the user picks an exercise, the card shows
 * the four contraindications as a single footer
 * line. The four contraindications are:
 *
 *  1. **Temperature** — cardiovascular conditions
 *     (vasovagal sensitivity, Raynaud's).
 *  2. **Intense exercise** — cardiac conditions,
 *     recent surgery, pregnancy.
 *  3. **Paced breathing** — pulmonary conditions
 *     (COPD), low blood pressure.
 *  4. **Paired muscle relaxation** — none absolute;
 *     relative in low-back pain or acute injury.
 *
 * The wording is the same four-item list in the
 * project's clinical-review allowlist; the gate
 * blocks the wording change without clinician
 * sign-off (docs/research/17-pre-merge-ci-gate.md).
 */
@Composable
fun TippCard(
    onPick: (choice: TippChoice) -> Unit,
    onDismiss: () -> Unit,
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
                text = "If it would help, a TIPP skill is here.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Pick one. The four TIPP options, in plain English.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TippChoice.values().forEach { choice ->
                    AssistChip(
                        onClick = { onPick(choice) },
                        label = { Text(choice.label) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(
                text = "Skip any that don't fit: cardiovascular for cold/ice; cardiac / surgery / pregnancy for intense exercise; pulmonary / low BP for paced breathing; low-back pain / acute injury for paired muscle relaxation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                AssistChip(
                    onClick = onDismiss,
                    label = { Text("Not now") },
                )
            }
        }
    }
}

/**
 * The four TIPP exercises, in the canonical Linehan
 * order. Each has a one-line [description] that the
 * project can show to a clinician and the user alike.
 */
enum class TippChoice(
    val label: String,
    val description: String,
) {
    TEMPERATURE(
        "Cold water on the face",
        "30 to 60 seconds of cold water on the cheeks/forehead; the dive reflex is the fastest parasympathetic switch.",
    ),
    INTENSE_EXERCISE(
        "Move hard for a minute",
        "Run, jump, sprint up stairs; the catecholamine burst interrupts rumination.",
    ),
    PACED_BREATHING(
        "Breathe out longer than in",
        "Inhale 4 seconds, exhale 6 to 8 seconds; the longer exhale is the parasympathetic cue.",
    ),
    PAIRED_MUSCLE_RELAXATION(
        "Tense and release",
        "Tense a muscle group for 5 seconds, then release for 10; pair the release with the exhale.",
    ),
}
