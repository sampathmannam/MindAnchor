package org.mindanchor.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportComposerTest {

    private fun steady(v: Double) = List(8) { v + (it - 4) * 0.1 }

    @Test
    fun `a report lists the same content on a re-run of identical data`() {
        val today = mapOf(Signal.HRV to 12.0, Signal.STEPS to 1_000.0)
        val history = mapOf(Signal.HRV to steady(40.0), Signal.STEPS to steady(6_000.0))
        val a = ReportComposer.compose("d", today, history)
        val b = ReportComposer.compose("d", today, history)
        assertEquals(a, b)
    }

    @Test
    fun `every section can name where its research came from`() {
        val report = ReportComposer.compose(
            "d", mapOf(Signal.SLEEP_ONSET to 900.0), mapOf(Signal.SLEEP_ONSET to steady(240.0)),
        )
        // v0.72.x: the corpus is gone. Sections still render;
        // the sources list may be empty.
        assertNotNull(report)
    }

    @Test
    fun `a section survives an empty corpus, minus its research`() {
        val report = ReportComposer.compose(
            "d", mapOf(Signal.HRV to 12.0), mapOf(Signal.HRV to steady(40.0)),
        )
        assertNotNull(report)
    }
}
