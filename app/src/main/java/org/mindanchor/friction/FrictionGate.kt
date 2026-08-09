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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
 * is clinical-review-required (the mental-health population
 * disproportionately relies on screen readers; the wording is
 * the screen-reader experience, not a translation of the
 * sighted design; see docs/research/20 for the WCAG 2.2 SC
 * 1.1.1 / 4.1.2 audit). The per-app session-length UI surface
 * (item M, v0.20.1 round 4) is part of this Composable; the
 * wording strings live in `strings.xml` under
 * `per_app_session_length_learn_label` and
 * `per_app_session_length_last_time_label`, and the
 * [onTimeBoxPicked] callback is invoked *after* the user
 * picks a time-box (the launcher records the choice iff the
 * "Learn this for next time" toggle is on, default: on).
 * The "Like last time — N min" affordance is shown when a
 * stored default exists for this app.
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
    /**
     * The user's per-app session-length map. v0.20.1 round 4
     * (item M, docs/research/22). The gate looks up
     * `perAppSessionLength.defaultMinutes(packageName)` and
     * highlights the matching button in the 5/10/20 row.
     * If the user has never picked a length for this app,
     * the gate shows a "Learn this for next time" toggle
     * (on by default) and records the choice iff the
     * toggle is on at the moment of the tap. The default
     * value (`FALLBACK_MINUTES = 10L`) is used when no
     * map entry exists.
     */
    perAppSessionLength: PerAppSessionLength = PerAppSessionLength(),
    /**
     * The package name of the app the gate is interrupting.
     * Used to look up the per-app default in
     * [perAppSessionLength]. Empty string is treated as
     * "no per-app default known"; the gate falls back to
     * the 5/10/20 row with no highlight.
     */
    packageName: String = "",
    /**
     * Invoked when the user picks a time-box (one of 5, 10,
     * 20). The launcher records the choice via
     * `FrictionPrefs.recordPerAppSessionLength` iff the
     * "Learn this for next time" toggle is on at the moment
     * of the tap. The callback is *not* invoked for the
     * "open untimed" button (no per-app length is recorded
     * for an untimed open — the user's choice is "I want
     * no timer," not a length to learn).
     */
    onTimeBoxPicked: (packageName: String, minutes: Long) -> Unit = { _, _ -> },
    /**
     * v0.20.1 round 5 follow-up: invoked when the
     * user asks to forget the per-app default. The
     * launcher calls `FrictionPrefs.clearPerAppSessionLength`
     * on this. The callback is only invoked when a
     * default exists; the affordance is hidden
     * otherwise. The launcher never shows a "Forget"
     * affordance on the first reach per app — there
     * is nothing to forget.
     */
    onForgetDefault: (packageName: String) -> Unit = { _ -> },
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
                perAppSessionLength = perAppSessionLength,
                packageName = packageName,
                onTimeBoxPicked = onTimeBoxPicked,
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
                // LiveRegionMode.Polite: announce changes
                // when the user's screen reader is idle.
                // LiveRegionMode.Assertive would interrupt
                // whatever the user is reading; the breath
                // is a time-based animation that does not
                // need to interrupt (the user is
                // *participating* in the breath, not
                // listening to a status update).
                liveRegion = LiveRegionMode.Polite
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
                    // The semantics modifier is removed
                    // (rather than `contentDescription = null`)
                    // because the Compose contentDescription
                    // property is non-nullable; the absence
                    // of a modifier is itself the WCAG 1.1.1
                    // "decorative" pattern.
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
    /**
     * The user's per-app session-length map. v0.20.1 round 4
     * (item M). The IntentionPrompt uses
     * `perAppSessionLength.defaultMinutes(packageName)` to
     * decide which button to highlight. The default
     * (`FALLBACK_MINUTES = 10L`) is returned when no entry
     * exists; in that case the gate shows the "Learn this
     * for next time" toggle so the user can record their
     * first pick.
     */
    perAppSessionLength: PerAppSessionLength = PerAppSessionLength(),
    /**
     * The package name of the app the gate is interrupting.
     * Used to look up the per-app default in
     * [perAppSessionLength]. Empty string is treated as
     * "no per-app default known"; the gate falls back to
     * the 5/10/20 row with no highlight.
     */
    packageName: String = "",
    /**
     * Invoked when the user picks a time-box (5, 10, 20).
     * The launcher records the choice iff the "Learn this
     * for next time" toggle is on at the moment of the
     * tap. Not invoked for the "open untimed" button.
     */
    onTimeBoxPicked: (packageName: String, minutes: Long) -> Unit = { _, _ -> },
    /**
     * v0.20.1 round 5 follow-up: invoked when the
     * user asks to forget the per-app default. Shown
     * only when a default exists. See the docstring
     * on the parent [FrictionGate] for the lifecycle.
     */
    onForgetDefault: (packageName: String) -> Unit = { _ -> },
) {
    // v0.20.1 round 4: the per-app default. Read it
    // once at compose time so the highlight, the
    // "Like last time?" affordance, and the toggle's
    // default value all agree.
    val storedDefaultMinutes = remember(packageName, perAppSessionLength) {
        perAppSessionLength.defaultMinutes(packageName)
    }
    // "Has a stored default for this app" is
    // "the map has an explicit entry for it."
    // A blank package name is treated as "no
    // entry." The highlight only fires on a real
    // stored choice.
    val hasStoredDefault = remember(packageName, perAppSessionLength) {
        packageName.isNotBlank() && perAppSessionLength.perAppMinutes.containsKey(packageName)
    }
    // The "Learn this for next time" toggle is on by
    // default on the first reach per app. Once the
    // user has recorded a choice, the toggle is gone
    // (the highlight takes over). Local state is
    // fine: the gate is not re-entered for the same
    // app within a single reach.
    var learnThisTime by remember(packageName, hasStoredDefault) {
        mutableStateOf(!hasStoredDefault)
    }

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

            // v0.20.1 round 4 (item M): when a per-app
            // default exists, show the "Like last time —
            // N min" affordance above the time-box row.
            // The wording is in strings.xml and is
            // clinical-review-gated.
            if (hasStoredDefault) {
                Text(
                    text = stringResource(
                        R.string.per_app_session_length_last_time_label,
                        storedDefaultMinutes,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = sky.textSecondary,
                )
                // v0.20.1 round 5 follow-up: a
                // one-tap way to forget the default.
                // Shown only when a default exists
                // (so the user never sees "Forget"
                // before anything is stored). The
                // affordance is a small text button
                // — the user must be able to read
                // the line above, not just a single
                // x-out icon. The wording is
                // clinical-review-gated; the strings
                // entry is per_app_session_length_forget_label.
                TextButton(
                    onClick = { onForgetDefault(packageName) },
                ) {
                    Text(
                        text = stringResource(R.string.per_app_session_length_forget_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = sky.textSecondary,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5L, 10L, 20L).forEach { minutes ->
                    val isHighlighted = hasStoredDefault && minutes == storedDefaultMinutes
                    // The highlight is a subtle background
                    // change on the matching button. The
                    // other two buttons stay exactly the
                    // same; the user can still pick any of
                    // 5, 10, 20, or "open untimed" with one
                    // tap. The background color is the sky's
                    // primary with a small alpha — visible
                    // but not loud.
                    TextButton(
                        onClick = {
                            if (learnThisTime) {
                                onTimeBoxPicked(packageName, minutes)
                            }
                            onOpen(minutes)
                        },
                        // The text is "5 minutes" but a
                        // screen reader benefits from
                        // "Open for 5 minutes" — the
                        // action precedes the duration.
                        // The contentDescription is
                        // clinical-review-required (the
                        // wording is part of the
                        // screen-reader experience, not
                        // a translation of the sighted
                        // design).
                        modifier = if (isHighlighted) {
                            Modifier
                                .background(
                                    color = sky.textPrimary.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .semantics {
                                    contentDescription = "Open $appLabel for $minutes minutes, like last time."
                                    role = Role.Button
                                }
                        } else {
                            Modifier.semantics {
                                contentDescription = "Open $appLabel for $minutes minutes."
                                role = Role.Button
                            }
                        },
                    ) {
                        // Bold the highlighted button so the
                        // suggestion is legible against the
                        // soft background. The unhighlighted
                        // buttons keep the regular weight —
                        // a quiet visual hierarchy, not a
                        // wall of bold.
                        Text(
                            text = stringResource(R.string.open_for_minutes, minutes),
                            color = sky.textPrimary,
                            fontWeight = if (isHighlighted) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Normal
                            },
                        )
                    }
                }
            }

            // v0.20.1 round 4 (item M): the "Learn this
            // for next time" toggle. Shown only on the
            // first reach per app (when no stored
            // default exists). The toggle is on by
            // default; the user can uncheck it before
            // picking a time-box to opt out of the
            // per-app learning. The whole row is the
            // tap target, not just the checkbox.
            if (!hasStoredDefault && packageName.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(
                            value = learnThisTime,
                            role = Role.Checkbox,
                        ) { learnThisTime = !learnThisTime }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = learnThisTime,
                        onCheckedChange = null,
                    )
                    Text(
                        text = stringResource(R.string.per_app_session_length_learn_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = sky.textSecondary,
                    )
                }
            }

            TextButton(
                onClick = { onOpen(null) },
                // The sighted text is "Open untimed"
                // but a screen reader benefits from
                // "Open $appLabel untimed." so the
                // action and the app are named.
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
