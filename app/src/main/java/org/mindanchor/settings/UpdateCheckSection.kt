package org.mindanchor.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import org.mindanchor.R
import org.mindanchor.update.UpdateChecker

/**
 * v0.30+ (security audit 2026-08-24) — the silent
 * GitHub Releases check that shipped in v0.25.9 was
 * a privacy contract violation. The user's check
 * affordance on Settings → About no longer
 * phones home; the button now opens the
 * [UpdateChecker.RELEASES_URL] in the user's
 * default browser. The status row (idle / newer /
 * failed) and the silent check at app start are
 * gone — there is no status to display because
 * the launcher does not make an outbound call.
 *
 * The button is preserved because it is the
 * documented distribution affordance for an
 * alpha cohort (the launcher is not on Play
 * Store yet; the sideload pattern is the
 * project's release channel). The launcher's
 * contribution to the distribution is the button;
 * the user's contribution is the browser
 * navigation to the releases page.
 */
@Composable
fun UpdateCheckSection() {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.check_for_updates),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.check_for_updates_subtitle_v030),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = {
                UpdateChecker(context.applicationContext).openReleasesPage()
            },
        ) {
            Text(stringResource(R.string.open_releases_page))
        }
    }
}
