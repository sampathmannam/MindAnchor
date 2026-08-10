package org.mindanchor.sim.personas

import org.mindanchor.vitals.DailyVitals
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Persona 3 of 5: a rotating shift worker.
 *
 * Behavioural shape (anchored research):
 *  - **Åkerstedt 2003**, *Occup. Med.* 53(2):89-94
 *    (DOI 10.1093/occmed/kqg046). Difficulty initiating sleep,
 *    shortened sleep, and somnolence during work hours are the
 *    principal acute symptoms of shift work.
 *  - **Kecklund & Axelsson 2016**, *BMJ* 355:i5210
 *    (DOI 10.1136/bmj.i5210). The sleep loss is concentrated
 *    around night and early-morning shifts; cardiometabolic
 *    stress and cognitive impairment are increased.
 *
 * Schedule: a 14-day rotation of day shifts (07:00-15:00),
 * evening shifts (15:00-23:00), and night shifts (23:00-07:00)
 * in a 4-4-6 pattern, with two rest days.
 *
 * What this exercises in the launcher:
 *  - The sunset window (22:00 → 07:00) is correct for day
 *    shifts, *wrong* for evening and night shifts (the wind-down
 *    comes at the *end* of the shift, not at 22:00). The
 *    editable window is the only correct response.
 *  - The regularity math should flag this persona as the
 *    *least* regular of the five (per Windred 2024, the strongest
 *    target).
 *  - The HRV/RHR numbers should run lower than the morning lark
 *    (Kecklund 2016) and the wellness math should report a
 *    chronic mild dip rather than a single-day event.
 */
class ShiftWorkerPersona : Persona {
    override val id = "shift_worker_rotating"
    override val name = "Rotating shift worker"
    override val description =
        "4-day day, 4-day evening, 6-day night rotation. " +
            "Anchored in Åkerstedt 2003 (acute shift-work symptoms) " +
            "and Kecklund & Axelsson 2016 (cardiometabolic and " +
            "cognitive cost)."

    override fun schedule(start: LocalDate, seed: Long): List<DailyVitals> {
        val rng = PersonaRng(seed, id)
        // 14-day rotation: 4 day, 4 evening, 4 night, 2 off.
        // This is a coarse 14-day window; the point is variation,
        // not a real rotation policy.
        val shiftPattern = listOf(
            "DAY", "DAY", "DAY", "DAY",
            "EVENING", "EVENING", "EVENING", "EVENING",
            "NIGHT", "NIGHT", "NIGHT", "NIGHT", "NIGHT", "NIGHT",
        )

        return (0 until 14).map { dayIndex ->
            val date = start.plusDays(dayIndex.toLong())
            val shift = shiftPattern[dayIndex]
            val params = when (shift) {
                "DAY" -> Quad(7 * 60, 380, 64.0, 42.0)        // sleep 23:00, wake 06:20
                "EVENING" -> Quad(2 * 60, 360, 65.0, 40.0)   // sleep 02:00, wake 08:00
                "NIGHT" -> Quad(9 * 60, 320, 67.0, 35.0)     // sleep 09:00, wake 14:20
                else -> Quad(8 * 60, 360, 63.0, 42.0)        // off day
            }
            val onsetBase = params.onsetBase
            val sleepBase = params.sleepBase
            val rhrBase = params.rhrBase
            val hrvBase = params.hrvBase
            val sleepOnset = (onsetBase + rng.nextGaussian(0.0, 30.0).roundToInt())
                .coerceIn(0, 1400)
            val sleepMinutes = (sleepBase + rng.nextGaussian(0.0, 30.0).roundToInt())
                .coerceIn(180, 540)
            val rhr = rng.nextGaussian(mean = rhrBase, sd = 2.5).coerceIn(50.0, 80.0)
            val meanHr = rhr + rng.nextGaussian(mean = 20.0, sd = 3.0).coerceAtLeast(8.0)
            val minHr = (rhr - rng.nextGaussian(mean = 9.0, sd = 1.5)).coerceAtLeast(40.0)
            val hrv = rng.nextGaussian(mean = hrvBase, sd = 5.0).coerceIn(20.0, 80.0)
            // Steps lower on night shifts, higher on day shifts.
            val stepsBase: Long = when (shift) {
                "DAY" -> 6_000L
                "EVENING" -> 5_000L
                "NIGHT" -> 3_000L
                else -> 4_000L
            }
            val steps = (stepsBase + rng.nextGaussian(0.0, 1_000.0).roundToInt())
                .coerceAtLeast(500L)
            val activeMinutes = (when (shift) {
                "DAY" -> 25
                "EVENING" -> 20
                "NIGHT" -> 10
                else -> 15
            } + rng.nextGaussian(0.0, 6.0).roundToInt()).coerceAtLeast(0)
            val mindfulness = rng.nextLong(0, 8).toInt()  // 0-7 min, sparse

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

    private data class Quad(val onsetBase: Int, val sleepBase: Int, val rhrBase: Double, val hrvBase: Double)
}
