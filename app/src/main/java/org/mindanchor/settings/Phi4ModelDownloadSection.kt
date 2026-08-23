package org.mindanchor.settings

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.mindanchor.R
import org.mindanchor.narrate.Phi4ModelDownload

/**
 * The Phi-4 mini download section. v0.23.0.
 *
 * Renders a single "Download Phi-4 mini (Q4_K_M, 2.49 GB)"
 * button when no model is on file. On tap, the section
 * enqueues a system download via [DownloadManager] and
 * waits for the completion broadcast. When the system
 * fires [DownloadManager.ACTION_DOWNLOAD_COMPLETE], the
 * receiver swaps a [pendingDownloadUri] state to a
 * non-null [Uri] and the section renders a one-tap
 * "Use this as the narrate model?" prompt.
 *
 * The receiver is registered with the activity's
 * context (NOT_RECEIVER_EXPORTED) because the broadcast
 * is delivered only to the app that enqueued the
 * download. A receiver registered at the manifest level
 * would be reachable by the system but also by anything
 * that knows the receiver's action; registering
 * dynamically to a context-scoped filter is the safer
 * shape.
 *
 * ## Lifecycle
 *
 * [DisposableEffect] ties the receiver to the
 * composition. The receiver is unregistered when the
 * composable leaves the composition (back-pressed, the
 * user navigates away, the activity finishes). A
 * download that completes while the user is elsewhere
 * is delivered to a now-dead receiver, and the file
 * sits in the system Downloads collection until the
 * user navigates back: the system notification is
 * still there, and a future visit to this screen will
 * see the file via the file picker. We do not queue
 * imports across navigation.
 */
@Suppress("FunctionNaming") // @Composable: PascalCase is the Compose convention.
@Composable
fun Phi4ModelDownloadSection(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    var pendingDownloadUri by remember { mutableStateOf<Uri?>(null) }
    var enqueueMessage by remember { mutableStateOf<Int?>(null) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id != downloadId) return
                val manager = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                    ?: return
                val query = DownloadManager.Query().setFilterById(id)
                val cursor = runCatching { manager.query(query) }.getOrNull() ?: return
                cursor.use { c ->
                    if (c.moveToFirst()) {
                        val localUriString = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                        // Defense in depth: the
                        // downloadId match should already
                        // be enough, but if the user has
                        // another download in flight at
                        // the same time, the basename
                        // check keeps the receiver from
                        // acting on someone else's file.
                        if (Phi4ModelDownload.isPhi4File(localUriString)) {
                            val localUri = localUriString?.let(Uri::parse)
                            if (localUri != null) {
                                pendingDownloadUri = localUri
                            }
                        }
                    }
                }
            }
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // v0.25.8: deprecation banner. The on-device
        // Phi-4 path is the v0.23.0–v0.25.6 way of
        // writing a daily letter; v0.25.7 ships an
        // LLM-driven path (Settings → Reading →
        // Daily letter (LLM)) that is the recommended
        // one. Extracted into [Phi4LegacyBanner] so
        // the section function stays under the
        // detekt `LongMethod` ceiling (60 lines).
        Phi4LegacyBanner()
        TextButton(
            onClick = {
                val id = Phi4ModelDownload.enqueue(context)
                if (id != null) {
                    downloadId = id
                    enqueueMessage = R.string.model_download_enqueued
                } else {
                    enqueueMessage = R.string.model_download_failed
                }
            },
        ) {
            Text(stringResource(R.string.model_download))
        }

        enqueueMessage?.let { msgRes ->
            Text(
                text = stringResource(msgRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val readyUri = pendingDownloadUri
        if (readyUri != null) {
            Text(
                text = stringResource(R.string.model_download_prompt),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            TextButton(onClick = {
                viewModel.importModel(readyUri)
                pendingDownloadUri = null
            }) {
                Text(stringResource(R.string.model_download_use))
            }
            TextButton(onClick = { pendingDownloadUri = null }) {
                Text(stringResource(R.string.model_download_dismiss))
            }
        }
    }
}

/**
 * The download ID the user just enqueued. Module-level
 * state because the receiver (registered for the
 * lifetime of the composition) needs to know whether
 * the broadcast belongs to the download the user
 * initiated or to a previous one.
 *
 * The user initiates at most one download at a time on
 * this surface, so a single Long is enough; switching
 * to a Set would be the right move if the section ever
 * allows concurrent downloads.
 */
private var downloadId: Long = -1L

/**
 * v0.25.8: the deprecation banner shown at the top of
 * [Phi4ModelDownloadSection]. Extracted into its own
 * composable so the section function stays under the
 * detekt `LongMethod` ceiling.
 *
 * The banner is two short lines: a "On-device model
 * (legacy)" title and a one-paragraph explainer
 * pointing the user at the v0.25.7 LLM-driven path
 * (Settings → Reading → Daily letter (LLM)).
 */
@Suppress("FunctionNaming") // @Composable: PascalCase is the Compose convention.
@Composable
private fun Phi4LegacyBanner() {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(R.string.model_legacy_header),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = stringResource(R.string.model_legacy_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
