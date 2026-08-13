package org.mindanchor.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mindanchor.R
import org.mindanchor.data.NotesPrefs
import org.mindanchor.note.ClassifierEnqueuer

/**
 * The "Re-classify all notes" settings section.
 * v0.25.0.
 *
 * The on-device classifier runs on every save
 * and on every body edit. A user who upgrades
 * the model, or who suspects a model glitch
 * has left a stale type on a note, can ask the
 * launcher to start over: every note's
 * `type` is reset to null, and the enqueuer is
 * asked to classify them all.
 *
 * The re-classification is a slow background
 * process — one model call per note, ~2-10s
 * each. The UI shows a "running" state
 * (button disabled, "in progress" copy) but
 * does not block the user from leaving the
 * screen. The enqueuer's scope outlives
 * `finish()` so a user who backs out of
 * Settings still gets the re-classification.
 *
 * The button is a one-tap action with a
 * confirmation dialog. The confirmation is
 * deliberate: a user with 1000 notes will
 * wait a long time for the re-classification
 * to drain, and an accidental tap is the
 * difference between "browse the notes
 * while the chips update over the next
 * 30 minutes" and "wait an hour for chips
 * to re-appear". The confirm dialog also
 * makes the cost visible.
 */
@Suppress("FunctionNaming") // @Composable: PascalCase is the Compose convention.
@Composable
fun NoteReclassifySection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showConfirm by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    val onConfirm: () -> Unit = {
        showConfirm = false
        running = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    runReclassify(context.applicationContext)
                }
            }
            // The "running" state is a
            // best-effort hint. The actual
            // work is on the enqueuer's
            // scope and may continue long
            // after the user backs out of
            // Settings. We clear `running`
            // immediately so the button is
            // usable again for a future
            // re-classify.
            running = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.note_reclassify_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(R.string.note_reclassify_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            enabled = !running,
            onClick = { showConfirm = true },
        ) {
            // v0.25.11 (B10): the label flips to
            // R.string.note_reclassify_running on tap; without
            // a polite live region a TalkBack user has to
            // navigate back to discover the state change.
            Text(
                text = stringResource(
                    if (running) R.string.note_reclassify_running
                    else R.string.note_reclassify_button
                ),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }

    if (showConfirm) {
        ReclassifyConfirmDialog(
            onConfirm = onConfirm,
            onDismiss = { showConfirm = false },
        )
    }
}

/**
 * The confirm dialog for the re-classify action.
 * Kept separate from [NoteReclassifySection] so the
 * section's Composable function stays under the
 * detekt LongMethod threshold.
 */
@Suppress("FunctionNaming") // @Composable: PascalCase is the Compose convention.
@Composable
private fun ReclassifyConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.note_reclassify_confirm_title)) },
        text = { Text(stringResource(R.string.note_reclassify_confirm_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.note_reclassify_confirm_yes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.note_reclassify_confirm_no))
            }
        },
    )
}

/**
 * The re-classify work itself, extracted so the
 * Composable stays small. Reads every note, resets
 * the type, and enqueues each note for fresh
 * classification. The actual model calls run on
 * the enqueuer's own scope and may continue long
 * after this function returns.
 */
private suspend fun runReclassify(context: Context) {
    val prefs = NotesPrefs(context)
    val state = prefs.notes.first()
    prefs.clearAllTypes()
    val enqueuer = ClassifierEnqueuer(context)
    enqueuer.enqueueAll(state.notes)
}
