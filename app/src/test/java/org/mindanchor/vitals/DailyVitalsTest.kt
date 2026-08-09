package org.mindanchor.vitals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class DailyVitalsTest {

    private val day = LocalDate.of(2024, 1, 1)

    /** Epoch millis for [hour]:[minute] on the fixed test day, UTC — so a 0 zone offset is "local". */
    private fun at(hour: Int, minute: Int = 0): Long =
        day.atTime(hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun `mean and min heart rate summarise the day's samples`() {
        val samples = listOf(at(22) to 60.0, at(23) to 80.0, at(23, 30) to 70.0)
        assertEquals(70.0, DailyVitalsReducer.meanHeartRate(samples)!!, 0.0001)
        assertEquals(60.0, DailyVitalsReducer.minHeartRate(samples))
    }

    @Test
    fun `resting heart rate and hrv are the mean of the day's readings`() {
        assertEquals(
            55.0,
            DailyVitalsReducer.restingHeartRate(listOf(at(6) to 50.0, at(7) to 60.0))!!,
            0.0001,
        )
        assertEquals(
            42.5,
            DailyVitalsReducer.hrvRmssd(listOf(at(6) to 40.0, at(7) to 45.0))!!,
            0.0001,
        )
    }

    @Test
    fun `an absent data type reduces to null, not zero`() {
        // Zero steps and no step data are different facts. A watch that
        // never writes a type — a COROS Pacer 3 and sleep, say — must not
        // be indistinguishable from a watch that recorded zero of it.
        assertNull(DailyVitalsReducer.meanHeartRate(emptyList()))
        assertNull(DailyVitalsReducer.minHeartRate(emptyList()))
        assertNull(DailyVitalsReducer.restingHeartRate(emptyList()))
        assertNull(DailyVitalsReducer.hrvRmssd(emptyList()))
        assertNull(DailyVitalsReducer.sleepMinutes(emptyList()))
        assertNull(DailyVitalsReducer.sleepOnset(emptyList(), zoneOffsetMinutes = 0))
        assertNull(DailyVitalsReducer.steps(emptyList()))
        assertNull(DailyVitalsReducer.activeMinutes(emptyList()))
    }

    @Test
    fun `sleep minutes sums every session's duration`() {
        val sessions = listOf(at(23) to at(23, 30), at(1) to at(6))
        assertEquals(330, DailyVitalsReducer.sleepMinutes(sessions))
    }

    @Test
    fun `steps sum across records, active minutes sum exercise durations`() {
        assertEquals(4_500L, DailyVitalsReducer.steps(listOf(1_200L, 1_800L, 1_500L)))
        assertEquals(
            45,
            DailyVitalsReducer.activeMinutes(listOf(at(7) to at(7, 30), at(18) to at(18, 15))),
        )
    }

    @Test
    fun `sleep onset past midnight counts as later, not earlier`() {
        // The whole point of the 18:00 origin, mirroring DeviationTest: in
        // minute-of-day terms 1am is smaller than 11pm, so a naive
        // comparison would call the night that ran latest the earliest one.
        val late = DailyVitalsReducer.sleepOnset(listOf(at(1) to at(6)), zoneOffsetMinutes = 0)!!
        val early = DailyVitalsReducer.sleepOnset(
            listOf(at(23) to at(23, 30)),
            zoneOffsetMinutes = 0,
        )!!
        assertTrue(late > early)

        assertEquals(0, DailyVitalsReducer.sleepOnset(listOf(at(18) to at(19)), 0))
        assertEquals(240, DailyVitalsReducer.sleepOnset(listOf(at(22) to at(22, 30)), 0))
        assertEquals(420, DailyVitalsReducer.sleepOnset(listOf(at(1) to at(2)), 0))
    }

    @Test
    fun `sleep onset follows a non-zero timezone offset`() {
        // A session starting at 22:00 UTC is 23:00 local at UTC+1; onset
        // should be computed against local time, not the raw UTC instant.
        val utcOnset = DailyVitalsReducer.sleepOnset(listOf(at(22) to at(22, 30)), 0)
        val shiftedOnset = DailyVitalsReducer.sleepOnset(listOf(at(22) to at(22, 30)), 60)
        assertEquals(240, utcOnset)
        assertEquals(300, shiftedOnset)
    }

    @Test
    fun `sleep onset uses the earliest session, since a later nap is not bedtime`() {
        val nap = at(14) to at(14, 30)
        val bedtime = at(22, 30) to at(23)
        assertEquals(
            DailyVitalsReducer.sleepOnset(listOf(nap), 0),
            DailyVitalsReducer.sleepOnset(listOf(nap, bedtime), 0),
        )
    }

    @Test
    fun `completeness is zero when the watch had nothing to say`() {
        val empty = DailyVitals.empty(day)
        assertEquals(0, empty.fieldsPresent)
        assertEquals(0.0, empty.completeness, 0.0001)
    }

    @Test
    fun `completeness counts each present field once, out of nine`() {
        // Mirrors a COROS Pacer 3 kind of day: heart rate and sleep
        // present, nothing about steps or activity or mindfulness.
        // v0.20.4 added a ninth field (mindfulnessMinutes) for the
        // wellness surface — see [DailyVitals.FIELD_COUNT].
        val partial = DailyVitals(
            date = day,
            restingHeartRate = 55.0,
            meanHeartRate = 70.0,
            minHeartRate = null,
            hrvRmssd = null,
            sleepMinutes = 420,
            sleepOnset = 240,
            steps = null,
            activeMinutes = null,
        )
        assertEquals(4, partial.fieldsPresent)
        assertEquals(4.0 / 9.0, partial.completeness, 0.0001)
    }

    @Test
    fun `a fully populated day reaches completeness of one`() {
        val full = DailyVitals(
            date = day,
            restingHeartRate = 55.0,
            meanHeartRate = 70.0,
            minHeartRate = 50.0,
            hrvRmssd = 42.0,
            sleepMinutes = 420,
            sleepOnset = 240,
            steps = 8_000L,
            activeMinutes = 30,
            // v0.20.4: mindfulnessMinutes is the ninth
            // measured field — see [DailyVitals.FIELD_COUNT].
            // A "fully populated" day in this test
            // means all nine are present, so the
            // mindfulness field has to be set too.
            mindfulnessMinutes = 12,
        )
        assertEquals(DailyVitals.FIELD_COUNT, full.fieldsPresent)
        assertEquals(1.0, full.completeness, 0.0001)
    }

    @Test
    fun `date does not count as a measured field`() {
        val dateOnly = DailyVitals.empty(day)
        assertEquals(day, dateOnly.date)
        assertEquals(0, dateOnly.fieldsPresent)
    }
}
