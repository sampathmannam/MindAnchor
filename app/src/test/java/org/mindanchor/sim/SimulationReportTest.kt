package org.mindanchor.sim

import org.junit.Test
import org.mindanchor.sim.personas.PersonaLibrary
import org.mindanchor.vitals.WellnessSignal

/**
 * The simulation report — runs all 5 personas and asserts the
 * shape of the launcher's output matches the persona's
 * research-anchored prediction.
 *
 * ## What this is and isn't
 *
 * This is *not* a finding-test (the launcher doesn't claim a
 * specific number per persona; it claims "given the data the
 * research describes, the math produces a stable per-person
 * baseline and a sensible distribution of direction bands").
 * The assertions here are sanity checks on that distribution,
 * not on the clinical truth of the research.
 *
 * What the report *is* the answer to: "does the launcher's
 * math respect the persona's research-anchored shape?" If the
 * insomniac persona's HRV baseline lands in the 30s and the
 * morning lark's lands in the 50s, the launcher is doing
 * what the research says. If both land in the 40s, something
 * is wrong.
 */
class SimulationReportTest {

    @Test
    fun `report prints a per-persona summary`() {
        val reports = SimulationReport.report()
        SimulationReport.printReport(reports)
        // The print itself is the assertion: if any summary
        // computed an unexpected distribution, the test failure
        // message in the report is the diagnostic. We assert the
        // overall shape below.
        for (pr in reports) {
            for (signal in WellnessSignal.ORDERED) {
                val s = pr.summaries[signal]!!
                val parts = listOf(s.atCount, s.aboveCount, s.muchAboveCount, s.belowCount, s.noDataCount)
                val total = parts.sum()
                check(total == 14) {
                    "${pr.persona.id} $signal: counts sum to $total, expected 14"
                }
            }
        }
    }

    @Test
    fun `morning lark has the highest mean HRV across the 5 personas`() {
        val reports = SimulationReport.report()
        val byMedian: Map<String, Double> = reports.associate { pr ->
            pr.persona.id to (pr.summaries[WellnessSignal.HRV]?.baselineMedian ?: 0.0)
        }
        val morningLarkHrv = byMedian["morning_lark_healthy"]!!
        val others = byMedian.filterKeys { it != "morning_lark_healthy" }.values
        assert(others.all { it < morningLarkHrv }) {
            "Morning lark HRV ($morningLarkHrv) should be the highest; " +
                "others: ${byMedian.filterKeys { it != "morning_lark_healthy" }}"
        }
    }

    @Test
    fun `depression persona has the lowest mean steps across the 5 personas`() {
        val reports = SimulationReport.report()
        val byMedian: Map<String, Double> = reports.associate { pr ->
            pr.persona.id to (pr.summaries[WellnessSignal.STEPS]?.baselineMedian ?: 0.0)
        }
        val depressionSteps = byMedian["depression_low_motivation"]!!
        val others = byMedian.filterKeys { it != "depression_low_motivation" }.values
        assert(others.all { it > depressionSteps }) {
            "Depression steps ($depressionSteps) should be the lowest; " +
                "others: ${byMedian.filterKeys { it != "depression_low_motivation" }}"
        }
    }

    @Test
    fun `every persona has an open-loop CAPTURE on at least 1 day`() {
        val reports = SimulationReport.report()
        for (pr in reports) {
            assert(pr.openLoopCaptureCount >= 1) {
                "${pr.persona.id}: open-loop should fire CAPTURE on at least 1 of 14 days " +
                    "with the default 22:00-07:00 window, got ${pr.openLoopCaptureCount}"
            }
        }
    }
}
