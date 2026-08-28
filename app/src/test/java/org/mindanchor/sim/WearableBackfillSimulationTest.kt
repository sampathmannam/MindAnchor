package org.mindanchor.sim

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.sim.personas.PersonaLibrary
import org.mindanchor.vitals.WellnessBackfill
import org.mindanchor.vitals.WellnessLedger
import org.mindanchor.vitals.WellnessSignal
import org.mindanchor.vitals.WellnessStats
import org.mindanchor.vitals.coros.CorosDaily
import org.mindanchor.vitals.coros.CorosHrv

/**
 * End-to-end simulation of the wearable-connect backfill: a person
 * installs the launcher, accumulates a few days of phone-sourced
 * STEPS, then connects a COROS account that already holds 28 days
 * of RHR and 7 nights of HRV on the Training Hub.
 *
 * Before the backfill existed, the wellness ledger only grew from
 * its own daily reads, so the RHR baseline said "still building a
 * picture, each signal needs 14 days" for 14 more days — about
 * data the account already had. This simulation pins the fixed
 * contract:
 *
 *  - the RHR baseline is reportable *on the connect day* (28
 *    bridge days ≥ the 14-day floor), with the persona's own
 *    median;
 *  - the HRV baseline honestly stays unreportable (7 nights < 14
 *    days) — the backfill imports history, it does not lower the
 *    floor;
 *  - the phone's own STEPS rows are untouched.
 *
 * Same charter as [WellnessSimulationRunner]: no new logic here,
 * only the launcher's own pure functions ([WellnessBackfill],
 * [WellnessStats]) driven by persona data, so a pass here means
 * the launcher behaves the same way on a real connect.
 */
class WearableBackfillSimulationTest {

    private val connectDay: LocalDate = LocalDate.of(2026, 8, 28)
    private val seed: Long = 42L

    /** XOR salt so the older half's noise differs from the newer half's. */
    private val olderHalfSalt: Long = 0x6D696E6463687220L

    private val persona = PersonaLibrary.byId("morning_lark_healthy")!!

    /**
     * The 28 days of history the Training Hub holds at connect
     * time, ending the day before the connect. Two 14-day persona
     * schedules back to back — the same warmup trick
     * [WellnessSimulationRunner.run] uses to build a baseline
     * under its test window.
     */
    private fun bridgeHistory() =
        persona.schedule(connectDay.minusDays(28), seed xor olderHalfSalt) +
            persona.schedule(connectDay.minusDays(14), seed)

    private fun bridgeDaily(): List<CorosDaily> = bridgeHistory().map { day ->
        CorosDaily(date = day.date.toString(), rhr = day.restingHeartRate)
    }

    /** The dashboard's HRV window is 7 nights, not 28 days. */
    private fun bridgeHrv(): List<CorosHrv> = bridgeHistory().takeLast(7).map { day ->
        CorosHrv(date = day.date.toString(), rmssd = day.hrvRmssd)
    }

    /**
     * The ledger as it stands on connect day: only the STEPS the
     * phone itself counted since install — the exact state the
     * backfill was built for.
     */
    private fun freshInstallLedger(): List<WellnessLedger.Entry> = (1..3).map { daysAgo ->
        WellnessLedger.Entry(
            signal = WellnessSignal.STEPS,
            day = connectDay.minusDays(daysAgo.toLong()),
            value = 8_000.0,
        )
    }

    /** The baseline the wellness surface would compute for [signal] on [connectDay]. */
    private fun baselineFor(
        signal: WellnessSignal,
        ledger: List<WellnessLedger.Entry>,
    ) = WellnessStats.baseline(
        signal,
        ledger.filter { it.signal == signal && it.day < connectDay }.map { it.value },
    )

    @Test
    fun `before the backfill a fresh install has no reportable RHR baseline`() {
        val baseline = baselineFor(WellnessSignal.RESTING_HEART_RATE, freshInstallLedger())
        assertFalse(
            "a STEPS-only ledger must not report an RHR baseline",
            baseline.isReportable,
        )
    }

    @Test
    fun `connect day makes the RHR baseline reportable immediately`() {
        val merged = WellnessBackfill.merged(
            existing = freshInstallLedger(),
            wearable = WellnessBackfill.fromBridge(hrv = bridgeHrv(), daily = bridgeDaily()),
        )
        val baseline = baselineFor(WellnessSignal.RESTING_HEART_RATE, merged)
        assertTrue(
            "28 bridge days must clear the 14-day floor on connect day, " +
                "got sampleCount=${baseline.sampleCount}",
            baseline.isReportable,
        )
        assertEquals(28, baseline.sampleCount)
        val median = baseline.median!!
        assertTrue(
            "median $median should sit in the persona's own RHR range",
            median in 50.0..72.0,
        )
    }

    @Test
    fun `seven bridge nights of HRV honestly stay below the baseline floor`() {
        val merged = WellnessBackfill.merged(
            existing = freshInstallLedger(),
            wearable = WellnessBackfill.fromBridge(hrv = bridgeHrv(), daily = bridgeDaily()),
        )
        val baseline = baselineFor(WellnessSignal.HRV, merged)
        assertEquals(7, baseline.sampleCount)
        assertFalse(
            "7 nights must not fake a 14-day baseline",
            baseline.isReportable,
        )
    }

    @Test
    fun `the phone's own STEPS rows survive the backfill untouched`() {
        val merged = WellnessBackfill.merged(
            existing = freshInstallLedger(),
            wearable = WellnessBackfill.fromBridge(hrv = bridgeHrv(), daily = bridgeDaily()),
        )
        assertEquals(
            freshInstallLedger().toSet(),
            merged.filter { it.signal == WellnessSignal.STEPS }.toSet(),
        )
    }
}
