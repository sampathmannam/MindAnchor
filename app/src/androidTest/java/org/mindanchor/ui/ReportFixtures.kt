package org.mindanchor.ui

import org.mindanchor.corpus.Passage
import org.mindanchor.report.Direction
import org.mindanchor.report.Label
import org.mindanchor.report.MeasureSource
import org.mindanchor.report.Observation
import org.mindanchor.report.Pattern
import org.mindanchor.report.Report
import org.mindanchor.report.ReportSection
import org.mindanchor.report.Signal
import org.mindanchor.report.Sourced
import org.mindanchor.report.StoredReport

/**
 * One report, shaped like a bad night on purpose, shared by every
 * instrumented test that needs to hand the report screen something to
 * show: two observations, a pattern and a generated paragraph at once is
 * close to the most the screen can ever legally display, so a layout or
 * affordance that holds here holds everywhere.
 *
 * Shared rather than duplicated so the screenshots and the semantics walk
 * photograph and walk the same truth — two fixtures drift, and a fault
 * that only exists in the state one of them renders is a fault nobody
 * finds.
 */
internal object ReportFixtures {

    fun stored(): StoredReport = StoredReport(
        report = Report(
            day = "2026-08-06",
            sections = listOf(
                ReportSection(
                    observation = Observation(
                        signal = Signal.SLEEP_ONSET,
                        direction = Direction.ABOVE,
                        today = 330.0,
                        usual = 240.0,
                    ),
                    passages = listOf(
                        Passage(
                            "sleep-regularity",
                            "Windred et al. 2024, Sleep 47:zsad285",
                            "In a large accelerometry cohort, sleep regularity predicted " +
                                "all-cause mortality more strongly than sleep duration did. " +
                                "Regularity here means the consistency of sleep and wake " +
                                "timing, not the number of hours.",
                        ),
                    ),
                ),
                ReportSection(
                    observation = Observation(
                        signal = Signal.HRV,
                        direction = Direction.BELOW,
                        today = 28.0,
                        usual = 46.0,
                    ),
                    passages = listOf(
                        Passage(
                            "hrv-rmssd",
                            "Shaffer & Ginsberg 2017, Frontiers in Public Health",
                            "RMSSD is the primary time-domain measure of short-term heart " +
                                "rate variability. It reflects vagally mediated, " +
                                "beat-to-beat changes in heart rhythm.",
                        ),
                    ),
                ),
            ),
            notYetKnown = listOf(Signal.STEPS),
        ),
        narration = "Heart-rate variability describes how much the time between your " +
            "heartbeats changes from one beat to the next. Sleep onset is simply when " +
            "you first fell asleep. Both were further from your usual last night than " +
            "they normally are.",
        patterns = listOf(
            Pattern(
                signal = Signal.SLEEP_ONSET,
                label = Label.VALENCE,
                similarDays = 22,
                medianWhenLikeToday = 2.0,
                medianOverall = 3.0,
            ),
        ),
    )

    /**
     * Yesterday's facts to go with [stored], one from each provenance so
     * all three source words render. STEPS carrying a value while the
     * report still says it is learning is deliberate, not a
     * contradiction: the diary shows what arrived yesterday, the baseline
     * needs weeks of arrivals before it may speak, and the gap between
     * those two is exactly what the facts block exists to fill.
     */
    fun facts(): Map<Signal, Sourced> = mapOf(
        Signal.HRV to Sourced(28.0, MeasureSource.MEASURED_HERE),
        Signal.SLEEP_MINUTES to Sourced(385.0, MeasureSource.WEARABLE),
        Signal.SLEEP_ONSET to Sourced(330.0, MeasureSource.PHONE_INFERRED),
        Signal.STEPS to Sourced(9214.0, MeasureSource.WEARABLE),
    )
}
