package org.mindanchor.vitals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * Tests for the camera-PPG front end.
 *
 * The signals here are synthesised rather than recorded, so that the true
 * answer is known exactly and every assertion is against a number the test
 * itself decided. Nothing is random: the only "noise" comes from a fixed
 * linear congruential generator, so a failure means the algorithm changed
 * and never that the dice fell differently.
 */
class PpgTest {

    private val fs = 30.0

    // --- Synthetic signals -----------------------------------------------

    /**
     * A deterministic stand-in for sensor noise, in [-1, 1).
     *
     * Knuth's MMIX constants. Deliberately not Math.random: a quality gate
     * that passes four times in five is not a passing test.
     */
    private class Lcg(private var state: Long) {
        fun next(): Double {
            state = state * 6364136223846793005L + 1442695040888963407L
            return ((state ushr 40).toDouble() / 8388608.0) - 1.0
        }
    }

    private fun gaussian(t: Double, centre: Double, width: Double): Double =
        exp(-((t - centre) * (t - centre)) / (2.0 * width * width))

    /**
     * One pulse: a systolic peak about 210 ms after the foot, then a
     * smaller dicrotic wave.
     *
     * Its duration is fixed rather than stretched to fill the beat. That is
     * what real pulse morphology does — the upstroke takes about as long
     * whatever the rate, and the diastolic runoff absorbs the difference —
     * and it is what makes peak-to-peak spacing equal the true interval. A
     * kernel scaled to each interval would move the peak by a fraction of
     * the interval and quietly smooth the very variability under test.
     */
    private fun beatKernel(secondsSinceFoot: Double): Double {
        if (secondsSinceFoot < 0.0 || secondsSinceFoot > 1.0) return 0.0
        return gaussian(secondsSinceFoot, 0.21, 0.075) +
            0.35 * gaussian(secondsSinceFoot, 0.45, 0.11)
    }

    /**
     * Builds a fingertip recording: overlapping pulses on a bright, drifting
     * pedestal, exactly as the camera sees it.
     *
     * [rsaMillis] modulates the interval sinusoidally to imitate respiratory
     * sinus arrhythmia, which is what gives the recording a known,
     * non-trivial variability. Returns the frame series and the true
     * intervals that produced it.
     */
    private fun synthesise(
        bpm: Double,
        seconds: Double,
        rsaMillis: Double = 0.0,
        rsaHz: Double = 0.25,
        wander: Double = 0.35,
        noise: Double = 0.02,
        seed: Long = 12345L,
        inverted: Boolean = false,
    ): Pair<List<Double>, List<Double>> {
        val baseRr = 60.0 / bpm
        val footTimes = ArrayList<Double>()
        val trueRr = ArrayList<Double>()
        footTimes.add(0.0)
        while (footTimes.last() < seconds + 1.5) {
            val rr = baseRr + (rsaMillis / 1000.0) * sin(2.0 * PI * rsaHz * footTimes.last())
            trueRr.add(rr * 1000.0)
            footTimes.add(footTimes.last() + rr)
        }

        val random = Lcg(seed)
        val frames = ArrayList<Double>()
        val total = (seconds * fs).toInt()
        var first = 0
        for (i in 0 until total) {
            val t = i / fs
            while (first < footTimes.size && footTimes[first] < t - 1.0) first++
            var value = 0.0
            var k = first
            while (k < footTimes.size && footTimes[k] <= t) {
                value += beatKernel(t - footTimes[k])
                k++
            }
            if (inverted) value = -value
            // Breathing and a slow thermal drift, both far larger than the
            // pulse itself — the thing the detrending has to survive.
            value += wander * (sin(2.0 * PI * 0.13 * t) + 0.6 * sin(2.0 * PI * 0.05 * t))
            value += 20.0 + 0.004 * t
            value += noise * random.next()
            frames.add(value)
        }
        return Pair(frames, trueRr)
    }

    private fun measuredBpm(reading: PpgReading): Double =
        60_000.0 / (reading.rrMillis.sum() / reading.rrMillis.size)

    // --- Rate accuracy ---------------------------------------------------

    @Test
    fun `a clean pulse wave is measured at the rate it was built with`() {
        for (bpm in listOf(45.0, 55.0, 62.0, 75.0, 96.0, 132.0)) {
            val (frames, _) = synthesise(bpm, seconds = 90.0)
            val reading = Ppg.read(frames, fs)
            assertTrue("$bpm bpm should be trustworthy", reading.quality.isTrustworthy)
            assertEquals("rate at $bpm bpm", bpm, measuredBpm(reading), 0.5)
        }
    }

    @Test
    fun `a signal inverted by the camera is recognised and still measured`() {
        // On some phones systole darkens the frame rather than brightening
        // it. Getting this backwards puts every peak on the diastolic foot,
        // which yields a believable rate and a corrupted interval series —
        // a wrong answer no plausibility check would ever question.
        val (frames, _) = synthesise(68.0, seconds = 90.0, inverted = true)
        val reading = Ppg.read(frames, fs)
        assertTrue("inversion should be detected", reading.invertedSignal)
        assertTrue(reading.quality.isTrustworthy)
        assertEquals(68.0, measuredBpm(reading), 0.5)
    }

    @Test
    fun `an upright signal is not flipped`() {
        val (frames, _) = synthesise(70.0, seconds = 90.0)
        val reading = Ppg.read(frames, fs)
        assertFalse(reading.invertedSignal)
        assertTrue(reading.skewness > 0.0)
    }

    @Test
    fun `intervals of a wave with known variability are recovered accurately`() {
        val (frames, trueRr) = synthesise(62.0, seconds = 100.0, rsaMillis = 45.0)
        val reading = Ppg.read(frames, fs)
        assertTrue(reading.quality.isTrustworthy)

        val truth = Hrv.timeDomain(trueRr)!!
        val measured = Hrv.analyse(reading.rrMillis).metrics!!

        // The point of the sub-sample interpolation and the split
        // detect/localise bands is that this stays within a few percent.
        assertEquals("RMSSD", truth.rmssdMillis, measured.rmssdMillis, 0.10 * truth.rmssdMillis)
        assertEquals("SDNN", truth.sdnnMillis, measured.sdnnMillis, 0.10 * truth.sdnnMillis)
        assertEquals("mean HR", truth.meanHeartRate, measured.meanHeartRate, 0.5)
    }

    // --- The quality gate ------------------------------------------------

    @Test
    fun `pure noise is refused rather than reported`() {
        val random = Lcg(999L)
        val frames = List(90 * fs.toInt()) { 128.0 + 6.0 * random.next() }
        val reading = Ppg.read(frames, fs)

        assertFalse("noise must never be trustworthy", reading.quality.isTrustworthy)

        // The trap this documents: noise passed through a heart-rate
        // bandpass with a refractory period produces intervals that are
        // almost all inside the physiological range, and an apparent pulse
        // in the seventies. Plausibility alone would wave this through.
        assertTrue(
            "plausibility alone cannot catch noise",
            reading.quality.plausibleIntervalFraction > 0.9,
        )
        val apparent = measuredBpm(reading)
        assertTrue("noise looks like a normal pulse rate", apparent > 50.0 && apparent < 110.0)

        // What actually catches it is the shape of the beats and the
        // steadiness of the rhythm.
        assertTrue(reading.quality.beatShapeCorrelation < 0.8)
        assertTrue(reading.quality.rhythmConsistency < 0.5)
    }

    @Test
    fun `a flat signal from no finger on the lens is refused`() {
        val frames = List(90 * fs.toInt()) { 200.0 + 0.0005 * it }
        val reading = Ppg.read(frames, fs)
        assertFalse(reading.quality.isTrustworthy)
        assertEquals("too few beats found", reading.quality.refusal)
    }

    @Test
    fun `a recording shorter than a minute is refused however clean it is`() {
        val (frames, _) = synthesise(62.0, seconds = 30.0)
        val reading = Ppg.read(frames, fs)
        assertFalse(reading.quality.isTrustworthy)
        assertEquals("recording shorter than 60 s", reading.quality.refusal)
    }

    @Test
    fun `a noisy recording is refused before its variability goes wrong`() {
        // This is the failure the beat-shape index exists for. At this noise
        // level the mean rate is still accurate to a fraction of a beat per
        // minute and every interval is plausible, yet RMSSD is inflated by
        // more than half. The gate has to refuse on shape alone.
        val (frames, _) = synthesise(62.0, seconds = 100.0, rsaMillis = 45.0, noise = 0.45, seed = 4242L)
        val reading = Ppg.read(frames, fs)

        assertEquals(1.0, reading.quality.plausibleIntervalFraction, 1e-9)
        assertEquals(62.0, measuredBpm(reading), 0.5)
        assertFalse("plausible intervals are not enough", reading.quality.isTrustworthy)
        assertEquals("beat shape inconsistent", reading.quality.refusal)
    }

    @Test
    fun `a mildly noisy recording still passes`() {
        // The gate has to refuse bad recordings without refusing ordinary
        // ones; a fingertip is never perfectly still.
        val (frames, _) = synthesise(62.0, seconds = 100.0, rsaMillis = 45.0, noise = 0.10, seed = 4242L)
        val reading = Ppg.read(frames, fs)
        assertTrue(reading.quality.isTrustworthy)
        assertEquals(62.0, measuredBpm(reading), 0.5)
    }

    // --- Filter behaviour ------------------------------------------------

    @Test
    fun `the moving average has no phase lag`() {
        // Any lag would displace peaks, and a lag that varied with rate
        // would be indistinguishable from real beat-to-beat variation.
        val impulse = List(41) { if (it == 20) 1.0 else 0.0 }
        val smoothed = Ppg.movingAverage(impulse, 5)
        for (d in 1..10) {
            assertEquals(
                "response must be symmetric about the impulse",
                smoothed[20 - d],
                smoothed[20 + d],
                1e-12,
            )
        }
    }

    @Test
    fun `an even window is widened to stay symmetric`() {
        val impulse = List(41) { if (it == 20) 1.0 else 0.0 }
        assertEquals(Ppg.movingAverage(impulse, 5), Ppg.movingAverage(impulse, 4))
    }

    @Test
    fun `the bandpass keeps the pulse and discards baseline wander`() {
        fun gainAt(hz: Double): Double {
            val wave = List(3000) { sin(2.0 * PI * hz * it / fs) }
            val filtered = Ppg.bandpass(wave, fs).subList(300, 2700)
            return (filtered.max() - filtered.min()) / 2.0
        }
        // Breathing and thermal drift, which dwarf the pulse in the raw frames.
        assertTrue("0.05 Hz wander must be crushed", gainAt(0.05) < 0.05)
        assertTrue("0.1 Hz wander must be crushed", gainAt(0.10) < 0.10)
        // The heart-rate band itself, roughly 42-210 bpm.
        assertTrue("60 bpm must pass", gainAt(1.0) > 0.8)
        assertTrue("42 bpm must pass", gainAt(0.7) > 0.7)
        assertTrue("72 bpm must pass", gainAt(1.2) > 0.8)
        // Above the band.
        assertTrue("3.5 Hz must be attenuated", gainAt(3.5) < 0.2)
    }

    @Test
    fun `parabolic refinement finds the peak between two frames`() {
        // Symmetric samples: the peak is exactly on the middle one.
        assertEquals(1.0, Ppg.refinePeak(listOf(1.0, 2.0, 1.0), 1), 1e-12)
        // Leaning right: the true peak is past the middle sample.
        assertEquals(1.0 + 1.0 / 6.0, Ppg.refinePeak(listOf(1.0, 2.0, 1.5), 1), 1e-12)
        // Leaning left, by symmetry.
        assertEquals(1.0 - 1.0 / 6.0, Ppg.refinePeak(listOf(1.5, 2.0, 1.0), 1), 1e-12)
        // At the very edge there is no triple to fit; fall back to the index.
        assertEquals(0.0, Ppg.refinePeak(listOf(1.0, 2.0, 1.5), 0), 1e-12)
    }

    @Test
    fun `the refractory period prevents a dicrotic notch being counted twice`() {
        val (frames, _) = synthesise(60.0, seconds = 90.0)
        val reading = Ppg.read(frames, fs)
        // 90 s at 60 bpm is about 90 beats. Counting the dicrotic wave too
        // would roughly double that.
        assertTrue("beats=${reading.quality.beatCount}", reading.quality.beatCount in 85..95)
        assertTrue(reading.rrMillis.all { it >= Ppg.MIN_RR_MS })
    }

    @Test
    fun `an empty or tiny recording is refused without throwing`() {
        val empty = Ppg.read(emptyList(), fs)
        assertFalse(empty.quality.isTrustworthy)
        assertTrue(empty.rrMillis.isEmpty())

        val tiny = Ppg.read(listOf(1.0, 2.0, 1.0), fs)
        assertFalse(tiny.quality.isTrustworthy)
    }

    @Test
    fun `the whole path from frames to hrv refuses when the signal is bad`() {
        val random = Lcg(7L)
        val frames = List(90 * fs.toInt()) { 128.0 + 6.0 * random.next() }
        val result = Hrv.fromPpg(frames, fs)
        assertTrue("no metrics from noise", result.metrics == null)
        assertTrue(result.refusal != null)
    }

    @Test
    fun `the whole path from frames to hrv succeeds on a good recording`() {
        val (frames, trueRr) = synthesise(62.0, seconds = 100.0, rsaMillis = 45.0)
        val result = Hrv.fromPpg(frames, fs)
        val metrics = result.metrics!!
        val truth = Hrv.timeDomain(trueRr)!!
        assertEquals(0, result.cleaned.rejectedCount)
        assertEquals(truth.rmssdMillis, metrics.rmssdMillis, 0.10 * truth.rmssdMillis)
        assertTrue(abs(metrics.meanHeartRate - 62.0) < 0.5)
    }
}
