package org.mindanchor.sim.personas

import org.mindanchor.vitals.DailyVitals
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Persona 4 of 5: a person with insomnia and elevated anxiety.
 *
 * Behavioural shape (anchored research):
 *  - **Harvey 2002**, *Behav. Res. Ther.* 40(8):869-893
 *    (DOI 10.1016/S0005-7967(01)00061-4). The cognitive model
 *    of insomnia: excessive negatively-toned cognitive activity
 *    (worry, rumination) → autonomic arousal + emotional
 *    distress → selective attention + safety behaviours →
 *    distorted perception of sleep deficit. Long sleep onset
 *    latency, short total sleep.
 *  - **Baglioni et al. 2016**, *Psychol. Bull.* 142(9):969-990
 *    (DOI 10.1037/bul0000053). The polysomnographic signature
 *    of anxiety-related insomnia: reduced REM latency, increased
 *    REM density, reduced slow-wave sleep, lower HRV.
 *
 * Schedule: short sleep (4h30m-5h30m), very late onset
 * (02:00-04:00), low HRV (the perseverative-cognition
 * hypothesis: the worry doesn't stop at bedtime).
 *
 * What this exercises in the launcher:
 *  - The bedtime list prompt should appear, but the persona
 *    frequently writes a *non-specific* item (the rumination
 *    interferes with the Scullin specificity heuristic).
 *  - The friction gate should fire more often than on any
 *    other persona (the rumination drives the doomscroll
 *    reach).
 *  - The regularity math should flag the sleep deficit and
 *    the HRV/RHR should run chronically below the per-person
 *    median (a low robust-z on most signals).
 */
class InsomniacPersona : Persona {
    override val id = "insomnia_anxious"
    override val name = "Insomnia with elevated anxiety"
    override val description =
        "Short sleep, very late onset, low HRV. " +
            "Anchored in Harvey 2002 (cognitive model) and " +
            "Baglioni et al. 2016 (polysomnographic signature)."

    override fun schedule(start: LocalDate, seed: Long): List<DailyVitals> {
        val rng = PersonaRng(seed, id)
        return (0 until 14).map { dayIndex ->
            val date = start.plusDays(dayIndex.toLong())
            // Onset drifts between 02:00 and 04:00. Some nights
            // (1 in 4) the rumination wins and onset is 04:30.
            val isBadNight = dayIndex % 4 == 1
            val onsetBase = if (isBadNight) (4 * 60 + 30) else (3 * 60)
            val sleepOnset = (onsetBase + rng.nextGaussian(0.0, 30.0).roundToInt())
                .coerceIn(0, 1400)
            // Short total sleep: 270-330 min.
            val sleepBase = if (isBadNight) 270 else 320
            val sleepMinutes = (sleepBase + rng.nextGaussian(0.0, 25.0).roundToInt())
                .coerceIn(180, 480)
            val rhr = rng.nextGaussian(mean = 70.0, sd = 3.0).coerceIn(55.0, 85.0)
            val meanHr = rhr + rng.nextGaussian(mean = 22.0, sd = 3.0).coerceAtLeast(8.0)
            val minHr = (rhr - rng.nextGaussian(mean = 10.0, sd = 2.0)).coerceAtLeast(40.0)
            val hrv = rng.nextGaussian(mean = 35.0, sd = 5.0).coerceIn(15.0, 60.0)
            val steps = (4_000L + rng.nextGaussian(0.0, 1_000.0).roundToInt())
                .coerceAtLeast(500L)
            val activeMinutes = (15 + rng.nextGaussian(0.0, 5.0).roundToInt())
                .coerceAtLeast(0)
            val mindfulness = ((rng.nextLong(0, 6) + rng.nextLong(0, 6)) / 2L).toInt()  // 0-5 min, sparse

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
