package org.mindanchor.settings

import android.app.role.RoleManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mindanchor.R
import org.mindanchor.launcher.DisplayApp

/**
 * Minimal settings: become the default launcher, manage hidden apps, and a
 * short honest "about". Everything else waits for its milestone.
 */
@Composable
fun SettingsScreen(
    hiddenApps: List<DisplayApp>,
    onUnhide: (DisplayApp) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.action_back))
        }

        Text(
            text = stringResource(R.string.settings),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(vertical = 16.dp),
        )

        val roleManager = context.getSystemService(RoleManager::class.java)
        val isDefault = roleManager?.isRoleHeld(RoleManager.ROLE_HOME) == true
        if (!isDefault) {
            TextButton(
                onClick = {
                    roleManager?.createRequestRoleIntent(RoleManager.ROLE_HOME)
                        ?.let { roleLauncher.launch(it) }
                },
            ) {
                Text(stringResource(R.string.set_default_launcher))
            }
        } else {
            Text(
                text = stringResource(R.string.is_default_launcher),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = stringResource(R.string.hidden_apps),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
        )
        if (hiddenApps.isEmpty()) {
            Text(
                text = stringResource(R.string.no_hidden_apps),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            hiddenApps.forEach { app ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onUnhide(app) }) {
                        Text(stringResource(R.string.action_unhide))
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.about_text),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 32.dp),
        )
    }
}
