package org.mindanchor.advisory

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mindanchor.R
import org.mindanchor.research.ProtocolStep

/**
 * Program 3 Task 5 — the evidence screen and the foreground player.
 *
 * Every target, exclusion, contraindication, step, stop rule, and
 * review status shown here is read directly from the registry object on
 * [AdvisoryUiState.Evidence.protocol] / [AdvisoryUiState.Player.protocol]
 * and rendered as plain list text — never paraphrased, and never a
 * control. The only interactive element on the evidence screen is the
 * one Start button; the only interactive elements on the player are
 * Stop and Stop-discomfort. Neither screen asks a question.
 *
 * @wording-reviewed — clinical-review-required, see docs/CLINICAL_REVIEW.md.
 */
@Suppress("FunctionNaming")
@Composable
fun AdvisoryScreen(
    state: AdvisoryUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReportDiscomfort: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Surface(modifier = Modifier.fillMaxSize()) {
        when (state) {
            is AdvisoryUiState.Evidence -> AdvisoryEvidenceContent(state, onStart)
            is AdvisoryUiState.Player -> AdvisoryPlayerContent(state, onStop, onReportDiscomfort)
            else -> Unit
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun AdvisoryEvidenceContent(state: AdvisoryUiState.Evidence, onStart: () -> Unit) {
    val protocol = state.protocol
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(protocol.targetState, style = MaterialTheme.typography.titleMedium)
        Text(protocol.mechanism)
        Text(protocol.userFacingExplanation)
        Text(stringResource(R.string.advisory_review_status, protocol.clinicalReviewStatus.name))
        Text(protocol.expectedOutcome)

        Text(protocol.intendedPopulation, style = MaterialTheme.typography.titleSmall)
        protocol.exclusions.forEach { Text("• $it") }

        protocol.eligibilityRules.forEach { Text("• $it") }
        protocol.contraindicationRules.forEach { Text("• $it") }

        protocol.steps.sortedBy(ProtocolStep::ordinal).forEach { step ->
            Text("${step.ordinal}. ${step.instruction} (${step.durationSeconds}s)")
        }

        Text(stringResource(R.string.advisory_max_duration, protocol.maxDurationSeconds))
        Text(stringResource(R.string.advisory_cooldown, protocol.cooldownSeconds))
        Text(stringResource(R.string.advisory_outcome_window, protocol.outcomeWindowSeconds))
        protocol.stopRules.forEach { Text("• ${it.name}") }
        Text(protocol.successInterpretation)

        state.startBlockedReason?.let { reason ->
            Text(mechanicalStartBlockedText(reason))
        }

        Button(onClick = onStart, enabled = state.startEnabled) {
            Text(stringResource(R.string.advisory_start_attestation))
        }
    }
}

/**
 * A mechanical, local-control statement only — never a reinterpretation
 * of the source. If the reason ever concerns the source itself rather
 * than a switch, an active episode, or a cooldown, this deliberately
 * falls back to an empty string rather than guessing at an explanation.
 */
@Composable
private fun mechanicalStartBlockedText(reason: AdvisoryIneligibleReason): String = when (reason) {
    AdvisoryIneligibleReason.DELIVERY_DISABLED -> stringResource(R.string.advisory_blocked_delivery_disabled)
    AdvisoryIneligibleReason.COOLDOWN_ACTIVE -> stringResource(R.string.advisory_blocked_cooldown_active)
    AdvisoryIneligibleReason.ACTIVE_EPISODE_EXISTS -> stringResource(R.string.advisory_blocked_active_episode)
    AdvisoryIneligibleReason.OPPORTUNITY_ALREADY_HANDLED -> stringResource(R.string.advisory_blocked_already_handled)
    else -> ""
}

@Suppress("FunctionNaming")
@Composable
private fun AdvisoryPlayerContent(
    state: AdvisoryUiState.Player,
    onStop: () -> Unit,
    onReportDiscomfort: () -> Unit,
) {
    val protocol = state.protocol
    val steps = protocol.steps.sortedBy(ProtocolStep::ordinal)
    val cycleMillis = steps.sumOf { it.durationSeconds.toLong() } * MILLIS_PER_SECOND
    val withinCycle = if (cycleMillis > 0) state.elapsedMillis % cycleMillis else 0L
    val activeStep = steps.stepAt(withinCycle)
    val remainingMillis = (protocol.maxDurationSeconds * MILLIS_PER_SECOND - state.elapsedMillis).coerceAtLeast(0L)

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Text(stringResource(R.string.advisory_time_remaining, remainingMillis / MILLIS_PER_SECOND))
        Text(activeStep?.instruction.orEmpty(), style = MaterialTheme.typography.headlineSmall)
        Row(modifier = Modifier.padding(top = 16.dp)) {
            OutlinedButton(onClick = onReportDiscomfort) { Text(stringResource(R.string.advisory_stop_discomfort)) }
            Button(onClick = onStop, modifier = Modifier.padding(start = 8.dp)) {
                Text(stringResource(R.string.advisory_stop))
            }
        }
    }
}

private fun List<ProtocolStep>.stepAt(withinCycleMillis: Long): ProtocolStep? {
    var upperBound = 0L
    return firstOrNull { step ->
        upperBound += step.durationSeconds * MILLIS_PER_SECOND
        withinCycleMillis < upperBound
    }
}

private const val MILLIS_PER_SECOND = 1_000L
