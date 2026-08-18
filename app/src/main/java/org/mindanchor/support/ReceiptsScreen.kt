@file:Suppress("MagicNumber", "MaxLineLength", "FunctionNaming")
package org.mindanchor.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.mindanchor.R
import java.time.LocalDate

/**
 * v0.38.0: DBT PLEASE-mastery log (Linehan 1993 ch. 9).
 *
 * One-line-per-day "what I did, however small." Date-stamped,
 * no streak, no score, list view.
 *
 * BPD-safety:
 *  - No count of consecutive days, no streak indicator.
 *  - No judgment language ("good day", "bad day").
 *  - The list is in chronological order with the most
 *    recent at the top. The list is the data; no derived
 *    metrics are shown.
 *  - The input field accepts one short line. Multi-line
 *    is not allowed (the audit recommends "however small"
 *    — long entries are a different kind of journal, and
 *    the user has the Notes screen for that).
 */
@Composable
fun ReceiptsScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ReceiptsPrefs(context) }
    var draft by rememberSaveable { mutableStateOf("") }
    var saved by remember { mutableStateOf<List<Receipt>>(emptyList()) }
    LaunchedEffect(Unit) { saved = prefs.list() }
    val today = LocalDate.now().toString()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                onClick = onDone,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { role = Role.Button },
            ) { Text(stringResource(R.string.action_back)) }
            Text(
                text = stringResource(R.string.receipts_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.receipts_caption),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(140) },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.receipts_input_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                enabled = draft.isNotBlank(),
                onClick = {
                    val text = draft
                    draft = ""
                    scope.launch {
                        prefs.save(LocalDate.now(), text)
                        saved = prefs.list()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { role = Role.Button },
            ) {
                Text(stringResource(R.string.receipts_save))
            }
            if (saved.isEmpty()) {
                Text(
                    text = stringResource(R.string.receipts_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(saved, key = { it.date }) { receipt ->
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Text(
                                text = receipt.date,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = receipt.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
            // v0.38.0: support_footer (audit #11) — APA
            // Digital Mental Health 101 non-replacement
            // disclaimer. Same body copy as the support hub.
            Text(
                text = stringResource(R.string.support_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 32.dp),
            )
        }
    }
}
