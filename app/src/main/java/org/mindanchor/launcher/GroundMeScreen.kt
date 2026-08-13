@file:Suppress("MaxLineLength", "FunctionNaming", "WildcardImport", "MagicNumber")
package org.mindanchor.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.mindanchor.R
import org.mindanchor.ui.CalmBackground
import org.mindanchor.ui.SkyContent

@Composable
fun GroundMeScreen(onClose: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf(GroundMode.Choose) }
    CalmBackground { sky ->
        when (mode) {
            GroundMode.Choose -> GroundMePicker(sky, { mode = it }, onClose)
            GroundMode.Breath -> TippBreath(sky) { mode = GroundMode.Choose }
            GroundMode.Cold -> GoCold(sky) { mode = GroundMode.Choose }
            GroundMode.Grounding -> FiveFourThreeTwoOne(sky) { mode = GroundMode.Choose }
        }
    }
}

private enum class GroundMode { Choose, Breath, Cold, Grounding }

@Composable
private fun GroundMePicker(sky: SkyContent, onPick: (GroundMode) -> Unit, onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp)
            .semantics(mergeDescendants = false) { contentDescription = "Ground me right now. Three options." },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.action_back), color = sky.textSecondary)
            }
        }
        Text(stringResource(R.string.ground_me_title), style = MaterialTheme.typography.titleLarge, color = sky.textPrimary, textAlign = TextAlign.Center)
        Text(stringResource(R.string.ground_me_subtitle), style = MaterialTheme.typography.bodyMedium, color = sky.textSecondary, textAlign = TextAlign.Center)
        GroundRow(stringResource(R.string.ground_me_breath), stringResource(R.string.ground_me_breath_seconds), sky) { onPick(GroundMode.Breath) }
        GroundRow(stringResource(R.string.ground_me_cold), stringResource(R.string.ground_me_cold_seconds), sky) { onPick(GroundMode.Cold) }
        GroundRow(stringResource(R.string.ground_me_grounding), stringResource(R.string.ground_me_grounding_seconds), sky) { onPick(GroundMode.Grounding) }
    }
}

@Composable private fun GroundRow(label: String, seconds: String, sky: SkyContent, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp), onClick = onClick, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = sky.textPrimary)
            Text(seconds, style = MaterialTheme.typography.bodySmall, color = sky.textSecondary)
        }
    }
}

@Composable private fun TippBreath(sky: SkyContent, onBack: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val totalCycles = 10
    var cycle by remember { mutableStateOf(0) }
    var phase by remember { mutableStateOf(BreathPhase.In) }
    val scale = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        repeat(totalCycles) {
            phase = BreathPhase.In
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            scale.animateTo(1.6f, tween(durationMillis = 5_000))
            phase = BreathPhase.Out
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            scale.animateTo(1f, tween(durationMillis = 7_000))
            cycle += 1
        }
    }
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
        Text(stringResource(R.string.ground_me_back), style = MaterialTheme.typography.labelLarge, color = sky.textSecondary, modifier = Modifier.heightIn(min = 48.dp).clickable { onBack() })
        Box(modifier = Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(220.dp).scale(scale.value).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), CircleShape))
            Text(
                text = if (cycle < totalCycles) when (phase) {
                    BreathPhase.In -> stringResource(R.string.ground_me_breath_in)
                    BreathPhase.Out -> stringResource(R.string.ground_me_breath_out)
                } else stringResource(R.string.ground_me_done),
                style = MaterialTheme.typography.titleLarge, color = sky.textPrimary,
            )
        }
        Text(stringResource(R.string.ground_me_cycle, cycle + 1, totalCycles), style = MaterialTheme.typography.bodyMedium, color = sky.textSecondary)
    }
}

private enum class BreathPhase { In, Out }

@Composable private fun GoCold(sky: SkyContent, onBack: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    var secondsLeft by remember { mutableStateOf(30) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        repeat(30) { tick ->
            secondsLeft = 30 - tick
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(1_000)
        }
        done = true
    }
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
        Text(stringResource(R.string.ground_me_back), style = MaterialTheme.typography.labelLarge, color = sky.textSecondary, modifier = Modifier.heightIn(min = 48.dp).clickable { onBack() })
        Text(stringResource(R.string.ground_me_cold_label), style = MaterialTheme.typography.titleLarge, color = sky.textPrimary, textAlign = TextAlign.Center)
        Text(if (done) stringResource(R.string.ground_me_done) else stringResource(R.string.ground_me_seconds, secondsLeft), style = MaterialTheme.typography.displayLarge, color = sky.textPrimary)
        Text(stringResource(R.string.ground_me_cold_hint), style = MaterialTheme.typography.bodyMedium, color = sky.textSecondary, textAlign = TextAlign.Center)
    }
}

@Composable private fun FiveFourThreeTwoOne(sky: SkyContent, onBack: () -> Unit) {
    val senseLabels = listOf(
        stringResource(R.string.ground_me_see, 5),
        stringResource(R.string.ground_me_touch, 4),
        stringResource(R.string.ground_me_hear, 3),
        stringResource(R.string.ground_me_smell, 2),
        stringResource(R.string.ground_me_taste, 1),
    )
    var index by rememberSaveable { mutableStateOf(0) }
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.ground_me_back), style = MaterialTheme.typography.labelLarge, color = sky.textSecondary, modifier = Modifier.heightIn(min = 48.dp).clickable { onBack() })
        Text(stringResource(R.string.ground_me_grounding_label), style = MaterialTheme.typography.titleLarge, color = sky.textPrimary)
        senseLabels.forEachIndexed { i, label ->
            val isActive = i == index
            val isPast = i < index
            Surface(modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp), onClick = { if (isActive && i < senseLabels.lastIndex) index = i + 1 }, color = if (isActive) MaterialTheme.colorScheme.surface.copy(alpha = 0.8f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge, color = if (isPast) sky.textSecondary else sky.textPrimary, modifier = Modifier.padding(16.dp), fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal)
            }
        }
    }
}
