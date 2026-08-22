@file:Suppress("MagicNumber", "MaxLineLength", "FunctionNaming", "LongMethod")
package org.mindanchor.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.mindanchor.R

/**
 * v0.28.0: the letter-to-a-part screen (IFS, Schwartz 1995).
 *
 * Three sub-screens via a state var:
 *   PICK    — which part is loudest
 *   TO      — write to the part
 *   FROM    — write from the part back to you (optional)
 *
 * The letter is in `rememberSaveable` so a config change (rotation)
 * keeps the draft. Nothing is saved on disk; the activity is the
 * boundary.
 *
 * ## BPD-safety
 *
 * No directive language. The "from the part" view is an
 * invitation, not a requirement. The Done button dismisses
 * without consequence. No score, no log, no streak.
 */
@Composable
fun LetterToPartScreen(onDone: () -> Unit) {
    var screen by rememberSaveable { mutableStateOf(LetterScreen.PICK) }
    var chosenPart by rememberSaveable { mutableStateOf<String?>(null) }
    var letter by rememberSaveable { mutableStateOf("") }
    var reply by rememberSaveable { mutableStateOf("") }
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "Letter to a part. " +
                    "Pick which part is loudest, then write to it. " +
                    "Optionally switch to writing from the part back to you."
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (screen) {
                LetterScreen.PICK -> LetterPickScreen(
                    onPick = { key, label ->
                        chosenPart = label
                        screen = LetterScreen.TO
                    },
                    onClose = onDone,
                )
                LetterScreen.TO -> LetterToScreen(
                    partName = chosenPart ?: "",
                    letter = letter,
                    onLetterChange = { letter = it },
                    onSwitchToFrom = { screen = LetterScreen.FROM },
                    onClose = onDone,
                )
                LetterScreen.FROM -> LetterFromScreen(
                    partName = chosenPart ?: "",
                    reply = reply,
                    onReplyChange = { reply = it },
                    onBack = { screen = LetterScreen.TO },
                    onClose = onDone,
                )
            }
        }
    }
}

private enum class LetterScreen { PICK, TO, FROM }

@Composable
private fun LetterPickScreen(
    onPick: (key: String, label: String) -> Unit,
    onClose: () -> Unit,
) {
    Text(
        text = stringResource(R.string.letter_to_part_title),
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.semantics { heading() },
    )
    Text(
        text = stringResource(R.string.letter_to_part_caption),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = stringResource(R.string.letter_to_part_pick_label),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    listOf(
        "angry" to R.string.letter_to_part_pick_angry,
        "scared" to R.string.letter_to_part_pick_scared,
        "dislike" to R.string.letter_to_part_pick_dislike,
        "critic" to R.string.letter_to_part_pick_critic,
        "protector" to R.string.letter_to_part_pick_protector,
        "other" to R.string.letter_to_part_pick_other,
    ).forEach { (key, labelRes) ->
        // Capture the resolved string in a local val so the
        // onClick callback (which is NOT @Composable) can pass
        // it to onPick. stringResource is @Composable and can
        // only be called from a @Composable scope.
        val label = stringResource(labelRes)
        TextButton(
            onClick = { onPick(key, label) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics { role = Role.Button },
        ) { Text(label) }
    }
    TextButton(
        onClick = onClose,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { role = Role.Button },
    ) { Text(stringResource(R.string.action_back)) }
}

@Composable
private fun LetterToScreen(
    partName: String,
    letter: String,
    onLetterChange: (String) -> Unit,
    onSwitchToFrom: () -> Unit,
    onClose: () -> Unit,
) {
    Text(
        text = partName,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = stringResource(R.string.letter_to_part_to_label),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = letter,
        onValueChange = onLetterChange,
        placeholder = { Text(stringResource(R.string.letter_to_part_to_hint)) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp),
        minLines = 6,
    )
    TextButton(
        onClick = onSwitchToFrom,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { role = Role.Button },
    ) { Text(stringResource(R.string.letter_to_part_switch)) }
    TextButton(
        onClick = onClose,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { role = Role.Button },
    ) { Text(stringResource(R.string.letter_to_part_done)) }
}

@Composable
private fun LetterFromScreen(
    partName: String,
    reply: String,
    onReplyChange: (String) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    Text(
        text = partName,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = stringResource(R.string.letter_to_part_from_label),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = reply,
        onValueChange = onReplyChange,
        placeholder = { Text(stringResource(R.string.letter_to_part_from_hint)) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp),
        minLines = 6,
    )
    TextButton(
        onClick = onBack,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { role = Role.Button },
    ) { Text(stringResource(R.string.action_back)) }
    TextButton(
        onClick = onClose,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { role = Role.Button },
    ) { Text(stringResource(R.string.letter_to_part_done)) }
}
