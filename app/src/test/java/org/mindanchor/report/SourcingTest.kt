package org.mindanchor.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourcingTest {

    @Test
    fun `a deliberate measurement beats the wearable, which beats inference`() {
        assertEquals(
            Sourced(30.0, MeasureSource.MEASURED_HERE),
            Sourcing.pick(measuredHere = 30.0, wearable = 40.0, phoneInferred = 50.0),
        )
        assertEquals(
            Sourced(40.0, MeasureSource.WEARABLE),
            Sourcing.pick(measuredHere = null, wearable = 40.0, phoneInferred = 50.0),
        )
        assertEquals(
            Sourced(50.0, MeasureSource.PHONE_INFERRED),
            Sourcing.pick(measuredHere = null, wearable = null, phoneInferred = 50.0),
        )
    }

    @Test
    fun `nothing from any source is nothing, not zero`() {
        assertNull(Sourcing.pick(null, null, null))
    }

    // --- the coverage summary ---

    private val coverage = listOf(
        Coverage(Signal.HRV, 12, "2026-08-06", MeasureSource.MEASURED_HERE),
        Coverage(Signal.STEPS, 0, null, null),
        Coverage(Signal.SLEEP_ONSET, 7, "2026-08-05", MeasureSource.PHONE_INFERRED),
    )

    @Test
    fun `coverage survives a round trip, absent days and sources included`() {
        assertEquals(coverage, CoverageLedger.decode(CoverageLedger.encode(coverage)))
    }

    @Test
    fun `a corrupt coverage line costs one row, never the summary`() {
        val raw = listOf(
            "HRV\t12\t2026-08-06\tMEASURED_HERE",
            "NOT_A_SIGNAL\t3\t-\t-",
            "STEPS\tnot-a-count\t-\t-",
            "SLEEP_ONSET\t7\t2026-08-05\tPHONE_INFERRED",
        ).joinToString("\n")
        assertEquals(
            listOf(Signal.HRV, Signal.SLEEP_ONSET),
            CoverageLedger.decode(raw).map { it.signal },
        )
    }

    @Test
    fun `a source name from some future version costs the word, not the row`() {
        val decoded = CoverageLedger.decode("HRV\t12\t2026-08-06\tSOME_FUTURE_SOURCE")
        assertEquals(1, decoded.size)
        assertEquals(12, decoded.single().daysOnFile)
        assertNull(decoded.single().lastSource)
    }
}
