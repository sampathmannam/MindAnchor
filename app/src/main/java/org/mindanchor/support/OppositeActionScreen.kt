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
 * v0.28.0: the Linehan (1993, ch. 8) Opposite Action skill.
 *
 * Four steps. Each step is a label and an optional free-text
 * field. No save, no log, no score. State is `rememberSaveable`
 * so a rotation keeps the partial draft.
 *
 * The four steps are:
 *  1. Name the emotion.
 *  2. Does it fit the facts? (evidence for / evidence against)
 *  3. What is the action urge?
 *  4. The opposite action. (yours to choose)
 *
 * ## BPD-safety
 *
 * No directive language. The free-text fields are *theirs* —
 * the surface never prescribes what to write. The Done button
 * dismisses at any time without saving. The back gesture
 * dismisses without consequence.
 */
@Composable
fun OppositeActionScreen(onDone: () -> Unit) {
    var step1 by rememberSaveable { mutableStateOf("") }
    var step2 by rememberSaveable { mutableStateOf("") }
    var step3 by rememberSaveable { mutableStateOf("") }
    var step4 by rememberSaveable { mutableStateOf("") }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "Opposite action. Four steps. " +
                    "Optional free text per step. Tap Done to finish."
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(R.string.opposite_action_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.opposite_action_caption),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OppositeStepField(
                labelRes = R.string.opposite_action_step_1_label,
                hintRes = R.string.opposite_action_step_1_hint,
                value = step1,
                onValueChange = { step1 = it },
            )
            OppositeStepField(
                labelRes = R.string.opposite_action_step_2_label,
                hintRes = R.string.opposite_action_step_2_hint,
                value = step2,
                onValueChange = { step2 = it },
            )
            OppositeStepField(
                labelRes = R.string.opposite_action_step_3_label,
                hintRes = R.string.opposite_action_step_3_hint,
                value = step3,
                onValueChange = { step3 = it },
            )
            OppositeStepField(
                labelRes = R.string.opposite_action_step_4_label,
                hintRes = R.string.opposite_action_step_4_hint,
                value = step4,
                onValueChange = { step4 = it },
            )
            TextButton(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { role = Role.Button },
            ) { Text(stringResource(R.string.opposite_action_done)) }
        }
    }
}

@Composable
private fun OppositeStepField(
    labelRes: Int,
    hintRes: Int,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelRes)) },
        placeholder = { Text(stringResource(hintRes)) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp),
        minLines = 2,
    )
}
