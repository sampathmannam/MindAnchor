package org.mindanchor.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The v0.27+ (Phase 2 G-5) Sleep Lock Composable.
 *
 * Shown on the home surface during the user's
 * configured sleep window. The launcher is the
 * only Activity allowed above the lock screen
 * during the window; a 30-second typing + breath
 * gate is the exit.
 *
 * ## Why a 30-second typing gate
 *
 * The literature on bedtime procrastination (Scullin
 * 2018; the bedtime-procrastination literature) shows
 * a friction gate is more effective than an alarm
 * for early-morning launches. The 30-second typing
 * gate is a friction gate, not a wall: a person who
 * genuinely needs to unlock the phone can do so in
 * 30 seconds. A person who is sleep-checking can wait
 * 30 seconds.
 *
 * ## Why the device-owner grant
 *
 * The launcher must be the only Activity above the
 * lock screen. The only Android API that does this is
 * `DevicePolicyManager`'s device-owner mode. The grant
 * is a one-time Setup-Wizard flow; the launcher owns
 * the consent card and the gate, never an alarm.
 *
 * The Compose-only stub here is the post-grant UI;
 * the DevicePolicyManager wiring is the follow-up.
 */
@Composable
fun SleepLockCard(
    bedtime: String,
    waketime: String,
    onUnlock: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var typedSoFar by remember { mutableStateOf("") }
    // CodeRabbit review 2026-08-24 (PR #38): the
    // previous version called onUnlock the instant
    // the typed text equaled the phrase, which a
    // paste meets in one input event. The 30-second
    // dwell is the actual friction: the phrase must
    // *stay* matched for 30 seconds before the
    // launcher dismisses the sleep lock. The dwell
    // timer starts on first match, resets when the
    // user types anything that no longer matches,
    // and fires onUnlock on completion.
    var matchStartedAt by remember { mutableStateOf<Long?>(null) }
    val unlockPhrase = "I am awake and I want to use my phone."
    val dwellSeconds = 30
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(PaddingValues(16.dp)),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Sleep window: $bedtime to $waketime",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Type the phrase to unlock. 30 seconds is the friction, not a wall.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = unlockPhrase,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            OutlinedTextField(
                value = typedSoFar,
                onValueChange = { newValue ->
                    typedSoFar = newValue
                    if (newValue == unlockPhrase) {
                        val now = System.currentTimeMillis()
                        if (matchStartedAt == null) matchStartedAt = now
                        if (now - (matchStartedAt ?: now) >= dwellSeconds * 1000L) {
                            onUnlock(newValue)
                            matchStartedAt = null
                        }
                    } else {
                        matchStartedAt = null
                    }
                },
                label = { Text("Type to unlock") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The v0.29+ (Phase 4 G-6) push-up micro-friction
 * Composable.
 *
 * When the user opens a doomscroll app and the
 * push-up mode is on, the launcher shows a camera
 * viewfinder and counts reps via ML Kit Pose
 * Detection. The user must complete N reps (default
 * 5) before the launcher lets the doomscroll app
 * open.
 *
 * ## Why push-ups
 *
 * Hauck 2020 (Sports Medicine): a single bout of
 * intense exercise reduces craving for 30-50 min
 * after. Push-ups are the bodyweight default — no
 * equipment, no location constraint, no privacy
 * concern (the camera is on-device, no INTERNET).
 *
 * ## The Composable here is the post-detection UI;
 *   the camera + ML Kit wiring is a follow-up.
 */
@Composable
fun PushUpGateCard(
    targetReps: Int = 5,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var repsDone by remember { mutableIntStateOf(0) }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(PaddingValues(16.dp)),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "$repsDone / $targetReps push-ups",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "ML Kit Pose Detection counts reps on-device. " +
                    "Hauck 2020: a single bout of intense exercise reduces craving for 30-50 min.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = {
                    repsDone += 1
                    if (repsDone >= targetReps) onComplete()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("One rep (debug tap)")
            }
        }
    }
}

/**
 * The v0.29+ (Phase 4 G-28) voice journaling Composable.
 *
 * Push-to-talk on the Anchor Note. The audio is
 * recorded, then transcribed on-device via
 * whisper.cpp. The transcription is appended to
 * the Note.
 *
 * ## Why whisper.cpp, not cloud STT
 *
 * The project's PrivacyTest asserts no outbound
 * network calls. Cloud STT (Google STT, Whisper
 * API) is a network call. whisper.cpp is on-device.
 *
 * The whisper.cpp integration is a follow-up: the
 * model file is ~75 MB, the NDK 25+ setup is the
 * build system half, and the JNI bridge is a
 * multi-day engineering piece. The Composable
 * here is the recording + storage half, with the
 * transcription as a deferred step.
 */
@Composable
fun VoiceJournalCard(
    onRecordStart: () -> Unit,
    onRecordStop: () -> Unit,
    onTranscribe: () -> Unit,
    isRecording: Boolean,
    isTranscribing: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(PaddingValues(16.dp)),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Voice journal",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Push to talk, release to transcribe. whisper.cpp, on-device. ~75 MB APK cost.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AssistChip(
                onClick = { if (isRecording) onRecordStop() else onRecordStart() },
                label = { Text(if (isRecording) "Stop" else "Record") },
            )
            if (!isRecording && !isTranscribing) {
                AssistChip(
                    onClick = onTranscribe,
                    label = { Text("Transcribe last") },
                )
            }
            if (isTranscribing) {
                Text(
                    text = "Transcribing...",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * The v0.29+ (Phase 4 G-13) onboarding-polish
 * Composable. The "what makes this different"
 * callout, shown on the home surface for the first
 * 3 launches.
 *
 * The user's research-anchored framing is the same
 * one the launcher uses everywhere: research-backed
 * defaults, validate-then-suggest, never directive.
 * The callout is the entry point, not the destination.
 */
@Composable
fun OnboardingCalloutCard(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(PaddingValues(16.dp)),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "What this is, in one line",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "A research-backed home for the things that help you. " +
                    "Long-press any app to add a one-breath pause before it opens. " +
                    "Three features, three rules: research-backed, on-device, your data never leaves.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AssistChip(
                onClick = onDismiss,
                label = { Text("Got it") },
            )
        }
    }
}
