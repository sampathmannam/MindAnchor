package org.mindanchor.vitals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasuredLedgerTest {

    private val entry = Measurement("2026-08-07", "HRV", 41.5)

    @Test
    fun `a measurement survives a round trip`() {
        assertEquals(listOf(entry), MeasuredLedger.decode(MeasuredLedger.encode(listOf(entry))))
    }

    @Test
    fun `a corrupt line costs one entry, never the file`() {
        val raw = listOf(
            "2026-08-07\tHRV\t41.5",
            "not a measurement at all",
            "2026-08-06\tHRV\tnot-a-number",
            "2026-08-05\t\t12.0",
            "2026-08-04\tHRV\t44.0",
        ).joinToString("\n")
        assertEquals(
            listOf(Measurement("2026-08-07", "HRV", 41.5), Measurement("2026-08-04", "HRV", 44.0)),
            MeasuredLedger.decode(raw),
        )
    }

    @Test
    fun `a second reading on the same day replaces the first`() {
        // Two readings in one day are a retake, not two data points: the
        // day contributes one value to the baseline, and the later
        // measurement is the one the person trusted enough to keep.
        val replaced = MeasuredLedger.upsert(listOf(entry), Measurement("2026-08-07", "HRV", 38.0))
        assertEquals(1, replaced.size)
        assertEquals(38.0, replaced.single().value, 1e-9)
    }

    @Test
    fun `a different key on the same day is kept alongside, not replaced`() {
        val both = MeasuredLedger.upsert(listOf(entry), Measurement("2026-08-07", "STEPS", 9000.0))
        assertEquals(2, both.size)
    }

    @Test
    fun `pruning drops old days and keeps the boundary day`() {
        val entries = listOf(
            Measurement("2026-04-01", "HRV", 40.0),
            Measurement("2026-04-09", "HRV", 41.0),
            Measurement("2026-08-07", "HRV", 42.0),
        )
        val kept = MeasuredLedger.prune(entries, keepFrom = "2026-04-09")
        assertEquals(listOf("2026-04-09", "2026-08-07"), kept.map { it.day })
    }

    @Test
    fun `empty input is an empty ledger, not an error`() {
        assertTrue(MeasuredLedger.decode("").isEmpty())
    }
}
