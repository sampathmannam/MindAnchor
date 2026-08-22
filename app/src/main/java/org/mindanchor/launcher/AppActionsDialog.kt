package org.mindanchor.launcher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.mindanchor.R

/**
 * Long-press actions for an app: favorite, rename, hide. Kept as a quiet
 * dialog — no destructive options, no red.
 */
@Composable
fun AppActionsDialog(
    app: DisplayApp,
    isFrictioned: Boolean,
    isAlwaysOpen: Boolean,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleHidden: () -> Unit,
    onToggleFriction: () -> Unit,
    onToggleAlwaysOpen: () -> Unit,
    onRename: (String?) -> Unit,
) {
    // v0.25.15: `renaming` and `newLabel` were `remember`-held
    // (Compose state lost on config change). Both are auto-Saveable
    // (Boolean and String) so a one-keyword swap to
    // `rememberSaveable` is the whole fix. The user is mid-typing
    // a custom name in the rename field; a config change should
    // not blank the field and re-open the long-press menu.
    var renaming by rememberSaveable { mutableStateOf(false) }
    var newLabel by rememberSaveable { mutableStateOf(app.label) }

    if (renaming) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.action_rename)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newLabel,
                        onValueChange = { newLabel = it },
                        // The field arrives pre-filled, so without a label
                        // a screen reader announces only the current
                        // contents — words that stop meaning anything the
                        // moment they are selected and retyped.
                        label = { Text(stringResource(R.string.rename_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Resetting belongs with the field it resets, not in the
                    // dismiss slot. Sitting there, it silently wiped a custom
                    // name for anyone who tapped the left button to back out.
                    TextButton(onClick = { onRename(null) }) {
                        Text(stringResource(R.string.action_reset_name))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onRename(newLabel) }) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(app.label) },
        text = {
            Column {
                TextButton(onClick = onToggleFavorite, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(
                            if (app.isFavorite) R.string.action_unfavorite
                            else R.string.action_favorite,
                        ),
                    )
                }
                TextButton(onClick = { renaming = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_rename))
                }
                TextButton(onClick = onToggleHidden, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(
                            if (app.isHidden) R.string.action_unhide
                            else R.string.action_hide,
                        ),
                    )
                }
                TextButton(onClick = onToggleFriction, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(
                            if (isFrictioned) R.string.action_remove_pause
                            else R.string.action_add_pause,
                        ),
                    )
                }
                // "Distracting" and "must reach me at 3am" are not
                // opposites. Someone on call marks their work messenger as
                // distracting for perfectly good reasons, and enforced
                // quiet hours would then close the channel their job runs
                // through. This is how they say so.
                TextButton(onClick = onToggleAlwaysOpen, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(
                            if (isAlwaysOpen) R.string.action_not_always_open
                            else R.string.action_always_open,
                        ),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}
