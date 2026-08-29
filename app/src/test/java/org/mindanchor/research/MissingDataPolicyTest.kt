package org.mindanchor.research

import java.lang.reflect.Modifier
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Program 1 Task 5 — the missing-data policy is one sentence long and this
 * is the test of it: nothing is imputed, interpolated, carried forward, or
 * filled in, and every absence is listed with a reason.
 *
 * The point is a series that cannot look complete when it is not. A gap
 * that quietly disappears into an interpolated value is the difference
 * between a personal record and a misleading one.
 */
class MissingDataPolicyTest {

    private fun date(day: Int) = LocalDate.of(2026, 8, day)

    @Test
    fun `the policy states its version and its rule`() {
        assertEquals("missing-data-v1", MissingDataPolicy.VERSION)
        assertTrue(MissingDataPolicy.STATEMENT.contains("Nothing is imputed"))
    }

    @Test
    fun `a record carries a reason and never a value`() {
        assertEquals(
            listOf("localDate", "variable", "reason"),
            MissingDataRecord::class.java.declaredFields
                .filter { !it.isSynthetic && !Modifier.isStatic(it.modifiers) }
                .map { it.name },
        )
    }

    @Test
    fun `nothing recorded means nothing reported`() {
        assertEquals(
            emptyList<MissingDataRecord>(),
            MissingDataPolicy.report(
                firstRecordDate = null,
                throughDate = date(10),
                measureDates = emptySet(),
                entryDatesWithoutContext = emptySet(),
                contextExtractionEnabled = true,
            ),
        )
    }

    @Test
    fun `every day without a measure is listed once`() {
        val report = MissingDataPolicy.report(
            firstRecordDate = date(1),
            throughDate = date(3),
            measureDates = setOf("2026-08-02"),
            entryDatesWithoutContext = emptySet(),
            contextExtractionEnabled = true,
        )
        assertEquals(
            listOf(
                MissingDataRecord("2026-08-01", "morning_measure", MissingDataReason.BEFORE_FIRST_RECORD),
                MissingDataRecord("2026-08-03", "morning_measure", MissingDataReason.NOT_RECORDED),
            ),
            report,
        )
    }

    @Test
    fun `days before the first measure are not counted as skipped`() {
        val report = MissingDataPolicy.report(
            firstRecordDate = date(1),
            throughDate = date(5),
            measureDates = setOf("2026-08-04"),
            entryDatesWithoutContext = emptySet(),
            contextExtractionEnabled = true,
        )
        assertEquals(
            listOf(
                MissingDataReason.BEFORE_FIRST_RECORD,
                MissingDataReason.BEFORE_FIRST_RECORD,
                MissingDataReason.BEFORE_FIRST_RECORD,
                MissingDataReason.NOT_RECORDED,
            ),
            report.map { it.reason },
        )
    }

    @Test
    fun `an entry with no context says whether extraction was off or failed`() {
        val disabled = MissingDataPolicy.report(
            firstRecordDate = date(1),
            throughDate = date(1),
            measureDates = setOf("2026-08-01"),
            entryDatesWithoutContext = setOf("2026-08-01"),
            contextExtractionEnabled = false,
        )
        assertEquals(
            listOf(MissingDataRecord("2026-08-01", "journal_context", MissingDataReason.EXTRACTION_DISABLED)),
            disabled,
        )

        val failed = MissingDataPolicy.report(
            firstRecordDate = date(1),
            throughDate = date(1),
            measureDates = setOf("2026-08-01"),
            entryDatesWithoutContext = setOf("2026-08-01"),
            contextExtractionEnabled = true,
        )
        assertEquals(
            listOf(MissingDataRecord("2026-08-01", "journal_context", MissingDataReason.EXTRACTION_FAILED)),
            failed,
        )
    }

    @Test
    fun `the report is sorted and free of duplicates`() {
        val report = MissingDataPolicy.report(
            firstRecordDate = date(1),
            throughDate = date(4),
            measureDates = setOf("2026-08-01"),
            entryDatesWithoutContext = setOf("2026-08-03", "2026-08-02"),
            contextExtractionEnabled = true,
        )
        assertEquals(report.sortedWith(compareBy({ it.localDate }, { it.variable })), report)
        assertEquals(report.distinct(), report)
    }

    @Test
    fun `a complete record produces an empty report`() {
        assertEquals(
            emptyList<MissingDataRecord>(),
            MissingDataPolicy.report(
                firstRecordDate = date(1),
                throughDate = date(2),
                measureDates = setOf("2026-08-01", "2026-08-02"),
                entryDatesWithoutContext = emptySet(),
                contextExtractionEnabled = true,
            ),
        )
    }

    @Test
    fun `a through date before the first record reports nothing`() {
        assertEquals(
            emptyList<MissingDataRecord>(),
            MissingDataPolicy.report(
                firstRecordDate = date(5),
                throughDate = date(1),
                measureDates = emptySet(),
                entryDatesWithoutContext = emptySet(),
                contextExtractionEnabled = true,
            ),
        )
    }

    @Test
    fun `the gap reasons Program 2 will need exist but are never produced here`() {
        val everyReasonProduced = MissingDataPolicy.report(
            firstRecordDate = date(1),
            throughDate = date(5),
            measureDates = setOf("2026-08-03"),
            entryDatesWithoutContext = setOf("2026-08-02"),
            contextExtractionEnabled = true,
        ).map { it.reason }.toSet()
        assertTrue(MissingDataReason.SENSOR_GAP !in everyReasonProduced)
        assertTrue(MissingDataReason.DEVICE_CHANGE_GAP !in everyReasonProduced)
    }
}
