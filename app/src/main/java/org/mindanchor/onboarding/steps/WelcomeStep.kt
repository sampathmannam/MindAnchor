/*
 * v0.35.1 — Welcome step. First screen of the setup wizard.
 *
 * One card, three lines (one per data source), one "Begin" button.
 * No "back" affordance — the back behaviour for this step is
 * system back, which dismisses the wizard. No progress bar (YAGNI,
 * BPD-strict: the user does not need to count steps).
 */
package org.mindanchor.onboarding.steps

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
            .padding(horizontal = 32.dp, vertical = 32.dp),
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
        SourceLine(
            label = stringResource(R.string.setup_wizard_source_health_connect_label),
            detail = stringResource(R.string.setup_wizard_source_health_connect_detail),
        )
        SourceLine(
            label = stringResource(R.string.setup_wizard_source_watch_label),
            detail = stringResource(R.string.setup_wizard_source_watch_detail),
        )
        SourceLine(
            label = stringResource(R.string.setup_wizard_source_coros_label),
            detail = stringResource(R.string.setup_wizard_source_coros_detail),
        )
        SourceLine(
            label = stringResource(R.string.setup_wizard_source_ppg_label),
            detail = stringResource(R.string.setup_wizard_source_ppg_detail),
        )
        Spacer(modifier = Modifier.height(16.dp))
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

@Composable
private fun SourceLine(label: String, detail: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
