/*
 * v0.37.0 — Polar step.
 *
 * Renamed from `CorosStep` in v0.37.0. The setup-wizard step
 * always hosted the Polar OAuth2 web bridge (`PolarSection`);
 * the class name was a pre-rename leftover from an earlier
 * Coros bridge that was removed. The function still calls
 * `PolarSection` and the user-visible copy is now "Your Polar
 * account" via the renamed `setup_wizard_polar_*` strings.
 *
 * Same "Continue" + "Skip" shape as PairWatchStep. The user
 * does not have to be signed in to advance — they can come
 * back from Settings at any time.
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
import org.mindanchor.settings.PolarSection

@Suppress("FunctionNaming")
@Composable
fun PolarStep(
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
            text = stringResource(R.string.setup_wizard_polar_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.setup_wizard_polar_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        PolarSection()
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
