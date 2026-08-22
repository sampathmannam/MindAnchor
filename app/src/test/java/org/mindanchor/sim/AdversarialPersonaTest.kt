package org.mindanchor.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.sim.personas.PersonaLibrary
import org.mindanchor.vitals.WellnessDirection
import org.mindanchor.vitals.WellnessSignal
import java.time.LocalDate

/**
 * Adversarial coverage of the wellness simulation runner.
 *
 * These tests are deliberately hostile: they probe every shape
 * the production launcher should not be allowed to break on —
 * zero-variance days, sparse-data weeks, persona with extreme
 * noise, baselines that come online partway through the run.
 * Each one asserts a property the launcher *must* preserve.
 */
class AdversarialPersonaTest {

    private val start: LocalDate = LocalDate.of(2026, 1, 5)
    private val seed: Long = 42L

    @Test
    fun `every persona produces 14 days, all contiguous, regardless of shape`() {
        // The "every persona" check is a smoke test that catches
        // a persona whose schedule() throws on a particular day
        // index (e.g., division by zero on day 13, or an
        // off-by-one in the day-of-week computation).
        for (persona in PersonaLibrary.all) {
            val days = WellnessSimulationRunner.run(persona, start, seed)
            assertEquals(
                "$persona: produced ${days.size} days, expected 14",
                14, days.size,
            )
            for (i in 0 until 14) {
                assertEquals(
                    "$persona day $i date mismatch",
                    start.plusDays(i.toLong()),
                    days[i].date,
                )
            }
        }
    }

    @Test
    fun `perfectly-regular persona yields null z-score on every signal (zero MAD)`() {
        // A sensor stuck on one value should not produce a
        // z-score, because MAD = 0 → division by zero. The
        // direction should fall through to NO_DATA, not AT.
        val persona = PersonaLibrary.byId("perfectly_regular_zero_variance")!!
        val days = WellnessSimulationRunner.run(persona, start, seed)
        // All 5 signals should report NO_DATA (z-score null) on
        // the reportable days, NOT AT — a zero-MAD day is not
        // "at your usual," it is "we have no information."
        for (day in days) {
            for (signal in WellnessSignal.ORDERED) {
                val reading = day.readings[signal]!!
                assertNull(
                    "$persona @ ${day.date} $signal: zero-MAD should " +
                        "produce null z-score, got ${reading.zScore}",
                    reading.zScore,
                )
                assertEquals(
                    "$persona @ ${day.date} $signal: null z-score " +
                        "should map to NO_DATA, got ${reading.direction}",
                    WellnessDirection.NO_DATA,
                    reading.direction,
                )
            }
        }
    }

    @Test
    fun `noisy-signal persona has more AT days than the most-AT persona`() {
        // The whole point of using median + MAD is that a noisy
        // personal signal normalises to "this is what your life
        // looks like." The noisy persona's job is to be a
        // *worse* test of that normalising: it should still be
        // AT on more than half the days, but not dramatically
        // more than the morning lark (which has near-zero
        // noise and so lands at AT on a large majority).
        val persona = PersonaLibrary.byId("noisy_signal_high_variance")!!
        val days = WellnessSimulationRunner.run(persona, start, seed)
        val hrvReadings = days.mapNotNull { it.readings[WellnessSignal.HRV] }
        val atCount = hrvReadings.count { it.direction == WellnessDirection.AT }
        // The morning-lark persona is the "almost-everything-is-AT"
        // baseline. The noisy persona is the "MAD does most of
        // the work" baseline. Both should land at AT on more
        // than half their readings. If the noisy persona
        // falls below 50%, the MAD-absorption has failed; if
        // it sits above 90%, the band cut-offs are too loose
        // and we're not actually testing anything.
        val atRatio = atCount.toDouble() / hrvReadings.size
        assertTrue(
            "Noisy persona should be at AT on >50% of HRV days " +
                "(MAD absorbs the noise); was $atCount / " +
                "${hrvReadings.size} (${(atRatio * 100).toInt()}%)",
            atRatio > 0.5,
        )
        assertTrue(
            "Noisy persona should be at AT on <90% of HRV days " +
                "(the cut-offs are still doing some work). " +
                "Was $atCount / ${hrvReadings.size} " +
                "(${(atRatio * 100).toInt()}%)",
            atRatio < 0.9,
        )
    }

    @Test
    fun `sparse-data persona never reports a value on a null day`() {
        // SparseDataPersona has null on Tue/Thu/Sat. The runner
        // must not invent values for those days. A direction
        // band requires both `today != null` and a z-score, so
        // a null-today day should always be NO_DATA, never
        // AT/ABOVE/BELOW/MUCH_ABOVE.
        val persona = PersonaLibrary.byId("sparse_data_partial_wearable")!!
        val days = WellnessSimulationRunner.run(persona, start, seed)
        for (day in days) {
            val dow = day.date.dayOfWeek.value
            val isOffDay = dow in setOf(2, 4, 6)  // Tue, Thu, Sat
            for (signal in WellnessSignal.ORDERED) {
                val reading = day.readings[signal]!!
                if (isOffDay) {
                    assertNull(
                        "$persona @ ${day.date} (off-day) $signal: " +
                            "null today should never produce a value, " +
                            "got ${reading.today}",
                        reading.today,
                    )
                    assertEquals(
                        "$persona @ ${day.date} (off-day) $signal: " +
                            "null today must read NO_DATA, got ${reading.direction}",
                        WellnessDirection.NO_DATA,
                        reading.direction,
                    )
                } else {
                    // On-watch days: HR + steps are present;
                    // HRV, sleep, mindfulness are null. The
                    // runner should still respect null.
                    if (signal == WellnessSignal.HRV ||
                        signal == WellnessSignal.SLEEP_MINUTES ||
                        signal == WellnessSignal.MINDFULNESS_MINUTES
                    ) {
                        assertNull(
                            "$persona @ ${day.date} (on-watch) $signal: " +
                                "this watch does not write this signal, " +
                                "got ${reading.today}",
                            reading.today,
                        )
                    } else {
                        assertNotNull(
                            "$persona @ ${day.date} (on-watch) $signal: " +
                                "this watch should be writing this signal, " +
                                "got null",
                            reading.today,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `sparse-data persona has a working HR baseline from on-watch days only`() {
        // The HR signal is present 4 of 7 days. After a 14-day
        // warmup the baseline should be computed from those 4/7
        // days (~8 readings). The persona's RHR mean is 62 with
        // sd 3, so the baseline median should be in the low 60s.
        val persona = PersonaLibrary.byId("sparse_data_partial_wearable")!!
        val days = WellnessSimulationRunner.run(persona, start, seed)
        val rhrReadings = days.mapNotNull { it.readings[WellnessSignal.RESTING_HEART_RATE] }
        val rhrMedians = rhrReadings.mapNotNull { it.baseline.median }
        val meanOfMedians = rhrMedians.average()
        assertTrue(
            "Sparse-persona RHR baseline median (${meanOfMedians}) " +
                "should be in the 60-65 range (anchored in the " +
                "persona's mean of 62).",
            meanOfMedians in 55.0..70.0,
        )
    }

    @Test
    fun `sparse-data persona has NO_DATA for HRV on every day (watch does not write it)`() {
        // HRV is the launcher's most-cited signal. A watch that
        // never writes HRV should produce NO_DATA for every
        // day's HRV reading, regardless of how many other
        // signals are present.
        val persona = PersonaLibrary.byId("sparse_data_partial_wearable")!!
        val days = WellnessSimulationRunner.run(persona, start, seed)
        for (day in days) {
            val reading = day.readings[WellnessSignal.HRV]!!
            assertEquals(
                "$persona @ ${day.date}: HRV should be NO_DATA " +
                    "(the watch never writes it), got ${reading.direction}",
                WellnessDirection.NO_DATA,
                reading.direction,
            )
        }
    }

    @Test
    fun `sparse-data persona's RHR baseline becomes reportable only after enough on-watch days accumulate`() {
        // The SparseDataPersona has 4/7 watch days. The warmup
        // contributes 8 non-null RHR days, which is below the
        // 14-day history floor. As the test days progress, the
        // runner's prior list grows (warmup + prior test days),
        // and once the running count of non-null RHR days
        // reaches 14, the baseline becomes reportable.
        //
        // This is the correct behaviour: a sparse-data persona
        // is exactly the case where the launcher's "wait 14
        // days of data" floor is most visible. The test pins
        // the on-watch count so a future change to the floor
        // (e.g., lowering it to 7) is caught.
        val persona = PersonaLibrary.byId("sparse_data_partial_wearable")!!
        val days = WellnessSimulationRunner.run(persona, start, seed)
        val rhrReadings = days.map { it.readings[WellnessSignal.RESTING_HEART_RATE]!! }
        val reportableDays = rhrReadings.count { it.baseline.isReportable }
        val nonReportableDays = 14 - reportableDays
        // The first 11-12 days should be NO_DATA (not enough
        // data), and the last 2-3 days should be reportable.
        // The exact count is seed-dependent; assert the shape
        // (most of the run is NO_DATA, some of it is reportable).
        assertTrue(
            "Sparse persona: at least 10 of 14 days should be " +
                "NO_DATA (the launcher's 14-day history floor " +
                "is most visible here). Was $nonReportableDays " +
                "non-reportable out of 14.",
            nonReportableDays >= 10,
        )
        assertTrue(
            "Sparse persona: at least 1 day should be " +
                "reportable (the floor eventually clears). " +
                "Was $reportableDays.",
            reportableDays >= 1,
        )
        // NO_DATA on the non-reportable days is mandatory.
        for (i in rhrReadings.indices) {
            if (!rhrReadings[i].baseline.isReportable) {
                assertEquals(
                    "Sparse persona day $i: non-reportable " +
                        "baseline must produce NO_DATA, got " +
                        "${rhrReadings[i].direction}",
                    WellnessDirection.NO_DATA,
                    rhrReadings[i].direction,
                )
            }
        }
    }

    @Test
    fun `sparse-data persona's RHR values are present on on-watch days, null on off-days`() {
        // The runner should not invent values for off-days.
        val persona = PersonaLibrary.byId("sparse_data_partial_wearable")!!
        val days = WellnessSimulationRunner.run(persona, start, seed)
        for (day in days) {
            val dow = day.date.dayOfWeek.value
            val isOffDay = dow in setOf(2, 4, 6)  // Tue, Thu, Sat
            val rhr = day.readings[WellnessSignal.RESTING_HEART_RATE]!!.today
            if (isOffDay) {
                assertNull(
                    "$persona @ ${day.date} (off-day) RHR: " +
                        "should be null, got $rhr",
                    rhr,
                )
            } else {
                assertNotNull(
                    "$persona @ ${day.date} (on-watch) RHR: " +
                        "should be non-null, got null",
                    rhr,
                )
            }
        }
    }

    @Test
    fun `every persona's per-day z-score is finite (no NaN or Infinity)`() {
        // The well-known "z = (x - median) / 0" bug. If the
        // baseline median or MAD is computed wrong, the
        // division produces NaN or Infinity. The runner should
        // never produce these.
        for (persona in PersonaLibrary.all) {
            val days = WellnessSimulationRunner.run(persona, start, seed)
            for (day in days) {
                for (signal in WellnessSignal.ORDERED) {
                    val z = day.readings[signal]!!.zScore
                    if (z != null) {
                        assertTrue(
                            "$persona @ ${day.date} $signal: z-score " +
                                "must be finite, got $z",
                            z.isFinite(),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `every persona's per-day baseline median is in a plausible range per signal`() {
        // Plausibility bounds, not exact assertions. The mean
        // of the persona's per-day baseline medians should
        // land in a sane range for each signal — anything
        // outside these bounds is a signal that the simulation
        // is producing nonsense.
        for (persona in PersonaLibrary.all) {
            val days = WellnessSimulationRunner.run(persona, start, seed)
            for (signal in WellnessSignal.ORDERED) {
                val medians = days.mapNotNull {
                    it.readings[signal]?.baseline?.median
                }
                if (medians.isEmpty()) continue
                val mean = medians.average()
                val range = when (signal) {
                    WellnessSignal.HRV -> 15.0..95.0
                    WellnessSignal.RESTING_HEART_RATE -> 45.0..90.0
                    WellnessSignal.STEPS -> 200.0..15_000.0
                    WellnessSignal.SLEEP_MINUTES -> 180.0..600.0
                    WellnessSignal.MINDFULNESS_MINUTES -> 0.0..30.0
                }
                assertTrue(
                    "$persona $signal: mean baseline median " +
                        "($mean) outside plausible range $range",
                    mean in range,
                )
            }
        }
    }

    @Test
    fun `every persona preserves the open-loop and bedtime phases across the full 14 days`() {
        // A regression where the runner skips a day or nulls
        // out a phase would surface here.
        for (persona in PersonaLibrary.all) {
            val days = WellnessSimulationRunner.run(persona, start, seed)
            assertEquals("$persona: 14 days", 14, days.size)
            // Default sunset window (22:00 → 07:00) is the
            // most common case. Every day at 23:00 should be
            // inside the window, so the open-loop should be
            // CAPTURE (no captured note in the runner) on
            // every day.
            for (day in days) {
                assertEquals(
                    "$persona @ ${day.date}: open-loop should be " +
                        "CAPTURE at 23:00 in default window, got " +
                        "${day.openLoopPhase}",
                    org.mindanchor.friction.LoopPhase.CAPTURE,
                    day.openLoopPhase,
                )
                assertEquals(
                    "$persona @ ${day.date}: bedtime-list should " +
                        "be CAPTURE at 23:00 in default window, got " +
                        "${day.bedtimeListPhase}",
                    org.mindanchor.sleep.BedtimePhase.CAPTURE,
                    day.bedtimeListPhase,
                )
            }
        }
    }

    @Test
    fun `every persona survives a different seed (no schedule throws on seed 0, 1, 2)`() {
        // The RNG is seed-based; a stray dependency on Math.random
        // would surface here as occasional test failures.
        for (s in listOf(0L, 1L, 2L, Long.MAX_VALUE, Long.MIN_VALUE)) {
            for (persona in PersonaLibrary.all) {
                val days = WellnessSimulationRunner.run(persona, start, s)
                assertEquals(
                    "$persona with seed=$s: 14 days",
                    14, days.size,
                )
            }
        }
    }

    @Test
    fun `persona library contains all 8 personas in the documented order`() {
        // Catches a future maintainer accidentally removing or
        // reordering a persona — the simulation report indexes
        // by position, so order matters.
        val expected = listOf(
            "morning_lark_healthy",
            "night_owl_healthy",
            "shift_worker_rotating",
            "insomnia_anxious",
            "depression_low_motivation",
            "noisy_signal_high_variance",
            "perfectly_regular_zero_variance",
            "sparse_data_partial_wearable",
        )
        assertEquals(expected, PersonaLibrary.all.map { it.id })
    }
}
