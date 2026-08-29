package org.mindanchor.research

import java.time.LocalDate
import kotlinx.serialization.Serializable

/**
 * Why a value is absent.
 *
 * [SENSOR_GAP] and [DEVICE_CHANGE_GAP] are capability without a detector,
 * the same discipline `LedgerEventKind.SENSOR_GAP` follows: Program 1 owns
 * no sensors, so no code path here produces them. They exist so Program 2
 * can report a real gap without changing the export shape or the frozen
 * data dictionary, and a test asserts Program 1 never emits one.
 */
@Serializable
enum class MissingDataReason {
    /** The person did not record it that day. */
    NOT_RECORDED,

    /** The date precedes the first record of that variable — not a skipped day. */
    BEFORE_FIRST_RECORD,

    /** Structural context derivation was switched off by the local kill switch. */
    EXTRACTION_DISABLED,

    /** Structural context derivation ran and did not produce rows. */
    EXTRACTION_FAILED,

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
     * Enumerates every absence between [firstRecordDate] and [throughDate]
     * inclusive.
     *
     * @param firstRecordDate the earliest local date on which anything was
     *   recorded, or null when nothing ever was — in which case the report
     *   is empty, because inventing absences for a person who has not
     *   started is not information.
     * @param measureDates the ISO local dates that do have a morning measure.
     * @param entryDatesWithoutContext ISO local dates carrying a Journal
     *   entry with no structural context rows.
     * @param contextExtractionEnabled the local kill switch's current state,
     *   which is what separates "switched off" from "ran and produced
     *   nothing".
     */
    fun report(
        firstRecordDate: LocalDate?,
        throughDate: LocalDate,
        measureDates: Set<String>,
        entryDatesWithoutContext: Set<String>,
        contextExtractionEnabled: Boolean,
    ): List<MissingDataRecord> {
        if (firstRecordDate == null || throughDate.isBefore(firstRecordDate)) return emptyList()

        val firstMeasureDate = measureDates.minOrNull()
        val measureGaps = generateSequence(firstRecordDate) { it.plusDays(1) }
            .takeWhile { !it.isAfter(throughDate) }
            .map { it.toString() }
            .filterNot { it in measureDates }
            .map { day ->
                val reason = if (firstMeasureDate == null || day < firstMeasureDate) {
                    MissingDataReason.BEFORE_FIRST_RECORD
                } else {
                    MissingDataReason.NOT_RECORDED
                }
                MissingDataRecord(day, VARIABLE_MORNING_MEASURE, reason)
            }

        val contextReason = if (contextExtractionEnabled) {
            MissingDataReason.EXTRACTION_FAILED
        } else {
            MissingDataReason.EXTRACTION_DISABLED
        }
        val contextGaps = entryDatesWithoutContext.map {
            MissingDataRecord(it, VARIABLE_JOURNAL_CONTEXT, contextReason)
        }

        return (measureGaps.toList() + contextGaps)
            .distinct()
            .sortedWith(compareBy({ it.localDate }, { it.variable }))
    }
}
