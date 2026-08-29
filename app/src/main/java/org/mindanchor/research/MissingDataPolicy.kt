package org.mindanchor.research

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.serialization.Serializable

/**
 * Why a value is absent.
 *
 * There is deliberately **no** case distinguishing "structural context
 * extraction was switched off" from "it ran and produced nothing". The
 * kill switch is a live user-toggleable flag and nothing records when it
 * was toggled, so labelling a six-week-old absence with today's flag state
 * would assert a cause nobody knows — a fabrication of exactly the kind
 * this policy exists to prevent, and a carry-forward of a reason rather
 * than a value. [CONTEXT_NOT_DERIVED] says only what is actually known.
 *
 * [SENSOR_GAP] and [DEVICE_CHANGE_GAP] are capability without a detector,
 * the same discipline `LedgerEventKind.SENSOR_GAP` follows: Program 1 owns
 * no sensors, so no code path here produces them. They exist so Program 2
 * can report a real gap without changing the export shape or the frozen
 * data dictionary, and a test exhausts this function's reachable inputs to
 * prove Program 1 never emits one.
 */
@Serializable
enum class MissingDataReason {
    /** The person did not record it that day. */
    NOT_RECORDED,

    /** The date precedes the first record of that variable — not a skipped day. */
    BEFORE_FIRST_RECORD,

    /** A Journal entry on this date has no structural context rows. Why is not recorded. */
    CONTEXT_NOT_DERIVED,

    /** Program 2: a signal source was unavailable. */
    SENSOR_GAP,

    /** Program 2: history moved to another device and a window has no coverage. */
    DEVICE_CHANGE_GAP,
}

/** One absent value, named and explained. Deliberately has no value field. */
@Serializable
data class MissingDataRecord(val localDate: String, val variable: String, val reason: MissingDataReason)

/**
 * The explicit missing-data policy, stated once and enforced by one pure
 * function.
 *
 * **Nothing is imputed, interpolated, carried forward, or filled in. Every
 * absence is listed with a reason.**
 *
 * A series that quietly looks complete is worse than one with visible
 * holes, because only the second lets a reader judge how much of a pattern
 * is real. So [report] enumerates absences rather than repairing them, and
 * [MissingDataRecord] has nowhere to put a value even if a later author
 * wanted to.
 *
 * The distinction between [MissingDataReason.BEFORE_FIRST_RECORD] and
 * [MissingDataReason.NOT_RECORDED] is the one that matters most: a person
 * who journaled for two weeks before ever completing a morning measure did
 * not skip fourteen measures.
 */
object MissingDataPolicy {

    const val VERSION = "missing-data-v1"

    const val STATEMENT =
        "Nothing is imputed, interpolated, carried forward, or filled in. " +
            "Every absence is listed with a reason."

    /** The morning research measure, one per local date. */
    const val VARIABLE_MORNING_MEASURE = "morning_measure"

    /** Structural context rows derived from a Journal entry. */
    const val VARIABLE_JOURNAL_CONTEXT = "journal_context"

    /**
     * Roughly a century. A report longer than a personal record could
     * plausibly be is a wrong clock or a corrupt row, not a long study,
     * and materialising one would hang the export.
     */
    const val MAX_REPORT_DAYS = 36_600L

    /**
     * Enumerates every absence between [firstRecordDate] and [throughDate]
     * inclusive. Dates outside that window are never reported, in either
     * direction.
     *
     * @param firstRecordDate the earliest local date on which anything was
     *   recorded, or null when nothing ever was — in which case the report
     *   is empty, because inventing absences for a person who has not
     *   started is not information.
     * @param allMeasureDates **every** local date that has a morning
     *   measure, not a recent window of them. The name is the contract: a
     *   windowed set would make the earliest window boundary look like the
     *   first measure ever taken, and turn months of genuinely skipped
     *   days into "hadn't started yet".
     * @param entryDatesWithoutContext local dates carrying a Journal entry
     *   with no structural context rows. Keyed by date, so several entries
     *   on one date report as one absence.
     * @throws IllegalArgumentException if the window is longer than
     *   [MAX_REPORT_DAYS].
     */
    fun report(
        firstRecordDate: LocalDate?,
        throughDate: LocalDate,
        allMeasureDates: Set<LocalDate>,
        entryDatesWithoutContext: Set<LocalDate>,
    ): List<MissingDataRecord> {
        if (firstRecordDate == null || throughDate.isBefore(firstRecordDate)) return emptyList()
        val span = ChronoUnit.DAYS.between(firstRecordDate, throughDate)
        require(span <= MAX_REPORT_DAYS) {
            "missing-data window of $span days exceeds $MAX_REPORT_DAYS; check the clock, not the policy"
        }

        val firstMeasureDate = allMeasureDates.minOrNull()
        val measureGaps = generateSequence(firstRecordDate) { it.plusDays(1) }
            .takeWhile { !it.isAfter(throughDate) }
            .filterNot { it in allMeasureDates }
            .map { day ->
                val reason = if (firstMeasureDate == null || day.isBefore(firstMeasureDate)) {
                    MissingDataReason.BEFORE_FIRST_RECORD
                } else {
                    MissingDataReason.NOT_RECORDED
                }
                MissingDataRecord(day.toString(), VARIABLE_MORNING_MEASURE, reason)
            }

        val contextGaps = entryDatesWithoutContext
            .filter { !it.isBefore(firstRecordDate) && !it.isAfter(throughDate) }
            .map {
                MissingDataRecord(it.toString(), VARIABLE_JOURNAL_CONTEXT, MissingDataReason.CONTEXT_NOT_DERIVED)
            }

        // No de-duplication pass: measure gaps are unique by construction,
        // entryDatesWithoutContext is a set, and the two groups always
        // differ in `variable`.
        return (measureGaps.toList() + contextGaps).sortedWith(compareBy({ it.localDate }, { it.variable }))
    }
}
