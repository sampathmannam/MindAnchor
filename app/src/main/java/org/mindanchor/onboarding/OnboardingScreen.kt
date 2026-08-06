package org.mindanchor.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mindanchor.R

/**
 * Goal-elicitation onboarding (ReDD workshops, CHI 2024: naming your own
 * struggle and matching tools to it beats any fixed toolset; "Going Light"
 * CHI 2026: imposed minimalism fails, self-endorsed structure works).
 * Nothing is enabled for the user — they choose, and the closing screen
 * points to where each choice is switched on.
 */
@Composable
fun OnboardingScreen(onDone: (Set<Goal>) -> Unit) {
    var step by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf(setOf<Goal>()) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            when (step) {
                0 -> {
                    Text(
                        text = stringResource(R.string.onboarding_welcome_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = stringResource(R.string.onboarding_welcome_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                    TextButton(onClick = { step = 1 }) {
                        Text(stringResource(R.string.onboarding_continue))
                    }
                }

                1 -> {
                    Text(
                        text = stringResource(R.string.onboarding_goals_title),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                    GoalRow(Goal.INTERRUPTIONS, R.string.goal_interruptions, selected) {
                        selected = it
                    }
                    GoalRow(Goal.COMPULSIVE_APPS, R.string.goal_compulsive, selected) {
                        selected = it
                    }
                    GoalRow(Goal.SLEEP, R.string.goal_sleep, selected) { selected = it }
                    GoalRow(Goal.MEASUREMENT, R.string.goal_measurement, selected) {
                        selected = it
                    }
                    TextButton(
                        onClick = { step = 2 },
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Text(stringResource(R.string.onboarding_continue))
                    }
                }

                else -> {
                    Text(
                        text = stringResource(R.string.onboarding_plan_title),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    if (Goal.INTERRUPTIONS in selected) {
                        PlanLine(R.string.plan_interruptions)
                    }
                    if (Goal.COMPULSIVE_APPS in selected) {
                        PlanLine(R.string.plan_compulsive)
                    }
                    if (Goal.SLEEP in selected) {
                        PlanLine(R.string.plan_sleep)
                    }
                    if (Goal.MEASUREMENT in selected) {
                        PlanLine(R.string.plan_measurement)
                    }
                    if (selected.isEmpty()) {
                        PlanLine(R.string.plan_none)
                    }
                    TextButton(
                        onClick = { onDone(selected) },
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Text(stringResource(R.string.onboarding_begin))
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalRow(
    goal: Goal,
    labelRes: Int,
    selected: Set<Goal>,
    onChange: (Set<Goal>) -> Unit,
) {
    // The whole row is the target, not just the checkbox. The emulator
    // caught this: tapping the words did nothing, so a goal could only be
    // chosen by hitting a small square — the worst possible target for
    // someone with tremor, large fingers, or in distress.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onChange(if (goal in selected) selected - goal else selected + goal)
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = goal in selected, onCheckedChange = null)
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun PlanLine(textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}
