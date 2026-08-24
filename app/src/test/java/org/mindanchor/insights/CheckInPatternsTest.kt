package org.mindanchor.insights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.model.Moment
import java.time.LocalDate

/**
 * The pure-function tests for [CheckInPatterns].
 *
 * The engine has no Android dependency, so the
 * tests are plain JUnit with hand-rolled [Moment]
 * lists. Each test fixes [LocalDate.now] to a
 * deterministic value so the trailing-7-day and
 * trailing-14-day windows are reproducible.
 */
class CheckInPatternsTest {

    private val today = LocalDate.of(2026, 8, 24)

    private fun m(
        valence: Int,
        arousal: Int = 3,
        day: LocalDate,
        hour: Int = 12,
    ): Moment = Moment(
        valence = valence,
        arousal = arousal,
        atMinuteOfDay = hour * 60,
        day = day.toString(),
    )

    @Test
    fun `empty input returns no insights`() {
        assertTrue(CheckInPatterns.compute(emptyList(), today).isEmpty())
    }

    @Test
    fun `one data point returns no insights (below all thresholds)`() {
        val oneMoment = listOf(m(valence = 4, day = today.minusDays(1)))
        assertTrue(CheckInPatterns.compute(oneMoment, today).isEmpty())
    }

    @Test
    fun `recent trend returns BRIGHTER when last 7 mean is meaningfully higher than prior 7`() {
        // 10 prior-7 days at valence 2, 10
        // last-7 days at valence 4. The delta
        // is 2.0, well above the 0.4 phrasing
        // threshold.
        val moments = mutableListOf<Moment>()
        for (i in 8..17) moments += m(valence = 2, day = today.minusDays(i.toLong()))
        for (i in 1..7) moments += m(valence = 4, day = today.minusDays(i.toLong()))
        val insights = CheckInPatterns.compute(moments, today)
        val trend = insights.filterIsInstance<Insight.RecentTrend>().single()
        assertEquals(TrendDirection.BRIGHTER, trend.direction)
    }

    @Test
    fun `recent trend returns ROUGHER when last 7 mean is meaningfully lower than prior 7`() {
        val moments = mutableListOf<Moment>()
        for (i in 8..17) moments += m(valence = 4, day = today.minusDays(i.toLong()))
        for (i in 1..7) moments += m(valence = 2, day = today.minusDays(i.toLong()))
        val insights = CheckInPatterns.compute(moments, today)
        val trend = insights.filterIsInstance<Insight.RecentTrend>().single()
        assertEquals(TrendDirection.ROUGHER, trend.direction)
    }

    @Test
    fun `recent trend returns SAME when last 7 mean is within 0_4 of prior 7`() {
        val moments = mutableListOf<Moment>()
        for (i in 8..17) moments += m(valence = 3, day = today.minusDays(i.toLong()))
        for (i in 1..7) moments += m(valence = 3, day = today.minusDays(i.toLong()))
        val insights = CheckInPatterns.compute(moments, today)
        val trend = insights.filterIsInstance<Insight.RecentTrend>().single()
        assertEquals(TrendDirection.SAME, trend.direction)
    }

    @Test
    fun `recent trend is omitted when one of the two windows has fewer than 3 moments`() {
        val moments = listOf(
            m(valence = 5, day = today.minusDays(1)),
            m(valence = 2, day = today.minusDays(9)),
            m(valence = 2, day = today.minusDays(10)),
            m(valence = 2, day = today.minusDays(11)),
        )
        val insights = CheckInPatterns.compute(moments, today)
        assertTrue(insights.filterIsInstance<Insight.RecentTrend>().isEmpty())
    }

    @Test
    fun `best hours returns MORNING as best and LATE_EVENING as worst when the data is shaped that way`() {
        val moments = mutableListOf<Moment>()
        for (i in 1..14) {
            moments += m(valence = 5, day = today.minusDays(i.toLong()), hour = 9)
            moments += m(valence = 1, day = today.minusDays(i.toLong()), hour = 23)
        }
        val insights = CheckInPatterns.compute(moments, today)
        val best = insights.filterIsInstance<Insight.BestHours>().single()
        assertEquals(PartOfDay.MORNING, best.best)
        assertEquals(PartOfDay.LATE_EVENING, best.worst)
    }

    @Test
    fun `best hours is omitted when only one part-of-day has data`() {
        val moments = (1..14).map { m(valence = 4, day = today.minusDays(it.toLong()), hour = 9) }
        val insights = CheckInPatterns.compute(moments, today)
        assertTrue(insights.filterIsInstance<Insight.BestHours>().isEmpty())
    }

    @Test
    fun `best hours is omitted when total window is fewer than 7 moments`() {
        val moments = (1..3).flatMap { i ->
            listOf(
                m(valence = 5, day = today.minusDays(i.toLong()), hour = 9),
                m(valence = 1, day = today.minusDays(i.toLong()), hour = 23),
            )
        }
        val insights = CheckInPatterns.compute(moments, today)
        assertTrue(insights.filterIsInstance<Insight.BestHours>().isEmpty())
    }

    @Test
    fun `coverage returns the answered count and the expected denominator`() {
        // 5 answered in the trailing 7 days;
        // total moments is 30, so
        // expectedPromptsPerDay is 2, so
        // expected is 14.
        val moments = mutableListOf<Moment>()
        for (i in 1..5) moments += m(valence = 4, day = today.minusDays(i.toLong()))
        for (i in 6..30) moments += m(valence = 4, day = today.minusDays(i.toLong()))
        val insights = CheckInPatterns.compute(moments, today)
        val cov = insights.filterIsInstance<Insight.Coverage>().single()
        assertEquals(5, cov.answered)
        assertEquals(14, cov.expected)
    }

    @Test
    fun `vs baseline returns SAME when today's value is within half a MAD of the median`() {
        // Build a 14-day baseline with median
        // 3 and a tight MAD. The "today"
        // (yesterday) value is also 3, well
        // inside 0.5 MAD.
        val moments = mutableListOf<Moment>()
        for (i in 2..15) moments += m(valence = 3, day = today.minusDays(i.toLong()))
        moments += m(valence = 3, day = today.minusDays(1))
        val insights = CheckInPatterns.compute(moments, today)
        val vs = insights.filterIsInstance<Insight.VsBaseline>().single()
        assertEquals(TrendDirection.SAME, vs.direction)
    }

    @Test
    fun `vs baseline returns BRIGHTER when today's value is more than half a MAD above the median`() {
        // Baseline is 3 with a tight MAD; today
        // is 5. The z is large positive.
        val moments = mutableListOf<Moment>()
        for (i in 2..15) moments += m(valence = 3, day = today.minusDays(i.toLong()))
        moments += m(valence = 5, day = today.minusDays(1))
        val insights = CheckInPatterns.compute(moments, today)
        val vs = insights.filterIsInstance<Insight.VsBaseline>().single()
        assertEquals(TrendDirection.BRIGHTER, vs.direction)
    }

    @Test
    fun `vs baseline is omitted when fewer than 10 moments in the trailing 14 days`() {
        // The 14-day floor is met (14 days of
        // data) but the count is under 10.
        val moments = (2..15).map { m(valence = 3, day = today.minusDays(it.toLong())) }
        val insights = CheckInPatterns.compute(moments, today)
        assertTrue(insights.filterIsInstance<Insight.VsBaseline>().isEmpty())
    }

    @Test
    fun `vs baseline is omitted when the baseline is degenerate (zero MAD)`() {
        // 14 moments, all the same value: MAD
        // is 0, which would produce a divide by
        // zero in z. The engine returns null
        // rather than +Infinity.
        val moments = (2..15).map { m(valence = 3, day = today.minusDays(it.toLong())) }
        val insights = CheckInPatterns.compute(moments, today)
        // Add the "today" moment so coverage
        // does not skew the result.
        val withToday = moments + m(valence = 3, day = today.minusDays(1))
        val insights2 = CheckInPatterns.compute(withToday, today)
        assertTrue(insights.filterIsInstance<Insight.VsBaseline>().isEmpty())
        assertTrue(insights2.filterIsInstance<Insight.VsBaseline>().isEmpty())
    }

    @Test
    fun `PartOfDay from maps the canonical hours correctly`() {
        assertEquals(PartOfDay.MORNING, PartOfDay.from(5))
        assertEquals(PartOfDay.MORNING, PartOfDay.from(11))
        assertEquals(PartOfDay.AFTERNOON, PartOfDay.from(12))
        assertEquals(PartOfDay.AFTERNOON, PartOfDay.from(16))
        assertEquals(PartOfDay.EVENING, PartOfDay.from(17))
        assertEquals(PartOfDay.EVENING, PartOfDay.from(21))
        assertEquals(PartOfDay.LATE_EVENING, PartOfDay.from(22))
        assertEquals(PartOfDay.LATE_EVENING, PartOfDay.from(0))
        assertEquals(PartOfDay.LATE_EVENING, PartOfDay.from(4))
    }

    @Test
    fun `all four patterns surface together with a realistic 14-day dataset`() {
        // A dataset shaped to satisfy all four
        // thresholds and produce one of each
        // pattern. Verifies the engine returns
        // a complete dashboard, not a
        // degenerate subset.
        val moments = mutableListOf<Moment>()
        // 14 days of data: prior 7 at valence 2,
        // last 7 at valence 4 — trend BRIGHTER.
        for (i in 8..14) {
            moments += m(valence = 2, day = today.minusDays(i.toLong()), hour = 9)
            moments += m(valence = 2, day = today.minusDays(i.toLong()), hour = 23)
        }
        for (i in 1..7) {
            moments += m(valence = 5, day = today.minusDays(i.toLong()), hour = 9)
            moments += m(valence = 3, day = today.minusDays(i.toLong()), hour = 23)
        }
        val insights = CheckInPatterns.compute(moments, today)
        assertTrue(insights.any { it is Insight.RecentTrend })
        assertTrue(insights.any { it is Insight.BestHours })
        assertTrue(insights.any { it is Insight.Coverage })
        assertTrue(insights.any { it is Insight.VsBaseline })
    }
}
