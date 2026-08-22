/*
 * v0.35.1 — Welcome step. First screen of the setup wizard.
 *
 * One card, three lines (one per data source), one "Begin" button.
 * No "back" affordance — the back behaviour for this step is
 * system back, which dismisses the wizard. No progress bar (YAGNI,
 * BPD-strict: the user does not need to count steps).
 *
 * v0.57.0: the four sources now render as
 * `Surface` cards with a small glyph on the
 * leading edge, a label, and a one-line
 * description. The pre-v0.57.0 design was
 * plain `Text` rows in a Column — the rows
 * had no visual grouping, no elevation, and
 * no affordance, so a user reading the
 * wizard for the first time could not tell
 * where one source ended and the next
 * began. The v0.57.0 cards share the
 * launcher's calm-launcher style (12dp
 * corner radius, surfaceVariant tint, 1dp
 * border) and use Unicode glyphs from the
 * system font for the four source icons —
 * no icon dependency.
 */
package org.mindanchor.onboarding.steps

import androidx.activity.compose.BackHandler
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
fun WelcomeStep(
    onContinue: () -> Unit,
    // v0.35.1: system back from the welcome step dismisses
    // the wizard. The activity owns the dismiss + finish
    // sequencing; the Composable is just the surface.
    onBack: () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.setup_wizard_welcome_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.setup_wizard_welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        // v0.57.0: each source is now a Surface card with a leading
        // glyph, a label, and a one-line description. The four cards
        // make the four choices obvious at a glance and the user
        // can scan the list (rather than reading each paragraph).
        SourceCard(
            glyph = "♥",
            label = stringResource(R.string.setup_wizard_source_health_connect_label),
            detail = stringResource(R.string.setup_wizard_source_health_connect_detail),
        )
        SourceCard(
            glyph = "◷",
            label = stringResource(R.string.setup_wizard_source_watch_label),
            detail = stringResource(R.string.setup_wizard_source_watch_detail),
        )
        SourceCard(
            glyph = "◯",
            label = stringResource(R.string.setup_wizard_source_polar_label),
            detail = stringResource(R.string.setup_wizard_source_polar_detail),
        )
        SourceCard(
            glyph = "◉",
            label = stringResource(R.string.setup_wizard_source_ppg_label),
            detail = stringResource(R.string.setup_wizard_source_ppg_detail),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.setup_wizard_begin))
        }
        Text(
            text = stringResource(R.string.setup_wizard_skip_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * v0.57.0: a single data-source card. One
 * leading glyph (24dp) on a tinted
 * `surfaceVariant` square, a label
 * (titleSmall), and a one-line description
 * (bodyMedium). The whole card is a Surface
 * with rounded corners so the four sources
 * read as four distinct units, not as a
 * single block of text.
 */
@Composable
private fun SourceCard(glyph: String, label: String, detail: String) {
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
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
