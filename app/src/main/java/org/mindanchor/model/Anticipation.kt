package org.mindanchor.model

import kotlin.math.abs

/**
 * What this person has actually reported on days that looked like today.
 *
 * ## Why a neighbourhood and not a prediction
 *
 * With a confirmed [Link] in hand the obvious next step is to fit a line
 * and read a predicted value off it. That was rejected. A regression
 * prediction on thirty days arrives with an interval so wide it covers
 * most of a five-point scale, and reporting the point estimate without
 * that interval is the single most misleading thing this app could do —
 * "you will feel 2.3 today" is a machine claiming to know something about
 * somebody's inner life that it cannot possibly know.
 *
 * So instead of predicting, this **looks it up**. Of all the days in this
 * person's own record, it takes the ones whose signal was nearest to
 * today's, and reports what they said on those days against what they say
 * on an ordinary day. That is a fact about their history, checkable
 * against their own memory, and wrong only if the arithmetic is wrong.
 *
 * The difference is not cosmetic. "On the nine days your sleep looked
 * like last night's, you have tended to report feeling worse than usual"
 * invites somebody to recognise something or to dismiss it. "You will
 * feel bad today" tells them how their day is going to go before they
 * have had it, which is both unfalsifiable and, for anyone already
 * keeping score against themselves, a small cruelty.
 */
object Anticipation {

    /**
     * Nearest days considered, as a share of the record.
     *
     * A quarter: wide enough that the median is not one bad night, narrow
     * enough that "days like today" still means something. On the
     * twenty-eight days [LinkFinder] requires, that is seven days.
     */
    const val NEIGHBOURHOOD_FRACTION = 0.25

    /** No neighbourhood smaller than this, whatever the fraction works out to. */
    const val MIN_NEIGHBOURS = 5

    /**
     * How far the neighbourhood's median must sit from the ordinary one
     * before it is worth saying out loud.
     *
     * Half a point on a one-to-five scale. Below that the two medians are
     * the same answer written differently, and a system that remarks on
     * every difference it can measure is one that people stop reading —
     * the same reasoning as [org.mindanchor.report.ReportComposer]'s
     * insistence that a steady day render as silence.
     */
    const val MIN_DIFFERENCE = 0.5

    /**
     * What was found, or nothing.
     *
     * [medianWhenLikeToday] and [medianOverall] are both on the label's
     * own one-to-five scale, so the pair can be read directly without any
     * further arithmetic by whatever displays them.
     */
    data class Neighbourhood(
        val similarDays: Int,
        val medianWhenLikeToday: Double,
        val medianOverall: Double,
    ) {
        val lower: Boolean get() = medianWhenLikeToday < medianOverall
    }

    /**
     * Looks up the days most like today and what was reported on them.
     *
     * [link] must already hold — see [Link.holds]. Without a confirmed
     * link this would be picking a signal at random and finding whatever
     * pattern noise happened to leave in it, which is precisely the
     * assuming-from-nothing this whole design refuses. A link that does
     * not hold returns null here, not a quieter version of the same claim.
     *
     * [history] is the same paired series the link was found in, and
     * [todaysSignal] is the value being asked about — the most recent
     * night's, in the ordinary case.
     */
    fun forToday(
        link: Link,
        history: List<Paired>,
        todaysSignal: Double,
    ): Neighbourhood? {
        if (!link.holds) return null
        if (history.size < LinkFinder.MIN_PAIRED_DAYS) return null

        val neighbours = maxOf(MIN_NEIGHBOURS, (history.size * NEIGHBOURHOOD_FRACTION).toInt())
        if (neighbours > history.size) return null

        // Nearest by signal value, ties broken by label so the same
        // history and the same day always select the same neighbours —
        // an answer that reshuffles between two runs on identical data
        // looks like new information when it is not.
        val nearest = history
            .sortedWith(
                compareBy<Paired> { abs(it.signal - todaysSignal) }
                    .thenBy { it.signal }
                    .thenBy { it.label },
            )
            .take(neighbours)

        val here = median(nearest.map { it.label }) ?: return null
        val overall = median(history.map { it.label }) ?: return null
        if (abs(here - overall) < MIN_DIFFERENCE) return null

        return Neighbourhood(
            similarDays = nearest.size,
            medianWhenLikeToday = here,
            medianOverall = overall,
        )
    }

    /** The middle value. Median rather than mean: one bad day is not the usual. */
    fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        }
    }
}
