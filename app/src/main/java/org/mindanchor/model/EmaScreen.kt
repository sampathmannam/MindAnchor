package org.mindanchor.model

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.mindanchor.R
import java.time.LocalDate
import java.time.LocalTime

/** How long the "thanks" line stays up before the screen is done. */
private const val SAVED_DISPLAY_MILLIS = 1_100L

/**
 * The check-in itself: two questions, five large answers each, then gone.
 *
 * ## Why the save is all-or-nothing
 *
 * [onSave] fires exactly once, with a complete [Moment], and only once
 * both scales have been answered. Backing out after the first question
 * and before the second — or never touching either — has to record
 * nothing at all: a half-answered mood is not a measurement, and
 * inventing a value for the missing half would teach the model a signal
 * nobody actually reported.
 *
 * ## Why five buttons rather than a slider
 *
 * A slider invites deliberation, and deliberation is how a one-tap
 * ecological-momentary-assessment protocol turns into a diary nobody
 * keeps up. Five is also the whole of [Moment]'s scale, so every button
 * is already a legal answer with nothing left to validate.
 *
 * Each target is at least 48dp tall — Android's own touch-target floor —
 * which matters more here than almost anywhere else in this app: this is
 * answered one-handed, mid-task, sometimes by someone for whom a tremor
 * is exactly the kind of thing this check-in is asking about.
 */
@Composable
fun EmaScreen(
    onSave: (Moment) -> Unit,
    /**
     * Called exactly once, however the screen ends — both questions
     * answered, "Not now" tapped after one or after neither. Answering
     * and skipping cost the same single call, which is what makes "Not
     * now" a real way out rather than a smaller failure.
     */
    onFinished: () -> Unit,
) {
    var valence by remember { mutableStateOf<Int?>(null) }
    var saved by remember { mutableStateOf(false) }

    // Shown, not skipped straight past — but only for a beat. Nothing
    // here is worth asking anyone to dismiss by hand.
    LaunchedEffect(saved) {
        if (saved) {
            delay(SAVED_DISPLAY_MILLIS)
            onFinished()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
        ) {
            Column(modifier = Modifier.align(Alignment.Center).fillMaxWidth()) {
                when {
                    saved -> Text(
                        text = stringResource(R.string.ema_saved),
                        style = MaterialTheme.typography.headlineSmall,
                    )

                    valence == null -> EmaQuestion(
                        questionRes = R.string.ema_valence,
                        lowRes = R.string.ema_valence_low,
                        highRes = R.string.ema_valence_high,
                        onChosen = { valence = it },
                    )

                    else -> EmaQuestion(
                        questionRes = R.string.ema_arousal,
                        lowRes = R.string.ema_arousal_low,
                        highRes = R.string.ema_arousal_high,
                        onChosen = { arousal ->
                            val now = LocalTime.now()
                            onSave(
                                Moment(
                                    valence = valence!!,
                                    arousal = arousal,
                                    atMinuteOfDay = now.hour * 60 + now.minute,
                                    day = LocalDate.now().toString(),
                                ),
                            )
                            saved = true
                        },
                    )
                }
            }

            if (!saved) {
                // Always in the same place, always this easy to reach.
                // Same idea as "never mind" on the breathing gate: leaving
                // must never feel like failing.
                TextButton(
                    onClick = onFinished,
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    Text(stringResource(R.string.ema_skip))
                }
            }
        }
    }
}

@Composable
private fun EmaQuestion(
    questionRes: Int,
    lowRes: Int,
    highRes: Int,
    onChosen: (Int) -> Unit,
) {
    Column {
        Text(
            text = stringResource(questionRes),
            style = MaterialTheme.typography.headlineSmall,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (Moment.MIN..Moment.MAX).forEach { value ->
                // NOTE(ci): first use of OutlinedButton in this codebase
                // (elsewhere it's always TextButton/Button); it is a
                // standard androidx.compose.material3 sibling of both and
                // the same library is already a dependency here, but this
                // exact call site is unverified without a build.
                OutlinedButton(
                    onClick = { onChosen(value) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                ) {
                    Text(text = value.toString(), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(lowRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(highRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
