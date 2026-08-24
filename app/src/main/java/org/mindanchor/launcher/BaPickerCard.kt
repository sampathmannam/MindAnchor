package org.mindanchor.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mindanchor.R

/**
 * The behavioural-activation weekly-picker card. Shown
 * on the Friday-evening PreHome surface when the user
 * has opted in (the [FrictionPrefs.baPromptEnabled]
 * setting). The card lets the user pick one mastery
 * (something to make or do) and one pleasure (something
 * to enjoy) activity for the weekend, then save the
 * pair as a [org.mindanchor.letters.Letter] with
 * `provider = ba-prompt` and body prefix `BA:`.
 *
 * ## Why mastery AND pleasure
 *
 * Lewinsohn et al. 1976 (the original BA
 * activity-scheduling protocol) distinguishes mastery
 * (an activity that produces a sense of competence)
 * from pleasure (an activity that produces a sense of
 * enjoyment). Picking only pleasure tends to drift
 * toward passive consumption; picking only mastery
 * tends to drift toward grinding. The pair is the
 * balance point Dimidjian 2006 found effective in the
 * BA RCT.
 *
 * ## Why validate-then-suggest copy
 *
 * The title is "If it would help, pick one thing" —
 * not "You should pick one thing". The first frame
 * gives the user a tool; the second is a directive. The
 * user with the most to gain is the one who is least
 * able to meet a directive today. v0.26+ (Phase 1
 * G-22) is BPD-safe by design.
 *
 * v0.26+ (Phase 1 G-22).
 */
@Composable
fun BaPickerCard(
    onSave: (mastery: String, pleasure: String) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var mastery by remember { mutableStateOf("") }
    var pleasure by remember { mutableStateOf("") }
    val canSave = mastery.isNotBlank() || pleasure.isNotBlank()
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(PaddingValues(16.dp)),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.ba_prompt_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = mastery,
                onValueChange = { mastery = it },
                label = { Text(stringResource(R.string.ba_prompt_mastery_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = pleasure,
                onValueChange = { pleasure = it },
                label = { Text(stringResource(R.string.ba_prompt_pleasure_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onSave(mastery, pleasure) },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.ba_prompt_save))
            }
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.ba_prompt_skip))
            }
        }
    }
}
