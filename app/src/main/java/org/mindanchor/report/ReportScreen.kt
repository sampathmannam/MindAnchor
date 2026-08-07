package org.mindanchor.report

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToLong
import org.mindanchor.R

/**
 * Renders the most recently stored [Report] — see [ReportScheduler] for how
 * it gets there.
 *
 * Three states before a single number appears: no report has ever been
 * generated ([R.string.report_none] — nothing has run yet, or there is
 * not enough history for any signal), a report exists and genuinely found
 * nothing unusual ([R.string.report_quiet] — the common, good outcome
 * [ReportComposer] is built around), or a report has one to three things
 * to say. Whichever of the three it is, [R.string.report_disclaimer]
 * always appears at the bottom: this screen prints a count from the
 * person's own history next to what the research says the signal *is*,
 * and it is never the thing that joins those two together.
 */
@Composable
fun ReportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { ReportStore(context.applicationContext) }
    val stored by store.stored.collectAsState(initial = null)
    val report = stored?.report
    val narration = stored?.narration
    val patterns = stored?.patterns.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.action_back))
        }

        Text(
            text = stringResource(R.string.report_section),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(vertical = 16.dp),
        )

        // The paragraph a narrator wrote, shown above the sections it was
        // drawn from and never in place of them — see NarrationGuard's own
        // KDoc for why a rejected or unwritten paragraph costs nothing but
        // itself. Blank is treated the same as absent: a narrator that
        // returned an all-whitespace string, which nothing here should
        // ever do, must not render an empty label above nothing.
        if (!narration.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.report_generated_label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = narration,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }

        // What PatternFinder found in this person's own history, shown
        // after the narration and before the sections — it is a count of
        // their own past days, not a look at today, so it belongs neither
        // above the paragraph about today nor inside a section that is
        // about today. Nothing renders at all when there is nothing to
        // say, the same discipline as everywhere else on this screen: a
        // heading and a caveat around an empty list would be a machine
        // announcing it found nothing worth finding.
        if (patterns.isNotEmpty()) {
            Text(
                text = stringResource(R.string.pattern_section),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            )
            // forEach is inline, so stringResource still runs in the
            // composable body — see the notYetKnown comment below for why
            // that distinction matters here.
            patterns.forEach { pattern ->
                val bodyRes = if (pattern.lower) R.string.pattern_lower else R.string.pattern_higher
                Text(
                    text = stringResource(
                        bodyRes,
                        pattern.similarDays,
                        stringResource(pattern.signal.displayNameRes()),
                        stringResource(pattern.label.displayNameRes()),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Text(
                text = stringResource(R.string.pattern_caveat),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }

        val current = report
        when {
            current == null -> Text(
                text = stringResource(R.string.report_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            current.isEmpty -> Text(
                text = stringResource(R.string.report_quiet),
                style = MaterialTheme.typography.bodyMedium,
            )

            else -> current.sections.forEach { section -> ReportSectionCard(section) }
        }

        if (current != null && current.notYetKnown.isNotEmpty()) {
            // Resolved through map, which is inline, so stringResource
            // still runs in the composable body — joinToString is not
            // inline and calling it from that lambda does not compile.
            val names = current.notYetKnown.map { stringResource(it.displayNameRes()) }
            Text(
                text = stringResource(R.string.report_still_learning, names.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        Text(
            text = stringResource(R.string.report_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 24.dp),
        )
    }
}

/**
 * One section: the observation in plain words, then the research behind
 * it or an honest statement that none was found. [ReportSection.sources]
 * is never optional in [Report.kt] and is not optional here either — a
 * claim with no passage to check it against is not shown as a claim.
 */
@Composable
private fun ReportSectionCard(section: ReportSection) {
    val observation = section.observation
    val signalName = stringResource(observation.signal.displayNameRes())
    val todayText = observation.signal.formatValue(observation.today)
    val usualText = observation.signal.formatValue(observation.usual)
    // A bedtime is not "higher". Every other signal here is a quantity
    // where more and less are the natural words; sleep onset is a clock
    // reading, where they are not, and a sentence that reads wrong is a
    // sentence somebody stops trusting.
    val clock = observation.signal == Signal.SLEEP_ONSET
    val bodyRes = when (observation.direction) {
        Direction.ABOVE -> if (clock) R.string.report_later else R.string.report_above
        Direction.BELOW -> if (clock) R.string.report_earlier else R.string.report_below
    }

    Column(modifier = Modifier.padding(top = 20.dp)) {
        Text(
            text = stringResource(bodyRes, signalName, todayText, usualText),
            style = MaterialTheme.typography.bodyLarge,
        )
        section.passages.forEach { passage ->
            Text(
                text = passage.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (section.passages.isEmpty()) {
            Text(
                text = stringResource(R.string.report_no_research),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            Text(
                text = stringResource(R.string.report_sources, section.sources.joinToString(" · ")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun Signal.displayNameRes(): Int = when (this) {
    Signal.HRV -> R.string.signal_hrv
    Signal.RESTING_HEART_RATE -> R.string.signal_resting_hr
    Signal.SLEEP_MINUTES -> R.string.signal_sleep_minutes
    Signal.SLEEP_ONSET -> R.string.signal_sleep_onset
    Signal.STEPS -> R.string.signal_steps
    Signal.VALENCE -> R.string.signal_valence
    Signal.AROUSAL -> R.string.signal_arousal
}

/**
 * Same two strings [Signal.displayNameRes] already uses for
 * [Signal.VALENCE] and [Signal.AROUSAL] — [Label] and [Signal] name the
 * same two axes for two different purposes, so the words a person reads
 * are the same words either way; see [SignalLabel]'s own KDoc.
 */
private fun Label.displayNameRes(): Int = when (this) {
    Label.VALENCE -> R.string.signal_valence
    Label.AROUSAL -> R.string.signal_arousal
}

/**
 * A number fit to show a person, never a raw double like `41.33333333`.
 * Valence and arousal live on a tidy 1–5 scale, so one decimal place
 * still means something on them; every other signal is a count of
 * milliseconds, minutes, or steps, where a fraction is not something
 * anyone measured and rounding to a whole number is the honest choice.
 *
 * Sleep onset is the exception that is not a count at all. It is stored
 * as minutes after 18:00 — see [ReportScheduler], which uses that frame so a
 * bedtime past midnight does not wrap — and "1430" is not a thing anybody
 * can check against their own memory of last night. It is turned back
 * into the clock.
 */
private fun Signal.formatValue(value: Double): String = when (this) {
    Signal.VALENCE, Signal.AROUSAL -> "%.1f".format(value)
    Signal.SLEEP_ONSET -> clockTime(value)
    else -> value.roundToLong().toString()
}

/**
 * Minutes after 18:00 back to a 24-hour clock reading.
 *
 * Internal rather than private only so it can be tested: it is the exact
 * inverse of [org.mindanchor.sleep.Deviation.minutesAfterSixPm], and an
 * inverse that is quietly wrong shows a person a bedtime they did not have.
 */
internal fun clockTime(minutesAfterSixPm: Double): String {
    val minuteOfDay = ((minutesAfterSixPm.roundToLong() + 18 * 60) % 1440 + 1440) % 1440
    return "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)
}
