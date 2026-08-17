/*
 * v0.35.1 — Health Connect step.
 *
 * Requests the 8 HC read permissions via the system permission
 * contract. The user lands back on the wizard after granting (or
 * denying) — both outcomes are valid, both advance the wizard.
 *
 * The permission flow uses `rememberLauncherForActivityResult` with
 * the system `RequestMultiplePermissions` contract. The 8
 * permission strings are the same set `HealthConnectSource.PERMISSIONS`
 * uses for reading.
 *
 * No "back" affordance — system back goes to Welcome.
 */
package org.mindanchor.onboarding.steps

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

@Suppress("FunctionNaming")
@Composable
fun HealthConnectStep(
    onSkip: () -> Unit,
    onDone: () -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        // Both granted and denied advance the wizard. A user who
        // denied is still on the home — the DataSourcesCard will
        // tell them they have not connected HC and they can re-run
        // the wizard from Settings.
        onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.setup_wizard_health_connect_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.setup_wizard_health_connect_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.setup_wizard_health_connect_what),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { launcher.launch(HEALTH_CONNECT_PERMISSIONS) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.setup_wizard_health_connect_grant))
        }
        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.setup_wizard_skip))
        }
    }
}

/**
 * The 8 Health Connect read permissions the launcher requests. The
 * list mirrors `HealthConnectSource.PERMISSIONS` — if a future version
 * adds a record there, add the permission here.
 *
 * The names are string literals because the Health Connect
 * permissions are declared in AndroidManifest.xml as custom
 * `android.permission.health.*` permissions and are not in the
 * `android.Manifest.permission` SDK constants. The runtime
 * permission request goes through the standard
 * `RequestMultiplePermissions` contract with the string names;
 * the system routes them to the Health Connect provider.
 */
private val HEALTH_CONNECT_PERMISSIONS: Array<String> = arrayOf(
    "android.permission.health.READ_HEART_RATE",
    "android.permission.health.READ_RESTING_HEART_RATE",
    "android.permission.health.READ_HEART_RATE_VARIABILITY",
    "android.permission.health.READ_SLEEP",
    "android.permission.health.READ_STEPS",
    "android.permission.health.READ_EXERCISE",
    "android.permission.health.READ_TOTAL_CALORIES_BURNED",
    "android.permission.health.READ_MINDFULNESS",
)
