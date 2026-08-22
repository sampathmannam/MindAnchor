@file:Suppress("MaxLineLength", "FunctionNaming", "WildcardImport", "MagicNumber")
package org.mindanchor.chain

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.mindanchor.R
import org.mindanchor.ui.CalmBackground
import org.mindanchor.ui.SkyContent

/**
 * v0.26.1 §3.4: "What just happened?" five-field chain capture.
 *
 * The screen shows five labelled text fields, one per step in the
 * IFS-style chain (Schwartz 1995;这对克line of the experience, the
 * interpretation, the part, the underlying want, the part to
 * bring). All five fields are independent and persist across
 * configuration changes via `rememberSaveable` — the screen never
 * auto-saves, only the "Save" button does.
 *
 * Save is a single append to [ChainCapturePrefs]. A blank
 * everything is a no-op: the screen keeps the form, and the
 * ledger write only happens when the user actively saves.
 */
@Composable
fun ChainCaptureScreen(
    onSaved: () -> Unit,
    onClose: () -> Unit,
    prefs: ChainCapturePrefs,
) {
    val a11y = stringResource(R.string.chain_a11y)
    val scope = rememberCoroutineScope()
    CalmBackground { sky ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .semantics(mergeDescendants = false) { contentDescription = a11y },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Header(sky, onClose)
            Spacer(Modifier.heightIn(min = 4.dp))
            Subtitle(sky)
            Spacer(Modifier.heightIn(min = 8.dp))
            var event by rememberSaveable { mutableStateOf("") }
            var interpretation by rememberSaveable { mutableStateOf("") }
            var part by rememberSaveable { mutableStateOf("") }
            var want by rememberSaveable { mutableStateOf("") }
            var partToBring by rememberSaveable { mutableStateOf("") }
            Field(stringResource(R.string.chain_field_event), event) { event = it }
            Field(stringResource(R.string.chain_field_interpretation), interpretation) { interpretation = it }
            Field(stringResource(R.string.chain_field_part), part) { part = it }
            Field(stringResource(R.string.chain_field_want), want) { want = it }
            Field(stringResource(R.string.chain_field_part_to_bring), partToBring) { partToBring = it }
            Spacer(Modifier.heightIn(min = 8.dp))
            SaveRow(
                sky = sky,
                onSave = {
                    scope.launch {
                        prefs.append(
                            ChainCapture(
                                atMillis = System.currentTimeMillis(),
                                event = event.trim(),
                                interpretation = interpretation.trim(),
                                part = part.trim(),
                                want = want.trim(),
                                partToBring = partToBring.trim(),
                            ),
                        )
                        onSaved()
                    }
                },
            )
        }
    }
}

@Composable
private fun Header(sky: SkyContent, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.action_back), color = sky.textSecondary)
        }
    }
    Text(
        stringResource(R.string.chain_title),
        style = MaterialTheme.typography.titleLarge,
        color = sky.textPrimary,
        textAlign = TextAlign.Start,
    )
}

@Composable
private fun Subtitle(sky: SkyContent) {
    Text(
        stringResource(R.string.chain_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = sky.textSecondary,
        textAlign = TextAlign.Start,
    )
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    )
}

@Composable
private fun SaveRow(sky: SkyContent, onSave: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        onClick = onSave,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
    ) {
        TextButton(onClick = onSave, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.action_save), color = sky.textPrimary)
        }
    }
}
