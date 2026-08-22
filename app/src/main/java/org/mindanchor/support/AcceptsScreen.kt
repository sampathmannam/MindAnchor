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
 * v0.28.0: the ACCEPTS self-soothing screen.
 *
 * Seven buttons (one per ACCEPTS letter). Tap one to read its
 * one-line body. Tap Done to leave. The activity is intentionally
 * a single screen with no scrollable content — a user in
 * distress needs one tap to the next thing, not a labyrinth.
 *
 * The state var `open` tracks which body is currently shown
 * (null = menu; non-null = the body text). The screen renders
 * the menu OR a single body, never both.
 *
 * ## BPD-safety
 *
 * Each body is one sentence, sensory-first, no interpretation.
 * The user is not told "this will help" — they are told what
 * the action is. The back gesture dismisses without consequence.
 */
@Composable
fun AcceptsScreen(onDone: () -> Unit) {
    var open by rememberSaveable { mutableStateOf<AcceptsLetter?>(null) }
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "A small way to land. " +
                    "Pick one. Do it for two minutes. " +
                    "That is the whole task."
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.accepts_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.accepts_caption),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val letters = listOf(
                AcceptsLetter.ACTIVITIES to R.string.accepts_activities,
                AcceptsLetter.CONTRIBUTING to R.string.accepts_contributing,
                AcceptsLetter.COMPARISONS to R.string.accepts_comparisons,
                AcceptsLetter.EMOTIONS to R.string.accepts_emotions,
                AcceptsLetter.PUSHAWAY to R.string.accepts_pushaway,
                AcceptsLetter.THOUGHTS to R.string.accepts_thoughts,
                AcceptsLetter.SENSATIONS to R.string.accepts_sensations,
            )
            letters.forEach { (letter, labelRes) ->
                TextButton(
                    onClick = { open = letter },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics { role = Role.Button },
                ) { Text(stringResource(labelRes)) }
            }
            // Show the body of the open letter (or nothing). The
            // open is dismissable; tapping a different letter
            // swaps the body. Tap Done to leave.
            open?.let { letter ->
                val bodyRes = when (letter) {
                    AcceptsLetter.ACTIVITIES -> R.string.accepts_activities_body
                    AcceptsLetter.CONTRIBUTING -> R.string.accepts_contributing_body
                    AcceptsLetter.COMPARISONS -> R.string.accepts_comparisons_body
                    AcceptsLetter.EMOTIONS -> R.string.accepts_emotions_body
                    AcceptsLetter.PUSHAWAY -> R.string.accepts_pushaway_body
                    AcceptsLetter.THOUGHTS -> R.string.accepts_thoughts_body
                    AcceptsLetter.SENSATIONS -> R.string.accepts_sensations_body
                }
                Text(
                    text = stringResource(bodyRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            TextButton(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { role = Role.Button },
            ) { Text(stringResource(R.string.accepts_done)) }
        }
    }
}

private enum class AcceptsLetter {
    ACTIVITIES, CONTRIBUTING, COMPARISONS, EMOTIONS, PUSHAWAY, THOUGHTS, SENSATIONS
}
