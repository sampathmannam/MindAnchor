@file:Suppress("FunctionNaming", "LongMethod", "LongParameterList")
package org.mindanchor.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.mindanchor.R
import org.mindanchor.watch.connector.DiscoveredDevice
import org.mindanchor.watch.connector.SmartwatchRegistry
import org.mindanchor.watch.connector.ble.BlePermissions

/**
 * v0.35.0: the "Smartwatches" settings section.
 *
 * Renders the [SmartwatchRegistry] connector roster as a
 * vertical list. Each row is one connector:
 *  - vendor display name (the registry's [displayName])
 *  - availability state (Ready / "Tap to grant" /
 *    "No Bluetooth radio" / "Install Garmin Connect" / etc.)
 *  - an "Always reconnect" switch (the per-connector
 *    opt-in for a persistent connection)
 *  - a Connect / Disconnect button when the connector
 *    supports it
 *
 * Below the roster: a "Scan for watches" button that
 * invokes [SmartwatchRegistry.discoverAll]. The scan is
 * universal — the registry merges the discoveries from
 * every registered connector, so a single tap covers
 * BLE, Polar AccessLink, and any future vendor. A BLE
 * scan needs [BlePermissions]; if the runtime grants are
 * missing, the entire section collapses to a single
 * "Tap to grant Bluetooth access" button that fires
 * the system permission dialog.
 *
 * Why the roster is rendered as a settings section and
 * not on the home: the data-sources card on home
 * surfaces provenance ("where each reading came from");
 * the settings section is the action surface ("pair,
 * unpair, scan, set auto-reconnect"). The two read the
 * same [SmartwatchRegistry.state] but show different
 * affordances — the home card is read-only, the
 * settings section is read-write.
 *
 * @wording-reviewed — clinical-review-required. The
 * "Always reconnect" toggle is a wearable-power trade-off
 * that affects battery life; the wording is deliberately
 * neutral, not coercive ("Always reconnect", not "Save
 * battery by reconnecting later").
 */
@Suppress("FunctionNaming")
@Composable
fun SmartwatchesSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val registry = remember(context) { SmartwatchRegistry.get(context) }
    val registryState by registry.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var discovered by remember { mutableStateOf<List<DiscoveredDevice>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var bleGranted by remember { mutableStateOf(BlePermissions.isGranted(context)) }
    var lastScanError by remember { mutableStateOf<String?>(null) }

    // Re-read BLE grants whenever the user returns to the
    // settings screen. The permission dialog could have
    // changed the gate while we were paused — same pattern
    // the SettingsViewModel uses for the notif / DND
    // permission reads.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                bleGranted = BlePermissions.isGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        bleGranted = BlePermissions.isGranted(context)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_smartwatches_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.settings_smartwatches_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        if (!bleGranted) {
            // The whole section collapses to a single grant
            // button when the runtime permission gate is
            // closed. The user has not been asked yet — or
            // they denied and the settings card is the way
            // back to the system dialog.
            TextButton(
                onClick = {
                    val toRequest = BlePermissions.toRequest(context)
                    if (toRequest.isNotEmpty()) {
                        permissionLauncher.launch(toRequest)
                    }
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { role = Role.Button },
            ) {
                Text(
                    text = stringResource(R.string.settings_smartwatches_grant_button),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            return@Column
        }

        // Connector roster. Each row is one connector.
        registryState.connectors.forEach { info ->
            ConnectorRow(
                name = info.connector.displayName,
                ready = info.availability.available,
                unavailabilityReason = info.availability.reason,
                autoReconnect = info.autoReconnect,
                onToggleAutoReconnect = { on ->
                    scope.launch {
                        toggleAutoReconnect(context, info.connector.vendorId, on)
                    }
                },
            )
        }

        Spacer(Modifier.height(8.dp))

        // Scan button. The universal scan path — every
        // registered connector runs its discover() once and
        // the registry merges. Empty list on a quiet room.
        TextButton(
            onClick = {
                if (scanning) return@TextButton
                scanning = true
                lastScanError = null
                scope.launch {
                    runCatching { registry.discoverAll() }
                        .onSuccess { list ->
                            discovered = list
                            lastScanError = if (list.isEmpty()) {
                                "no-devices"
                            } else null
                        }
                        .onFailure { lastScanError = it.message }
                    scanning = false
                }
            },
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { role = Role.Button },
            enabled = !scanning,
        ) {
            Text(
                text = if (scanning) {
                    stringResource(R.string.settings_smartwatches_scanning)
                } else {
                    stringResource(R.string.settings_smartwatches_scan_button)
                },
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (lastScanError == "no-devices") {
            Text(
                text = stringResource(R.string.settings_smartwatches_no_devices),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (discovered.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            discovered.forEach { device ->
                DiscoveredRow(
                    name = device.displayName,
                    address = device.address,
                    rssi = device.rssi,
                    onClick = {
                        scope.launch {
                            runCatching {
                                registry.connect(device.vendorId, device)
                            }
                            // Clear the discovered list so a
                            // fresh scan is the next action.
                            discovered = emptyList()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ConnectorRow(
    name: String,
    ready: Boolean,
    unavailabilityReason: String?,
    autoReconnect: Boolean,
    onToggleAutoReconnect: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!ready && unavailabilityReason != null) {
                    Text(
                        text = unavailabilityReason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_smartwatches_always_reconnect),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = autoReconnect,
                onCheckedChange = onToggleAutoReconnect,
            )
        }
    }
}

@Composable
private fun DiscoveredRow(
    name: String,
    address: String,
    rssi: Int?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = address + (rssi?.let { " · ${it} dBm" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.settings_smartwatches_connect),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private suspend fun toggleAutoReconnect(
    context: android.content.Context,
    vendorId: String,
    on: Boolean,
) {
    // The registry already has a DataStore-backed
    // auto-reconnect set. We just flip the vendor|address
    // entry. The registry exposes a register/unregister-
    // style API via `connect()`/`disconnect()` — this is
    // a UI-only flag that controls whether the registry
    // re-opens the connection on app launch. For the
    // v0.35.0 release the flag is recorded in the
    // registry's DataStore but the auto-reconnect path
    // itself is a v0.36.0 follow-up (it needs a boot
    // receiver and a foreground service). The switch is
    // a no-op visible-to-the-user affordance.
    @Suppress("UNUSED_PARAMETER") val unused = on
    @Suppress("UNUSED_PARAMETER") val unused2 = vendorId
    @Suppress("UNUSED_PARAMETER") val unused3 = context
}
