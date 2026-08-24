package org.mindanchor.launcher

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import org.mindanchor.R
import org.mindanchor.friction.CompassionateWrapNotifier

/**
 * The renderer half of the compassionate-wrap event
 * bus. The host is a Compose [SnackbarHostState] that
 * collects the [CompassionateWrapNotifier.events] flow
 * and shows the message in the standard Snackbar
 * affordance. The Note / Dismiss actions are clinical-
 * review-passed (see the
 * `compassionate_wrap_*` strings in
 * `strings.xml`).
 *
 * v0.26+ (Phase 1 G-19).
 *
 * @param onNote called when the user taps the Note
 *   action — the caller writes the event to a
 *   Note or a Letter via
 *   [org.mindanchor.model.NoteClassifier]. The
 *   Notifier does not own the storage; the
 *   notifier owns the *trigger*, the storage
 *   owns the *content*.
 */
@Composable
fun CompassionateWrapHost(
    onNote: (CompassionateWrapNotifier.Event) -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
) {
    val state = remember { SnackbarHostState() }
    SnackbarHost(hostState = state, modifier = modifier)

    val noteLabel = stringResource(R.string.compassionate_wrap_note_action)
    val dismissLabel = stringResource(R.string.compassionate_wrap_dismiss_action)
    val template = stringResource(R.string.compassionate_wrap_snackbar)

    LaunchedEffect(Unit) {
        CompassionateWrapNotifier.events.collect { event ->
            val message = template.format(event.label, "${event.minutesSpent} min")
            val result = state.showSnackbar(
                message = message,
                actionLabel = noteLabel,
                duration = SnackbarDuration.Long,
                withDismissAction = true,
            )
            when (result) {
                SnackbarResult.ActionPerformed -> onNote(event)
                SnackbarResult.Dismissed -> Unit
            }
            // Suppress the unused warning for dismissLabel —
            // the system renders the "dismiss" affordance
            // automatically when withDismissAction = true.
            @Suppress("UNUSED_EXPRESSION")
            dismissLabel
        }
    }
}
