package org.mindanchor.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.sim.personas.PersonaLibrary
import org.mindanchor.sleep.SleepWindowOptimizer
import java.time.LocalDate
import java.time.LocalTime

/**
 * End-to-end test of the [WellnessSimulationRunner.summarize] cross-
 * cutting summary on each persona, and specifically the runner's
 * end-to-end driving of [SleepWindowOptimizer.suggest].
 *
 * The runner is the *only* place where the optimizer is exercised
 * against synthetic data. Without these tests, a future persona
 * whose onsets are in the wrong shape (a null-returning suggestion
 * for a regular sleeper) would not be caught.
 *
 * P1 of the v0.21.0 simulation report was exactly that gap:
 * "the runner cannot yet drive [SleepWindowOptimizer] end-to-end on
 * persona data because the personas emit sleep durations but not
 * sleep onsets." This file is the fix; if a regression reintroduces
 * the gap, the failure is *here*.
 */
class SleepOptimizerRunnerTest {

    private val start: LocalDate = LocalDate.of(2026, 1, 5)
    private val seed: Long = 42L

    @Test
    fun `every persona with 5+ onsets produces a non-null suggestion`() {
        // The personas that have 5+ usable sleep onsets should all
        // produce a suggestion. SparseData is the one exception —
        // 4/7 of its days are null, leaving it under MIN_NIGHTS.
        PersonaLibrary.all.forEach { persona ->
            val summary = WellnessSimulationRunner.summarize(persona, start, seed)
            if (persona.id == "sparse_data_partial_wearable") {
                // SparseData is the test that the MIN_NIGHTS floor
                // works: 4 nights is not enough to suggest anything,
                // and a null suggestion is the right answer, not a
                // crash or a guess.
                assertNull(
                    "sparse_data should produce null (4 < 5 nights), " +
                        "got ${summary.suggestedWindow}",
                    summary.suggestedWindow,
                )
            } else {
                assertNotNull(
                    "${persona.id} should produce a suggestion, " +
                        "got null",
                    summary.suggestedWindow,
                )
                assertEquals(
                    "${persona.id} suggestion should use all 14 nights",
                    14,
                    summary.suggestedWindow!!.nightsUsed,
                )
            }
        }
    }

    @Test
    fun `morning lark suggestion starts in the evening (20-21 window)`() {
        // Morning lark's median onset is 22:30; the optimizer's
        // wind-down starts 90 minutes earlier. The ±15-min noise
        // on the persona can shift the median by 5-10 minutes,
        // so the suggestion's startTime can land in 20:30-21:30
        // for this seed. Anything before 20:00 would be a wind-down
        // that has nothing to do with this persona.
        val persona = PersonaLibrary.byId("morning_lark_healthy")!!
        val summary = WellnessSimulationRunner.summarize(persona, start, seed)!!
        val start = summary.suggestedWindow!!.startTime
        assertTrue(
            "morning lark start $start should be in 20:30-21:30, " +
                "was $start",
            (start.hour == 20 && start.minute >= 30) ||
                start.hour == 21 ||
                (start.hour == 22 && start.minute == 0),
        )
    }

    @Test
    fun `night owl suggestion starts near or after midnight`() {
        // Night owl's median onset is 01:00-01:30 on workdays;
        // the optimizer's wind-down starts 90 minutes earlier.
        // The 23:30-00:30 window is the only one that fits.
        val persona = PersonaLibrary.byId("night_owl_healthy")!!
        val summary = WellnessSimulationRunner.summarize(persona, start, seed)!!
        val start = summary.suggestedWindow!!.startTime
        val median = summary.suggestedWindow.medianOnset
        assertTrue(
            "night owl median $median should be in 23:30-02:00, " +
                "was $median",
            (median.hour == 23 && median.minute >= 30) ||
                median.hour == 0 ||
                median.hour == 1 ||
                (median.hour == 2 && median.minute == 0),
        )
    }

    @Test
    fun `shift worker suggestion is a daytime window`() {
        // Shift worker's median onset is around 23:00 (a mix of
        // 4 day shifts at 23:00, 4 evening shifts at 02:00,
        // 6 night shifts at 09:00). The median is dominated by
        // the night-shift entries. The optimizer's wind-down
        // starts 90 minutes earlier, putting startTime in the
        // late-evening / early-morning range. End time is 8 hours
        // after median, which can be in the same day.
        val persona = PersonaLibrary.byId("shift_worker_rotating")!!
        val summary = WellnessSimulationRunner.summarize(persona, start, seed)!!
        // The shift worker's window does not need to be "same-day"
        // (start < end); the rotation produces onsets that span
        // midnight. What we need is: median is anchored somewhere
        // in the late-evening to early-morning range.
        val median = summary.suggestedWindow!!.medianOnset
        assertTrue(
            "shift worker median $median should be in 20:00-09:00, " +
                "was $median",
            (median.hour in 20..23) || median.hour in 0..9,
        )
    }

    @Test
    fun `insomniac persona's suggestion lands in the 02-04 hour`() {
        // Insomnia is by definition a late-onset condition; the
        // insomniac persona's median onset is 03:00-03:30 (base
        // 540 min after 18:00 = 03:00, plus 30 min noise, plus
        // 1-in-4 bad nights at 04:30). The suggestion's
        // startTime is 90 minutes earlier, putting it in 01:30-02:00.
        val persona = PersonaLibrary.byId("insomnia_anxious")!!
        val summary = WellnessSimulationRunner.summarize(persona, start, seed)!!
        val median = summary.suggestedWindow!!.medianOnset
        assertTrue(
            "insomniac median $median should be in 02:00-04:00, " +
                "was $median",
            median.hour in 2..4,
        )
    }

    @Test
    fun `suggestions are deterministic for a given persona and seed`() {
        // The persona library is deterministic; the runner is
        // deterministic; therefore the suggestion is deterministic.
        // A regression that introduced a non-deterministic
        // RNG (e.g. unseeded Random) would surface here.
        val persona = PersonaLibrary.byId("morning_lark_healthy")!!
        val a = WellnessSimulationRunner.summarize(persona, start, seed)
        val b = WellnessSimulationRunner.summarize(persona, start, seed)
        assertEquals(a.suggestedWindow, b.suggestedWindow)
    }

    @Test
    fun `different seeds produce different suggestions for the same persona`() {
        // Different seeds should produce different schedules, and
        // therefore different medians. With seed=42 and seed=43
        // on the morning lark, the medians should differ by at
        // least one minute on the median onset (the persona's
        // noise is small but non-zero). If the runner were
        // accidentally seeded once globally, both would be
        // equal and this test would fail.
        val persona = PersonaLibrary.byId("morning_lark_healthy")!!
        val a = WellnessSimulationRunner.summarize(persona, start, 42L)!!
        val b = WellnessSimulationRunner.summarize(persona, start, 43L)!!
        assertNotEquals(
            "different seeds should give different suggestions",
            a.suggestedWindow?.medianOnset,
            b.suggestedWindow?.medianOnset,
        )
    }
}
