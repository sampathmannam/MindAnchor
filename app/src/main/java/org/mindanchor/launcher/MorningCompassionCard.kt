package org.mindanchor.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mindanchor.R

/**
 * The morning self-compassion break card. Shown on
 * the first PreHome cold-start of the day, when the
 * user has opted in (the [FrictionPrefs.morningCompassionEnabled]
 * setting). The card is the entry point to a 90-second
 * micro-protocol that wraps the existing
 * [org.mindanchor.friction.BreathingProtocol] with the
 * framing from Neff 2003 / Linardon 2020.
 *
 * ## Why a card rather than a full-screen activity
 *
 * The 90-second duration is shorter than a deep-focus
 * ritual; a full-screen activity for 90 seconds is more
 * friction than the ritual itself. A card on the home
 * surface lets the user tap to expand the breath
 * protocol, or tap "Not now" to dismiss. The two
 * affordances are first-class siblings, not an "Are you
 * sure?" confirmation step.
 *
 * ## Evidence anchor
 *
 * Neff KD (2003) Self-compassion: an alternative
 * conceptualization of a healthy attitude toward oneself.
 * Linardon J (2020) meta-analysis of 27 RCTs of
 * smartphone-based self-compassion apps — distress
 * g=-0.32, self-compassion g=0.31 (95% CI 0.07–0.56).
 *
 * v0.26+ (Phase 1 G-21).
 */
@Composable
fun MorningCompassionCard(
    onStart: () -> Unit,
    onSkip: () -> Unit,
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
                text = stringResource(R.string.morning_compassion_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Three small breaths, a touch, a phrase.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Begin")
            }
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.morning_compassion_skip))
            }
        }
    }
}
