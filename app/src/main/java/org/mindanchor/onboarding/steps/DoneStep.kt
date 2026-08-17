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
 */
package org.mindanchor.onboarding.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
fun DoneStep(
    onFinish: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 32.dp),
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
        SourceRow(
            label = stringResource(R.string.setup_wizard_source_health_connect_label),
            // Per-step state is read at compose time. The home's
            // DataSourcesCard will give the user the real per-source
            // status; this row is a one-line acknowledgement that
            // the user did or did not do each step.
            state = stringResource(R.string.setup_wizard_done_set_in_settings),
        )
        SourceRow(
            label = stringResource(R.string.setup_wizard_source_watch_label),
            state = stringResource(R.string.setup_wizard_done_set_in_settings),
        )
        SourceRow(
            label = stringResource(R.string.setup_wizard_source_polar_label),
            state = stringResource(R.string.setup_wizard_done_set_in_settings),
        )
        SourceRow(
            label = stringResource(R.string.setup_wizard_source_ppg_label),
            state = stringResource(R.string.setup_wizard_done_set_in_settings),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.setup_wizard_done_finish))
        }
    }
}

@Composable
private fun SourceRow(label: String, state: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = state,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
