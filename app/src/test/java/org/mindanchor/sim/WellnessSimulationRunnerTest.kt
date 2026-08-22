package org.mindanchor.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.sim.personas.PersonaLibrary
import org.mindanchor.vitals.WellnessDirection
import org.mindanchor.vitals.WellnessSignal
import java.time.LocalDate
import java.time.LocalTime

/**
 * Smoke tests for the wellness simulation runner.
 *
 * These are the first tests that exercise the launcher's pure-Kotlin
 * wellness math end-to-end on synthetic data. They are deliberately
 * permissive — the goal is to confirm the runner wires [WellnessStats],
 * [OpenLoop.phase], and [BedtimeList.phase] correctly. Per-persona
 * behavioural assertions belong in [PersonaSimulationTest] once the
 * issue tracker (WP-5) is in place.
 */
class WellnessSimulationRunnerTest {

    private val start: LocalDate = LocalDate.of(2026, 1, 5)
    private val seed: Long = 42L

    @Test
    fun `runner produces one SimulationDay per scheduled day`() {
        PersonaLibrary.all.forEach { persona ->
            val days = WellnessSimulationRunner.run(persona, start, seed)
            assertEquals("$persona produced ${days.size} days, not 14", 14, days.size)
        }
    }

    @Test
    fun `every SimulationDay has all 5 wellness signals`() {
        PersonaLibrary.all.forEach { persona ->
            val days = WellnessSimulationRunner.run(persona, start, seed)
            days.forEach { day ->
                assertEquals(
                    "$persona @ ${day.date} missing signals",
                    WellnessSignal.ORDERED.size,
                    day.readings.size,
                )
                WellnessSignal.ORDERED.forEach { signal ->
                    assertTrue(
                        "$persona @ ${day.date} missing $signal",
                        day.readings.containsKey(signal),
                    )
                }
            }
        }
    }

    @Test
    fun `morning lark has all AT bands on most days (low variance, healthy persona)`() {
        // The morning lark persona is the "everything is fine" baseline.
        // Most days should land in the AT band on every signal, with
        // occasional ABOVE / BELOW from the Gaussian noise.
        val persona = PersonaLibrary.byId("morning_lark_healthy")!!
        val days = WellnessSimulationRunner.run(persona, start, seed)
        val totalReadings = days.size * WellnessSignal.ORDERED.size
        val atCount = days.flatMap { it.readings.values }
            .count { it.direction == WellnessDirection.AT }
        // Healthy baseline should be at AT on >50% of readings —
        // a stricter threshold would be over-fitting to the seed.
        assertTrue(
            "Morning lark should be at AT on more than half its readings, " +
                "was $atCount / $totalReadings",
            atCount.toDouble() / totalReadings > 0.5,
        )
    }

    @Test
    fun `insomniac persona has chronically low HRV baseline`() {
        // The insomniac persona has HRV mean ~35 ms (vs morning
        // lark mean ~55 ms). The launcher's per-person baseline
        // normalises within the persona, so a chronically low
        // persona reads as "around your usual" — which is the
        // honest N-of-1 result, not a clinical claim. The point
        // is the *median* is in the 30s, not that every day
        // shows BELOW.
        val persona = PersonaLibrary.byId("insomnia_anxious")!!
        val days = WellnessSimulationRunner.run(persona, start, seed)
        val hrvDays = days.mapNotNull { it.readings[WellnessSignal.HRV] }
        val medians = hrvDays.mapNotNull { it.baseline.median }
        val meanMedian = medians.average()
        assertTrue(
            "Insomniac HRV baseline median ($meanMedian ms) should be " +
                "below 40 ms (anchored in Baglioni 2016 PSG signature)",
            meanMedian < 40.0,
        )
    }

    @Test
    fun `shift worker has lower mean step count than morning lark`() {
        // The shift worker averages ~4,000 steps/day; the morning
        // lark averages ~8,000. The runner's per-person baseline
        // normalises within the persona, so we compare the
        // baseline medians rather than the direction bands.
        val lark = WellnessSimulationRunner.run(
            PersonaLibrary.byId("morning_lark_healthy")!!, start, seed,
        )
        val shift = WellnessSimulationRunner.run(
            PersonaLibrary.byId("shift_worker_rotating")!!, start, seed,
        )
        val larkStepsMedian = lark.first()
            .readings[WellnessSignal.STEPS]!!
            .baseline.median!!
        val shiftStepsMedian = shift.first()
            .readings[WellnessSignal.STEPS]!!
            .baseline.median!!
        assertTrue(
            "Shift worker steps median ($shiftStepsMedian) should be " +
                "less than morning lark ($larkStepsMedian)",
            shiftStepsMedian < larkStepsMedian,
        )
    }

    @Test
    fun `open loop fires CAPTURE during default quiet hours (22-07) at 23-00`() {
        // At 23:00 with the default 22:00→07:00 window, the open
        // loop should be in CAPTURE (the prompt to write something
        // down). The runner assumes no note was captured, which is
        // the most common case.
        PersonaLibrary.all.forEach { persona ->
            val days = WellnessSimulationRunner.run(persona, start, seed)
            days.forEach { day ->
                assertEquals(
                    "$persona @ ${day.date} should be CAPTURE at 23:00 " +
                        "in the default quiet window, was ${day.openLoopPhase}",
                    org.mindanchor.friction.LoopPhase.CAPTURE,
                    day.openLoopPhase,
                )
            }
        }
    }

    @Test
    fun `bedtime list fires CAPTURE during default quiet hours`() {
        PersonaLibrary.all.forEach { persona ->
            val days = WellnessSimulationRunner.run(persona, start, seed)
            days.forEach { day ->
                assertEquals(
                    "$persona @ ${day.date} should be CAPTURE at 23:00 " +
                        "in the default quiet window, was ${day.bedtimeListPhase}",
                    org.mindanchor.sleep.BedtimePhase.CAPTURE,
                    day.bedtimeListPhase,
                )
            }
        }
    }

    @Test
    fun `non-default sunset window still fires the open loop at 23-00 for early chronotype`() {
        // A late chronotype (night owl) with the launcher's default
        // 22:00 window is at 23:00 still inside the window, so the
        // open loop should fire CAPTURE — the runner does not judge
        // whether the *window* is right, only whether the *prompt*
        // would show.
        val nightOwl = PersonaLibrary.byId("night_owl_healthy")!!
        val days = WellnessSimulationRunner.run(nightOwl, start, seed)
        assertTrue(days.all { it.openLoopPhase == org.mindanchor.friction.LoopPhase.CAPTURE })
    }

    @Test
    fun `a non-default sunset window that does not cover 23-00 leaves the open loop silent`() {
        // If a night-shift worker has set their window to 09:00→17:00,
        // 23:00 is outside the window — the open loop should be NONE
        // (the launcher says nothing at 23:00 because the wind-down
        // is in the morning for this person).
        val nightShift = PersonaLibrary.byId("shift_worker_rotating")!!
        val days = WellnessSimulationRunner.run(
            nightShift, start, seed,
            sunsetStart = LocalTime.of(9, 0),
            sunsetEnd = LocalTime.of(17, 0),
        )
        assertTrue(
            "Open loop should be NONE at 23:00 with a 09-17 window",
            days.all { it.openLoopPhase == org.mindanchor.friction.LoopPhase.NONE },
        )
    }
}
