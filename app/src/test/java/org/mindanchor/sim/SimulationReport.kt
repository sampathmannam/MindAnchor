package org.mindanchor.sim

import org.mindanchor.sim.personas.Persona
import org.mindanchor.sim.personas.PersonaLibrary
import org.mindanchor.vitals.WellnessDirection
import org.mindanchor.vitals.WellnessSignal
import java.time.LocalDate

/**
 * A per-persona summary of the simulation: the distribution of
 * direction bands per signal, the persona's per-signal median,
 * the [WellnessSimulationRunner.summarize] cross-cutting
 * output (sleep window suggestion), and a one-line
 * research-anchored verdict.
 *
 * The report is the WP-5 deliverable. It is not the issue tracker
 * (that lives in `tools/sim/issues.json` in production; for the
 * test source set the report is the report). The report answers
 * the single question that matters: "given a 14-day schedule
 * shaped like the persona the research describes, does the
 * launcher's math produce results that look like the research
 * predicted?"
 *
 * Output is a plain text block on stdout. The report is a test
 * (not a main()) so it runs under the standard Gradle test
 * pipeline and fails on any assertion violation.
 */
object SimulationReport {

    data class SignalSummary(
        val signal: WellnessSignal,
        val atCount: Int,
        val aboveCount: Int,
        val muchAboveCount: Int,
        val belowCount: Int,
        val noDataCount: Int,
        // v0.58.0: the new MUCH_BELOW band.
        // See [WellnessDirection] KDoc for
        // the design rationale. The
        // [SignalSummary] carries the
        // per-band count so the report
        // print shows the symmetric "way
        // above" / "way below" outliers
        // when a persona's days include
        // them.
        val muchBelowCount: Int = 0,
        val baselineMedian: Double?,
    ) {
        val total: Int get() = atCount + aboveCount + muchAboveCount + belowCount + muchBelowCount + noDataCount
    }

    data class PersonaReport(
        val persona: Persona,
        val summaries: Map<WellnessSignal, SignalSummary>,
        val openLoopCaptureCount: Int,
        val bedtimeCaptureCount: Int,
        val sleepSuggestion: String,
    )

    fun report(
        start: LocalDate = LocalDate.of(2026, 1, 5),
        seed: Long = 42L,
    ): List<PersonaReport> {
        val out = mutableListOf<PersonaReport>()
        for (persona in PersonaLibrary.all) {
            val days = WellnessSimulationRunner.run(persona, start, seed)
            val summaries = WellnessSignal.ORDERED.associateWith { signal ->
                val readings = days.mapNotNull { it.readings[signal] }
                val medians = readings.mapNotNull { it.baseline.median }
                SignalSummary(
                    signal = signal,
                    atCount = readings.count { it.direction == WellnessDirection.AT },
                    aboveCount = readings.count { it.direction == WellnessDirection.ABOVE },
                    muchAboveCount = readings.count { it.direction == WellnessDirection.MUCH_ABOVE },
                    belowCount = readings.count { it.direction == WellnessDirection.BELOW },
                    noDataCount = readings.count { it.direction == WellnessDirection.NO_DATA },
                    // v0.58.0: count the new
                    // MUCH_BELOW band. Most
                    // personas will have
                    // [muchBelowCount = 0]
                    // because the persona
                    // distributions are
                    // symmetric around the
                    // median; the depression
                    // and insomnia personas
                    // can land in MUCH_BELOW
                    // for sleep / HRV.
                    muchBelowCount = readings.count { it.direction == WellnessDirection.MUCH_BELOW },
                    baselineMedian = if (medians.isEmpty()) null else medians.average(),
                )
            }
            // The cross-cutting summary (the sleep window suggestion
            // driven by the persona's 14 days of sleep onsets) is
            // the v0.22.0 addition: the runner now exercises
            // [org.mindanchor.sleep.SleepWindowOptimizer] end-to-end
            // on each persona, and the report surfaces what the
            // user would see in the settings → suggested wind-down
            // card. SparseData under 5 nights is the one
            // expectation: the optimizer returns null and the
            // report shows "no suggestion yet".
            val summary = WellnessSimulationRunner.summarize(persona, start, seed)
            val sleepSuggestion = summary.suggestedWindow?.let { s ->
                "median=${s.medianOnset}  start=${s.startTime}  end=${s.endTime}  (from ${s.nightsUsed} nights)"
            } ?: "no suggestion yet (under 5 nights of usable data)"

            out.add(
                PersonaReport(
                    persona = persona,
                    summaries = summaries,
                    openLoopCaptureCount = days.count { it.openLoopPhase.name == "CAPTURE" },
                    bedtimeCaptureCount = days.count { it.bedtimeListPhase.name == "CAPTURE" },
                    sleepSuggestion = sleepSuggestion,
                ),
            )
        }
        return out
    }

    fun printReport(reports: List<PersonaReport>) {
        for (pr in reports) {
            println()
            println("================================================================")
            println("PERSONA: ${pr.persona.id} — ${pr.persona.name}")
            println(pr.persona.description)
            println("----------------------------------------------------------------")
            for (signal in WellnessSignal.ORDERED) {
                val s = pr.summaries[signal]!!
                val median = s.baselineMedian?.let { "%.2f".format(it) } ?: "n/a"
                println(
                    "  ${signal.name.padEnd(22)} " +
                        "median=$median  " +
                        "AT=${s.atCount}  ABOVE=${s.aboveCount}  " +
                        "MUCH_ABOVE=${s.muchAboveCount}  BELOW=${s.belowCount}  " +
                        "MUCH_BELOW=${s.muchBelowCount}  " +
                        "NO_DATA=${s.noDataCount}",
                )
            }
            println("  open-loop CAPTURE  = ${pr.openLoopCaptureCount} / 14 days")
            println("  bedtime CAPTURE    = ${pr.bedtimeCaptureCount} / 14 days")
            println("  sleep suggestion   = ${pr.sleepSuggestion}")
        }
    }
}
