package org.mindanchor.research

import java.lang.reflect.Modifier
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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

    private fun measure(day: Int, reason: MissingDataReason) =
        MissingDataRecord(date(day).toString(), MissingDataPolicy.VARIABLE_MORNING_MEASURE, reason)

    private fun context(day: Int) =
        MissingDataRecord(
            date(day).toString(),
            MissingDataPolicy.VARIABLE_JOURNAL_CONTEXT,
            MissingDataReason.CONTEXT_NOT_DERIVED,
        )

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
                allMeasureDates = emptySet(),
                entryDatesWithoutContext = emptySet(),
            ),
        )
    }

    @Test
    fun `every day without a measure is listed once`() {
        assertEquals(
            listOf(
                measure(1, MissingDataReason.BEFORE_FIRST_RECORD),
                measure(3, MissingDataReason.NOT_RECORDED),
            ),
            MissingDataPolicy.report(
                firstRecordDate = date(1),
                throughDate = date(3),
                allMeasureDates = setOf(date(2)),
                entryDatesWithoutContext = emptySet(),
            ),
        )
    }

    @Test
    fun `days before the first measure are not counted as skipped`() {
        assertEquals(
            listOf(
                measure(1, MissingDataReason.BEFORE_FIRST_RECORD),
                measure(2, MissingDataReason.BEFORE_FIRST_RECORD),
                measure(3, MissingDataReason.BEFORE_FIRST_RECORD),
                measure(5, MissingDataReason.NOT_RECORDED),
            ),
            MissingDataPolicy.report(
                firstRecordDate = date(1),
                throughDate = date(5),
                allMeasureDates = setOf(date(4)),
                entryDatesWithoutContext = emptySet(),
            ),
        )
    }

    @Test
    fun `an entry with no context says only what is known`() {
        assertEquals(
            listOf(context(1)),
            MissingDataPolicy.report(
                firstRecordDate = date(1),
                throughDate = date(1),
                allMeasureDates = setOf(date(1)),
                entryDatesWithoutContext = setOf(date(1)),
            ),
        )
    }

    @Test
    fun `the report is sorted by date and variable`() {
        assertEquals(
            listOf(
                measure(1, MissingDataReason.BEFORE_FIRST_RECORD),
                context(2),
                context(3),
                measure(3, MissingDataReason.NOT_RECORDED),
                measure(4, MissingDataReason.NOT_RECORDED),
            ),
            MissingDataPolicy.report(
                firstRecordDate = date(1),
                throughDate = date(4),
                allMeasureDates = setOf(date(2), date(5)),
                entryDatesWithoutContext = setOf(date(3), date(2)),
            ),
        )
    }

    @Test
    fun `a context gap outside the window is not reported`() {
        assertEquals(
            listOf(context(2)),
            MissingDataPolicy.report(
                firstRecordDate = date(2),
                throughDate = date(2),
                allMeasureDates = setOf(date(2)),
                entryDatesWithoutContext = setOf(date(1), date(2), date(3)),
            ),
        )
    }

    @Test
    fun `a complete record produces an empty report`() {
        assertEquals(
            emptyList<MissingDataRecord>(),
            MissingDataPolicy.report(
                firstRecordDate = date(1),
                throughDate = date(2),
                allMeasureDates = setOf(date(1), date(2)),
                entryDatesWithoutContext = emptySet(),
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
                allMeasureDates = emptySet(),
                entryDatesWithoutContext = emptySet(),
            ),
        )
    }

    @Test
    fun `an implausible window fails loudly instead of hanging`() {
        val thrown = assertThrows(IllegalArgumentException::class.java) {
            MissingDataPolicy.report(
                firstRecordDate = date(1),
                throughDate = LocalDate.of(9999, 12, 31),
                allMeasureDates = emptySet(),
                entryDatesWithoutContext = emptySet(),
            )
        }
        assertTrue(thrown.message.orEmpty().contains("check the clock"))
    }

    @Test
    fun `the boundary of the permitted window still reports`() {
        val through = date(1).plusDays(MissingDataPolicy.MAX_REPORT_DAYS)
        val report = MissingDataPolicy.report(
            firstRecordDate = date(1),
            throughDate = through,
            allMeasureDates = emptySet(),
            entryDatesWithoutContext = emptySet(),
        )
        assertEquals((MissingDataPolicy.MAX_REPORT_DAYS + 1).toInt(), report.size)
    }

    @Test
    fun `Program 1 can only ever produce three of the five reasons`() {
        val ranges = listOf(date(1) to date(1), date(1) to date(4), date(3) to date(1))
        val measureSets = listOf(emptySet(), setOf(date(1)), setOf(date(3)), setOf(date(1), date(2), date(3), date(4)))
        val contextSets = listOf(emptySet(), setOf(date(1)), setOf(date(2), date(4)), setOf(date(9)))

        val produced = ranges.flatMap { (from, through) ->
            measureSets.flatMap { measures ->
                contextSets.flatMap { contexts ->
                    MissingDataPolicy.report(from, through, measures, contexts).map { it.reason } +
                        MissingDataPolicy.report(null, through, measures, contexts).map { it.reason }
                }
            }
        }.toSet()

        assertEquals(
            setOf(
                MissingDataReason.NOT_RECORDED,
                MissingDataReason.BEFORE_FIRST_RECORD,
                MissingDataReason.CONTEXT_NOT_DERIVED,
            ),
            produced,
        )
        assertTrue(MissingDataReason.SENSOR_GAP !in produced)
        assertTrue(MissingDataReason.DEVICE_CHANGE_GAP !in produced)
    }
}
