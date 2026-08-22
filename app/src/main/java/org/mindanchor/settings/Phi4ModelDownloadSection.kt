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
import androidx.compose.runtime.LaunchedEffect
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
    var pendingDownloadUri by remember { mutableStateOf<Uri?>(null) }
    var existingDownloadUri by remember { mutableStateOf<Uri?>(null) }

    Phi4DownloadReceiver(
        onPhi4Downloaded = { uri -> pendingDownloadUri = uri },
    )
    ScanForExistingDownload(
        onFound = { uri -> existingDownloadUri = uri },
    )

    DownloadControls(
        viewModel = viewModel,
        existingDownloadUri = existingDownloadUri,
        onExistingUsed = { existingDownloadUri = null },
        pendingDownloadUri = pendingDownloadUri,
        onPendingUsed = { pendingDownloadUri = null },
    )
}

/**
 * The download button + the "your download is ready" /
 * "your existing file is ready" prompts. v0.30.1 split
 * out of [Phi4ModelDownloadSection] so the section
 * itself stays inside detekt's 60-line method cap.
 */
@Suppress("FunctionNaming") // @Composable: PascalCase is the Compose convention.
@Composable
private fun DownloadControls(
    viewModel: SettingsViewModel,
    existingDownloadUri: Uri?,
    onExistingUsed: () -> Unit,
    pendingDownloadUri: Uri?,
    onPendingUsed: () -> Unit,
) {
    val context = LocalContext.current
    var enqueueMessage by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ExistingDownloadOffer(
            uri = existingDownloadUri,
            onUse = { uri ->
                viewModel.importModel(uri)
                onExistingUsed()
            },
            onDismiss = onExistingUsed,
        )

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
                onPendingUsed()
            }) {
                Text(stringResource(R.string.model_download_use))
            }
            TextButton(onClick = onPendingUsed) {
                Text(stringResource(R.string.model_download_dismiss))
            }
        }
    }
}

/**
 * The "a Phi-4 file is already in your Downloads folder"
 * offer. v0.30.1. Renders nothing when [uri] is null.
 */
@Suppress("FunctionNaming") // @Composable: PascalCase is the Compose convention.
@Composable
private fun ExistingDownloadOffer(
    uri: Uri?,
    onUse: (Uri) -> Unit,
    onDismiss: () -> Unit,
) {
    if (uri == null) return
    Text(
        text = stringResource(R.string.model_existing_offer),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 4.dp),
    )
    TextButton(onClick = { onUse(uri) }) {
        Text(stringResource(R.string.model_existing_use))
    }
    TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.model_existing_dismiss))
    }
}

/**
 * Scans the public Downloads dir for an existing
 * Phi-4 file once per composition. v0.30.1 split
 * out of [Phi4ModelDownloadSection] so the section
 * itself stays inside detekt's 60-line method cap.
 *
 * The scan runs once; the result is held in the
 * caller's state, so the offer does not flicker
 * across recompositions. The caller's UI is
 * "no existing download found" when nothing is on
 * disk — the same state as before — and the user
 * can fall back to the regular download flow.
 */
@Suppress("FunctionNaming") // @Composable: PascalCase is the Compose convention.
@Composable
private fun ScanForExistingDownload(
    onFound: (Uri) -> Unit,
) {
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (done) return@LaunchedEffect
        val found = Phi4ModelDownload.findExistingDownload()
        if (found != null) onFound(found)
        // Mark done either way, so we don't re-scan
        // on every recomposition.
        done = true
    }
}

/**
 * The BroadcastReceiver that listens for a fresh
 * Phi-4 download to complete and surfaces its
 * local URI to [onPhi4Downloaded]. v0.30.1 split
 * out of [Phi4ModelDownloadSection] so the section
 * itself stays inside detekt's 60-line method cap.
 *
 * The receiver is registered with the activity's
 * context (RECEIVER_NOT_EXPORTED) because the
 * ACTION_DOWNLOAD_COMPLETE broadcast is delivered
 * only to the app that enqueued the download.
 */
@Suppress("FunctionNaming") // @Composable: PascalCase is the Compose convention.
@Composable
private fun Phi4DownloadReceiver(
    onPhi4Downloaded: (Uri) -> Unit,
) {
    val context = LocalContext.current
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
                                onPhi4Downloaded(localUri)
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
