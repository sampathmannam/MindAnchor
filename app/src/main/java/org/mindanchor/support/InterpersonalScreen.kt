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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.mindanchor.R

/**
 * v0.27.0: the DBT Module 4 (Interpersonal Effectiveness) menu +
 * three script screens (DEAR MAN, GIVE, FAST), per Linehan 1993.
 *
 * The activity hosts all four screens; navigation is via a
 * state var. The menu shows three buttons (one per skill); each
 * script screen shows the acronym, the line-by-line gloss, and
 * an optional "draft of what you might say" text field.
 *
 * State is rememberSaveable so a config change (rotation) keeps
 * the menu choice and the partial draft.
 *
 * ## BPD-safety
 *
 * The draft is *optional* and is *theirs* — the surface never
 * says "send this" or "you should...". The Done button dismisses
 * without saving. The back gesture dismisses without consequence.
 */
@Composable
fun InterpersonalScreen(onDone: () -> Unit) {
    var screen by rememberSaveable { mutableStateOf(InterpersonalScreen.MENU) }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when (screen) {
                InterpersonalScreen.MENU -> InterpersonalMenu(
                    onPickDearMan = { screen = InterpersonalScreen.DEAR_MAN },
                    onPickGive = { screen = InterpersonalScreen.GIVE },
                    onPickFast = { screen = InterpersonalScreen.FAST },
                    onClose = onDone,
                )
                InterpersonalScreen.DEAR_MAN -> DearManScript(onClose = onDone)
                InterpersonalScreen.GIVE -> GiveScript(onClose = onDone)
                InterpersonalScreen.FAST -> FastScript(onClose = onDone)
            }
        }
    }
}

private enum class InterpersonalScreen { MENU, DEAR_MAN, GIVE, FAST }

@Composable
private fun InterpersonalMenu(
    onPickDearMan: () -> Unit,
    onPickGive: () -> Unit,
    onPickFast: () -> Unit,
    onClose: () -> Unit,
) {
    Text(
        text = stringResource(R.string.interpersonal_title),
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.semantics { heading() },
    )
    Text(
        text = stringResource(R.string.interpersonal_caption),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(
        onClick = onPickDearMan,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { role = Role.Button },
    ) { Text(stringResource(R.string.interpersonal_dear_man_button)) }
    TextButton(
        onClick = onPickGive,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { role = Role.Button },
    ) { Text(stringResource(R.string.interpersonal_give_button)) }
    TextButton(
        onClick = onPickFast,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { role = Role.Button },
    ) { Text(stringResource(R.string.interpersonal_fast_button)) }
    TextButton(
        onClick = onClose,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { role = Role.Button },
    ) { Text(stringResource(R.string.action_back)) }
}

@Composable
private fun DearManScript(onClose: () -> Unit) {
    ScriptScaffold(
        titleRes = R.string.dear_man_title,
        captionRes = R.string.dear_man_caption,
        lines = listOf(
            R.string.dear_man_d,
            R.string.dear_man_e,
            R.string.dear_man_a,
            R.string.dear_man_r,
            R.string.dear_man_m,
            R.string.dear_man_an,
            R.string.dear_man_n,
        ),
        draftLabelRes = R.string.dear_man_draft_label,
        draftHintRes = R.string.dear_man_draft_hint,
        doneRes = R.string.dear_man_done,
        onClose = onClose,
    )
}

@Composable
private fun GiveScript(onClose: () -> Unit) {
    ScriptScaffold(
        titleRes = R.string.give_title,
        captionRes = R.string.give_caption,
        lines = listOf(
            R.string.give_g,
            R.string.give_i,
            R.string.give_v,
            R.string.give_e,
        ),
        draftLabelRes = R.string.give_draft_label,
        draftHintRes = R.string.give_draft_hint,
        doneRes = R.string.give_done,
        onClose = onClose,
    )
}

@Composable
private fun FastScript(onClose: () -> Unit) {
    ScriptScaffold(
        titleRes = R.string.fast_title,
        captionRes = R.string.fast_caption,
        lines = listOf(
            R.string.fast_f,
            R.string.fast_a,
            R.string.fast_s,
            R.string.fast_t,
        ),
        draftLabelRes = R.string.fast_draft_label,
        draftHintRes = R.string.fast_draft_hint,
        doneRes = R.string.fast_done,
        onClose = onClose,
    )
}

@Composable
private fun ScriptScaffold(
    titleRes: Int,
    captionRes: Int,
    lines: List<Int>,
    draftLabelRes: Int,
    draftHintRes: Int,
    doneRes: Int,
    onClose: () -> Unit,
) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.semantics { heading() },
    )
    Text(
        text = stringResource(captionRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    lines.forEach { lineRes ->
        Text(
            text = stringResource(lineRes),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
    // Optional draft. BPD-safe: the field is the user's; the
    // surface never says "send this" or "you should say...".
    var draft by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        label = { Text(stringResource(draftLabelRes)) },
        placeholder = { Text(stringResource(draftHintRes)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        minLines = 3,
    )
    TextButton(
        onClick = onClose,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { role = Role.Button },
    ) { Text(stringResource(doneRes)) }
}
