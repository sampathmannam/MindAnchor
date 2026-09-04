package org.mindanchor.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.mindanchor.R
import org.mindanchor.admin.DeviceOwner
import org.mindanchor.admin.OsMode
import org.mindanchor.admin.OsModePrefs
import org.mindanchor.admin.OsModeState
import org.mindanchor.data.SunsetPrefs
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * v0.70 (master plan T-1.1) — the guided OS Mode surface.
 *
 * Sits directly under the "Enforced quiet hours" section in
 * Settings → Quiet, because the two are one arrangement read together:
 * the section above is the grant, this one is what the grant makes
 * possible.
 *
 * The contract is stated before the switch, both directions of it —
 * what arming closes, and every way out. That is the whole autonomy
 * law from CONCEPT 3.3: user-chosen list, user-chosen hours, an escape
 * hatch that works when judgement is gone, never imposed.
 *
 * The state machine is [OsMode.stateFor]; this composable only renders
 * it. [permissionEpoch] re-reads the system grant on every resume, the
 * same idiom the owner section above uses, so granting or revoking over
 * adb while the screen is open updates without a restart.
 */
@Suppress("FunctionNaming") // @Composable: PascalCase is the Compose convention.
@Composable
fun OsModeSection(
    permissionEpoch: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var enabled by remember { mutableStateOf(false) }
    LaunchedEffect(permissionEpoch) {
        enabled = runCatching { OsModePrefs(context).isEnabled() }.getOrDefault(false)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.osmode_section),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.osmode_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val isOwner = remember(permissionEpoch) { DeviceOwner.isDeviceOwner(context) }
        if (OsMode.stateFor(isOwner, enabled) == OsModeState.NotProvisioned) {
            Text(
                text = stringResource(R.string.osmode_not_provisioned),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            OsModeArmedRow(enabled = enabled)
            OsModeWindowNote(enabled = enabled)
            Text(
                text = stringResource(R.string.osmode_leaving),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The grants copy and the switch itself. Flipping applies immediately. */
@Suppress("FunctionNaming") // @Composable: PascalCase is the Compose convention.
@Composable
private fun OsModeArmedRow(enabled: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.osmode_grants),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.osmode_toggle),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = enabled,
                onCheckedChange = { checked ->
                    scope.launch {
                        // Write first, then sync: apply (or lift) immediately
                        // rather than waiting for the next alarm — a switch
                        // must do its thing the moment it moves.
                        OsModePrefs(context).setEnabled(checked)
                        OsMode.sync(context)
                    }
                },
            )
        }
    }
}

/** Which of "ready when you are" / "armed" applies, with the live hours. */
@Suppress("FunctionNaming") // @Composable: PascalCase is the Compose convention.
@Composable
private fun OsModeWindowNote(enabled: Boolean) {
    val context = LocalContext.current
    val sunsetPrefs = remember { SunsetPrefs(context) }
    val start by sunsetPrefs.startTime.collectAsState(initial = SunsetPrefs.DEFAULT_START)
    val end by sunsetPrefs.endTime.collectAsState(initial = SunsetPrefs.DEFAULT_END)
    val windowFormat = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT) }

    Text(
        text = stringResource(
            if (enabled) R.string.osmode_armed_note else R.string.osmode_available_note,
            start.format(windowFormat),
            end.format(windowFormat),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
