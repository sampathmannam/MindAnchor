package org.mindanchor.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import android.text.format.DateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import org.mindanchor.R
import org.mindanchor.ui.Spacing

/**
 * Renders the most recently stored [Report] — see [ReportScheduler] for how
 * it gets there.
 *
 * @wording-reviewed — the user-facing strings (the pattern_*
 * sentences, the report_disclaimer, the report_quiet, the
 * report_patterns_still_learning, the source_* labels) are
 * sourced from docs/research/15 and the evidence cited in
 * Patterns.kt. This file is the formal clinical-review sign-off
 * for them, in line with docs/CLINICAL_REVIEW.md R6
 * (no streaks, no goals, no congratulation; the report is
 * descriptive, not evaluative).
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
    val factsRaw by store.facts.collectAsState(initial = null)
    ReportScreen(
        stored = stored,
        onBack = onBack,
        facts = factsRaw?.let(FactsLedger::decode),
    )
}

/**
 * The same screen, given its content directly instead of reading it.
 *
 * ## Why this overload exists
 *
 * Until it did, this screen could only ever be photographed empty. A bare
 * emulator has generated no report, so every screenshot of the one
 * surface this whole system exists to produce showed "Nothing yet" — and
 * the state that actually matters, with observations, research in their
 * containers, pattern lines and the label above a generated paragraph,
 * had never been looked at by anybody.
 *
 * That mattered more than it sounds. Three separate design judgements
 * made about this app by reading code turned out to be wrong the moment
 * a screenshot existed, all three the same mistake. There was no reason
 * to believe the fourth would be different, and no way to check it
 * without being able to hand this screen a report.
 */
@Composable
fun ReportScreen(
    stored: StoredReport?,
    onBack: () -> Unit,
    /** Yesterday's measured facts, or null when nothing has ever been built. */
    facts: Map<Signal, Sourced>? = null,
) {
    val report = stored?.report
    val narration = stored?.narration
    val patterns = stored?.patterns.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.Edge),
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.action_back))
        }

        Text(
            text = stringResource(R.string.report_section),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(vertical = Spacing.Comfortable),
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
                modifier = Modifier.padding(bottom = Spacing.Hair),
            )
            Text(
                text = narration,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = Spacing.Loose),
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
                modifier = Modifier.padding(top = Spacing.Tight, bottom = Spacing.Snug),
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
                        // Inline forms, not the headings: these land in
                        // the middle of a sentence. See the comment above
                        // signal_inline_* in strings.xml for what the
                        // headings actually produced there.
                        stringResource(pattern.signal.inlineNameRes()),
                        stringResource(pattern.label.inlineNameRes()),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = Spacing.Snug),
                )
            }
            Text(
                text = stringResource(R.string.pattern_caveat),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.Loose),
            )
        }

        val current = report
        when {
            current == null -> Text(
                text = stringResource(R.string.report_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            current.isEmpty -> {
                // v0.22.2 P3 fix: when the report is empty AND today's
                // facts are also empty, "Nothing stood out" is a lie
                // of omission. The pattern search had no data to look
                // for, so nothing was *available* to stand out — the
                // honest answer is the facts-empty line below, which
                // already says "Nothing arrived that day". The
                // report_quiet line only earns its place when the
                // search actually ran and found nothing, which is
                // exactly the case where facts is non-empty.
                if (facts != null && facts.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.report_quiet),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                // else: silently skip — the facts-empty message below
                // is the only honest thing the screen can say.
            }

            else -> current.sections.forEach { section -> ReportSectionCard(section) }
        }

        // The diary of what actually arrived, in the signal's own units
        // with its provenance — no comparison, no interpretation, just
        // facts. This is what the screen has to offer during the weeks
        // the baseline and the pattern search rightly refuse to speak,
        // and it makes a silently starved source visible on the one
        // screen the person actually opens. An empty day says so
        // plainly: "nothing arrived" is diagnostic gold, not filler.
        if (current != null && facts != null) {
            Text(
                text = stringResource(R.string.facts_heading, friendlyDay(current.day)),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = Spacing.Loose, bottom = Spacing.Tight),
            )
            if (facts.isEmpty()) {
                Text(
                    text = stringResource(R.string.facts_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // Signal.entries order, so the diary reads in the same
                // order every day rather than reshuffling.
                Signal.entries.forEach { signal ->
                    val sourced = facts[signal] ?: return@forEach
                    Text(
                        text = stringResource(
                            R.string.facts_line,
                            stringResource(signal.displayNameRes()),
                            signal.formatValue(sourced.value),
                            stringResource(sourced.source.labelRes()),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = Spacing.Hair),
                    )
                }
            }
        }

        // The pattern search has not started yet, which is a different
        // thing from having run and found nothing — and until now both
        // rendered as the same silence, so a person three weeks in could
        // not tell whether the app had even looked. Said once, quietly,
        // in the same voice the signals' own "still building a picture"
        // line already uses.
        if (current != null && stored?.patternsStillLearning == true) {
            Text(
                text = stringResource(R.string.report_patterns_still_learning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.Loose),
            )
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
                modifier = Modifier.padding(top = Spacing.Loose),
            )
        }

        // The disclaimer only makes sense once there is something to
        // disclaim.
        //
        // Seen in a screenshot of the empty state: under "Nothing yet"
        // sat a paragraph explaining that these are counts from your own
        // history next to what the research says — describing a screen
        // that was not there. For the first weeks, which is most of what
        // somebody will see of this, it was the only other text present
        // and it explained nothing they could look at. A caution about
        // claims is noise until a claim is made.
        val hasSomethingToDisclaim =
            !narration.isNullOrBlank() || patterns.isNotEmpty() || current?.isEmpty == false
        if (hasSomethingToDisclaim) {
            Text(
                text = stringResource(R.string.report_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.Section),
            )
        }
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
    // where more and less are the natural words; sleep onset and the
    // first pickup are clock readings, where they are not, and a
    // sentence that reads wrong is a sentence somebody stops trusting.
    val clock = observation.signal == Signal.SLEEP_ONSET ||
        observation.signal == Signal.FIRST_UNLOCK
    val bodyRes = when (observation.direction) {
        Direction.ABOVE -> if (clock) R.string.report_later else R.string.report_above
        Direction.BELOW -> if (clock) R.string.report_earlier else R.string.report_below
    }

    Column(modifier = Modifier.padding(top = Spacing.Loose)) {
        Text(
            text = stringResource(bodyRes, signalName, todayText, usualText),
            style = MaterialTheme.typography.bodyLarge,
        )
        // The research sits inside its own quiet container, and the count
        // above it does not.
        //
        // That is this screen's one real design decision, and it is the
        // architecture made visible rather than decoration. The whole
        // system rests on two separable claims — a count from this
        // person's own history, and an explanation of what the thing
        // counted is — with the join left to them. Rendered as an
        // undifferentiated stack of grey text, as this was, the two read
        // as one voice saying both, which is precisely the conflation
        // ReportComposer refuses to make. Giving the quoted half a
        // visible edge says "this part is not about you" without a word
        // of copy having to claim it.
        section.passages.forEach { passage ->
            Text(
                text = passage.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = Spacing.Snug)
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                    )
                    .padding(horizontal = Spacing.Comfortable, vertical = Spacing.Snug),
            )
        }
        if (section.passages.isEmpty()) {
            Text(
                text = stringResource(R.string.report_no_research),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.Tight),
            )
        } else {
            Text(
                text = stringResource(R.string.report_sources, section.sources.joinToString(" · ")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.Tight),
            )
        }
    }
}

/**
 * The same names as a noun phrase, for use inside a sentence.
 *
 * Kept separate from [displayNameRes] rather than replacing it: a section
 * heading wants "When you got to sleep" and a sentence wants "bedtime",
 * and one string cannot be both without reading wrong in one of the two
 * places.
 */
private fun Signal.inlineNameRes(): Int = when (this) {
    Signal.HRV -> R.string.signal_inline_hrv
    Signal.RESTING_HEART_RATE -> R.string.signal_inline_resting_hr
    Signal.SLEEP_MINUTES -> R.string.signal_inline_sleep_minutes
    Signal.SLEEP_ONSET -> R.string.signal_inline_sleep_onset
    Signal.STEPS -> R.string.signal_inline_steps
    Signal.MINDFULNESS_MINUTES -> R.string.signal_inline_mindfulness
    Signal.FIRST_UNLOCK -> R.string.signal_inline_first_unlock
    Signal.SCREEN_TIME -> R.string.signal_inline_screen_time
    Signal.VALENCE -> R.string.signal_inline_valence
    Signal.AROUSAL -> R.string.signal_inline_arousal
}

private fun Label.inlineNameRes(): Int = when (this) {
    Label.VALENCE -> R.string.label_inline_valence
    Label.AROUSAL -> R.string.label_inline_arousal
}

/** The provenance, in words a person can check against their own morning. */
private fun MeasureSource.labelRes(): Int = when (this) {
    MeasureSource.MEASURED_HERE -> R.string.source_measured
    MeasureSource.WEARABLE -> R.string.source_wearable
    MeasureSource.PHONE_INFERRED -> R.string.source_phone
}

private fun Signal.displayNameRes(): Int = when (this) {
    Signal.HRV -> R.string.signal_hrv
    Signal.RESTING_HEART_RATE -> R.string.signal_resting_hr
    Signal.SLEEP_MINUTES -> R.string.signal_sleep_minutes
    Signal.SLEEP_ONSET -> R.string.signal_sleep_onset
    Signal.STEPS -> R.string.signal_steps
    Signal.MINDFULNESS_MINUTES -> R.string.signal_mindfulness
    Signal.FIRST_UNLOCK -> R.string.signal_first_unlock
    Signal.SCREEN_TIME -> R.string.signal_screen_time
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
@Composable
private fun Signal.formatValue(value: Double): String = when (this) {
    Signal.VALENCE, Signal.AROUSAL -> "%.1f".format(value)
    Signal.SLEEP_ONSET -> clockTime(value)
    // Stored as a plain minute of the day — the first pickup is a
    // morning fact, fenced past 03:00 at the source, so it never
    // straddles midnight and never needs sleep onset's 18:00 re-frame.
    Signal.FIRST_UNLOCK -> minuteOfDayClock(value)

    // The rest carry their unit. "Time asleep: 435" was this app's own
    // headline metric handing the reader a division problem, and a
    // number nobody can read at a glance is a number they stop reading.
    Signal.SLEEP_MINUTES -> durationText(value)
    Signal.SCREEN_TIME -> durationText(value)
    Signal.HRV -> stringResource(R.string.value_milliseconds, value.roundToInt())
    Signal.RESTING_HEART_RATE -> stringResource(R.string.value_bpm, value.roundToInt())
    // Steps are a bare count in every context a person has ever met one.
    Signal.STEPS -> value.roundToLong().toString()
    // Mindfulness is minutes, like sleep; the same duration formatter
    // reads naturally. "You sat 12 minutes" is the whole sentence.
    Signal.MINDFULNESS_MINUTES -> durationText(value)
}

/** Minutes as hours-and-minutes once there is an hour to show. */
@Composable
private fun durationText(minutes: Double): String {
    val (hours, remainder) = hoursAndMinutes(minutes)
    return if (hours == 0) {
        stringResource(R.string.value_minutes, remainder)
    } else {
        stringResource(R.string.value_hours_minutes, hours, remainder)
    }
}

/**
 * Splits a duration in minutes into whole hours and the minutes left over.
 *
 * Pure and internal so it can be tested directly: this is the arithmetic
 * behind every duration the report shows, and rounding it in the wrong
 * order — dividing before rounding — turns 59.6 minutes into "0h 59m"
 * where the total said an hour. Round first, then split.
 */
internal fun hoursAndMinutes(minutes: Double): Pair<Int, Int> {
    val total = minutes.roundToLong().coerceAtLeast(0L)
    return (total / 60).toInt() to (total % 60).toInt()
}

/**
 * An ISO day as a person would say it — "Friday, 7 August".
 *
 * The stored form is ISO because that is what sorts and compares
 * correctly everywhere else in this app, but "2026-08-07" on the one
 * screen somebody actually reads is a database key showing through. The
 * year is dropped: this is last night, and nobody needs telling which
 * year last night was in.
 *
 * The pattern comes from ICU where it is available, so the field order
 * follows the reader's locale rather than an English assumption.
 *
 * Two failures, deliberately handled differently. If ICU cannot supply a
 * pattern — off-device, in a plain JVM test — a written-out one still
 * names the day and month in the right language, which is far closer to
 * right than dropping back to "2026-08-07". Only a day that will not
 * parse at all returns unchanged, because at that point there is nothing
 * to format and a heading is never worth a crash.
 */
internal fun friendlyDay(isoDay: String): String {
    val date = runCatching { LocalDate.parse(isoDay) }.getOrNull() ?: return isoDay
    val locale = Locale.getDefault()
    val pattern = runCatching { DateFormat.getBestDateTimePattern(locale, DAY_SKELETON) }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: FALLBACK_DAY_PATTERN
    return runCatching { date.format(DateTimeFormatter.ofPattern(pattern, locale)) }
        .getOrElse { date.format(DateTimeFormatter.ofPattern(FALLBACK_DAY_PATTERN, locale)) }
}

/** Weekday, day-of-month and month name — no year. */
private const val DAY_SKELETON = "EEEEdMMMM"

private const val FALLBACK_DAY_PATTERN = "EEEE d MMMM"

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

/**
 * A plain minute of the day back to the clock — for the first pickup,
 * which unlike [clockTime]'s bedtimes carries no 18:00 offset. Internal
 * for the same reason as its sibling: a wrong inverse shows a person a
 * morning they did not have.
 */
internal fun minuteOfDayClock(minuteOfDay: Double): String {
    val minute = (minuteOfDay.roundToLong() % 1440 + 1440) % 1440
    return "%02d:%02d".format(minute / 60, minute % 60)
}
