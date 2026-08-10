package org.mindanchor.sim.personas

import org.mindanchor.vitals.DailyVitals
import java.time.LocalDate

/**
 * Persona: a flat, zero-variance schedule — every day is identical.
 *
 * Designed to test the launcher's response to MAD = 0. Per
 * [PersonalBaseline.robustZ], a zero MAD should return null zScore
 * (no variance → no z-score). The direction should then be NO_DATA
 * (not AT) because the launcher's rule is "absence of variance is
 * information, not a z-score" — `WellnessSignals.kt` calls this
 * out explicitly.
 *
 * A real user cannot produce zero MAD in practice, but a faulty
 * sensor stuck on one value, or a half-deleted record that always
 * returns the same number, can. The launcher should refuse to
 * claim "this is your usual" on data that has no variation.
 */
class PerfectlyRegularPersona : Persona {
    override val id = "perfectly_regular_zero_variance"
    override val name = "Perfectly regular (zero-variance) persona"
    override val description =
        "Every signal has the same value on every day. The launcher " +
            "should refuse to surface a z-score when the per-person MAD " +
            "is zero — absence of variance is information, not a band."

    override fun schedule(start: LocalDate, seed: Long): List<DailyVitals> =
        (0 until 14).map { dayIndex ->
            DailyVitals(
                date = start.plusDays(dayIndex.toLong()),
                restingHeartRate = 60.0,
                meanHeartRate = 78.0,
                minHeartRate = 52.0,
                hrvRmssd = 50.0,
                sleepMinutes = 420,
                sleepOnset = 240,
                steps = 7_500L,
                activeMinutes = 25,
                mindfulnessMinutes = 10,
            )
        }
}
