package org.mindanchor.friction

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.platform.LocalContext
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
    /** How hard to push this time; see [FrictionTone]. */
    tone: FrictionTone = FrictionTone.FULL,
) {
    // The breath is skipped entirely below FULL rather than shortened.
    // A hurried version of a calming ritual is not calming.
    var breathDone by remember(tone) { mutableStateOf(tone != FrictionTone.FULL) }

    CalmBackground { sky ->
        when {
            !breathDone -> BreathingPause(
                sky = sky,
                onFinished = { breathDone = true },
                onNeverMind = onNeverMind,
            )

            // Asking a fourth time in ten minutes does not produce a fourth
            // answer; it produces a person who has learned to swipe past
            // anything this app shows them. So it stops asking, says the
            // plain thing once, and gets out of the way.
            tone == FrictionTone.FEATHER -> Feather(
                sky = sky,
                appLabel = appLabel,
                onOpen = { onOpen(null) },
                onNeverMind = onNeverMind,
            )

            else -> IntentionPrompt(
                sky = sky,
                appLabel = appLabel,
                onOpen = onOpen,
                onNeverMind = onNeverMind,
            )
        }
    }
}

/**
 * The lightest touch: one observation, no question, one way through.
 *
 * It states the count rather than judging it. "You have opened this four
 * times" is a fact the person can do something with; "you keep opening
 * this" is a verdict, and a launcher has no standing to deliver one.
 */
@Composable
private fun Feather(
    sky: SkyContent,
    appLabel: String,
    onOpen: () -> Unit,
    onNeverMind: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text = stringResource(R.string.friction_feather, appLabel),
                style = MaterialTheme.typography.bodyLarge,
                color = sky.textPrimary,
            )
            TextButton(onClick = onOpen) {
                Text(
                    text = stringResource(R.string.friction_feather_open),
                    style = MaterialTheme.typography.titleMedium,
                    color = sky.textPrimary,
                )
            }
            TextButton(onClick = onNeverMind) {
                Text(
                    text = stringResource(R.string.never_mind),
                    style = MaterialTheme.typography.bodyMedium,
                    color = sky.textSecondary,
                )
            }
        }
    }
}

private const val BREATH_MILLIS = 6_000

@Composable
private fun BreathingPause(sky: SkyContent, onFinished: () -> Unit, onNeverMind: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    var phaseIn by remember { mutableStateOf(true) }

    // Users who have asked the system to remove animations get the same
    // pause, the same haptics and the same wording — just no pulsing circle.
    val animationsEnabled = remember {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }

    // A single finite breath, not an endless loop. An infinite transition
    // here kept animating behind the intention prompt long after the breath
    // was over — burning frames, and leaving the UI permanently non-idle.
    val scale = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        val half = (BREATH_MILLIS / 2)
        if (animationsEnabled) {
            scale.animateTo(1.6f, tween(half, easing = FastOutSlowInEasing))
        } else {
            delay(half.toLong())
        }
        phaseIn = false
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        if (animationsEnabled) {
            scale.animateTo(1f, tween(half, easing = FastOutSlowInEasing))
        } else {
            delay(half.toLong())
        }
        onFinished()
    }


    Box(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .scale(scale.value)
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
