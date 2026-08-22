package org.mindanchor.sim.personas

import org.mindanchor.vitals.DailyVitals
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Persona 5 of 5: low-motivation depression, no wearable-detected
 * exercise.
 *
 * Behavioural shape (anchored research):
 *  - **Dimidjian et al. 2006**, *J. Consult. Clin. Psychol.*
 *    74(4):658-670 (DOI 10.1037/0022-006X.74.4.658). Behavioural
 *    activation, in a head-to-head RCT with cognitive therapy
 *    and antidepressants, was comparable to antidepressants and
 *    superior to cognitive therapy in more severely depressed
 *    patients. The launcher's "small things" affordance is a
 *    BA lever — one small scheduled activity, not a script.
 *  - **Brosschot, Gerin, & Thayer 2006**, *J. Psychosom. Res.*
 *    60(2):113-124 (DOI 10.1016/j.jpsychores.2005.06.074).
 *    Perseverative cognition (worry, rumination) prolongs
 *    physiological stress responses; chronic low mood is
 *    associated with low HRV via this mechanism.
 *
 * Schedule: low activity (steps ~2,000/day), sleep variable
 * (6h30m-7h30m), HRV chronically low. The persona is *capable*
 * of the small things — the issue is the absence of activation
 * cues, not a physical limit.
 *
 * What this exercises in the launcher:
 *  - The friction gate's "small things" affordance should fire
 *    often. The persona's reach is a doomscroll reach, not a
 *    productive reach, and a BA-style "do this instead" prompt
 *    is the lever the literature supports.
 *  - The wellness math should report a *chronic* low HRV and
 *    low activity, not a single-day event. The per-person
 *    anomaly cut-offs (Jacobson 2019) are the right framing.
 *  - The open-loop prompt should appear at the wind-down, but
 *    the persona frequently writes the same item across days
 *    (the open loop does not close) — the launcher's design
 *    is to *not* nag, and this persona tests that.
 */
class DepressionLowMotivationPersona : Persona {
    override val id = "depression_low_motivation"
    override val name = "Low-motivation depression"
    override val description =
        "Low activity, variable sleep, low HRV. Anchored in " +
            "Dimidjian et al. 2006 (behavioral activation RCT) and " +
            "Brosschot, Gerin, & Thayer 2006 (perseverative " +
            "cognition)."

    override fun schedule(start: LocalDate, seed: Long): List<DailyVitals> {
        val rng = PersonaRng(seed, id)
        return (0 until 14).map { dayIndex ->
            val date = start.plusDays(dayIndex.toLong())
            // Sleep is the most variable of the five personas —
            // 6h30m to 7h30m, onset 23:00 to 02:00.
            // [DailyVitals.sleepOnset] is "minutes after 18:00":
            // 23:00 = 5h after 18:00 = 300, 02:00 = 8h after 18:00
            // = 480. The persona's previous version used raw
            // minute-of-day (23*60=1380) which was out of range and
            // put the median onset 18 hours in the future — see
            // [org.mindanchor.sim.WellnessSimulationRunner.summarize].
            // Mid-point: 5.5h after 18:00 = 330, ±90 min noise.
            val sleepOnset = (330 + rng.nextGaussian(0.0, 90.0).roundToInt())
                .coerceIn(0, 1400)
            val sleepMinutes = (420 + rng.nextGaussian(0.0, 30.0).roundToInt())
                .coerceIn(300, 540)
            val rhr = rng.nextGaussian(mean = 68.0, sd = 2.5).coerceIn(55.0, 80.0)
            val meanHr = rhr + rng.nextGaussian(mean = 20.0, sd = 3.0).coerceAtLeast(8.0)
            val minHr = (rhr - rng.nextGaussian(mean = 9.0, sd = 1.5)).coerceAtLeast(40.0)
            // HRV chronically low. This is the persona's signal.
            val hrv = rng.nextGaussian(mean = 32.0, sd = 4.0).coerceIn(15.0, 55.0)
            // Steps much lower than any other persona — this is
            // the BA lever's signal: a launcher that does not
            // notice low activity is failing this persona.
            val steps = (2_000L + rng.nextGaussian(0.0, 600.0).roundToInt())
                .coerceAtLeast(200L)
            val activeMinutes = (5 + rng.nextGaussian(0.0, 4.0).roundToInt())
                .coerceAtLeast(0)
            // No meditation practice. The persona is at the
            // stage where the friction gate *is* the practice.
            val mindfulness = rng.nextLong(0, 4).toInt()

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
