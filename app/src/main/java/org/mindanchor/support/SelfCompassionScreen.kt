@file:Suppress("MagicNumber", "MaxLineLength", "FunctionNaming", "LongMethod")
package org.mindanchor.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.mindanchor.R

/**
 * v0.27.0: the Neff (2003) self-compassion break.
 *
 * Three sentences at 15 seconds each (45 seconds total). Each
 * sentence is shown one at a time, large, centred, with a thin
 * linear progress indicator across the top of the screen. The
 * Done button at the bottom dismisses at any time; the back
 * gesture also dismisses (no log, no save).
 *
 * The 3 sentences are:
 *  1. This is a moment of suffering.
 *  2. Suffering is part of being human.
 *  3. May I be kind to myself.
 *
 * Validation-first by design. The 3 sentences are *descriptive*
 * ("this is what is happening"), not directive ("you must...").
 * The Done button is not a "save" — nothing is saved. The user
 * can leave at any time without consequence.
 */
@Composable
fun SelfCompassionScreen(onDone: () -> Unit) {
    val lines = listOf(
        stringResource(R.string.self_compassion_line_1),
        stringResource(R.string.self_compassion_line_2),
        stringResource(R.string.self_compassion_line_3),
    )
    val secondsPerLine = 15
    var currentLine by remember { mutableIntStateOf(0) }
    var elapsedInLine by remember { mutableIntStateOf(0) }

    LaunchedEffect(currentLine) {
        elapsedInLine = 0
        while (elapsedInLine < secondsPerLine) {
            delay(1_000)
            elapsedInLine += 1
        }
        if (currentLine < lines.lastIndex) {
            currentLine += 1
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "Self-compassion break. Three sentences at 15 seconds each. " +
                    "Tap Done to finish early."
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
            LinearProgressIndicator(
                progress = {
                    val total = secondsPerLine * lines.size
                    val done = currentLine * secondsPerLine + elapsedInLine
                    (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 4.dp),
            )

            Text(
                text = stringResource(R.string.self_compassion_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.self_compassion_caption),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = lines[currentLine],
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            TextButton(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { role = Role.Button },
            ) {
                Text(stringResource(R.string.self_compassion_done))
            }
        }
    }
}
