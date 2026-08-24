package org.mindanchor.prehome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * v0.26+ (spec Phase 1) — the doomscroll prompt
 * dialog. The `PreHomeActivity` shows this dialog
 * when the user tries to open a flagged app from
 * the moment-of-pause surface. The user picks one
 * of three actions: open anyway, hold (return to
 * the moment-of-pause surface), or pick different
 * (dismiss the dialog and the launch intent).
 *
 * ## Why "open" and not "ok"
 *
 * The KDoc in the spec is explicit: the wording is
 * "validate-then-suggest, never directive". The
 * label is the user's action, not the launcher's
 * verdict. "Open" is what the user wants to do;
 * "ok" is the launcher's approval. The user does
 * not need the launcher's approval to open the app.
 *
 * ## Why a Composable and not a DialogFragment
 *
 * The `PreHomeActivity` is Compose-first; the rest
 * of the launcher is migrating to Compose. A
 * Compose dialog is the path of least resistance.
 */
@Composable
fun DoomscrollPromptDialog(
    appLabel: String,
    onOpen: () -> Unit,
    onHold: () -> Unit,
    onPickDifferent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Column(
            modifier = Modifier.padding(PaddingValues(20.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "You were about to open $appLabel",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "If it would help, the launcher can hold for a moment. " +
                    "If you want to open it, that's fine too.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onHold,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Hold for a moment")
            }
            OutlinedButton(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open anyway")
            }
            TextButton(
                onClick = onPickDifferent,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Pick a different app")
            }
        }
    }
}
