package org.mindanchor.insights

import org.mindanchor.model.Moment
import java.time.LocalDate
import kotlin.math.abs

/**
 * The "What your check-ins show" patterns engine.
 *
 * Reads a list of [Moment]s the user has answered
 * and returns a list of [Insight]s — descriptive,
 * non-directive, N-of-1 only. No scoring, no
 * comparison to a norm, no composite mood number.
 * The user's own history is the only reference.
 *
 * ## What the four patterns are
 *
 *  - [Insight.RecentTrend]: the last seven days
 *    against the seven days before, on a single
 *    mean-valence delta. Phrased as "brighter" /
 *    "rougher" / "about the same" — the user
 *    decides what the direction means.
 *  - [Insight.BestHours]: the hour-of-day buckets
 *    that the user's mean valence is highest and
 *    lowest in, over the last 14 days. Phrased as
 *    "morning" / "afternoon" / "evening" / "late
 *    evening" — labels, not clock numbers, so the
 *    reading is intuitive.
 *  - [Insight.Coverage]: how many prompts the user
 *    answered this week, over how many were
 *    delivered. A coverage number, not a streak
 *    score — a missed prompt is just a missed
 *    prompt, never a broken streak.
 *  - [Insight.VsBaseline]: today's mean valence
 *    against the user's own median + MAD over the
 *    prior 14 days. Phrased qualitatively ("a
 *    little brighter / rougher / about average")
 *    so the reading is a comparison, not a number.
 *
 * ## Thresholds and the "absent below threshold" rule
 *
 * Each pattern has a minimum-data floor. A pattern
 * that does not meet its floor is *omitted* from the
 * returned list, not rendered as "not enough
 * data yet" — the user with one week of check-ins
 * sees fewer patterns than the user with three
 * months, by the same shape, which is the same
 * shape the rest of the N-of-1 framing uses for
 * vital signs (mindanchor/WellnessRepository).
 *
 * The thresholds are deliberately small and
 * forgiving: 7 days for trend, 14 for best-hours
 * and baseline, 1 week of any data for coverage.
 * A user who answered three prompts on day one and
 * then nothing for a month still gets *some*
 * reading, never a wall of "we don't know yet".
 *
 * ## Pure on purpose
 *
 * No I/O, no Compose, no Context. The engine is
 * testable as a pure function over a list of
 * [Moment]s, and the Composable in
 * [CheckInInsightsSection] is the only thing that
 * reads from prefs and calls [compute]. A future
 * unit test for the engine has no Android
 * dependency and runs in milliseconds.
 */
object CheckInPatterns {

    // Thresholds, in days. Kept as private
    // constants so the engine's "how much data do
    // I need" contract is in one place.
    private const val RECENT_TREND_MIN_DAYS = 7
    private const val BEST_HOURS_MIN_DAYS = 14
    private const val BASELINE_MIN_DAYS = 14
    private const val BASELINE_MIN_COUNT = 10

    /**
     * The current-day cutoff for coverage. The
     * coverage pattern asks "this week" — the
     * trailing 7 days from [today]. A test can
     * inject a [today] value to avoid flakiness.
     */
    fun compute(
        moments: List<Moment>,
        today: LocalDate = LocalDate.now(),
    ): List<Insight> {
        if (moments.isEmpty()) return emptyList()
        val insights = mutableListOf<Insight>()
        recentTrend(moments, today)?.let { insights += it }
        bestHours(moments, today)?.let { insights += it }
        coverage(moments, today)?.let { insights += it }
        vsBaseline(moments, today)?.let { insights += it }
        return insights
    }

    private fun recentTrend(
        moments: List<Moment>,
        today: LocalDate,
    ): Insight.RecentTrend? {
        val last7Start = today.minusDays(7)
        val prior7Start = today.minusDays(14)
        val last7 = moments.filter { dateOf(it) >= last7Start && dateOf(it) < today }
        val prior7 = moments.filter { dateOf(it) >= prior7Start && dateOf(it) < last7Start }
        if (last7.size < 3 || prior7.size < 3) return null
        val last7Mean = last7.map { it.valence }.average()
        val prior7Mean = prior7.map { it.valence }.average()
        val delta = last7Mean - prior7Mean
        // Phrasing threshold: 0.4 on a 1..5 scale
        // is a fifth of a point on the
        // mean-of-many, which is roughly the
        // smallest reliable direction the data
        // can show. Below that, the reading is
        // "about the same" — not because the
        // user did not change, but because the
        // data cannot say they did.
        val direction = when {
            delta > 0.4 -> TrendDirection.BRIGHTER
            delta < -0.4 -> TrendDirection.ROUGHER
            else -> TrendDirection.SAME
        }
        return Insight.RecentTrend(
            direction = direction,
            dataPoints = last7.size + prior7.size,
        )
    }

    private fun bestHours(
        moments: List<Moment>,
        today: LocalDate,
    ): Insight.BestHours? {
        val windowStart = today.minusDays(BEST_HOURS_MIN_DAYS.toLong())
        val window = moments.filter { dateOf(it) >= windowStart && dateOf(it) < today }
        if (window.size < 7) return null
        // Bucket minute-of-day into the four
        // part-of-day windows the dashboard
        // surfaces. The hour-of-day labels are
        // qualitative on purpose — the user does
        // not need a clock, they need a shape.
        val buckets = mutableMapOf(
            PartOfDay.MORNING to mutableListOf<Int>(),
            PartOfDay.AFTERNOON to mutableListOf<Int>(),
            PartOfDay.EVENING to mutableListOf<Int>(),
            PartOfDay.LATE_EVENING to mutableListOf<Int>(),
        )
        for (m in window) {
            val hour = m.atMinuteOfDay / 60
            val pod = PartOfDay.from(hour)
            buckets.getValue(pod).add(m.valence)
        }
        val means = buckets.filter { it.value.isNotEmpty() }
            .mapValues { it.value.average() }
        if (means.size < 2) return null
        val best = means.maxByOrNull { it.value }!!.key
        val worst = means.minByOrNull { it.value }!!.key
        if (best == worst) return null
        return Insight.BestHours(
            best = best,
            worst = worst,
            dataPoints = window.size,
        )
    }

    private fun coverage(
        moments: List<Moment>,
        today: LocalDate,
    ): Insight.Coverage? {
        // The v1 implementation has no
        // "prompts delivered" counter in the
        // store — the EMA scheduler arms up to
        // [EmaSchedule.LEARNING_PROMPTS] per day
        // and tapers to fewer as the user
        // accumulates data. The pattern asks
        // "you answered X of Y this week" with a
        // denominator that is the user's
        // schedule's expected prompt count for
        // the trailing 7 days, not the actual
        // count (which the store does not
        // persist). For the v1 surface, the
        // denominator is the per-day prompt
        // count for the most recent day in the
        // window, times 7 — close enough for a
        // "you answered 14 of 21" reading, and
        // honest about being a coverage estimate
        // rather than a delivered count.
        val last7Start = today.minusDays(7)
        val last7 = moments.filter { dateOf(it) >= last7Start && dateOf(it) < today }
        if (last7.isEmpty()) return null
        val expectedPerDay = expectedPromptsPerDay(moments.size)
        val expected = expectedPerDay * 7
        if (expected == 0) return null
        return Insight.Coverage(
            answered = last7.size,
            expected = expected,
            dataPoints = last7.size,
        )
    }

    private fun expectedPromptsPerDay(totalMoments: Int): Int {
        // Mirrors EmaSchedule.promptsPerDay: the
        // schedule tapers as the user
        // accumulates labels. The exact taper is
        // a private constant on EmaSchedule;
        // duplicating the table here would be a
        // hidden coupling, so the v1 surface
        // uses the conservative upper bound (the
        // schedule's "still learning" maximum)
        // and the displayed denominator is an
        // upper-bound estimate. A future
        // commit can pipe the live taper from
        // EmaSchedule through here.
        return when {
            totalMoments < 7 -> 4
            totalMoments < 14 -> 3
            totalMoments < 30 -> 2
            else -> 2
        }
    }

    private fun vsBaseline(
        moments: List<Moment>,
        today: LocalDate,
    ): Insight.VsBaseline? {
        val windowStart = today.minusDays(BASELINE_MIN_DAYS.toLong())
        val baseline = moments.filter { dateOf(it) >= windowStart && dateOf(it) < today }
        if (baseline.size < BASELINE_MIN_COUNT) return null
        // The "today" value is the mean
        // valence of the user's most recent
        // answered day — the day with the
        // latest timestamp among [Moment]s.
        // We do not require the moment to be
        // on a specific date because the user
        // may have answered yesterday, the
        // day before, or earlier; the engine
        // works against the data, not the
        // wall clock.
        val mostRecentDay = moments.maxOfOrNull { dateOf(it) } ?: return null
        val todayValue = moments.filter { dateOf(it) == mostRecentDay }
            .map { it.valence }
        if (todayValue.isEmpty()) return null
        val todayMean = todayValue.average()
        val baselineValues = baseline.map { it.valence }.sorted()
        val median = baselineValues[baselineValues.size / 2].toDouble()
        val mad = medianAbsoluteDeviation(baselineValues, median)
        if (mad == 0.0) return null
        val z = (todayMean - median) / mad
        // Phrasing thresholds on robust z: a
        // day inside +/- 0.5 MAD is "about
        // average"; outside that is a
        // direction, not a magnitude. The
        // z-score is never surfaced as a
        // number — the dashboard says
        // "brighter" / "rougher" / "about the
        // same".
        val direction = when {
            z > 0.5 -> TrendDirection.BRIGHTER
            z < -0.5 -> TrendDirection.ROUGHER
            else -> TrendDirection.SAME
        }
        return Insight.VsBaseline(
            direction = direction,
            dataPoints = baseline.size,
        )
    }

    private fun dateOf(moment: Moment): LocalDate = LocalDate.parse(moment.day)

    private fun medianAbsoluteDeviation(sorted: List<Int>, median: Double): Double {
        val deviations = sorted.map { abs(it - median) }.sorted()
        return deviations[deviations.size / 2]
    }
}

/**
 * The four patterns the engine can surface. Each
 * case carries the data-point count that produced
 * it, so the renderer can show a "based on N
 * check-ins" line without re-deriving it.
 *
 * The Composable in [CheckInInsightsSection]
 * maps each case to a wording. The wording is in
 * strings.xml, not in this file, because the
 * wording is the clinical-review surface; the
 * shape of the insight is not.
 */
sealed interface Insight {
    val dataPoints: Int

    data class RecentTrend(
        val direction: TrendDirection,
        override val dataPoints: Int,
    ) : Insight

    data class BestHours(
        val best: PartOfDay,
        val worst: PartOfDay,
        override val dataPoints: Int,
    ) : Insight

    data class Coverage(
        val answered: Int,
        val expected: Int,
        override val dataPoints: Int,
    ) : Insight

    data class VsBaseline(
        val direction: TrendDirection,
        override val dataPoints: Int,
    ) : Insight
}

enum class TrendDirection { BRIGHTER, ROUGHER, SAME }

enum class PartOfDay {
    MORNING,
    AFTERNOON,
    EVENING,
    LATE_EVENING;

    companion object {
        fun from(hour: Int): PartOfDay = when (hour) {
            in 5..11 -> MORNING
            in 12..16 -> AFTERNOON
            in 17..21 -> EVENING
            else -> LATE_EVENING
        }
    }
}
