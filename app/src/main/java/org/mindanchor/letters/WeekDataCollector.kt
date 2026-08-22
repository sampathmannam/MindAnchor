package org.mindanchor.letters

import android.content.Context
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.mindanchor.data.NotesPrefs
import org.mindanchor.data.db.PulseResult
import org.mindanchor.model.MomentStore
import org.mindanchor.sleep.SleepRepository

/**
 * Gathers the past 7 days of the user's own data into a
 * [WeekData] the model can read.
 *
 * The collector is the only piece of the letter pipeline that
 * knows about the rest of the app — the writer and the prompt
 * are deliberately ignorant of where the data comes from, so
 * a future caller can swap a [WeekData] from any source (a
 * fixture in a test, a hand-built snapshot for the
 * "Generate now" button) and the model will treat it the same.
 *
 * Each surface returns an empty string when the user has no
 * data in that surface for the window. [LetterPrompting.build]
 * then decides whether the week is sparse enough to be worth
 * writing from; the collector's job is to gather, not to
 * decide.
 *
 * ## What this is not
 *
 * This is not a re-implementation of the night report. The night
 * report's input is a single day; the letter's input is a
 * seven-day window. The surfaces that exist for the night
 * report are reused where they fit, but the letter needs
 * summaries (averages, trajectories) that the night report
 * does not compute.
 */
class WeekDataCollector(private val context: Context) {

    /**
     * The 7-day window ending today, inclusive. `end` is today
     * and `start` is 6 days before.
     */
    private fun window(): Pair<LocalDate, LocalDate> {
        val end = LocalDate.now()
        return (end.minusDays(WINDOW_BACK_DAYS)) to end
    }

    suspend fun collectLastWeek(): WeekData = withContext(Dispatchers.IO) {
        val (start, end) = window()
        val who5 = collectWho5Summary(start, end)
        val ema = collectEmaSummary(start, end)
        val notes = collectNotesSummary(start, end)
        val sleep = collectSleepSummary(start, end)
        val smallThings = emptyList<String>() // reserved for v0.24.0 follow-up
        val patterns = emptyList<String>() // reserved for v0.24.0 follow-up
        WeekData(
            start = start,
            end = end,
            who5Summary = who5,
            emaSummary = ema,
            notesSummary = notes,
            sleepSummary = sleep,
            patternsLines = patterns,
            smallThings = smallThings,
        )
    }

    /**
     * The WHO-5 summary line: how many of the last 7 days were
     * scored, the score trajectory, and the median. The model
     * reads numbers, not chart series; the trajectory is a
     * one-word bucket ("rising", "falling", "steady") which is
     * what the [org.mindanchor.report.ReportScreen] already
     * prints to the user.
     */
    private suspend fun collectWho5Summary(start: LocalDate, end: LocalDate): String {
        return runCatching {
            val db = org.mindanchor.data.db.AnchorDatabase.get(context)
            val history: List<PulseResult> = db.pulses().history().first()
            val zone = java.time.ZoneId.systemDefault()
            val inWindow = history.filter { p ->
                val date = java.time.Instant.ofEpochMilli(p.takenAt)
                    .atZone(zone).toLocalDate()
                date in start..end
            }
            if (inWindow.isEmpty()) return@runCatching ""
            val scores = inWindow.map { it.score }
            val median = scores.sorted()[scores.size / 2]
            val trajectory = trajectoryFor(scores)
            "${inWindow.size}/${WINDOW_TOTAL_DAYS} days scored. Median $median/25. Trajectory: $trajectory."
        }.getOrDefault("").orEmpty()
    }

    private fun trajectoryFor(scores: List<Int>): String {
        if (scores.size < TRAJECTORY_MIN) return "steady"
        val first = scores.take(scores.size / 2).average()
        val second = scores.drop(scores.size / 2).average()
        val delta = second - first
        return when {
            delta > TRAJECTORY_THRESHOLD -> "rising"
            delta < -TRAJECTORY_THRESHOLD -> "falling"
            else -> "steady"
        }
    }

    private suspend fun collectEmaSummary(start: LocalDate, end: LocalDate): String {
        return runCatching {
            val store = MomentStore(context)
            val moments = store.moments.first()
                .filter { runCatching { LocalDate.parse(it.day) }.getOrNull()?.let { d -> d in start..end } == true }
            if (moments.isEmpty()) return@runCatching ""
            val avgValence = moments.map { it.valence }.average()
            val avgArousal = moments.map { it.arousal }.average()
            "${moments.size} check-ins. " +
                "Average valence ${"%.1f".format(avgValence)}/5, " +
                "arousal ${"%.1f".format(avgArousal)}/5."
        }.getOrDefault("").orEmpty()
    }

    private suspend fun collectNotesSummary(start: LocalDate, end: LocalDate): String {
        return runCatching {
            val state = NotesPrefs(context).notes.first()
            val all = state.notes
            val startMs = start.atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli()
            val endMs = end.atTime(END_HOUR, END_MINUTE, END_SECOND)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli()
            val inWindow = all.filter { it.updatedAt in startMs..endMs }
            if (inWindow.isEmpty()) return@runCatching ""
            val count = inWindow.size
            val recent = inWindow.maxByOrNull { it.updatedAt }
            val preview = recent?.body?.take(PREVIEW_CHARS)?.replace("\n", " ").orEmpty()
            val typed = typedCountsLine(inWindow)
            // v0.25.0: if at least one note in the
            // window has a type, the line carries
            // the per-type counts. Otherwise the
            // model isn't on the phone (or hasn't
            // reached the user's notes yet) and we
            // fall back to the original line.
            if (typed == null) {
                if (preview.isBlank()) {
                    "$count note${if (count == 1) "" else "s"} this week."
                } else {
                    "$count note${if (count == 1) "" else "s"} this week. " +
                        "Most recent: \"$preview\"."
                }
            } else {
                val suffix = if (preview.isBlank()) "" else " Most recent: \"$preview\"."
                "$count note${if (count == 1) "" else "s"} this week: $typed.$suffix"
            }
        }.getOrDefault("").orEmpty()
    }

    /**
     * v0.25.0: build a "N tasks, M reminders, K journal"
     * line for the notes in [notes]. Returns null if
     * every note in the window has `type = null` (the
     * model isn't on the phone, or the upgrade pass
     * hasn't reached these notes) — the caller falls
     * back to the v0.24.0 line in that case.
     *
     * The four types are listed in a fixed order
     * (general / task / reminder / journal) regardless
     * of which is most common. The order matches the
     * filter chip row in the notes screen and the
     * [org.mindanchor.model.NoteType] enum, so the
     * prompt is the same shape the user sees.
     */
    private fun typedCountsLine(notes: List<org.mindanchor.model.Note>): String? {
        val typed = notes.filter { it.type != null }
        if (typed.isEmpty()) return null
        val counts = org.mindanchor.model.NoteType.values().associateWith { 0 }.toMutableMap()
        for (n in typed) {
            val t = n.type ?: continue
            counts[t] = (counts[t] ?: 0) + 1
        }
        val parts = counts.entries
            .filter { it.value > 0 }
            .map { (type, c) -> "$c ${type.name.lowercase()}${if (c == 1) "" else "s"}" }
        return parts.joinToString(", ")
    }

    private fun collectSleepSummary(start: LocalDate, end: LocalDate): String {
        return runCatching {
            val repo = SleepRepository(context)
            val summary = repo.estimate() ?: return@runCatching ""
            val windows = summary.windows.filter { it.wakeDate in start..end }
            if (windows.isEmpty()) return@runCatching ""
            val zone = java.time.ZoneId.systemDefault()
            val bedMinutes = windows.map { gap ->
                java.time.Instant.ofEpochMilli(gap.startMillis).atZone(zone).toLocalTime()
                    .toSecondOfDay() / SECONDS_PER_MINUTE
            }
            val wakeMinutes = windows.map { gap ->
                java.time.Instant.ofEpochMilli(gap.endMillis).atZone(zone).toLocalTime()
                    .toSecondOfDay() / SECONDS_PER_MINUTE
            }
            val bedMedian = median(bedMinutes)
            val wakeMedian = median(wakeMinutes)
            "Median bedtime ${formatHm(bedMedian)}, wake ${formatHm(wakeMedian)}."
        }.getOrDefault("").orEmpty()
    }

    private fun median(values: List<Int>): Int {
        if (values.isEmpty()) return 0
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    private fun formatHm(minutes: Int): String =
        "%02d:%02d".format(minutes / SECONDS_PER_MINUTE, minutes % SECONDS_PER_MINUTE)

    private companion object {
        /** 7-day window: end is today, start is 6 days back. */
        const val WINDOW_BACK_DAYS = 6L
        const val WINDOW_TOTAL_DAYS = 7
        /** 0..23. The last hour of the day. */
        const val END_HOUR = 23
        const val END_MINUTE = 59
        const val END_SECOND = 59
        /** 60-character cap on the preview that goes into the prompt. */
        const val PREVIEW_CHARS = 60
        /** seconds-per-minute for time math. */
        const val SECONDS_PER_MINUTE = 60
        /** Trajectory needs at least this many points to be meaningful. */
        const val TRAJECTORY_MIN = 3
        /** Mean-score delta (second-half minus first-half) that flips a bucket. */
        const val TRAJECTORY_THRESHOLD = 2.0
    }
}
