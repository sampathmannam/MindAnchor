package org.mindanchor.report

import org.mindanchor.corpus.Passage

/** A thing that can be counted about a person, day by day. */
enum class Signal {
    HRV,
    RESTING_HEART_RATE,
    SLEEP_MINUTES,
    SLEEP_ONSET,
    STEPS,
    VALENCE,
    AROUSAL,
    ;

    /**
     * Search terms for the corpus.
     *
     * These describe **what the signal is**, never what a change in it
     * means. "vagal parasympathetic recovery" fetches the explanation of
     * HRV; it does not fetch, and must not fetch, an interpretation of
     * somebody's HRV having fallen.
     */
    val corpusQuery: String
        get() = when (this) {
            HRV -> "heart rate variability RMSSD vagal parasympathetic"
            RESTING_HEART_RATE -> "resting heart rate recovery load"
            SLEEP_MINUTES -> "sleep duration hours"
            SLEEP_ONSET -> "sleep regularity timing bedtime circadian"
            STEPS -> "physical activity steps movement"
            VALENCE -> "momentary affect valence mood assessment"
            AROUSAL -> "arousal activation energy"
        }
}

/** Higher or lower than this person's own usual. */
enum class Direction { ABOVE, BELOW }

/**
 * One signal, today, against this person's own history.
 *
 * There is deliberately no z-score field. The robust z is a screening
 * device whose magnitude stops meaning anything once the dispersion floor
 * binds — see [org.mindanchor.model.Baseline]. Carrying it here would
 * invite something downstream to print it, and "you are 44 standard
 * deviations from normal" is alarming and meaningless.
 */
data class Observation(
    val signal: Signal,
    val direction: Direction,
    /** Today's value, in the signal's own units. */
    val today: Double,
    /** This person's own median, in the same units. */
    val usual: Double,
)

/**
 * A piece of the report: what was noticed, and what the corpus says the
 * signal is.
 *
 * [sources] is not optional. A section whose passages cannot be named is
 * a section making an unfalsifiable claim, and this whole design rests on
 * every statement being checkable.
 */
data class ReportSection(
    val observation: Observation,
    val passages: List<Passage>,
) {
    val sources: List<String> get() = passages.map { it.source }.distinct()
}

/**
 * A night's report, or the absence of one.
 *
 * [sections] being empty is a legitimate and common outcome. A steady
 * week produces nothing to say, and a report that manufactures something
 * every night teaches somebody to stop reading it.
 */
data class Report(
    val day: String,
    val sections: List<ReportSection>,
    /** Signals that had no baseline yet, so nothing could be said. */
    val notYetKnown: List<Signal>,
) {
    val isEmpty: Boolean get() = sections.isEmpty()
}
