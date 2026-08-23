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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import org.mindanchor.R
import org.mindanchor.update.UpdateChecker
import org.mindanchor.update.UpdateInfo

/**
 * v0.25.9 (auto-update): the user-driven "Check for
 * updates" affordance on Settings → About. Always
 * rendered; the button is the trigger and the status
 * row below the button reports the result of the most
 * recent check (idle / newer / failed).
 *
 * The silent check at app start (see [HomeActivity]) uses
 * the same [UpdateChecker] and writes to the same
 * [org.mindanchor.update.UpdatePrefs] cache, so a tap here
 * is a force-refresh — the cache is bypassed and the
 * network is hit unconditionally.
 */
@Composable
fun UpdateCheckSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf<Status>(Status.Idle) }

    fun runCheck() {
        scope.launch {
            status = Status.Checking
            try {
                val info = UpdateChecker(context.applicationContext).check()
                status = if (info != null) Status.Newer(info) else Status.UpToDate
            } catch (_: Exception) {
                status = Status.Failed
            }
        }
    }

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
        when (val s = status) {
            Status.Idle -> Text(
                text = "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Status.Checking -> Text(
                text = "…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Status.UpToDate -> Text(
                text = stringResource(R.string.check_for_updates_idle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is Status.Newer -> {
                Text(
                    text = stringResource(R.string.check_for_updates_newer, s.info.version),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, s.info.url.toUri()),
                        )
                    }
                }) {
                    Text(stringResource(R.string.open_release))
                }
            }
            Status.Failed -> Text(
                text = stringResource(R.string.check_for_updates_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        OutlinedButton(
            onClick = { runCheck() },
            enabled = status !is Status.Checking,
        ) {
            Text(stringResource(R.string.check_for_updates))
        }
    }
}

/**
 * Three explicit states. `Idle` is the initial state
 * before any check has run in this session; once a
 * check has run, the state becomes either `UpToDate`
 * (latest = current, no action) or `Newer(info)`
 * (a release page URL to open). `Failed` means a
 * network or parse error and the user can retry.
 */
sealed interface Status {
    data object Idle : Status
    data object UpToDate : Status
    data class Newer(val info: UpdateInfo) : Status
    data object Failed : Status
    data object Checking : Status
}
