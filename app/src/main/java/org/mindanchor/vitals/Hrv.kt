package org.mindanchor.vitals

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Which artifact-rejection rule to apply.
 *
 * [COMBINED] is the default and what everything here is tuned around. The
 * other two exist because each one alone has a blind spot worth being able
 * to demonstrate.
 */
enum class ArtifactRule {
    /** Karlsson's local-median test and Malik's successive test, both. */
    COMBINED,

    /** Local median only. Robust to isolated outliers, blind to bigeminy. */
    KARLSSON_MEDIAN,

    /** Successive-difference only. Catches alternation, drifts on long runs. */
    MALIK,
}

/**
 * What survived artifact rejection, and what did not.
 *
 * [acceptedIndices] are positions in the original series. They are kept
 * because RMSSD needs to know which accepted intervals were genuinely
 * *adjacent* — see [Hrv.timeDomain].
 */
data class CleanedBeats(
    val acceptedIndices: List<Int>,
    val acceptedMillis: List<Double>,
    val rejectedCount: Int,
    val totalCount: Int,
) {
    val rejectedFraction: Double
        get() = if (totalCount == 0) 0.0 else rejectedCount.toDouble() / totalCount

    val acceptedDurationMillis: Double
        get() = acceptedMillis.sum()
}

/**
 * Time-domain HRV, as defined by the 1996 Task Force standard.
 *
 * [sdnnMillis] is reported but should be read with care: over a single
 * minute SDNN mostly reflects respiration, and is not comparable to the
 * five-minute SDNN that published norms are built on. [rmssdMillis] is the
 * one that survives a short window, and the one worth trending.
 */
data class HrvMetrics(
    val meanRrMillis: Double,
    val meanHeartRate: Double,
    val sdnnMillis: Double,
    val rmssdMillis: Double,
    val pnn50Percent: Double,
    /** Accepted intervals used for mean, SDNN. */
    val intervalCount: Int,
    /** Adjacent accepted pairs used for RMSSD, pNN50. Fewer, after rejection. */
    val successivePairCount: Int,
)

/**
 * The outcome of asking for HRV. [metrics] is null whenever the answer
 * would not be worth having, with [refusal] saying why in plain words.
 */
data class HrvResult(
    val cleaned: CleanedBeats,
    val metrics: HrvMetrics?,
    val refusal: String?,
)

/**
 * Turns beat-to-beat intervals into heart-rate variability.
 *
 * Almost all of the difficulty here is artifact rejection, and it is worth
 * being blunt about why. RMSSD is the root mean square of *successive
 * differences*, so it squares the very thing a missed or spurious beat
 * produces. One missed beat merges two intervals into a double-length one
 * and creates two large differences where there were none. On a synthetic
 * series with five injected artifacts among ninety beats — under 6% of the
 * recording — naive RMSSD came out at 215 ms against a true value of
 * 2.6 ms. Eighty-two times too large, from a recording that looks fine.
 *
 * So nothing is computed before the series has been cleaned, and the number
 * of rejected beats is always reported rather than quietly absorbed.
 *
 * Standards and methods followed:
 *
 *  - Task Force of the European Society of Cardiology and the North
 *    American Society of Pacing and Electrophysiology (1996), "Heart rate
 *    variability: standards of measurement, physiological interpretation,
 *    and clinical use", Circulation 93(5):1043-1065 — the metric
 *    definitions, and the 20% tolerance that Malik's editing rule uses.
 *  - Karlsson et al. (2012), "Automatic filtering of outliers in RR
 *    intervals before analysis of heart rate variability in Holter
 *    recordings: a comparison with carefully edited data", BioMedical
 *    Engineering OnLine 11:2 — the local-median test.
 *  - Shaffer & Ginsberg (2017), "An Overview of Heart Rate Variability
 *    Metrics and Norms", Frontiers in Public Health 5:258 — what an
 *    ultra-short window can and cannot support.
 */
object Hrv {

    /**
     * The tolerance both rejection tests use.
     *
     * Twenty percent is Malik's figure and the one the Task Force report
     * carries. It is deliberately loose: genuine respiratory sinus
     * arrhythmia in a healthy person at rest can swing intervals by well
     * over a tenth, and a tighter rule would shave off exactly the
     * variability being measured. Checked against a synthetic recording
     * with 120 ms of respiratory modulation — a genuinely high-variability
     * subject — this rejected nothing at all.
     */
    const val TOLERANCE = 0.20

    /**
     * Five intervals: the one under test and two either side.
     *
     * Wide enough that a single artifact cannot drag the median, narrow
     * enough to follow a real drift in rate.
     */
    const val MEDIAN_WINDOW = 5

    /**
     * The smallest number of clean intervals worth reporting on.
     *
     * The Task Force standard defines short-term HRV over five minutes.
     * That is not something anybody will hold a fingertip still for, and
     * the ultra-short-term literature — Shaffer & Ginsberg (2017), and
     * Baek et al. (2015) in Telemedicine and e-Health 21(5) — finds RMSSD
     * reliable from about a minute, while SDNN needs longer. So a minute is
     * the floor, expressed two ways: enough elapsed time, and enough beats
     * that the statistic is not resting on a handful of them.
     *
     * At a slow 45 bpm a minute is only 45 beats, so the count is the
     * binding constraint there; at 100 bpm the duration binds instead. Both
     * must hold.
     */
    const val MIN_CLEAN_INTERVALS = 50
    const val MIN_CLEAN_DURATION_MILLIS = 60_000.0

    /**
     * Beyond this, the recording is refused outright rather than reported
     * from what is left.
     *
     * Heavy editing does not merely lose beats, it selects them: the
     * intervals that survive are the ones that agreed with their
     * neighbours, so what remains is biased smooth and RMSSD reads low. The
     * HRV literature commonly treats more than about 5% correction as
     * making time-domain measures unreliable. A fifth is set as the hard
     * refusal — lenient by that standard, but past it the number would be
     * an artifact of the cleaning rather than a measurement.
     */
    const val MAX_REJECTED_FRACTION = 0.20

    /**
     * Removes intervals that a heart did not produce.
     *
     * Three tests, in order:
     *
     *  1. Range. Anything outside 42-210 bpm is not a beat-to-beat interval
     *     — it is a missed beat merged with its neighbour, or a doubled
     *     detection. Excluded intervals are also kept out of the median
     *     window below, so a wild value cannot influence the judgement of
     *     the beats around it.
     *
     *  2. Karlsson's local median. An interval must sit within [TOLERANCE]
     *     of the median of its five-interval neighbourhood. This is the
     *     robust test — it has no memory, so one bad beat cannot cascade
     *     into its successors the way a rule anchored on the previous value
     *     can.
     *
     *  3. Malik's successive test. An interval must also sit within
     *     [TOLERANCE] of the last *accepted* one. This exists to cover the
     *     median's blind spot: in a strictly alternating rhythm — bigeminy,
     *     where every other beat is ectopic — three of any five neighbours
     *     share the value under test, so the median agrees with it and the
     *     local test accepts the entire series unchanged. Malik's rule
     *     rejects half of it, the rejection fraction then exceeds
     *     [MAX_REJECTED_FRACTION], and the recording is refused. That is
     *     the right outcome: an alternating rhythm is a question for a
     *     clinician, not something to average into a wellbeing baseline.
     *
     * Comparing against the last accepted interval rather than the last
     * observed one is deliberate, and is what stops a single ectopic beat
     * from dragging the reference along behind it.
     */
    fun clean(rrMillis: List<Double>, rule: ArtifactRule = ArtifactRule.COMBINED): CleanedBeats {
        val n = rrMillis.size
        val inRange = BooleanArray(n) { rrMillis[it] in Ppg.MIN_RR_MS..Ppg.MAX_RR_MS }
        val half = MEDIAN_WINDOW / 2

        val acceptedIndices = ArrayList<Int>()
        val acceptedMillis = ArrayList<Double>()
        var previousAccepted: Double? = null

        for (i in 0 until n) {
            if (!inRange[i]) continue
            val value = rrMillis[i]

            if (rule == ArtifactRule.COMBINED || rule == ArtifactRule.KARLSSON_MEDIAN) {
                val neighbourhood = ArrayList<Double>(MEDIAN_WINDOW)
                for (k in (i - half).coerceAtLeast(0)..(i + half).coerceAtMost(n - 1)) {
                    if (inRange[k]) neighbourhood.add(rrMillis[k])
                }
                // With nothing to compare against, the interval is
                // unverifiable rather than good; leave it out.
                if (neighbourhood.size < 2) continue
                val localMedian = Ppg.median(neighbourhood)
                if (localMedian <= 0.0) continue
                if (abs(value - localMedian) > TOLERANCE * localMedian) continue
            }

            if (rule == ArtifactRule.COMBINED || rule == ArtifactRule.MALIK) {
                val reference = previousAccepted
                if (reference != null && abs(value - reference) > TOLERANCE * reference) continue
            }

            acceptedIndices.add(i)
            acceptedMillis.add(value)
            previousAccepted = value
        }

        return CleanedBeats(
            acceptedIndices = acceptedIndices,
            acceptedMillis = acceptedMillis,
            rejectedCount = n - acceptedIndices.size,
            totalCount = n,
        )
    }

    /**
     * Task Force time-domain metrics over an already-clean series.
     *
     * This applies no minimum and no gate; it is the arithmetic on its own,
     * separated so it can be checked against hand-worked values. Callers
     * wanting a number to act on should use [analyse].
     *
     * The subtlety is in the successive differences. After rejection the
     * accepted intervals are no longer necessarily neighbours, and the
     * difference between two intervals that had a rejected beat between
     * them is not a successive difference at all — it spans the artifact
     * that was just removed, and carries its full magnitude. Taking it
     * anyway would undo the rejection entirely. So [acceptedIndices] is
     * consulted, and only pairs still adjacent in the original series
     * contribute to RMSSD and pNN50.
     *
     * Both SDNN and RMSSD divide by one less than their sample count. For
     * RMSSD that is the Task Force formula exactly, since N intervals give
     * N-1 differences; for SDNN it is the ordinary sample standard
     * deviation. pNN50 counts differences strictly *greater* than 50 ms,
     * again as the standard defines it, and is expressed as a percentage of
     * the successive pairs actually used.
     */
    fun timeDomain(acceptedIndices: List<Int>, acceptedMillis: List<Double>): HrvMetrics? {
        val n = acceptedMillis.size
        if (n < 2 || acceptedIndices.size != n) return null

        val meanRr = acceptedMillis.sum() / n
        var sumSquares = 0.0
        for (v in acceptedMillis) sumSquares += (v - meanRr) * (v - meanRr)
        val sdnn = sqrt(sumSquares / (n - 1))

        var differenceSquares = 0.0
        var pairs = 0
        var over50 = 0
        for (i in 0 until n - 1) {
            if (acceptedIndices[i + 1] != acceptedIndices[i] + 1) continue
            val difference = acceptedMillis[i + 1] - acceptedMillis[i]
            differenceSquares += difference * difference
            if (abs(difference) > 50.0) over50++
            pairs++
        }
        if (pairs == 0) return null

        return HrvMetrics(
            meanRrMillis = meanRr,
            meanHeartRate = 60_000.0 / meanRr,
            sdnnMillis = sdnn,
            rmssdMillis = sqrt(differenceSquares / pairs),
            pnn50Percent = 100.0 * over50 / pairs,
            intervalCount = n,
            successivePairCount = pairs,
        )
    }

    /** Overload for a series known to be contiguous, such as a test fixture. */
    fun timeDomain(rrMillis: List<Double>): HrvMetrics? =
        timeDomain(rrMillis.indices.toList(), rrMillis)

    /**
     * Cleans a series and returns metrics, or refuses.
     *
     * The refusal is the point of this function. A person checking in on a
     * difficult evening will read whatever number appears as a fact about
     * their nervous system, so the only honest options are a number that
     * has earned its place or a clear statement that this recording did
     * not work. There is no third option where a shaky measurement is
     * reported with a quiet asterisk.
     */
    fun analyse(rrMillis: List<Double>, rule: ArtifactRule = ArtifactRule.COMBINED): HrvResult {
        val cleaned = clean(rrMillis, rule)

        val refusal = when {
            cleaned.acceptedMillis.size < MIN_CLEAN_INTERVALS ->
                "fewer than $MIN_CLEAN_INTERVALS clean beats"
            cleaned.acceptedDurationMillis < MIN_CLEAN_DURATION_MILLIS ->
                "under 60 s of clean beats"
            cleaned.rejectedFraction > MAX_REJECTED_FRACTION ->
                "more than 20% of beats rejected"
            else -> null
        }

        return HrvResult(
            cleaned = cleaned,
            metrics = if (refusal == null) timeDomain(cleaned.acceptedIndices, cleaned.acceptedMillis) else null,
            refusal = refusal,
        )
    }

    /**
     * The whole path from camera frames to HRV.
     *
     * Both gates must pass. The signal gate in [Ppg] catches recordings
     * where the beats were found badly; the gate here catches series where
     * too much had to be thrown away. They fail on different things, and a
     * recording only counts if it survives both.
     */
    fun fromPpg(
        samples: List<Double>,
        sampleRate: Double,
        rule: ArtifactRule = ArtifactRule.COMBINED,
    ): HrvResult {
        val reading = Ppg.read(samples, sampleRate)
        if (!reading.quality.isTrustworthy) {
            return HrvResult(
                cleaned = CleanedBeats(emptyList(), emptyList(), 0, reading.rrMillis.size),
                metrics = null,
                refusal = reading.quality.refusal,
            )
        }
        return analyse(reading.rrMillis, rule)
    }
}
