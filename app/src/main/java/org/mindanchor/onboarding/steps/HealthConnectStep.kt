/*
 * v0.35.2 — Health Connect step.
 *
 * Requests the 8 HC read permissions via the explicit Health
 * Connect intent. The contract lives in
 * `vitals/HealthConnectRequestContract.kt` so the Settings →
 * Sources → Health Connect section uses the same launcher (and
 * the same "remember the contract" fix that the v0.23.0 launcher
 * cache applied to the previous contract).
 *
 * Why a custom contract (and not the SDK one):
 *   `androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()`
 *   delegates to the system `RequestMultiplePermissions` contract on
 *   Android 14+ (verified in the SDK 1.1.0 bytecode — the
 *   `HealthPermissionsRequestModuleContract` constructor wraps
 *   `ActivityResultContracts.RequestMultiplePermissions`). The system
 *   contract dismisses itself immediately because
 *   `android.permission.health.*` are not standard runtime
 *   permissions; the system has no UI to render and closes the
 *   dialog in ~50ms, advancing the wizard without granting anything.
 *
 *   The right UI is the dedicated Health Connect controller
 *   (`com.google.android.healthconnect.controller` on Android 14+,
 *   `com.google.android.apps.healthdata` on 13 and below). We pick
 *   the right package at runtime and launch it directly. The
 *   result is read back via `getGrantedPermissions()` from the SDK
 *   after the user returns — that avoids depending on the result
 *   Intent, which the dedicated UI does not always set.
 *
 * The 8 permission strings come from `HealthConnectSource.PERMISSIONS`
 * (filtered to those the current provider can actually supply) so
 * the Settings "change what is shared" flow and the wizard stay in
 * lockstep.
 *
 * The user lands back on the wizard after granting (or denying) —
 * both outcomes are valid, both advance the wizard. A user who
 * denied is still on the home — the DataSourcesCard will tell them
 * they have not connected HC and they can re-run the wizard from
 * Settings.
 *
 * No "back" affordance — system back goes to Welcome.
 */
package org.mindanchor.onboarding.steps

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mindanchor.R
import org.mindanchor.vitals.HealthConnectRequestPermissionsContract
import org.mindanchor.vitals.HealthConnectSource

@Suppress("FunctionNaming")
@Composable
fun HealthConnectStep(
    onSkip: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = HealthConnectRequestPermissionsContract(),
    ) { _ ->
        // Whatever happened, advance the wizard. If the user granted
        // some permissions, the next Sync will surface them. If they
        // denied, the home's DataSourcesCard will say "no sources
        // connected" and they can re-run the wizard from Settings.
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
            onClick = {
                val perms = HealthConnectSource.effectivePermissions(context)
                if (perms.isEmpty()) {
                    onDone()
                } else {
                    launcher.launch(perms)
                }
            },
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
