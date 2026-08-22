package org.mindanchor.sim.personas

import org.mindanchor.vitals.DailyVitals
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Persona 1 of 5: a healthy morning lark.
 *
 * Behavioural shape (anchored research):
 *  - **Roenneberg et al. 2007**, *Sleep Med. Rev.* 11(6):429-438
 *    (DOI 10.1016/j.smrv.2007.07.005). Early chronotype: the
 *    population's natural early-bird end. Stable sleep timing,
 *    short sleep latency, low social jetlag.
 *  - **Windred et al. 2024**, *SLEEP* 47(1):zsad285
 *    (DOI 10.1093/sleep/zsad285). Highly regular sleep
 *    → low all-cause mortality risk, the "regularity is the
 *    target" baseline the launcher's regularity framing rests on.
 *
 * What this exercises in the launcher:
 *  - The wellness math should report a *flat* direction profile —
 *    a 14-day schedule of small, plausible variation should not
 *    surface "extreme low" or "extreme high" bands.
 *  - The sunset-mode "never mind" affordance should rarely fire
 *    (this persona does not reach for doomscroll apps often).
 *  - The bedtime list prompt should appear at the wind-down, and
 *    the user should write *specific* items.
 */
class MorningLarkPersona : Persona {
    override val id = "morning_lark_healthy"
    override val name = "Healthy morning lark"
    override val description =
        "Early chronotype, highly regular sleep, healthy adult. " +
            "Anchored in Roenneberg et al. 2007 (chronotype) and " +
            "Windred et al. 2024 (sleep regularity)."

    override fun schedule(start: LocalDate, seed: Long): List<DailyVitals> {
        val rng = PersonaRng(seed, id)
        return (0 until 14).map { dayIndex ->
            val date = start.plusDays(dayIndex.toLong())
            // Each field: small Gaussian noise around a healthy mean.
            val rhr = rng.nextGaussian(mean = 58.0, sd = 1.5).coerceIn(50.0, 72.0)
            val meanHr = rhr + rng.nextGaussian(mean = 18.0, sd = 2.0).coerceAtLeast(8.0)
            val minHr = (rhr - rng.nextGaussian(mean = 8.0, sd = 1.5)).coerceAtLeast(40.0)
            val hrv = rng.nextGaussian(mean = 55.0, sd = 4.0).coerceIn(30.0, 90.0)
            // Sleep onset: 22:30 ± 20 min, converted to minutes after 18:00.
            val sleepOnset = (270 + rng.nextGaussian(mean = 0.0, sd = 15.0).roundToInt())
                .coerceIn(0, 600)
            // Total sleep: 7h40m ± 25 min.
            val sleepMinutes = (460 + rng.nextGaussian(mean = 0.0, sd = 25.0).roundToInt())
                .coerceIn(360, 540)
            val steps = (8_000L + rng.nextGaussian(mean = 0.0, sd = 1_200.0).roundToInt())
                .coerceAtLeast(1_500L)
            val activeMinutes = (35 + rng.nextGaussian(mean = 0.0, sd = 8.0).roundToInt())
                .coerceAtLeast(10)
            val mindfulness = (10 + rng.nextGaussian(mean = 0.0, sd = 3.0).roundToInt())
                .coerceAtLeast(0)

            DailyVitals(
                date = date,
                restingHeartRate = rhr,
                meanHeartRate = meanHr,
                minHeartRate = minHr,
                hrvRmssd = hrv,
                sleepMinutes = sleepMinutes,
                sleepOnset = sleepOnset,
                steps = steps,
                activeMinutes = activeMinutes,
                mindfulnessMinutes = mindfulness,
            )
        }
    }
}
