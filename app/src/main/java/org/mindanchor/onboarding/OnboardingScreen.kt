package org.mindanchor.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.mindanchor.R
import org.mindanchor.sunset.Chronotype

/**
 * Goal-elicitation onboarding (ReDD workshops, CHI 2024: naming your own
 * struggle and matching tools to it beats any fixed toolset; "Going Light"
 * CHI 2026: imposed minimalism fails, self-endorsed structure works).
 * Nothing is enabled for the user — they choose, and the closing screen
 * points to where each choice is switched on.
 *
 * The chronotype step is a one-tap answer to "when are you most awake?"
 * (Roenneberg 2007; Wittmann 2006; Åkerstedt 2003 + Kecklund 2016 for
 * shift work). The launcher's quiet-hours default derives from the
 * answer, with the explicit "not set" option for anyone who would
 * rather pick the times themselves.
 */
@Suppress("FunctionNaming")
@Composable
fun OnboardingScreen(
    onDone: (Set<Goal>, Chronotype) -> Unit,
) {
    var step by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf(setOf<Goal>()) }
    var chronotype by remember { mutableStateOf(Chronotype.UNKNOWN) }

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
                0 -> WelcomeStep(onNext = { step = 1 })
                1 -> GoalsStep(
                    selected = selected,
                    onChange = { selected = it },
                    onNext = { step = 2 },
                )
                2 -> ChronotypeStep(
                    selected = chronotype,
                    onChange = { chronotype = it },
                    onNext = { step = 3 },
                )
                else -> PlanStep(
                    selected = selected,
                    chronotype = chronotype,
                    onBegin = { onDone(selected, chronotype) },
                )
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun WelcomeStep(onNext: () -> Unit) {
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
    TextButton(onClick = onNext) {
        Text(stringResource(R.string.onboarding_continue))
    }
}

@Suppress("FunctionNaming")
@Composable
private fun GoalsStep(
    selected: Set<Goal>,
    onChange: (Set<Goal>) -> Unit,
    onNext: () -> Unit,
) {
    Text(
        text = stringResource(R.string.onboarding_goals_title),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(bottom = 16.dp),
    )
    GoalRow(Goal.INTERRUPTIONS, R.string.goal_interruptions, selected, onChange)
    GoalRow(Goal.COMPULSIVE_APPS, R.string.goal_compulsive, selected, onChange)
    GoalRow(Goal.SLEEP, R.string.goal_sleep, selected, onChange)
    GoalRow(Goal.MEASUREMENT, R.string.goal_measurement, selected, onChange)
    TextButton(
        onClick = onNext,
        modifier = Modifier.padding(top = 16.dp),
    ) {
        Text(stringResource(R.string.onboarding_continue))
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ChronotypeStep(
    selected: Chronotype,
    onChange: (Chronotype) -> Unit,
    onNext: () -> Unit,
) {
    Text(
        text = stringResource(R.string.onboarding_chronotype_title),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    Text(
        text = stringResource(R.string.onboarding_chronotype_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 16.dp),
    )
    ChronotypeRow(Chronotype.MORNING_LARK, R.string.chronotype_morning_lark, selected, onChange)
    ChronotypeRow(Chronotype.NEUTRAL, R.string.chronotype_neutral, selected, onChange)
    ChronotypeRow(Chronotype.NIGHT_OWL, R.string.chronotype_night_owl, selected, onChange)
    ChronotypeRow(Chronotype.SHIFT_WORKER, R.string.chronotype_shift_worker, selected, onChange)
    TextButton(
        onClick = onNext,
        modifier = Modifier.padding(top = 16.dp),
    ) {
        Text(stringResource(R.string.onboarding_continue))
    }
}

@Suppress("FunctionNaming")
@Composable
private fun PlanStep(
    selected: Set<Goal>,
    chronotype: Chronotype,
    onBegin: () -> Unit,
) {
    Text(
        text = stringResource(R.string.onboarding_plan_title),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(bottom = 12.dp),
    )
    if (Goal.INTERRUPTIONS in selected) {
        PlanLine(stringResource(R.string.plan_interruptions))
    }
    if (Goal.COMPULSIVE_APPS in selected) {
        PlanLine(stringResource(R.string.plan_compulsive))
    }
    if (Goal.SLEEP in selected) {
        PlanLine(stringResource(R.string.plan_sleep))
    }
    if (Goal.MEASUREMENT in selected) {
        PlanLine(stringResource(R.string.plan_measurement))
    }
    if (chronotype != Chronotype.UNKNOWN) {
        PlanLine(
            stringResource(
                R.string.plan_chronotype,
                stringResource(chronotype.labelRes()),
            ),
        )
    }
    if (selected.isEmpty() && chronotype == Chronotype.UNKNOWN) {
        PlanLine(stringResource(R.string.plan_none))
    }
    TextButton(
        onClick = onBegin,
        modifier = Modifier.padding(top = 16.dp),
    ) {
        Text(stringResource(R.string.onboarding_begin))
    }
}

private fun Chronotype.labelRes(): Int = when (this) {
    Chronotype.MORNING_LARK -> R.string.chronotype_morning_lark
    Chronotype.NEUTRAL -> R.string.chronotype_neutral
    Chronotype.NIGHT_OWL -> R.string.chronotype_night_owl
    Chronotype.SHIFT_WORKER -> R.string.chronotype_shift_worker
    Chronotype.UNKNOWN -> R.string.chronotype_unknown
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
    // someone with tremor, large fingers, or in distress. Toggle
    // semantics live on the row too, so a screen reader hears the words
    // and the checked state as one thing, at a full 48dp.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = goal in selected, role = Role.Checkbox) {
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

@Suppress("FunctionNaming")
@Composable
private fun ChronotypeRow(
    chronotype: Chronotype,
    labelRes: Int,
    selected: Chronotype,
    onChange: (Chronotype) -> Unit,
) {
    // Same row-tap reasoning as [GoalRow]: the whole row is the
    // target, not just the radio dot. A 48dp row is reachable for
    // someone with tremor, large fingers, or in distress, and the
    // semantics give a screen reader the label and the selected
    // state as one thing.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(
                selected = selected == chronotype,
                role = Role.RadioButton,
                onClick = { onChange(chronotype) },
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected == chronotype, onClick = null)
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun PlanLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}
