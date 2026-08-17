@file:Suppress("FunctionNaming", "LongMethod")
package org.mindanchor.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.mindanchor.R
import org.mindanchor.ui.SkyContent
import java.text.DateFormat
import java.util.Date

/**
 * v0.33.0 / v0.35.0: the "Where it comes from" home card.
 *
 * Surfaces the three wearable sources the user has (or has not)
 * opted in to:
 *
 *  1. **Wearable** — Health Connect. The "any watch" surface.
 *     If HC is unavailable (no install, no permissions), the
 *     row is dim and says "Not connected. Tap to set up.".
 *  2. **Coros Pace 3** — the Coros side-channel. If the user
 *     has not connected, the row is dim and says the same
 *     "Not connected" line. If they have, the row says when
 *     the bridge last synced.
 *  3. **PPG** — the camera-based heart-rhythm measurement.
 *     Always present (the camera is always there); the row
 *     says when the last reading was taken or "No readings
 *     yet" if the user has never sat down with the lens.
 *
 * Research basis:
 *  - Apple Health's "preferred source" pattern (apptizo.com
 *    2026): the user should be able to see, at a glance,
 *    which device each reading came from. The card makes the
 *    pick visible on the home rather than burying it in
 *    settings.
 *  - Lindsay 2024 JMIR: showing the user their own data
 *    lifecycle ("last sync 2h ago") increases trust in the
 *    summary surface and reduces the "where did this number
 *    come from" hesitation that drives people to disengage.
 *
 * What the card is NOT:
 *  - Not a dashboard. No scores, no charts, no "your data
 *    is great / worrying" labels. The card is provenance.
 *  - Not a clinical-judgment surface. The wording never
 *    asserts that any source is "better" or "worse" than
 *    the others — per the v0.33.0 research audit.
 *
 * The card is hidden entirely when every source is
 * unavailable and there is no PPG data yet — the empty
 * state of the card would be the empty state of the user's
 * wearable story, and that is not a thing to surface on
 * the home before the user has done anything.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
fun DataSourcesCard(
    sky: SkyContent,
    healthConnectStatus: LauncherViewModel.HealthConnectStatus,
    corosDataStatus: LauncherViewModel.CorosDataStatus,
    ppgLastMeasurement: LauncherViewModel.PpgLastMeasurement?,
    onTapWearable: () -> Unit = {},
    onTapCoros: () -> Unit = {},
    onTapPpg: () -> Unit = {},
) {
    // Show nothing on the home when every source is
    // unavailable. A card with three "Not connected" lines
    // would invite the user to read the absence as a failure
    // of the launcher to do its job, not as the genuine
    // empty state it is.
    val anyRowVisible = healthConnectStatus is LauncherViewModel.HealthConnectStatus.Granted ||
        corosDataStatus is LauncherViewModel.CorosDataStatus.Connected ||
        corosDataStatus is LauncherViewModel.CorosDataStatus.ConnectedNoData ||
        ppgLastMeasurement != null
    if (!anyRowVisible) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.home_sources_title),
            style = MaterialTheme.typography.titleMedium,
            color = sky.textPrimary,
        )
        Text(
            text = stringResource(R.string.home_sources_caption),
            style = MaterialTheme.typography.bodySmall,
            color = sky.textSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        SourceRow(
            sky = sky,
            titleRes = R.string.home_sources_wearable,
            statusText = healthConnectStatusText(healthConnectStatus),
            onClick = onTapWearable,
        )
        SourceRow(
            sky = sky,
            titleRes = R.string.home_sources_coros,
            statusText = corosDataStatusText(corosDataStatus),
            onClick = onTapCoros,
        )
        SourceRow(
            sky = sky,
            titleRes = R.string.home_sources_ppg,
            statusText = ppgStatusText(ppgLastMeasurement),
            onClick = onTapPpg,
        )
    }
}

@Composable
private fun SourceRow(
    sky: SkyContent,
    titleRes: Int,
    statusText: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.bodyLarge,
            color = sky.textPrimary,
        )
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = sky.textSecondary,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * The user-facing status line for the Health Connect row.
 * The card never makes a clinical claim about the data —
 * it just says whether HC is on the device and whether
 * the launcher has permission to read it.
 */
@Composable
private fun healthConnectStatusText(status: LauncherViewModel.HealthConnectStatus): String =
    when (status) {
        is LauncherViewModel.HealthConnectStatus.Granted ->
            // We deliberately do not surface "Last reading X ago"
            // here — the wellness card already does that. The data-
            // sources card is provenance, not summary.
            stringResource(R.string.home_sources_wearable) + " — ready"
        is LauncherViewModel.HealthConnectStatus.NotGranted ->
            stringResource(R.string.home_sources_status_not_connected)
        is LauncherViewModel.HealthConnectStatus.Unavailable ->
            stringResource(R.string.home_sources_status_not_connected)
    }

/**
 * The Coros row's status line. "Not connected" when the user
 * has not opted in, "Last sync Nh ago" when the bridge has
 * run. The last-sync string is the existing
 * `home_sources_status_last_sync` with a formatted time.
 */
@Composable
private fun corosDataStatusText(status: LauncherViewModel.CorosDataStatus): String =
    when (status) {
        is LauncherViewModel.CorosDataStatus.NotConnected ->
            stringResource(R.string.home_sources_status_not_connected)
        is LauncherViewModel.CorosDataStatus.ConnectedNoData ->
            stringResource(R.string.home_sources_status_no_readings)
        is LauncherViewModel.CorosDataStatus.Connected -> {
            val stamp = remember(status.lastSyncEpochMs) {
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(status.lastSyncEpochMs))
            }
            stringResource(R.string.home_sources_status_last_sync, stamp)
        }
    }

/**
 * The PPG row's status line. "No readings yet" when the user
 * has never sat down with the camera, "Last reading Nh ago"
 * otherwise. PPG sessions are the only source whose timestamp
 * is the *measurement* time (Coros is the sync time, Health
 * Connect is the record-arrival time) — the wording reflects
 * that honestly.
 */
@Composable
private fun ppgStatusText(measurement: LauncherViewModel.PpgLastMeasurement?): String =
    if (measurement == null) {
        stringResource(R.string.home_sources_status_no_readings)
    } else {
        val stamp = remember(measurement.startEpochMs) {
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(measurement.startEpochMs))
        }
        stringResource(R.string.home_sources_status_last_reading, stamp)
    }
