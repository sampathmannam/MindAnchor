@file:Suppress("MaxLineLength", "FunctionNaming", "WildcardImport", "MagicNumber")
package org.mindanchor.ifs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
 * v0.26.1 §3.4: "Which part is loud?" IFS picker.
 *
 * A 2-column grid of named parts (Schwartz 1995). The user taps
 * one, the choice is saved, and the screen closes. The latest
 * pick is highlighted on the next visit so a returning user can
 * see what they last named.
 *
 * The grid uses `FlowRow` so the chips wrap on narrow screens
 * rather than clipping — the same defensive layout pattern as
 * the rest of the launcher (every surface scrolls; nothing clips).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun IfsPickerScreen(
    onPicked: () -> Unit,
    onClose: () -> Unit,
    prefs: IfsPickerPrefs,
    parts: List<String> = IfsPickerPrefs.DEFAULT_PARTS,
) {
    val latest by prefs.latest.collectAsState(initial = null)
    val a11y = stringResource(R.string.ifs_a11y)
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
            PartGrid(
                parts = parts,
                selected = latest?.partName,
                sky = sky,
                onPick = { name ->
                    scope.launch {
                        prefs.append(IfsPick(atMillis = System.currentTimeMillis(), partName = name))
                        onPicked()
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
        stringResource(R.string.ifs_title),
        style = MaterialTheme.typography.titleLarge,
        color = sky.textPrimary,
    )
}

@Composable
private fun Subtitle(sky: SkyContent) {
    Text(
        stringResource(R.string.ifs_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = sky.textSecondary,
        textAlign = TextAlign.Start,
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PartGrid(
    parts: List<String>,
    selected: String?,
    sky: SkyContent,
    onPick: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        parts.forEach { name ->
            val isSelected = name == selected
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp),
                onClick = { onPick(name) },
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                },
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) sky.textPrimary else sky.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                )
            }
        }
    }
}
