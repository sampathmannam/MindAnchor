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
import androidx.compose.runtime.remember
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
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleHidden: () -> Unit,
    onRename: (String?) -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    var newLabel by remember { mutableStateOf(app.label) }

    if (renaming) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.action_rename)) },
            text = {
                OutlinedTextField(
                    value = newLabel,
                    onValueChange = { newLabel = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { onRename(newLabel) }) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { onRename(null) }) {
                    Text(stringResource(R.string.action_reset_name))
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
