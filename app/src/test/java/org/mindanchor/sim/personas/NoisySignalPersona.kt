package org.mindanchor.sim.personas

import org.mindanchor.vitals.DailyVitals
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Persona: extreme noise — large day-to-day variance.
 *
 * Behavioural shape (anchored in the same research as [MorningLarkPersona]
 * — Roenneberg 2007, Windred 2024 — but with sd 4x normal so the
 * per-person MAD is large and most days are inside +/- 1 robust z).
 * Specifically designed to exercise [WellnessDirection.bandFor]'s
 * +/- 1 / +/- 2 cut-offs: a persona whose noise is so big that the
 * direction band reads AT for the entire 14-day window.
 */
class NoisySignalPersona : Persona {
    override val id = "noisy_signal_high_variance"
    override val name = "High-variance noise persona"
    override val description =
        "Healthy baseline with 4x normal noise; the launcher should " +
            "report AT on most days because the personal MAD absorbs " +
            "the variance. Anchored in Windred 2024 (per-person " +
            "regularity normalisation)."

    override fun schedule(start: LocalDate, seed: Long): List<DailyVitals> {
        val rng = PersonaRng(seed, id)
        return (0 until 14).map { dayIndex ->
            val date = start.plusDays(dayIndex.toLong())
            val rhr = rng.nextGaussian(mean = 60.0, sd = 8.0).coerceIn(45.0, 85.0)
            val meanHr = rhr + rng.nextGaussian(mean = 20.0, sd = 5.0).coerceAtLeast(8.0)
            val minHr = (rhr - rng.nextGaussian(mean = 9.0, sd = 3.0)).coerceAtLeast(40.0)
            val hrv = rng.nextGaussian(mean = 50.0, sd = 12.0).coerceIn(15.0, 95.0)
            val sleepMinutes = (440 + rng.nextGaussian(0.0, 80.0).roundToInt())
                .coerceIn(180, 720)
            val sleepOnset = (270 + rng.nextGaussian(0.0, 90.0).roundToInt())
                .coerceIn(0, 1000)
            val steps = (8_000L + rng.nextGaussian(0.0, 3_000.0).roundToInt())
                .coerceAtLeast(500L)
            DailyVitals(
                date = date,
                restingHeartRate = rhr,
                meanHeartRate = meanHr,
                minHeartRate = minHr,
                hrvRmssd = hrv,
                sleepMinutes = sleepMinutes,
                sleepOnset = sleepOnset,
                steps = steps,
                activeMinutes = (30 + rng.nextGaussian(0.0, 20.0).roundToInt())
                    .coerceAtLeast(0),
                mindfulnessMinutes = (8 + rng.nextGaussian(0.0, 6.0).roundToInt())
                    .coerceAtLeast(0),
            )
        }
    }
}
