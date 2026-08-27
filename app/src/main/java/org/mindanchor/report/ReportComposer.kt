package org.mindanchor.report

import org.mindanchor.corpus.Passage
// v0.72.x: corpus import is removed; the [corpus]
// parameter is no longer passed to [compose]. The
// report still runs without it.
import org.mindanchor.corpus.Retrieval
import org.mindanchor.model.Baseline

/**
 * Turns a day of signals into what can honestly be said about it.
 *
 * ## The rule this file exists to enforce
 *
 * Research says **what a signal is**. The personal baseline says **it
 * moved**. Nothing anywhere is allowed to say **what that means about
 * the person** — not the corpus, not the model, not this.
 *
 * That is not squeamishness. Cross-person inference from phone signals
 * does not transfer, and the literature that would supply the
 * interpretation is exactly the literature that collapses when applied to
 * an individual. So the report is built to be two separable claims: a
 * count from somebody's own history, and an explanation of what the thing
 * counted is. The join between them is left to the person, who has
 * context no amount of measurement can supply.
 *
 * ## Why an empty report is a success
 *
 * A steady week produces no sections. This is the common case and it must
 * render as silence. Something that finds something to say every single
 * night is not observant, it is noise, and it teaches people to stop
 * reading — which costs the one night it would have mattered.
 */
object ReportComposer {

    /**
     * At most this many sections, however much moved.
     *
     * A report listing seven simultaneous abnormalities reads as an
     * emergency whatever its wording, and the most likely cause of seven
     * at once is a bad measurement day rather than seven real changes.
     */
    const val MAX_SECTIONS = 3

    /** Passages fetched per observation. */
    const val PASSAGES_PER_SECTION = 2

    /**
     * Builds the day's report.
     *
     * [history] maps each signal to that person's own past values, most
     * recent last and **not including today**. A signal with too little
     * history is reported in [Report.notYetKnown] rather than guessed at.
     *
     * [today] holds today's value per signal; a signal absent from it was
     * not measured, which is different from being normal and is simply
     * skipped.
     */
    fun compose(
        day: String,
        today: Map<Signal, Double>,
        history: Map<Signal, List<Double>>,
    ): Report {
        val notYetKnown = mutableListOf<Signal>()
        val observations = mutableListOf<Pair<Observation, Double>>()

        today.forEach { (signal, value) ->
            val past = history[signal].orEmpty()
            if (past.size < Baseline.MIN_OBSERVATIONS) {
                notYetKnown += signal
                return@forEach
            }
            val z = Baseline.robustZ(value, past)
            if (z == null) {
                notYetKnown += signal
                return@forEach
            }
            if (!Baseline.isNotable(z)) return@forEach
            val median = Baseline.median(past) ?: return@forEach
            observations += Observation(
                signal = signal,
                direction = if (z > 0) Direction.ABOVE else Direction.BELOW,
                today = value,
                usual = median,
            ) to kotlin.math.abs(z)
        }

        val sections = observations
            // Most unusual first, then by signal name so a tie does not
            // reorder the report between runs on identical data.
            .sortedWith(compareByDescending<Pair<Observation, Double>> { it.second }
                .thenBy { it.first.signal.name })
            .take(MAX_SECTIONS)
            .map { (observation, _) ->
                ReportSection(
                    observation = observation,
                    passages = emptyList(),
                )
            }

        return Report(
            day = day,
            sections = sections,
            notYetKnown = notYetKnown.distinct().sortedBy { it.name },
        )
    }
}
