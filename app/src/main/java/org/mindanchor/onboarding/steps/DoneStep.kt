/*
 * v0.35.1 — Done step. The closing screen of the setup wizard.
 *
 * Three rows that mirror the `DataSourcesCard` on the home: each
 * row says whether the source is connected or not, and gives a
 * one-line path back to it. The "Done" button marks the wizard as
 * completed and finishes the activity.
 *
 * No "back" affordance — system back finishes the activity. The
 * user has finished the wizard, and the home is one tap away.
 *
 * v0.57.0: the four sources now render
 * as `Surface` cards with a leading
 * glyph, a label, and a trailing
 * "Set in Settings" status pill. The
 * pre-v0.57.0 design was a plain
 * two-column `Row` (label + state) —
 * the rows had no visual grouping, no
 * elevation, and no sense that the four
 * sources were a list of decisions the
 * user could revisit. The v0.57.0 cards
 * share the wizard's design language
 * with [WelcomeStep] (12dp corner
 * radius, surfaceVariant tint, 1dp
 * elevation) and use the same Unicode
 * glyphs so the user can see, at a
 * glance, that the four sources on the
 * welcome step and the four sources on
 * the done step are the same four
 * sources.
 */
package org.mindanchor.onboarding.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mindanchor.R

@Suppress("FunctionNaming")
@Composable
fun DoneStep(
    onFinish: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.setup_wizard_done_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.setup_wizard_done_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        // v0.57.0: each source is now a Surface card with a leading
        // glyph (matching WelcomeStep) and a trailing status pill.
        // The status pill is a small rounded Surface so the user
        // can see at a glance that the four sources are a list of
        // decisions and not a paragraph of text.
        DoneSourceCard(
            glyph = "♥",
            label = stringResource(R.string.setup_wizard_source_health_connect_label),
            state = stringResource(R.string.setup_wizard_done_set_in_settings),
        )
        DoneSourceCard(
            glyph = "◷",
            label = stringResource(R.string.setup_wizard_source_watch_label),
            state = stringResource(R.string.setup_wizard_done_set_in_settings),
        )
        DoneSourceCard(
            glyph = "◯",
            label = stringResource(R.string.setup_wizard_source_polar_label),
            state = stringResource(R.string.setup_wizard_done_set_in_settings),
        )
        DoneSourceCard(
            glyph = "◉",
            label = stringResource(R.string.setup_wizard_source_ppg_label),
            state = stringResource(R.string.setup_wizard_done_set_in_settings),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.setup_wizard_done_finish))
        }
    }
}

/**
 * v0.57.0: a single data-source card on the Done step. The glyph on
 * the leading edge matches the WelcomeStep card for the same source
 * so the user can see, at a glance, that the four sources on the
 * welcome step and the four sources on the done step are the same
 * four sources. The trailing status pill is a small rounded Surface
 * so the "Set in Settings" copy reads as a status badge, not a
 * floating label.
 */
@Composable
private fun DoneSourceCard(glyph: String, label: String, state: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = glyph,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            // v0.57.0: the trailing status pill.
            // A small rounded Surface with
            // `primaryContainer` so the user
            // sees the four sources as a list
            // of decisions, not a paragraph
            // of text. The pill's copy is the
            // same for all four sources on
            // the done step ("Set in
            // Settings") — the per-source
            // truth lives on the home's
            // DataSourcesCard.
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = state,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}
