package org.mindanchor.friction

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.mindanchor.R
import org.mindanchor.ui.CalmBackground
import org.mindanchor.ui.SkyContent

/**
 * The one sec-style gate (Grüning et al., PNAS 2023): a single ~6-second
 * guided breath, then "what are you here to do?" with a time-boxed choice.
 * "Never mind" is always available — the 36%-abandonment door is the whole
 * point, and leaving must never feel like failing.
 */
@Composable
fun FrictionGate(
    appLabel: String,
    onOpen: (minutes: Long?) -> Unit,
    onNeverMind: () -> Unit,
) {
    var breathDone by remember { mutableStateOf(false) }

    CalmBackground { sky ->
        if (!breathDone) {
            BreathingPause(
                sky = sky,
                onFinished = { breathDone = true },
                onNeverMind = onNeverMind,
            )
        } else {
            IntentionPrompt(
                sky = sky,
                appLabel = appLabel,
                onOpen = onOpen,
                onNeverMind = onNeverMind,
            )
        }
    }
}

private const val BREATH_MILLIS = 6_000

@Composable
private fun BreathingPause(sky: SkyContent, onFinished: () -> Unit, onNeverMind: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    var phaseIn by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        delay(BREATH_MILLIS / 2L)
        phaseIn = false
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        delay(BREATH_MILLIS / 2L)
        onFinished()
    }

    val transition = rememberInfiniteTransition(label = "breath")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = BREATH_MILLIS
                1f at 0
                1.6f at BREATH_MILLIS / 2
                1f at BREATH_MILLIS
            },
        ),
        label = "breathScale",
    )

    Box(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .scale(scale)
                    .background(
                        color = sky.textPrimary.copy(alpha = 0.25f),
                        shape = CircleShape,
                    ),
            )
            Text(
                text = stringResource(
                    if (phaseIn) R.string.breath_in else R.string.breath_out,
                ),
                style = MaterialTheme.typography.titleLarge,
                color = sky.textSecondary,
            )
        }
        TextButton(
            onClick = onNeverMind,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Text(stringResource(R.string.never_mind), color = sky.textSecondary)
        }
    }
}

@Composable
private fun IntentionPrompt(
    sky: SkyContent,
    appLabel: String,
    onOpen: (minutes: Long?) -> Unit,
    onNeverMind: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.intention_question, appLabel),
                style = MaterialTheme.typography.headlineSmall,
                color = sky.textPrimary,
            )
            Text(
                text = stringResource(R.string.intention_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = sky.textSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5L, 10L, 20L).forEach { minutes ->
                    TextButton(onClick = { onOpen(minutes) }) {
                        Text(
                            stringResource(R.string.open_for_minutes, minutes),
                            color = sky.textPrimary,
                        )
                    }
                }
            }
            TextButton(onClick = { onOpen(null) }) {
                Text(stringResource(R.string.open_untimed), color = sky.textSecondary)
            }
        }
        TextButton(
            onClick = onNeverMind,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Text(stringResource(R.string.never_mind), color = sky.textSecondary)
        }
    }
}
