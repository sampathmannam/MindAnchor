package org.mindanchor.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The v0.28+ (Phase 3 G-8) expressive writing prompt.
 *
 * Shown on the home surface after a 1-2 check-in (WHO-5
 * band ≤ LOW, the Topp 2015 cut-off). The card offers a
 * single sentence: "Write three sentences about what
 * you are feeling."
 *
 * ## Why a minimum-dosage 3-sentence prompt
 *
 * Pennebaker 1997 (the original expressive writing
 * paradigm) found the 4-day, 15-min/day writing
 * trial effective. The 3-sentence prompt is the
 * minimum-dosage version that survives the home
 * surface: a user in a 1-2 mood is more likely to
 * write three sentences than to commit to a 15-min
 * session. The 3-sentence minimum is the entry
 * point, not the destination; a user who wants
 * the full Pennebaker protocol uses the Letters
 * editor.
 *
 * ## Why never required
 *
 * The card is an offer, not a directive. The user
 * can always dismiss. BPD-safe by design: a low
 * WHO-5 score followed by a directive ("you must
 * write") is the exact moment not to be
 * directive.
 */
@Composable
fun ExpressiveWritingCard(
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    val canSave = text.isNotBlank()
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
                text = "If it would help, write three sentences about what you are feeling.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Pennebaker 1997: writing about feelings has measurable effects on mood. " +
                    "Three sentences is the entry point, not the destination.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = false,
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            AssistChip(
                onClick = { onSave(text); text = "" },
                enabled = canSave,
                label = { Text("Save as a Note") },
            )
            AssistChip(
                onClick = onDismiss,
                label = { Text("Not now") },
            )
        }
    }
}
