package org.mindanchor.sim.personas

import org.mindanchor.vitals.DailyVitals
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Persona 2 of 5: a healthy night owl with social jetlag.
 *
 * Behavioural shape (anchored research):
 *  - **Roenneberg et al. 2007**, *Sleep Med. Rev.* 11(6):429-438
 *    (DOI 10.1016/j.smrv.2007.07.005). Late chronotype, natural
 *    preference for late sleep / late wake.
 *  - **Wittmann, Dinich, Merrow, & Roenneberg 2006**,
 *    *Chronobiology International* 23(1-2):497-509
 *    (DOI 10.1080/07420520500545999). Social jetlag: the
 *    difference between work-day and free-day sleep timing.
 *    Late chronotypes accumulate the most sleep debt on work
 *    days and partially compensate on free days.
 *
 * What this exercises in the launcher:
 *  - The 22:00 default sunset window is the *wrong* window
 *    for this persona on free days (onset ~01:30 is well past
 *    the wind-down). The launcher's editable window is what
 *    makes the launcher usable for this persona.
 *  - The regularity math should *flag* this persona as less
 *    regular than the morning lark (work days ≠ free days),
 *    which is the right finding per Windred et al. 2024.
 *  - The friction gate should fire more often on free days
 *    (the persona stays up later, more reaches, more
 *    temptation to doomscroll).
 */
class NightOwlPersona : Persona {
    override val id = "night_owl_healthy"
    override val name = "Healthy night owl with social jetlag"
    override val description =
        "Late chronotype, partial compensation on free days. " +
            "Anchored in Wittmann et al. 2006 (social jetlag) and " +
            "Roenneberg et al. 2007 (chronotype distribution)."

    override fun schedule(start: LocalDate, seed: Long): List<DailyVitals> {
        val rng = PersonaRng(seed, id)
        return (0 until 14).map { dayIndex ->
            val date = start.plusDays(dayIndex.toLong())
            // Mon..Fri = work days, Sat..Sun = free days.
            val dow = date.dayOfWeek
            val isWorkDay = dow.value in 1..5
            // Work days: sleep onset ~01:00 (i.e. very late, 7h after 18:00).
            // Free days: sleep onset ~01:30, but *more* sleep
            // (compensation per Wittmann 2006).
            val onsetWork = (7 * 60 + rng.nextGaussian(0.0, 30.0).roundToInt())
            val onsetFree = (7 * 60 + 30 + rng.nextGaussian(0.0, 30.0).roundToInt())
            val sleepOnset = (if (isWorkDay) onsetWork else onsetFree).coerceIn(0, 1000)
            // Free days: +60 min sleep (compensation).
            val baseSleep = if (isWorkDay) 360 else 420
            val sleepMinutes = (baseSleep + rng.nextGaussian(0.0, 30.0).roundToInt())
                .coerceIn(240, 600)

            val rhr = rng.nextGaussian(mean = 60.0, sd = 2.0).coerceIn(50.0, 75.0)
            val meanHr = rhr + rng.nextGaussian(mean = 20.0, sd = 3.0).coerceAtLeast(8.0)
            val minHr = (rhr - rng.nextGaussian(mean = 9.0, sd = 1.5)).coerceAtLeast(40.0)
            // HRV slightly lower than morning lark (short sleep, late chronotype).
            val hrv = rng.nextGaussian(mean = 48.0, sd = 5.0).coerceIn(25.0, 85.0)
            val steps = (7_000L + rng.nextGaussian(0.0, 1_400.0).roundToInt())
                .coerceAtLeast(1_000L)
            val activeMinutes = (25 + rng.nextGaussian(0.0, 8.0).roundToInt())
                .coerceAtLeast(5)
            val mindfulness = (5 + rng.nextGaussian(0.0, 3.0).roundToInt())
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
