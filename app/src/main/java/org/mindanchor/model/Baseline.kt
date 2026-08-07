package org.mindanchor.model

import kotlin.math.abs

/**
 * One person's own normal, and how far from it today is.
 *
 * ## Why this is the whole design
 *
 * Cross-person inference from phone signals does not transfer. Müller,
 * Harari et al. (*Scientific Reports* 2021) is the reference failure: a
 * GPS-mobility depression pipeline scored AUC 0.82 in 57 students and
 * **0.57 — chance — in 5,262**. The field mostly read that as "sensing
 * does not work". The precise reading is that *population models applied
 * to individuals* do not work.
 *
 * A baseline built from one person's own history is a different object.
 * It never has to generalise to anyone, which is the only thing the
 * evidence says it would fail at.
 *
 * So nothing here holds a threshold taken from a paper. There is no
 * "resting HRV below 30 ms is concerning", because that number is a
 * population fact and this file only deals in one person. Every statement
 * it can make is of the form *this is unusual for you*, measured against
 * what you have actually done.
 *
 * ## Why median and MAD rather than mean and standard deviation
 *
 * Both of the ordinary statistics are wrecked by exactly the days that
 * matter. One sleepless week moves a mean and inflates a standard
 * deviation enough that the following bad week reads as normal — the
 * baseline quietly absorbs the thing it exists to detect.
 *
 * The median and the median absolute deviation have a breakdown point of
 * 50%: half the observations would have to be extreme before the estimate
 * moves. The scaling constant 1.4826 makes MAD comparable to a standard
 * deviation for normally-distributed data, so a "robust z" reads on the
 * familiar scale without inheriting the fragility.
 */
object Baseline {

    /**
     * Below this many observations there is no such thing as "usual" and
     * this refuses to pretend otherwise.
     *
     * Fourteen because most of what is being tracked is daily and has a
     * weekly rhythm — a working week and a weekend look different, and a
     * baseline built from one of them alone would call the other one an
     * anomaly, every week, forever.
     */
    const val MIN_OBSERVATIONS = 14

    /** Makes MAD comparable to a standard deviation under normality. */
    const val MAD_TO_SIGMA = 1.4826

    /**
     * A dispersion floor, in robust-z units of the median.
     *
     * Someone whose bedtime is genuinely identical every night has a MAD
     * near zero, and dividing by it turns a five-minute difference into a
     * z-score of forty. The floor is proportional to the median rather
     * than absolute so it means the same thing whether the signal is
     * milliseconds of HRV or thousands of steps.
     */
    const val MIN_DISPERSION_FRACTION = 0.02

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

    /** Median absolute deviation, already scaled to sigma-equivalent units. */
    fun robustSigma(values: List<Double>): Double? {
        val centre = median(values) ?: return null
        val mad = median(values.map { abs(it - centre) }) ?: return null
        val scaled = mad * MAD_TO_SIGMA
        val floor = abs(centre) * MIN_DISPERSION_FRACTION
        return maxOf(scaled, floor)
    }

    /**
     * How unusual [today] is against [history], in robust standard
     * deviations, or null when there is not yet enough history to say.
     *
     * Returning null is a real answer and callers must render it as
     * silence. A system that always has something to report about you has
     * taught you to stop reading it.
     *
     * [history] must not include [today]; comparing a value against a set
     * that contains it drags the baseline toward the very observation
     * being tested and shrinks every deviation.
     *
     * **The magnitude is not always meaningful.** When a history is almost
     * perfectly regular the dispersion floor binds, and the returned value
     * can be enormous — a simulation of twenty identical nights plus six
     * bad ones produces z ≈ 44. That is the floor doing its job, not a
     * measurement. Treat the sign and [isNotable] as the output; never put
     * the raw number in front of a person, because "you are 44 standard
     * deviations from normal" is both alarming and meaningless.
     */
    fun robustZ(today: Double, history: List<Double>): Double? {
        if (history.size < MIN_OBSERVATIONS) return null
        val centre = median(history) ?: return null
        val sigma = robustSigma(history) ?: return null
        if (sigma <= 0.0) return null
        return (today - centre) / sigma
    }

    /**
     * Whether a deviation is worth showing a person at all.
     *
     * Two robust sigma, in the direction that matters. Not because two is
     * a magic number — it is roughly the 95th percentile under normality,
     * which is the familiar convention — but because the alternative is
     * choosing a threshold from a paper about other people, and this file
     * exists specifically to not do that.
     *
     * The honest limitation, written down rather than buried: the
     * observations are not independent and are not normally distributed,
     * so this is a **screening heuristic for what to look at**, not a
     * hypothesis test, and nothing downstream should describe it as
     * significance.
     */
    const val NOTABLE_Z = 2.0

    fun isNotable(z: Double?): Boolean = z != null && abs(z) >= NOTABLE_Z
}
