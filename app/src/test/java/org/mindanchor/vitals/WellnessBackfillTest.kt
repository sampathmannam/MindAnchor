package org.mindanchor.vitals

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.vitals.coros.CorosDaily
import org.mindanchor.vitals.coros.CorosHrv

/**
 * The pure rules for backfilling the wellness ledger from a
 * wearable bridge's historical series.
 *
 * The scenario these pin: a freshly connected COROS bridge
 * arrives with 28 days of RHR and 7 nights of HRV already on
 * the Training Hub, while the wellness ledger has only the few
 * days of STEPS the phone accumulated since install. Without a
 * backfill the baseline waits another 14 days on data the
 * account already has; with it, the history the person already
 * generated counts immediately.
 *
 * The provenance rule under test is [org.mindanchor.report.Sourcing.pick]
 * projected onto a provenance-less ledger: an existing row was
 * written by the live read path (which already picked
 * measured-here over wearable), so it always wins; a
 * measured-here value beats the bridge's wearable value for a
 * day the ledger lacks; the bridge fills the rest.
 */
class WellnessBackfillTest {

    private val d1: LocalDate = LocalDate.of(2026, 8, 1)
    private val d2: LocalDate = LocalDate.of(2026, 8, 2)
    private val d3: LocalDate = LocalDate.of(2026, 8, 3)

    private fun entry(signal: WellnessSignal, day: LocalDate, value: Double) =
        WellnessLedger.Entry(signal = signal, day = day, value = value)

    // ------------------------------------------------------ fromBridge

    @Test
    fun `fromBridge maps nightly HRV and daily RHR into ledger entries`() {
        val entries = WellnessBackfill.fromBridge(
            hrv = listOf(
                CorosHrv(date = "2026-08-01", rmssd = 41.0),
                CorosHrv(date = "2026-08-02", rmssd = 44.5),
            ),
            daily = listOf(
                CorosDaily(date = "2026-08-01", rhr = 55.0),
                CorosDaily(date = "2026-08-02", rhr = 57.0),
            ),
        )
        assertEquals(
            setOf(
                entry(WellnessSignal.HRV, d1, 41.0),
                entry(WellnessSignal.HRV, d2, 44.5),
                entry(WellnessSignal.RESTING_HEART_RATE, d1, 55.0),
                entry(WellnessSignal.RESTING_HEART_RATE, d2, 57.0),
            ),
            entries.toSet(),
        )
    }

    @Test
    fun `fromBridge drops rows with missing values, junk zeros, or bad dates`() {
        val entries = WellnessBackfill.fromBridge(
            hrv = listOf(
                // The value never arrived.
                CorosHrv(date = "2026-08-01", rmssd = null),
                // A wire format this build does not parse costs the row, not the sync.
                CorosHrv(date = "20260801", rmssd = 40.0),
            ),
            daily = listOf(
                // 0 bpm is not a resting heart rate; a zero here would
                // poison the personal median for months.
                CorosDaily(date = "2026-08-02", rhr = 0.0),
                CorosDaily(date = "not-a-date", rhr = 55.0),
            ),
        )
        assertTrue("junk rows must be dropped, got $entries", entries.isEmpty())
    }

    // ---------------------------------------------------------- merged

    @Test
    fun `merged fills days the ledger has never seen and keeps other signals`() {
        val existing = listOf(
            entry(WellnessSignal.STEPS, d1, 8_000.0),
            entry(WellnessSignal.STEPS, d2, 7_500.0),
        )
        val wearable = listOf(
            entry(WellnessSignal.RESTING_HEART_RATE, d1, 55.0),
            entry(WellnessSignal.RESTING_HEART_RATE, d2, 57.0),
            entry(WellnessSignal.RESTING_HEART_RATE, d3, 56.0),
        )
        val merged = WellnessBackfill.merged(existing, wearable)
        assertEquals((existing + wearable).toSet(), merged.toSet())
    }

    @Test
    fun `merged never replaces an existing row for the same signal and day`() {
        // The existing row came from the live read path, which
        // already applied measured-here-over-wearable; the backfill
        // (wearable provenance) must not rewrite it.
        val existing = listOf(entry(WellnessSignal.RESTING_HEART_RATE, d1, 52.0))
        val wearable = listOf(entry(WellnessSignal.RESTING_HEART_RATE, d1, 60.0))
        val merged = WellnessBackfill.merged(existing, wearable)
        assertEquals(existing, merged)
    }

    @Test
    fun `merged prefers a measured-here value over the bridge value for a missing day`() {
        // Sourcing.pick: MEASURED_HERE > WEARABLE. A camera-PPG HRV
        // taken on a day the ledger never recorded must win over the
        // watch's number for that night.
        val wearable = listOf(entry(WellnessSignal.HRV, d1, 40.0))
        val measuredHere = listOf(entry(WellnessSignal.HRV, d1, 52.0))
        val merged = WellnessBackfill.merged(emptyList(), wearable, measuredHere)
        assertEquals(listOf(entry(WellnessSignal.HRV, d1, 52.0)), merged)
    }

    @Test
    fun `merged is driven by the bridge's days — a measured-here day the bridge lacks is not added`() {
        // The backfill's job is to import the bridge's series. A
        // measured-here day outside that series was either already
        // recorded by the live path on the day it was taken, or is
        // not this code path's business.
        val wearable = listOf(entry(WellnessSignal.HRV, d1, 40.0))
        val measuredHere = listOf(entry(WellnessSignal.HRV, d2, 52.0))
        val merged = WellnessBackfill.merged(emptyList(), wearable, measuredHere)
        assertEquals(listOf(entry(WellnessSignal.HRV, d1, 40.0)), merged)
    }
}
