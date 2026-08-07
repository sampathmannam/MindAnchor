package org.mindanchor.vitals

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * How much to trust one camera-PPG recording.
 *
 * Every field is reported rather than folded into a single number, because
 * when a measurement is refused the person deserves to be told which part
 * of it failed — "your finger moved" and "hold still for longer" are
 * different instructions.
 */
data class PpgQuality(
    val beatCount: Int,
    val durationSeconds: Double,
    /** Fraction of intervals lying between 42 and 210 bpm. */
    val plausibleIntervalFraction: Double,
    /** 1.0 when every pulse is the same height; falls as the finger shifts. */
    val amplitudeConsistency: Double,
    /** Fraction of neighbouring intervals within 20% of each other. */
    val rhythmConsistency: Double,
    /** Mean correlation of each beat against the average beat. */
    val beatShapeCorrelation: Double,
    val isTrustworthy: Boolean,
    /** Plain-language reason the recording was refused, or null if it passed. */
    val refusal: String?,
)

/**
 * The result of reading one recording: where the beats were, how far apart,
 * and whether any of it should be believed.
 */
data class PpgReading(
    /** Beat times in seconds from the start, at sub-frame resolution. */
    val peakSeconds: List<Double>,
    /** Beat-to-beat intervals in milliseconds. */
    val rrMillis: List<Double>,
    /** True when systole *darkened* the frame and the signal was flipped. */
    val invertedSignal: Boolean,
    val skewness: Double,
    val quality: PpgQuality,
)

/**
 * Turns a series of mean-red-channel frame values into beat-to-beat
 * intervals.
 *
 * The person holds a fingertip over the rear camera with the torch lit for
 * a minute or two. Each frame collapses to one number, and at roughly 30
 * frames per second that series carries the pulse as a small ripple — a
 * percent or two — riding on a large, drifting pedestal.
 *
 * Three things make this harder than it sounds, and each one is answered
 * deliberately below:
 *
 *  - The pedestal wanders far more than the pulse does, as the finger warms
 *    and the auto-exposure hunts. Anything that measures absolute level
 *    rather than shape will measure the wander instead.
 *  - Thirty frames a second quantises a beat time to 33 ms. Resting RMSSD
 *    is often only 20-50 ms, so whole-frame peak positions would bury the
 *    quantity being measured under the measurement grid itself.
 *  - Noise that leaves every interval looking perfectly physiological can
 *    still inflate RMSSD several-fold. Plausibility checks alone do not
 *    notice this, which is the failure this file is most careful about.
 *
 * Kept pure and free of Android for the same reason
 * [org.mindanchor.admin.SuspensionGuard] is: the number leaving here feeds
 * a personal baseline, and a plausible wrong number is worse than an honest
 * refusal.
 */
object Ppg {

    // --- Bands -----------------------------------------------------------

    /**
     * The heart-rate band. 0.7-3.5 Hz is 42-210 bpm, which brackets every
     * rate a person could plausibly hold a finger still at.
     */
    const val DETECT_LOW_HZ = 0.7
    const val DETECT_HIGH_HZ = 3.5

    /**
     * A wider ceiling used only to pin down *where* a beat is, once the
     * narrow band has said that one is there.
     *
     * The narrow band is deliberately blunt: it turns the pulse train into
     * something close to a sinusoid, which is exactly what makes it a
     * reliable way to find beats without the dicrotic notch masquerading as
     * a second one. But that same smoothing broadens the peak and blurs its
     * timing. Measured against a synthetic wave of known variability,
     * locating peaks on the narrow band alone under-reads RMSSD by about
     * 7%; locating them on this wider band brings the error under 1%.
     *
     * 8 Hz is Elgendi's original upper cutoff for PPG. It is kept well
     * below the 15 Hz Nyquist limit of a 30 fps camera, above which there
     * is nothing but sensor noise and rolling-shutter artefact.
     */
    const val LOCALISE_HIGH_HZ = 8.0

    // --- Physiological limits --------------------------------------------

    const val MIN_BPM = 42.0
    const val MAX_BPM = 210.0
    val MIN_RR_MS = 60_000.0 / MAX_BPM   // 285.7 ms
    val MAX_RR_MS = 60_000.0 / MIN_BPM   // 1428.6 ms

    // --- Peak detection --------------------------------------------------

    /**
     * The two window lengths of Elgendi's detector: roughly the width of a
     * systolic peak, and roughly the width of a whole beat.
     *
     * From Elgendi et al. (2013), "Systolic Peak Detection in Acceleration
     * Photoplethysmograms Measured from Emergency Responders in Tropical
     * Conditions", PLoS ONE 8(10). The method compares a short moving
     * average against a long one; where the short one rises above the long
     * one, a beat is happening. It is built entirely from moving averages,
     * needs no training, and adapts to amplitude on its own — which suits a
     * signal whose scale depends on how hard somebody is pressing.
     */
    const val PEAK_WINDOW_SECONDS = 0.111
    const val BEAT_WINDOW_SECONDS = 0.667

    /** Elgendi's offset on the block threshold, as a fraction of mean energy. */
    const val ELGENDI_BETA = 0.02

    /**
     * Below this absolute skewness the waveform is treated as symmetric and
     * its polarity as unknowable. That only happens at very high rates,
     * where consecutive pulses overlap into something near-sinusoidal — and
     * there the choice does not matter, because peaks and troughs are
     * equally spaced.
     */
    const val SYMMETRY_EPSILON = 0.05

    // --- Gate thresholds -------------------------------------------------

    /**
     * A minute is the shortest window from which RMSSD is generally
     * accepted as standing in for a full short-term recording (Task Force
     * of the ESC and NASPE, 1996; Shaffer & Ginsberg, 2017). Anything
     * briefer is refused rather than reported with a caveat nobody reads.
     */
    const val MIN_DURATION_SECONDS = 60.0

    const val MIN_BEATS = 40
    const val MIN_PLAUSIBLE_FRACTION = 0.80
    const val MIN_AMPLITUDE_CONSISTENCY = 0.40
    const val MIN_RHYTHM_CONSISTENCY = 0.60

    /**
     * The strictest gate, and the one that earns its keep.
     *
     * Timing jitter from a noisy recording does not produce implausible
     * intervals — it produces perfectly ordinary-looking ones that are each
     * a few milliseconds wrong. On synthetic signals, noise at that level
     * left mean heart rate accurate to a twentieth of a beat per minute
     * while inflating RMSSD by 22%, then 66%, then 112%, with every
     * plausibility check still passing. Beat-shape correlation is what
     * notices: it fell 0.996, 0.984, 0.971, 0.946, 0.913 across the same
     * range. The threshold sits where the RMSSD error crosses roughly 5%.
     */
    const val MIN_BEAT_SHAPE_CORRELATION = 0.98

    // --- Filtering -------------------------------------------------------

    /**
     * A centred moving average, with the ends held rather than tapered.
     *
     * The window is forced odd so that it is symmetric about the sample it
     * replaces. That symmetry is the whole point: a filter with any phase
     * lag shifts peaks, and a lag that varies with heart rate shifts them
     * by differing amounts, which would be indistinguishable from real
     * beat-to-beat variation. A symmetric average has exactly zero phase,
     * so it can smooth as hard as it likes without moving a beat.
     */
    fun movingAverage(samples: List<Double>, window: Int): List<Double> {
        val n = samples.size
        if (n == 0) return emptyList()
        var w = max(1, window)
        if (w % 2 == 0) w += 1
        if (w == 1) return samples.toList()
        val half = w / 2

        // Holding the end values steady is the least-wrong option: tapering
        // the window would make the ends quieter than the middle and invite
        // the detector to miss the first and last beats.
        fun at(i: Int) = samples[i.coerceIn(0, n - 1)]

        val out = DoubleArray(n)
        var sum = 0.0
        for (k in -half..half) sum += at(k)
        out[0] = sum / w
        for (i in 1 until n) {
            sum += at(i + half) - at(i - 1 - half)
            out[i] = sum / w
        }
        return out.toList()
    }

    /** Window length whose first spectral null sits at [cutHz]. */
    fun windowFor(sampleRate: Double, cutHz: Double): Int =
        max(1, (sampleRate / cutHz).roundToInt())

    /**
     * Band-pass by subtracting one moving average from another.
     *
     * The long average is an estimate of the baseline, so subtracting it
     * removes the drifting pedestal — this is the detrending step and the
     * high-pass in one. The short average removes what is above the band.
     * Both are symmetric, so neither moves a peak.
     *
     * Measured on 30 fps sine waves, the 0.7-3.5 Hz setting passes 0.93 to
     * 1.07 across 0.7-1.2 Hz, and attenuates baseline wander at 0.05 Hz to
     * 0.008. A boxcar has untidy sidelobes — about 0.17 up at 5-8 Hz —
     * which is why beat *detection* never relies on this band alone.
     */
    fun bandpass(
        samples: List<Double>,
        sampleRate: Double,
        lowCutHz: Double = DETECT_LOW_HZ,
        highCutHz: Double = DETECT_HIGH_HZ,
    ): List<Double> {
        val smoothed = movingAverage(samples, windowFor(sampleRate, highCutHz))
        val baseline = movingAverage(samples, windowFor(sampleRate, lowCutHz))
        return List(samples.size) { smoothed[it] - baseline[it] }
    }

    // --- Orientation -----------------------------------------------------

    /**
     * Third standardised moment.
     *
     * Used here to decide which way up the signal is. A pulse has a fast
     * upstroke and a slow decay, so the correct orientation is the skewed
     * one; Elgendi (2016), "Optimal Signal Quality Index for
     * Photoplethysmogram Signals", Bioengineering 3(4), found skewness the
     * single best quality index for PPG, and it doubles neatly as an
     * orientation test.
     *
     * This matters because whether systole brightens or darkens the frame
     * depends on the phone: reflective geometry against the sensor glass
     * can go either way. Guessing wrong puts every "peak" on the diastolic
     * foot — which still yields a believable heart rate, and so would never
     * be caught by a plausibility check.
     */
    fun skewness(values: List<Double>): Double {
        val n = values.size
        if (n < 3) return 0.0
        val mean = values.sum() / n
        var variance = 0.0
        for (v in values) variance += (v - mean) * (v - mean)
        variance /= n
        if (variance <= 0.0) return 0.0
        val sd = sqrt(variance)
        var acc = 0.0
        for (v in values) {
            val z = (v - mean) / sd
            acc += z * z * z
        }
        return acc / n
    }

    // --- Peaks -----------------------------------------------------------

    /**
     * Sub-sample peak position by fitting a parabola through the peak
     * sample and its two neighbours.
     *
     * Without this, a beat time can only land on a frame boundary, and the
     * resulting 33 ms staircase adds variability that was never in the
     * heart. On synthetic signals of known variability, whole-frame peaks
     * inflated RMSSD by 9-14%; this interpolation brought the error under
     * 1%.
     */
    fun refinePeak(values: List<Double>, index: Int): Double {
        if (index <= 0 || index >= values.size - 1) return index.toDouble()
        val a = values[index - 1]
        val b = values[index]
        val c = values[index + 1]
        val denominator = a - 2.0 * b + c
        if (denominator == 0.0) return index.toDouble()
        val delta = 0.5 * (a - c) / denominator
        // A shift of more than one sample means the three points were not a
        // peak at all; trust the raw index instead of extrapolating.
        return if (delta < -1.0 || delta > 1.0) index.toDouble() else index + delta
    }

    /**
     * Elgendi's two-moving-average detector, with a refractory period.
     *
     * [detectBand] decides *whether* a beat is happening; [localiseBand]
     * decides *when*. Splitting the two is what keeps the interval timing
     * honest, as [LOCALISE_HIGH_HZ] explains.
     *
     * The signal is squared with negatives clipped away, so that the
     * upstroke dominates and the diastolic dip cannot contribute. Blocks
     * where the short average exceeds the long one mark candidate beats,
     * and blocks briefer than a systolic peak are discarded as noise.
     *
     * The refractory period is the last guard: no two beats closer than
     * [MIN_RR_MS] apart, because at 210 bpm the heart is already at its
     * ceiling and anything faster is the dicrotic notch being counted
     * twice. When a candidate arrives too soon, the taller of the two wins
     * rather than the earlier — a late true beat should not be discarded
     * because a noise spike preceded it.
     */
    fun detectPeaks(
        detectBand: List<Double>,
        localiseBand: List<Double>,
        sampleRate: Double,
    ): List<Double> {
        val n = detectBand.size
        if (n < 3) return emptyList()

        val clipped = List(n) { val v = detectBand[it]; if (v > 0.0) v * v else 0.0 }
        val peakAverage = movingAverage(clipped, max(1, (sampleRate * PEAK_WINDOW_SECONDS).roundToInt()))
        val beatAverage = movingAverage(clipped, max(1, (sampleRate * BEAT_WINDOW_SECONDS).roundToInt()))
        val alpha = ELGENDI_BETA * (clipped.sum() / n)

        val minBlock = max(1, (sampleRate * PEAK_WINDOW_SECONDS).roundToInt())
        val refractorySamples = sampleRate * (MIN_RR_MS / 1000.0)

        val peaks = ArrayList<Double>()
        var i = 0
        while (i < n) {
            if (peakAverage[i] <= beatAverage[i] + alpha) {
                i++
                continue
            }
            var j = i
            while (j < n && peakAverage[j] > beatAverage[j] + alpha) j++
            if (j - i >= minBlock) {
                var best = i
                for (k in i until j) if (localiseBand[k] > localiseBand[best]) best = k
                val position = refinePeak(localiseBand, best)
                val previous = peaks.lastOrNull()
                if (previous == null || position - previous >= refractorySamples) {
                    peaks.add(position)
                } else {
                    val previousIndex = previous.roundToInt().coerceIn(0, n - 1)
                    if (localiseBand[best] > localiseBand[previousIndex]) {
                        peaks[peaks.size - 1] = position
                    }
                }
            }
            i = j
        }
        return peaks
    }

    /** Beat-to-beat intervals in milliseconds, from peak positions in samples. */
    fun rrMillis(peakSamples: List<Double>, sampleRate: Double): List<Double> =
        if (peakSamples.size < 2) emptyList()
        else List(peakSamples.size - 1) {
            (peakSamples[it + 1] - peakSamples[it]) * 1000.0 / sampleRate
        }

    // --- Quality ---------------------------------------------------------

    /** Fraction of intervals a human heart could actually have produced. */
    fun plausibleIntervalFraction(rrMillis: List<Double>): Double {
        if (rrMillis.isEmpty()) return 0.0
        val ok = rrMillis.count { it in MIN_RR_MS..MAX_RR_MS }
        return ok.toDouble() / rrMillis.size
    }

    /**
     * How steady the pulse height is, judged robustly.
     *
     * Median absolute deviation rather than standard deviation, so that two
     * or three beats lost to a shift of the finger do not by themselves
     * condemn an otherwise good minute. The 1.4826 factor puts MAD on the
     * same scale as a standard deviation for normally distributed values.
     */
    fun amplitudeConsistency(amplitudes: List<Double>): Double {
        if (amplitudes.size < 2) return 0.0
        val med = median(amplitudes)
        if (med <= 0.0) return 0.0
        val mad = median(amplitudes.map { abs(it - med) })
        return max(0.0, 1.0 - min(1.0, (1.4826 * mad) / med))
    }

    /** Fraction of neighbouring intervals that agree within 20%. */
    fun rhythmConsistency(rrMillis: List<Double>): Double {
        if (rrMillis.size < 2) return 0.0
        var ok = 0
        for (i in 0 until rrMillis.size - 1) {
            val previous = rrMillis[i]
            if (previous > 0.0 && abs(rrMillis[i + 1] - previous) / previous <= 0.20) ok++
        }
        return ok.toDouble() / (rrMillis.size - 1)
    }

    /**
     * Mean correlation of each beat against the average beat.
     *
     * From Orphanidou et al. (2015), "Signal-Quality Indices for the ECG
     * and PPG: Derivation and Applications to Wireless Monitoring", IEEE
     * Journal of Biomedical and Health Informatics 19(3). A real pulse
     * repeats its shape; noise does not. This is the only measure here that
     * sees the *waveform* rather than the intervals, and so the only one
     * that catches a recording whose intervals are all individually
     * believable and collectively wrong.
     *
     * The window is scaled to the median interval so that a fast pulse is
     * not compared across two beats at once.
     */
    fun beatShapeCorrelation(
        signal: List<Double>,
        peakSamples: List<Double>,
        sampleRate: Double,
        rrMillis: List<Double>,
    ): Double {
        if (peakSamples.size < 3 || rrMillis.isEmpty()) return 0.0
        val medianSamples = median(rrMillis) / 1000.0 * sampleRate
        val before = max(1, (0.35 * medianSamples).roundToInt())
        val after = max(1, (0.45 * medianSamples).roundToInt())

        val segments = ArrayList<List<Double>>()
        for (peak in peakSamples) {
            val centre = peak.roundToInt()
            // Beats clipped by the start or end of the recording are left
            // out rather than padded; a half-beat correlates poorly against
            // a whole one and would read as noise that is not there.
            if (centre - before >= 0 && centre + after < signal.size) {
                segments.add(signal.subList(centre - before, centre + after + 1))
            }
        }
        if (segments.size < 3) return 0.0

        val length = segments[0].size
        val template = DoubleArray(length)
        for (segment in segments) for (k in 0 until length) template[k] += segment[k]
        for (k in 0 until length) template[k] /= segments.size
        val templateList = template.toList()

        var total = 0.0
        for (segment in segments) total += max(0.0, correlation(segment, templateList))
        return total / segments.size
    }

    // --- Entry point -----------------------------------------------------

    /**
     * Reads a recording end to end: filter, orient, find beats, measure
     * intervals, and decide whether to stand behind any of it.
     *
     * [samples] is one mean-red-channel value per frame; [sampleRate] is
     * frames per second. Intervals are returned even when the recording is
     * refused, because the caller may want to show the person what was
     * seen — but [PpgQuality.isTrustworthy] is the only thing that should
     * ever decide whether a number reaches the baseline model.
     */
    fun read(samples: List<Double>, sampleRate: Double): PpgReading {
        val durationSeconds = if (sampleRate > 0.0) samples.size / sampleRate else 0.0

        val detectUpright = bandpass(samples, sampleRate, DETECT_LOW_HZ, DETECT_HIGH_HZ)
        val localiseUpright = bandpass(samples, sampleRate, DETECT_LOW_HZ, LOCALISE_HIGH_HZ)

        val skew = skewness(localiseUpright)
        val inverted = skew < -SYMMETRY_EPSILON
        val detectBand = if (inverted) detectUpright.map { -it } else detectUpright
        val localiseBand = if (inverted) localiseUpright.map { -it } else localiseUpright

        val peaks = detectPeaks(detectBand, localiseBand, sampleRate)
        val intervals = rrMillis(peaks, sampleRate)
        val amplitudes = peaks.map { localiseBand[it.roundToInt().coerceIn(0, max(0, localiseBand.size - 1))] }

        val plausible = plausibleIntervalFraction(intervals)
        val amplitude = amplitudeConsistency(amplitudes)
        val rhythm = rhythmConsistency(intervals)
        val shape = beatShapeCorrelation(localiseBand, peaks, sampleRate, intervals)

        // Ordered so the reason returned is the one the person can act on
        // first: length before stillness, stillness before subtlety.
        val refusal = when {
            durationSeconds < MIN_DURATION_SECONDS -> "recording shorter than 60 s"
            peaks.size < MIN_BEATS -> "too few beats found"
            plausible < MIN_PLAUSIBLE_FRACTION -> "intervals outside 42-210 bpm"
            amplitude < MIN_AMPLITUDE_CONSISTENCY -> "pulse amplitude unstable"
            rhythm < MIN_RHYTHM_CONSISTENCY -> "rhythm unstable"
            shape < MIN_BEAT_SHAPE_CORRELATION -> "beat shape inconsistent"
            else -> null
        }

        return PpgReading(
            peakSeconds = if (sampleRate > 0.0) peaks.map { it / sampleRate } else emptyList(),
            rrMillis = intervals,
            invertedSignal = inverted,
            skewness = skew,
            quality = PpgQuality(
                beatCount = peaks.size,
                durationSeconds = durationSeconds,
                plausibleIntervalFraction = plausible,
                amplitudeConsistency = amplitude,
                rhythmConsistency = rhythm,
                beatShapeCorrelation = shape,
                isTrustworthy = refusal == null,
                refusal = refusal,
            ),
        )
    }

    /** Convenience overload for callers holding a primitive frame buffer. */
    fun read(samples: DoubleArray, sampleRate: Double): PpgReading =
        read(samples.toList(), sampleRate)

    // --- Small shared helpers --------------------------------------------

    internal fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle]
        else 0.5 * (sorted[middle - 1] + sorted[middle])
    }

    internal fun correlation(a: List<Double>, b: List<Double>): Double {
        val n = min(a.size, b.size)
        if (n < 2) return 0.0
        var meanA = 0.0
        var meanB = 0.0
        for (i in 0 until n) {
            meanA += a[i]
            meanB += b[i]
        }
        meanA /= n
        meanB /= n
        var covariance = 0.0
        var varianceA = 0.0
        var varianceB = 0.0
        for (i in 0 until n) {
            val da = a[i] - meanA
            val db = b[i] - meanB
            covariance += da * db
            varianceA += da * da
            varianceB += db * db
        }
        if (varianceA <= 0.0 || varianceB <= 0.0) return 0.0
        return covariance / sqrt(varianceA * varianceB)
    }
}
