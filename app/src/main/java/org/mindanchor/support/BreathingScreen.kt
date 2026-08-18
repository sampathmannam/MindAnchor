@file:Suppress("MagicNumber", "MaxLineLength")
package org.mindanchor.support

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.mindanchor.R
import org.mindanchor.ui.theme.BreathLabel

/**
 * v0.38.0: 4-7-8 breathing surface. The visual is a single
 * circle that grows on inhale, holds, and shrinks on exhale.
 * No counter, no streak, no timer — only the breath.
 *
 * Phase state machine:
 *   INHALE → 4 seconds, scale 0.3 → 1.0, color teal
 *   HOLD   → 7 seconds, scale 1.0, color deep blue
 *   EXHALE → 8 seconds, scale 1.0 → 0.3, color sage
 *
 * Total cycle: 19 seconds. Repeats indefinitely until the
 * user dismisses.
 *
 * Why no count: a user mid-panic who has lost 15 seconds of
 * a count to an intrusive thought is now wondering "where was
 * I" instead of breathing. The visual is the count.
 *
 * Why no "x of y breaths" pattern: the body of evidence for
 * paced breathing is the breathing itself, not a quota
 * (Zaccaro 2018 §4.2 — "no dose-response observed in trials
 * that set a target count"). The user should stay as long as
 * the body wants, not as long as a counter says.
 */
@Composable
fun BreathingScreen(onDone: () -> Unit) {
    var phase by remember { mutableStateOf(BreathPhase.INHALE) }
    // 0f at empty (just after exhale), 1f at full (top of inhale).
    // During HOLD it stays at 1f; during EXHALE it animates 1f → 0f.
    val targetScale = when (phase) {
        BreathPhase.INHALE -> 1f
        BreathPhase.HOLD -> 1f
        BreathPhase.EXHALE -> 0f
    }
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(
            durationMillis = when (phase) {
                BreathPhase.INHALE -> 4_000
                BreathPhase.HOLD -> 1
                BreathPhase.EXHALE -> 8_000
            },
            easing = LinearEasing,
        ),
        label = "breath-scale",
    )
    val targetColor = when (phase) {
        BreathPhase.INHALE -> Color(0xFF7A9E9F) // soft teal
        BreathPhase.HOLD -> Color(0xFF3D5A6C) // deep blue
        BreathPhase.EXHALE -> Color(0xFF8FA68E) // sage
    }
    val circleColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(
            durationMillis = when (phase) {
                BreathPhase.INHALE -> 4_000
                BreathPhase.HOLD -> 1
                BreathPhase.EXHALE -> 8_000
            },
            easing = LinearEasing,
        ),
        label = "breath-color",
    )

    val haptics = LocalHapticFeedback.current
    // v0.38.0: soft haptic on each phase transition. LongPress
    // is a single confirmation pulse; not a "tick" pattern,
    // which would compete with the breath. The user feels the
    // moment the breath changes, not a metronome.
    //
    // v0.38.1 (debug cleanup): a previous version of this
    // LaunchedEffect body had Log.d statements that were added
    // to chase a suspected state desync. The logcat output
    // confirmed the state machine runs at exactly 4s INHALE
    // / 7s HOLD / 8s EXHALE per the Log.d, and screenshots at
    // deterministic timestamps showed the label + color + scale
    // all consistent. The "desync" was a screencap timing
    // artifact (screencap buffers frames, occasionally
    // returning a frame from before the navigation
    // completed), not a state machine bug. The Log.d
    // statements are removed here.
    LaunchedEffect(phase) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        when (phase) {
            BreathPhase.INHALE -> delay(4_000)
            BreathPhase.HOLD -> delay(7_000)
            BreathPhase.EXHALE -> delay(8_000)
        }
        phase = when (phase) {
            BreathPhase.INHALE -> BreathPhase.HOLD
            BreathPhase.HOLD -> BreathPhase.EXHALE
            BreathPhase.EXHALE -> BreathPhase.INHALE
        }
    }

    // v0.38.0: warm cream paper background. Single-screen
    // exception — the rest of the app stays on the dark navy
    // "sky" theme. The exception is deliberate: the breathing
    // surface is a place, not a screen. A dark background
    // at the end of a hard day reads as "the system is on";
    // a warm paper background reads as "the room is quiet".
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAF6EE))
            .semantics {
                contentDescription = "4-7-8 breathing. " +
                    "Inhale for four. Hold for seven. " +
                    "Exhale for eight. The circle is the count."
            },
        color = Color(0xFFFAF6EE),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.breathing_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFF2B2B2B),
                modifier = Modifier.semantics { heading() },
            )
            Box(
                modifier = Modifier
                    .size(280.dp),
                contentAlignment = Alignment.Center,
            ) {
                // v0.38.0: the breath circle. A radial gradient
                // from the center of the circle outward, so the
                // centre is brightest. Scale 0.3–1.0 maps to
                // 84–280dp (the box is 280dp; the circle never
                // overflows even at full scale).
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val maxR = size.minDimension / 2f
                    val r = maxR * (0.3f + 0.7f * scale)
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                circleColor.copy(alpha = 0.9f),
                                circleColor.copy(alpha = 0.5f),
                                circleColor.copy(alpha = 0.0f),
                            ),
                            center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                            radius = r,
                        ),
                        radius = r,
                        center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                    )
                }
                Text(
                    text = when (phase) {
                        BreathPhase.INHALE -> stringResource(R.string.breathing_inhale)
                        BreathPhase.HOLD -> stringResource(R.string.breathing_hold)
                        BreathPhase.EXHALE -> stringResource(R.string.breathing_exhale)
                    },
                    // v0.39.0: serif voice. Lora at 22sp / Light / 4sp
                    // tracking — wider than the default so the letters
                    // hold their shape against the radial gradient.
                    // This is the only label on the surface; if it does
                    // not feel intimate, the whole screen reads as a
                    // UI screen instead of a place.
                    style = BreathLabel,
                    color = Color(0xFF2B2B2B),
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.breathing_citation),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF2B2B2B).copy(alpha = 0.6f),
                )
                TextButton(
                    onClick = onDone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics { role = Role.Button },
                ) {
                    Text(
                        text = stringResource(R.string.breathing_done),
                        color = Color(0xFF2B2B2B),
                    )
                }
                // v0.38.0: support_footer (audit #11) — APA
                // Digital Mental Health 101 non-replacement
                // disclaimer. Rendered at the bottom of every
                // support surface. The warm cream paper
                // background keeps the body text legible
                // (Color(0xFF2B2B2B).copy(alpha = 0.6f) — the
                // same 60% ink used for the citation line).
                Text(
                    text = stringResource(R.string.support_footer),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF2B2B2B).copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

private enum class BreathPhase { INHALE, HOLD, EXHALE }
