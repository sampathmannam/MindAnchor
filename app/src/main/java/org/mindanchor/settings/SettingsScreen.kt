package org.mindanchor.settings

import android.app.role.RoleManager
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.mindanchor.R
import org.mindanchor.launcher.DisplayApp
import org.mindanchor.ui.NatureScene

/**
 * Minimal settings: default-launcher role, notification batching, hidden
 * apps, and a short honest "about". Everything else waits for its milestone.
 */
@Composable
fun SettingsScreen(
    allApps: List<DisplayApp>,
    hiddenApps: List<DisplayApp>,
    onUnhide: (DisplayApp) -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val activityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    val batchingEnabled by viewModel.batchingEnabled.collectAsState()
    val batchedApps by viewModel.batchedApps.collectAsState()

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
                        ?.let { activityLauncher.launch(it) }
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

        // --- Notification batching (F1) ---
        Text(
            text = stringResource(R.string.batching_section),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
        )
        Text(
            text = stringResource(R.string.batching_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!viewModel.hasNotificationAccess()) {
            TextButton(
                onClick = {
                    activityLauncher.launch(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                    )
                },
            ) {
                Text(stringResource(R.string.grant_notification_access))
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.batching_toggle),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = batchingEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            permissionLauncher.launch(
                                android.Manifest.permission.POST_NOTIFICATIONS,
                            )
                        }
                        viewModel.setBatchingEnabled(enabled)
                    },
                )
            }

            if (batchingEnabled) {
                TextButton(onClick = viewModel::releaseNow) {
                    Text(stringResource(R.string.digest_release_now))
                }
                Text(
                    text = stringResource(R.string.batching_choose_apps),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                allApps.forEach { app ->
                    val packageName = app.component.substringBefore('/')
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = packageName in batchedApps,
                            onCheckedChange = { viewModel.setAppBatched(packageName, it) },
                        )
                    }
                }
            }
        }

        // --- Home screen appearance ---
        val natureScene by viewModel.natureScene.collectAsState()
        Text(
            text = stringResource(R.string.appearance_section),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
        )
        Text(
            text = stringResource(R.string.appearance_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        listOf(
            NatureScene.ROTATE to R.string.scene_rotate,
            NatureScene.MEADOW to R.string.scene_meadow,
            NatureScene.WATER to R.string.scene_water,
            NatureScene.FOREST to R.string.scene_forest,
            NatureScene.OFF to R.string.scene_off,
        ).forEach { (scene, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setNatureScene(scene) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = natureScene == scene,
                    onClick = { viewModel.setNatureScene(scene) },
                )
                Text(
                    text = stringResource(label),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        // --- Sunset mode (F4) ---
        val sunsetEnabled by viewModel.sunsetEnabled.collectAsState()
        Text(
            text = stringResource(R.string.sunset_section),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
        )
        Text(
            text = stringResource(R.string.sunset_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!viewModel.hasDndAccess()) {
            TextButton(
                onClick = {
                    activityLauncher.launch(
                        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
                    )
                },
            ) {
                Text(stringResource(R.string.grant_dnd_access))
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.sunset_toggle),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = sunsetEnabled,
                    onCheckedChange = viewModel::setSunsetEnabled,
                )
            }
        }

        // --- Sleep rhythm (F5) ---
        val sleepSummary by viewModel.sleepSummary.collectAsState()
        Text(
            text = stringResource(R.string.sleep_section),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
        )
        if (!viewModel.hasUsageAccess()) {
            Text(
                text = stringResource(R.string.sleep_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = {
                    activityLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    viewModel.refreshSleep()
                },
            ) {
                Text(stringResource(R.string.grant_usage_access))
            }
        } else {
            val summary = sleepSummary
            if (summary == null || summary.windows.isEmpty()) {
                Text(
                    text = stringResource(R.string.sleep_no_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                summary.regularityScore?.let { score ->
                    Text(
                        text = stringResource(R.string.sleep_regularity, score),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                summary.windows.forEach { window ->
                    Text(
                        text = stringResource(
                            R.string.sleep_window_row,
                            window.wakeDate.toString(),
                            "%.1f".format(window.durationHours),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(R.string.sleep_regularity_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        // --- Hidden apps ---
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

        // --- Wellbeing pulse (F7) ---
        Text(
            text = stringResource(R.string.pulse_section),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
        )
        Text(
            text = stringResource(R.string.pulse_section_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = {
                context.startActivity(
                    Intent(context, org.mindanchor.pulse.PulseActivity::class.java),
                )
            },
        ) {
            Text(stringResource(R.string.pulse_take))
        }

        // --- Support / crisis resources ---
        Text(
            text = stringResource(R.string.crisis_section),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
        )
        Text(
            text = stringResource(R.string.crisis_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:988")),
                )
            },
        ) {
            Text(stringResource(R.string.crisis_us))
        }
        TextButton(
            onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:14416")),
                )
            },
        ) {
            Text(stringResource(R.string.crisis_india))
        }
        Text(
            text = stringResource(R.string.crisis_more),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = stringResource(R.string.about_text),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 32.dp),
        )
    }
}
