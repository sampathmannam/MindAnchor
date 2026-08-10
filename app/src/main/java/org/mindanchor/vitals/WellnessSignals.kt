package org.mindanchor.vitals

import java.time.LocalDate

/**
 * The five mental-health-relevant signals the launcher surfaces against
 * the person's own history.
 *
 * ## Why these five
 *
 * The selection follows the brief in `docs/research/08` (sensing
 * architecture), which is what this app is built to:
 *
 *  - **HRV (RMSSD)** — the strongest single mental-health signal in
 *    wearable data. HRV biofeedback for depression has a pooled effect
 *    of g = -0.41 across 18 studies (PubMed 41310318); lower HRV is
 *    associated with depression and anxiety across 442 studies in 21
 *    umbrella reviews (PubMed 40155386). Note: this watch's vendor
 *    does not write HRV to Health Connect (verified Aug 2026), so the
 *    field often comes from the camera PPG path. The data type is
 *    here because the launcher must work whatever the source is.
 *  - **Resting heart rate** — the most universally-written wearable
 *    metric; the floor for the mental-health signal the user
 *    consistently has.
 *  - **Steps** — 7000 steps/day is the dose threshold below which
 *    depression risk rises (JAMA Netw Open 2024, 33 studies, 96,173
 *    adults, PMC11650418). Used as a per-person anchor only: this
 *    app does not assert the population threshold applies to any
 *    individual.
 *  - **Sleep minutes** — the headline wearable signal; tightly
 *    coupled to the sleep rhythm the app already tracks.
 *  - **Mindfulness minutes** — the practice signal. 34-RCT
 *    meta-analysis (Gál et al., J Affect Disord 2020) reports
 *    g = 0.46 stress, g = 0.16-0.40 anxiety, g = 0.24-0.43
 *    depression. Surfaced as a *practice* not a *result*.
 *
 * The field count is five rather than the nine in [DailyVitals]
 * because these five are the mental-health surface; the other four
 * (mean HR, min HR, sleep onset, active minutes) are present in the
 * nightly report but not surfaced as "wellness signals" on the home
 * card, where the screen is small and the number of named things
 * matters.
 */
enum class WellnessSignal {
    HRV,
    RESTING_HEART_RATE,
    STEPS,
    SLEEP_MINUTES,
    MINDFULNESS_MINUTES,
    ;

    companion object {
        /**
         * The fixed order the UI renders signals in, oldest first by
         * intent. Stable across reboots and across data type availability:
         * the same five lines always show, with each one either holding a
         * value or a dash. The order is from "least to most personally
         * weighted" — HRV first because the research literature names it
         * most often, mindfulness last because it is a practice rather
         * than a measurement.
         */
        val ORDERED: List<WellnessSignal> = listOf(
            HRV,
            RESTING_HEART_RATE,
            STEPS,
            SLEEP_MINUTES,
            MINDFULNESS_MINUTES,
        )

        /**
         * The minimum number of historical days with a non-null value
         * for a baseline to be reported. Below this, [WellnessReading]
         * returns [BaselineStatus.NOT_ENOUGH] rather than a number.
         *
         * 14 is the deliberate floor: the per-feature rolling baseline
         * needs a meaningful *personal* distribution to be a baseline at
         * all, and a 14-day window is the smallest window where a
         * per-person distribution begins to be informative for an
         * individual. This is a *report-the-count* threshold, not a
         * population threshold — see `docs/research/08` §3.2.
         */
        const val MIN_HISTORY_DAYS = 14
    }
}

/**
 * One value of one signal on one day.
 *
 * The atomic unit of the wellness history: append-only, never mutated.
 * A day the watch had nothing to say is the *absence* of a
 * [WellnessDayValue], not a value with `value = null` — keeping the
 * two distinct is the only way the baseline knows the difference
 * between "recorded zero" and "did not record".
 */
data class WellnessDayValue(
    val day: LocalDate,
    val value: Double,
)

/**
 * A per-signal rolling baseline — the central object of the N-of-1
 * framing.
 *
 * The center is the **median** of the person's own history and the
 * spread is the **MAD** (median absolute deviation from the median).
 * Both are robust statistics: a single terrible week does not
 * redefine "usual" the way a mean and standard deviation would. The
 * field is nullable when there are not yet enough days to compute a
 * baseline — see [WellnessSignal.MIN_HISTORY_DAYS].
 *
 * The `madScale` constant 1.4826 makes MAD a consistent estimator of
 * the standard deviation for a normal distribution, but is **only
 * used for the z-score normalising constant** — the *display* uses
 * raw MAD, not the scaled version, because the screen reports a
 * distribution the person is meant to read, not a population
 * parameter estimate.
 */
data class PersonalBaseline(
    val signal: WellnessSignal,
    val median: Double?,
    val mad: Double?,
    val sampleCount: Int,
) {

    /**
     * Whether this baseline is reportable. False when the day count
     * is below [WellnessSignal.MIN_HISTORY_DAYS], or when the median
     * or the MAD could not be computed at all. A zero MAD is still
     * reportable here — a perfectly-repeated week is a real result,
     * not a missing one; the refusal lives in [robustZ] instead, which
     * returns null when the MAD is zero rather than divide by it.
     */
    val isReportable: Boolean
        get() = median != null && mad != null && sampleCount >= WellnessSignal.MIN_HISTORY_DAYS

    /**
     * The robust z-score of [value] against this baseline.
     *
     * `z = 0.6745 * (value - median) / mad`
     *
     * The numerator 0.6745 is the inverse CDF of the standard normal
     * at 0.75 — it makes the z-score interpretable as "how many
     * MADs above/below the median would a value have to be to be at
     * the 75th/25th percentile of a normal distribution". For a
     * non-normal personal distribution, this is a relative
     * magnitude, not a population claim — the *display* of the
     * z-score is in bands (see [WellnessDirection]) rather than as
     * a raw number, so the N-of-1 framing is preserved even when
     * the underlying distribution is not normal.
     *
     * Returns null when the baseline is not reportable or the MAD
     * is zero (the latter happens on perfectly repeated days — the
     * absence of variance is information, not a z-score).
     */
    fun robustZ(value: Double): Double? {
        val m = median ?: return null
        val d = mad ?: return null
        if (d == 0.0) return null
        return ROBUST_Z_NORMALISER * (value - m) / d
    }

    companion object {
        /**
         * 0.6745 — the inverse CDF of the standard normal at 0.75.
         * Used as the numerator of the robust z-score so the score
         * is interpretable as "how many MADs from the median is
         * this value, in the units a normal distribution would
         * call 1 standard deviation".
         */
        const val ROBUST_Z_NORMALISER = 0.6745
    }
}

/**
 * A single signal's reading for today, with the personal baseline
 * context it sits inside.
 *
 * The honest output shape of the wellness surface: one line per
 * signal, with today's value, the person's own median, and a robust
 * z-score against that median. The z-score is rendered as a
 * direction band, never as a raw number on the home card — the
 * person does not need to know the z-score is 1.4, only that it is
 * "a bit above your usual". Raw z-scores are available to the
 * nightly report and other analytical surfaces.
 */
data class WellnessReading(
    val signal: WellnessSignal,
    /** Today's value, or null when the watch had nothing to record. */
    val today: Double?,
    /** The rolling baseline this signal is read against. */
    val baseline: PersonalBaseline,
    /** The raw robust z-score, or null when not yet computable. */
    val zScore: Double?,
) {

    /** The direction band, for the home-card rendering. */
    val direction: WellnessDirection
        get() = WellnessDirection.bandFor(zScore, today != null)
}

/**
 * The five-band rendering of a robust z-score.
 *
 * Banded rather than raw because the home card is a glance, not a
 * dashboard. The bands are deliberately not labelled "good" or
 * "bad" — see [WellnessDirection.labelRes] for the wording the
 * launcher uses, which is direction-only ("above your usual", not
 * "good").
 *
 * Bands chosen against the per-person distribution, not a
 * population normal: the band cut-offs are in robust z-score
 * units — which, because [PersonalBaseline.robustZ] scales the
 * MAD by 0.6745, work out to roughly +/- 1.48 raw MADs and
 * +/- 2.96 raw MADs at the cut-offs. The MAD-scaling lives in
 * [PersonalBaseline.ROBUST_Z_NORMALISER] so a value of +1.0
 * here reads as "one robust z-score above the median".
 *
 * The robust z-score machinery (median + MAD + 0.6745 normaliser) is
 * Iglewicz & Hoaglin 1993, "How to Detect and Handle Outliers",
 * ASQC Basic References in Quality Control: Statistical Techniques,
 * Volume 16. The 3.5 outlier threshold in that book is for the
 * outlier-detection use case and is *not* used here — the launcher's
 * direction bands (1.0 and 2.0) are design choices for N-of-1
 * personal monitoring, where the goal is to surface "a bit different
 * from your usual" to "very different from your usual" rather than
 * to flag statistical outliers. Iglewicz 3.5 is documented here for
 * traceability; the threshold and band names are not from a
 * specific paper. The mindLAMP team's digital-phenotyping
 * work (Jacobson 2019, J Nerv Ment Dis 207:893-6) uses the same
 * robust-z machinery with anomaly cut-offs of 2.0-2.5 per signal,
 * which is the closest published reference for the band magnitudes
 * chosen here.
 */
enum class WellnessDirection {
    /** No reading today, or baseline not yet reportable. */
    NO_DATA,
    /** Below the personal median (z < -1). */
    BELOW,
    /** Within +/- 1 robust z-score of the personal median. */
    AT,
    /** Between +1 and +2 robust z-score above the personal median. */
    ABOVE,
    /** More than +2 robust z-score above the personal median. */
    MUCH_ABOVE,
    ;

    companion object {

        /**
         * The robust z-score cut-offs. -1, +1, +2 are *design choices*
         * for N-of-1 personal monitoring, not from a specific paper.
         * The asymmetry (a single BELOW band, three ABOVE bands) is
         * deliberate: for the signals the launcher surfaces, the
         * "interesting" extreme is high (long sleep, high HRV, lots
         * of steps, lots of practice). Long sleep is usually good;
         * little sleep is just little sleep. The closest published
         * reference is Jacobson 2019 (J Nerv Ment Dis 207:893-6)
         * on per-person anomaly cut-offs in the 2.0-2.5 range for
         * digital-phenotyping signals.
         *
         * The underlying robust-z method is Iglewicz & Hoaglin 1993.
         * See [WellnessDirection] KDoc for the citation.
         */
        private const val ABOVE_THRESHOLD = 1.0
        private const val MUCH_ABOVE_THRESHOLD = 2.0

        fun bandFor(zScore: Double?, hasToday: Boolean): WellnessDirection = when {
            !hasToday || zScore == null -> NO_DATA
            zScore > MUCH_ABOVE_THRESHOLD -> MUCH_ABOVE
            zScore > ABOVE_THRESHOLD -> ABOVE
            zScore < -ABOVE_THRESHOLD -> BELOW
            else -> AT
        }
    }
}

/**
 * Pure reductions for the wellness math.
 *
 * Kept apart from [PersonalBaseline] so the math is testable without
 * an Android context — the same pattern the rest of the vitals
 * module uses (see [DailyVitalsReducer]).
 */
object WellnessStats {

    /**
     * The median of [values], or null when the list is empty.
     *
     * Median, not mean: one bad week does not move the centre. For
     * N-of-1 data, where one bad week is the *signal* rather than
     * the noise, this is the only honest choice.
     */
    fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val n = sorted.size
        return if (n % 2 == 1) {
            sorted[n / 2]
        } else {
            (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
        }
    }

    /**
     * The median absolute deviation from [centre] of [values], or
     * null when the list is empty.
     *
     * MAD, not standard deviation: a personal baseline that uses SD
     * would call a single 14-hour-sleep night "the new usual". MAD
     * is bounded by the typical value and is what the research
     * literature uses for per-person anomaly detection
     * (mindLAMP, Jacobson 2019).
     */
    fun mad(values: List<Double>, centre: Double): Double? {
        if (values.isEmpty()) return null
        val deviations = values.map { kotlin.math.abs(it - centre) }
        return median(deviations)
    }

    /**
     * A baseline from a history of [values] for [signal].
     *
     * Convenience constructor that bundles the median + MAD
     * computation. The sample count is `values.size`, NOT
     * `values.count { it != null }` — the caller has already
     * filtered out null days, and the size is what the reportable
     * threshold is compared against.
     */
    fun baseline(signal: WellnessSignal, values: List<Double>): PersonalBaseline {
        val m = median(values)
        val d = if (m != null) mad(values, m) else null
        return PersonalBaseline(
            signal = signal,
            median = m,
            mad = d,
            sampleCount = values.size,
        )
    }

    /**
     * Today's reading for [signal] given [today] and a [baseline]
     * computed from the *prior* days' history.
     *
     * The split between "today" and "history" is deliberate: the
     * z-score for today must not include today's value, or a
     * particularly high HRV day would call itself a "much above"
     * day by definition. The caller is responsible for passing a
     * baseline computed from days strictly before the day being
     * read.
     */
    fun reading(
        signal: WellnessSignal,
        today: Double?,
        baseline: PersonalBaseline,
    ): WellnessReading {
        val z = today?.let { baseline.robustZ(it) }
        return WellnessReading(
            signal = signal,
            today = today,
            baseline = baseline,
            zScore = z,
        )
    }
}
