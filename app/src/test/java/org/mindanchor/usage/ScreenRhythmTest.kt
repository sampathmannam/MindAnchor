package org.mindanchor.usage

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The screen-rhythm arithmetic, leaning on the cases that lie: spans
 * crossing midnight, days from before the log's reach, and the
 * difference between "zero minutes" and "nobody was watching".
 */
class ScreenRhythmTest {

    private val zone = ZoneId.of("UTC")
    private val monday = LocalDate.of(2026, 8, 3)
    private val tuesday = monday.plusDays(1)
    private val wednesday = monday.plusDays(2)

    private fun at(day: LocalDate, hour: Int, minute: Int): Long =
        day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun on(day: LocalDate, hour: Int, minute: Int) =
        ScreenEvent(ScreenEventKind.INTERACTIVE, at(day, hour, minute))

    private fun off(day: LocalDate, hour: Int, minute: Int) =
        ScreenEvent(ScreenEventKind.NON_INTERACTIVE, at(day, hour, minute))

    private fun unlock(day: LocalDate, hour: Int, minute: Int) =
        ScreenEvent(ScreenEventKind.UNLOCKED, at(day, hour, minute))

    /** An anchor event on the day before, so later days count as observed. */
    private fun anchored(vararg events: ScreenEvent): List<ScreenEvent> =
        listOf(off(monday, 12, 0)) + events

    @Test
    fun `an evening running past midnight charges each day its own share`() {
        val events = anchored(
            on(tuesday, 23, 30),
            off(wednesday, 0, 45),
        )
        val rhythm = ScreenRhythm.days(events, listOf(tuesday, wednesday), zone, at(wednesday, 12, 0))
        assertEquals(30, rhythm.getValue(tuesday).screenMinutes)
        assertEquals(45, rhythm.getValue(wednesday).screenMinutes)
    }

    @Test
    fun `the first unlock is the first after three in the morning, not the last of the night`() {
        val events = anchored(
            unlock(tuesday, 0, 40),
            unlock(tuesday, 2, 59),
            unlock(tuesday, 7, 22),
            unlock(tuesday, 9, 0),
            off(tuesday, 9, 30),
        )
        val rhythm = ScreenRhythm.days(events, listOf(tuesday), zone, at(tuesday, 12, 0))
        assertEquals(7 * 60 + 22, rhythm.getValue(tuesday).firstUnlockMinute)
    }

    @Test
    fun `a day the log never saw is null, never a fabricated zero`() {
        // Events begin Tuesday. Monday has no events — not because the
        // phone was untouched but because the system had already
        // forgotten it — and Tuesday itself may only be half a day.
        val events = listOf(
            on(tuesday, 9, 0),
            off(tuesday, 9, 40),
            on(wednesday, 8, 0),
            off(wednesday, 8, 20),
        )
        val rhythm = ScreenRhythm.days(
            events, listOf(monday, tuesday, wednesday), zone, at(wednesday, 12, 0),
        )
        assertNull(rhythm.getValue(monday).screenMinutes)
        assertNull(rhythm.getValue(monday).firstUnlockMinute)
        assertNull(rhythm.getValue(tuesday).screenMinutes)
        assertEquals(20, rhythm.getValue(wednesday).screenMinutes)
    }

    @Test
    fun `an observed day the phone truly slept through is an honest zero`() {
        val events = anchored(
            on(wednesday, 8, 0),
            off(wednesday, 8, 10),
        )
        val rhythm = ScreenRhythm.days(events, listOf(tuesday, wednesday), zone, at(wednesday, 12, 0))
        assertEquals(0, rhythm.getValue(tuesday).screenMinutes)
        assertNull(rhythm.getValue(tuesday).firstUnlockMinute)
    }

    @Test
    fun `a span still open when the log ends is closed by now, not lost`() {
        val events = anchored(on(tuesday, 8, 0))
        val rhythm = ScreenRhythm.days(events, listOf(tuesday), zone, at(tuesday, 8, 50))
        assertEquals(50, rhythm.getValue(tuesday).screenMinutes)
    }

    @Test
    fun `an unlock stands in for a dropped interactive event`() {
        val events = anchored(
            unlock(tuesday, 7, 0),
            off(tuesday, 7, 30),
        )
        val rhythm = ScreenRhythm.days(events, listOf(tuesday), zone, at(tuesday, 12, 0))
        assertEquals(30, rhythm.getValue(tuesday).screenMinutes)
    }

    @Test
    fun `event order on the way in does not matter`() {
        val ordered = anchored(
            on(tuesday, 10, 0),
            off(tuesday, 10, 20),
            on(tuesday, 14, 0),
            off(tuesday, 14, 15),
        )
        val shuffled = listOf(ordered[3], ordered[0], ordered[4], ordered[2], ordered[1])
        assertEquals(
            ScreenRhythm.days(ordered, listOf(tuesday), zone, at(tuesday, 20, 0)),
            ScreenRhythm.days(shuffled, listOf(tuesday), zone, at(tuesday, 20, 0)),
        )
        assertEquals(
            35,
            ScreenRhythm.days(shuffled, listOf(tuesday), zone, at(tuesday, 20, 0))
                .getValue(tuesday).screenMinutes,
        )
    }

    @Test
    fun `no events at all means no answers at all`() {
        val rhythm = ScreenRhythm.days(emptyList(), listOf(tuesday), zone, at(tuesday, 12, 0))
        assertNull(rhythm.getValue(tuesday).screenMinutes)
        assertNull(rhythm.getValue(tuesday).firstUnlockMinute)
    }

    @Test
    fun `doubled events do not double the minutes`() {
        // The log can repeat itself; a second INTERACTIVE while the span
        // is open must not restart or extend it.
        val events = anchored(
            on(tuesday, 9, 0),
            on(tuesday, 9, 5),
            unlock(tuesday, 9, 6),
            off(tuesday, 9, 30),
            off(tuesday, 9, 31),
        )
        val rhythm = ScreenRhythm.days(events, listOf(tuesday), zone, at(tuesday, 12, 0))
        assertEquals(30, rhythm.getValue(tuesday).screenMinutes)
    }
}
