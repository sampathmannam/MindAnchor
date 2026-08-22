/*
 * v0.35.1 — Pair a watch step.
 *
 * Embeds the existing `SmartwatchesSection` (BLE scan, tap-to-
 * connect, "Always reconnect" switch). The user can pair one watch
 * or several, or skip the step entirely. Advancing the wizard
 * (whether by Skip or by Next) does not require a watch to be
 * paired — the DataSourcesCard on the home will show "no watch" and
 * the user can re-run the wizard from Settings at any time.
 *
 * The "next" button at the bottom is what the user uses to advance
 * after pairing. The "Skip" affordance advances without pairing.
 */
package org.mindanchor.onboarding.steps

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mindanchor.R
import org.mindanchor.settings.SmartwatchesSection

@Suppress("FunctionNaming")
@Composable
fun PairWatchStep(
    onSkip: () -> Unit,
    onDone: () -> Unit,
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
            text = stringResource(R.string.setup_wizard_pair_watch_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.setup_wizard_pair_watch_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        SmartwatchesSection()
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.setup_wizard_continue))
        }
        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.setup_wizard_skip))
        }
    }
}
