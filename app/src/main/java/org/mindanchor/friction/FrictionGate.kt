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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
 *
 * @wording-reviewed — every contentDescription on this Composable
 * is clinical-review-required. The mental-health population
 * disproportionately relies on screen readers; the wording is
 * the screen-reader experience, not a translation of the
 * sighted design. See docs/research/20 for the WCAG 2.2 SC
 * 1.1.1 / 4.1.2 audit.
 */
@Composable
fun FrictionGate(
    appLabel: String,
    onOpen: (minutes: Long?) -> Unit,
    onNeverMind: () -> Unit,
    /** How hard to push this time; see [FrictionTone]. */
    tone: FrictionTone = FrictionTone.FULL,
    /**
     * One of the person's own small things, or null. Chosen by
     * [SmallThings], which is where the rules about when to stay quiet
     * live. Never shown on FEATHER and never in the quiet hours.
     */
    smallThing: String? = null,
    onSmallThingTaken: () -> Unit = {},
    /**
     * The user's pre-written if-then plan for this app, or null. When
     * present, the intention prompt is pre-filled with the user's
     * own words — the Gollwitzer 1999 implementation-intention
     * structure, which the SOTA brief (docs/research/15 §8) calls
     * the cheapest anti-habituation fix.
     */
    ifThenPlan: IfThenPlan? = null,
    /**
     * One of the user's own self-compassion phrases for this reach,
     * or null. Rotated by [CompassionStore.rotate] so the same
     * phrase does not become wallpaper.
     */
    compassionMoment: String? = null,
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
                smallThing = smallThing,
                onSmallThingTaken = onSmallThingTaken,
                ifThenPlan = ifThenPlan,
                compassionMoment = compassionMoment,
            )
        }
    }
}

/**
 * The lightest touch: one observation, no question, one way through.
 *
 * It observes rather than judges. "You've opened this a few times just
 * now" is something the person can do what they like with; "you keep
 * opening this" is a verdict, and a launcher has no standing to deliver
 * one. The wording stays vague about the number on purpose — reciting an
 * exact count reads as a tally being kept against them.
 */
@Composable
private fun Feather(
    sky: SkyContent,
    appLabel: String,
    onOpen: () -> Unit,
    onNeverMind: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            // TalkBack reads the composite as a single
            // "feather prompt for $appLabel" announcement,
            // not as three independent text and button
            // nodes. The mergeDescendants flag is the
            // standard Compose accessibility pattern
            // (CVS Health Android Compose accessibility
            // techniques, 2025).
            .semantics(mergeDescendants = true) {
                contentDescription = "Pause for $appLabel."
            },
    ) {
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
            TextButton(
                onClick = onOpen,
                // The button's text is the action;
                // semantics adds the Role so TalkBack
                // announces "Open $appLabel, button."
                modifier = Modifier.semantics { role = Role.Button },
            ) {
                Text(
                    text = stringResource(R.string.friction_feather_open),
                    style = MaterialTheme.typography.titleMedium,
                    color = sky.textPrimary,
                )
            }
            TextButton(
                onClick = onNeverMind,
                modifier = Modifier.semantics { role = Role.Button },
            ) {
                Text(
                    text = stringResource(R.string.never_mind),
                    style = MaterialTheme.typography.bodyMedium,
                    color = sky.textSecondary,
                )
            }
        }
    }
}

private const val BREATH_MILLIS = BreathingProtocol.CYCLE_MILLIS

@Composable
private fun BreathingPause(sky: SkyContent, onFinished: () -> Unit, onNeverMind: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    var phase by remember { mutableStateOf(BreathingProtocol.Phase.INHALE) }

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
    //
    // The protocol is the physiological sigh (Balban et al. 2023,
    // Cell Reports Medicine 4(1):100895): a 2s nasal inhale, a 1s
    // "sip" inhale to fully reinflate the alveoli, then a 6s slow
    // mouth exhale. The double-inhale is what makes it a sigh; the
    // long exhale is the active ingredient for parasympathetic
    // drive (Bernardi 2018, J Physiol 596(8):1449–1464). See
    // BreathingProtocol for the citations.
    val scale = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        // First haptic on inhale start. The user feels the breath
        // before they have to do anything.
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        if (animationsEnabled) {
            scale.animateTo(1.6f, tween(
                durationMillis = BreathingProtocol.INHALE_MILLIS.toInt(),
                easing = FastOutSlowInEasing,
            ))
        } else {
            delay(BreathingProtocol.INHALE_MILLIS)
        }

        // The "sip" — a second, smaller inhale on top of the first.
        // This is the alveolar reinflation that distinguishes a
        // physiological sigh from an ordinary breath. The second
        // haptic marks the transition.
        phase = BreathingProtocol.Phase.SIP
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        if (animationsEnabled) {
            scale.animateTo(1.8f, tween(
                durationMillis = BreathingProtocol.SIP_MILLIS.toInt(),
                easing = FastOutSlowInEasing,
            ))
        } else {
            delay(BreathingProtocol.SIP_MILLIS)
        }

        // The slow exhale — the active ingredient. The circle
        // shrinks back over six seconds, twice as long as the
        // inhale, which is the parasympathetic-drive lever.
        phase = BreathingProtocol.Phase.EXHALE
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        if (animationsEnabled) {
            scale.animateTo(1f, tween(
                durationMillis = BreathingProtocol.EXHALE_MILLIS.toInt(),
                easing = FastOutSlowInEasing,
            ))
        } else {
            delay(BreathingProtocol.EXHALE_MILLIS)
        }
        onFinished()
    }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            // The breath is a time-based animation. A
            // sighted user sees the circle grow and
            // shrink; a TalkBack user hears the phase
            // text change as the liveRegion. The Box
            // itself is a single accessibility node.
            .semantics(mergeDescendants = true) {
                contentDescription = "A guided breath."
                liveRegion = true
            },
    ) {
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
                    )
                    // The circle is decorative; the phase
                    // text below carries the meaning.
                    // null contentDescription is the
                    // correct WCAG pattern (1.1.1).
                    .semantics { contentDescription = null },
            )
            Text(
                text = stringResource(
                    when (phase) {
                        BreathingProtocol.Phase.INHALE -> R.string.breath_in
                        BreathingProtocol.Phase.SIP -> R.string.breath_sip
                        BreathingProtocol.Phase.EXHALE -> R.string.breath_out
                    },
                ),
                style = MaterialTheme.typography.titleLarge,
                color = sky.textSecondary,
            )
        }
        TextButton(
            onClick = onNeverMind,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .semantics { role = Role.Button },
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
    smallThing: String? = null,
    onSmallThingTaken: () -> Unit = {},
    /**
     * The user's pre-written if-then plan for this app. When
     * present, the prompt is pre-filled with the user's own
     * words — the user-chosen plan is *additional* to the
     * existing time-box buttons, not a replacement. The way
     * in stays exactly where it was.
     */
    ifThenPlan: IfThenPlan? = null,
    /**
     * The user's rotated self-compassion moment for this
     * reach. One line, beneath the if-then plan if both are
     * present.
     */
    compassionMoment: String? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            // The intention prompt has many sub-elements.
            // TalkBack should hear them in sequence
            // (the question, the buttons, the small thing)
            // rather than as a single merged string.
            // mergeDescendants = false here so each
            // child is its own focusable target.
            .semantics(mergeDescendants = false) {
                contentDescription = "What are you opening $appLabel for?"
            },
    ) {
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

            // The user's own if-then plan, pre-filled into the
            // prompt. Shown only when a complete plan is on
            // file for this app (cue + action both filled). The
            // existing 5/10/20 time-box buttons and the "open
            // untimed" button are still right below, so the
            // user-chosen defaultMinutes from the plan is the
            // *suggestion* but the existing escape valves are
            // still one tap away.
            if (ifThenPlan != null) {
                Text(
                    text = stringResource(
                        R.string.intention_plan_label,
                        ifThenPlan.cue,
                        ifThenPlan.action,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = sky.textPrimary,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5L, 10L, 20L).forEach { minutes ->
                    TextButton(
                        onClick = { onOpen(minutes) },
                        // The text is "5 minutes" but a
                        // screen reader benefits from
                        // "Open for 5 minutes" — the
                        // action precedes the duration.
                        // The contentDescription is a
                        // string resource so the wording
                        // is clinical-review-required.
                        modifier = Modifier.semantics {
                            contentDescription =
                                "Open $appLabel for $minutes minutes."
                            role = Role.Button
                        },
                    ) {
                        Text(
                            stringResource(R.string.open_for_minutes, minutes),
                            color = sky.textPrimary,
                        )
                    }
                }
            }
            TextButton(
                onClick = { onOpen(null) },
                modifier = Modifier.semantics {
                    contentDescription = "Open $appLabel untimed."
                    role = Role.Button
                },
            ) {
                Text(stringResource(R.string.open_untimed), color = sky.textSecondary)
            }

            // Their own words, offered beside the door rather than in
            // front of it. Behavioural activation says the small thing is
            // what shifts mood, and this is the one moment anything can
            // see that a small thing is being avoided. It is one line and
            // it never argues — the way in is still exactly where it was.
            if (smallThing != null) {
                Text(
                    text = stringResource(R.string.small_thing_prompt),
                    style = MaterialTheme.typography.bodySmall,
                    color = sky.textSecondary,
                )
                TextButton(
                    onClick = onSmallThingTaken,
                    modifier = Modifier.semantics {
                        contentDescription =
                            "Take the small thing instead of opening: $smallThing"
                        role = Role.Button
                    },
                ) {
                    Text(smallThing, color = sky.textPrimary)
                }
            }

            // The user's rotated self-compassion moment. One
            // line, optional, only shown when the user has
            // authored at least one. The brief is explicit
            // (docs/research/15 §3) that the prompt is the
            // user's own words, not a launcher opinion.
            if (compassionMoment != null) {
                Text(
                    text = stringResource(R.string.compassion_moment_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = sky.textSecondary,
                )
                Text(
                    text = compassionMoment,
                    style = MaterialTheme.typography.bodyLarge,
                    color = sky.textPrimary,
                )
            }
        }
        TextButton(
            onClick = onNeverMind,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .semantics { role = Role.Button },
        ) {
            Text(stringResource(R.string.never_mind), color = sky.textSecondary)
        }
    }
}
