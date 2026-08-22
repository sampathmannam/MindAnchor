package org.mindanchor.sim.personas

import org.mindanchor.vitals.DailyVitals
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Persona: sparse wearable data — the watch only writes 2-3 days
 * per week. Most fields are null on most days.
 *
 * This is the realistic shape of a COROS Pacer 3 user (HR + exercise
 * only, no sleep or HRV), or someone who keeps their watch on the
 * charger 4 days a week. The launcher must not pretend the missing
 * days had data — null is null, and the baseline computation must
 * skip it.
 *
 * The runner is the test: after the 14-day warmup, the persona
 * should produce a baseline only on the days where the warmup
 * actually had a value. Direction bands should appear on reportable
 * days, NO_DATA on the rest.
 */
class SparseDataPersona : Persona {
    override val id = "sparse_data_partial_wearable"
    override val name = "Sparse wearable data persona"
    override val description =
        "Watch only writes 2-3 days/week (e.g., COROS Pacer 3 with " +
            "no sleep or HRV). Most fields are null on most days. " +
            "The launcher should compute the baseline only on the " +
            "non-null days and refuse to fill in missing values."

    override fun schedule(start: LocalDate, seed: Long): List<DailyVitals> {
        val rng = PersonaRng(seed, id)
        return (0 until 14).map { dayIndex ->
            val date = start.plusDays(dayIndex.toLong())
            // Has-watch days: Mon, Wed, Fri, Sun. No-watch days: Tue, Thu, Sat.
            val hasWatch = date.dayOfWeek.value in setOf(1, 3, 5, 7)
            if (!hasWatch) {
                // No data at all on the off-days. The launcher
                // must read these as null, not zero.
                DailyVitals(
                    date = date,
                    restingHeartRate = null,
                    meanHeartRate = null,
                    minHeartRate = null,
                    hrvRmssd = null,
                    sleepMinutes = null,
                    sleepOnset = null,
                    steps = null,
                    activeMinutes = null,
                    mindfulnessMinutes = null,
                )
            } else {
                // On-watch days, but only HR + steps; no sleep, no HRV
                // (a realistic shape for a watch that doesn't write
                // those to Health Connect).
                val rhr = rng.nextGaussian(mean = 62.0, sd = 3.0).coerceIn(50.0, 80.0)
                DailyVitals(
                    date = date,
                    restingHeartRate = rhr,
                    meanHeartRate = rhr + rng.nextGaussian(15.0, 3.0).coerceAtLeast(8.0),
                    minHeartRate = (rhr - rng.nextGaussian(8.0, 1.5)).coerceAtLeast(40.0),
                    hrvRmssd = null,
                    sleepMinutes = null,
                    sleepOnset = null,
                    steps = (6_000L + rng.nextGaussian(0.0, 1_500.0).roundToInt())
                        .coerceAtLeast(500L),
                    activeMinutes = (20 + rng.nextGaussian(0.0, 6.0).roundToInt())
                        .coerceAtLeast(0),
                    mindfulnessMinutes = null,
                )
            }
        }
    }
}
