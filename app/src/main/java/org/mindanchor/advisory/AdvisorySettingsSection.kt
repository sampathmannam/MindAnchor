package org.mindanchor.advisory

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.mindanchor.R
import org.mindanchor.settings.SettingsViewModel

/**
 * Program 3 Task 5 — two independent switches, both closed by default.
 *
 * These are configuration controls, not episode eligibility questions:
 * turning the master switch off hides any advisory card; turning
 * delivery off is the reachable kill switch — it prevents Start and
 * stops an active episode through
 * [EpisodeEventType.STOPPED_KILL_SWITCH]. Neither toggle records an
 * attestation; only a person's own Start tap on the evidence screen
 * ever does that.
 *
 * @wording-reviewed — clinical-review-required, see docs/CLINICAL_REVIEW.md.
 */
@Suppress("FunctionNaming")
@Composable
fun AdvisorySettingsSection(viewModel: SettingsViewModel) {
    val masterEnabled by viewModel.advisoryMasterEnabled.collectAsState()
    val deliveryAllowed by viewModel.advisoryDeliveryAllowed.collectAsState()

    AdvisorySwitchRow(
        title = stringResource(R.string.advisory_master_setting),
        checked = masterEnabled,
        onCheckedChange = viewModel::setAdvisoryMasterEnabled,
    )
    if (masterEnabled) {
        AdvisorySwitchRow(
            title = stringResource(R.string.advisory_delivery_setting),
            checked = deliveryAllowed,
            onCheckedChange = viewModel::setAdvisoryDeliveryAllowed,
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun AdvisorySwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
