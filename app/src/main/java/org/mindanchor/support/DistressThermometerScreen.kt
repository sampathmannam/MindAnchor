@file:Suppress("MagicNumber", "MaxLineLength", "FunctionNaming", "LongMethod")
package org.mindanchor.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.mindanchor.R

/**
 * v0.28.0: the Distress Thermometer screen (DBT + Gross).
 *
 * A 0–100 slider with a single matching suggestion after the
 * release. The slider is the only input; the suggestion is the
 * only output. There is no log, no save, no score. The Done
 * button dismisses; the back gesture also dismisses.
 *
 * The matching band is:
 *   0–30  → "A small thing" — body-first grounding
 *   31–60 → "Noticeable" — name what is here (Lieberman 2007)
 *   61–85 → "A lot" — TIPP (DBT Linehan 1993)
 *   86–100 → "Right now, very hard" — call a crisis line
 *
 * BPD-safe: validate-then-suggest framing, no directive
 * language, no all-or-nothing. The slider does not have a
 * "good" or "bad" anchor — every value is the right answer.
 */
@Composable
fun DistressThermometerScreen(
    onDone: () -> Unit,
    onOpenSupport: () -> Unit = onDone,
) {
    var value by remember { mutableFloatStateOf(50f) }
    val rounded = value.toInt()
    val (labelRes, suggestionRes) = when {
        rounded <= 30 -> R.string.distress_thermo_label_low to R.string.distress_thermo_low_suggestion
        rounded <= 60 -> R.string.distress_thermo_label_mid to R.string.distress_thermo_mid_suggestion
        rounded <= 85 -> R.string.distress_thermo_label_high to R.string.distress_thermo_high_suggestion
        else -> R.string.distress_thermo_label_extreme to R.string.distress_thermo_extreme_suggestion
    }
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "Distress thermometer. " +
                    "Slide to where it is, not where you want it to be. " +
                    "There is no right answer."
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(R.string.distress_thermo_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.distress_thermo_caption),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.distress_thermo_value, rounded),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = value,
                onValueChange = { value = it },
                valueRange = 0f..100f,
                steps = 99,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.heightIn(min = 8.dp))
            Text(
                text = stringResource(R.string.distress_thermo_suggestion_intro),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(suggestionRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (rounded >= 86) {
                // The high-distress band gets a one-tap "open the
                // Support group" affordance. v0.28.0 wired this to
                // onDone (a silent dismiss); v0.28.1 wires it to
                // onOpenSupport so the affordance actually does what
                // its label says.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = onOpenSupport,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .semantics { role = Role.Button },
                    ) { Text(stringResource(R.string.support_shortcut)) }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = onDone,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .semantics { role = Role.Button },
                ) { Text(stringResource(R.string.distress_thermo_done)) }
            }
        }
    }
}
