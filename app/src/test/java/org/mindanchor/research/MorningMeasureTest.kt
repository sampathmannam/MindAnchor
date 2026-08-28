package org.mindanchor.research

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MorningMeasureTest {
    @Test
    fun `all five values must be in one to five`() {
        assertThrows(IllegalArgumentException::class.java) {
            MorningMeasure.create(
                localDate = LocalDate.parse("2026-08-28"),
                now = 1_000L,
                mood = 0,
                anxiety = 3,
                angerUrge = 3,
                energyFunction = 3,
                sleepQuality = 3,
                sourceDeviceId = "d1",
            )
        }
    }

    @Test
    fun `instrument version is frozen`() {
        assertEquals("morning-v1", MorningMeasure.INSTRUMENT_VERSION)
    }

    @Test
    fun `mood upper bound is enforced`() {
        assertThrows(IllegalArgumentException::class.java) {
            MorningMeasure.create(
                localDate = LocalDate.parse("2026-08-28"),
                now = 1_000L,
                mood = 6,
                anxiety = 3,
                angerUrge = 3,
                energyFunction = 3,
                sleepQuality = 3,
                sourceDeviceId = "d1",
            )
        }
    }

    @Test
    fun `anxiety out of range is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            MorningMeasure.create(
                localDate = LocalDate.parse("2026-08-28"),
                now = 1_000L,
                mood = 3,
                anxiety = 0,
                angerUrge = 3,
                energyFunction = 3,
                sleepQuality = 3,
                sourceDeviceId = "d1",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            MorningMeasure.create(
                localDate = LocalDate.parse("2026-08-28"),
                now = 1_000L,
                mood = 3,
                anxiety = 6,
                angerUrge = 3,
                energyFunction = 3,
                sleepQuality = 3,
                sourceDeviceId = "d1",
            )
        }
    }

    @Test
    fun `angerUrge out of range is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            MorningMeasure.create(
                localDate = LocalDate.parse("2026-08-28"),
                now = 1_000L,
                mood = 3,
                anxiety = 3,
                angerUrge = 0,
                energyFunction = 3,
                sleepQuality = 3,
                sourceDeviceId = "d1",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            MorningMeasure.create(
                localDate = LocalDate.parse("2026-08-28"),
                now = 1_000L,
                mood = 3,
                anxiety = 3,
                angerUrge = 6,
                energyFunction = 3,
                sleepQuality = 3,
                sourceDeviceId = "d1",
            )
        }
    }

    @Test
    fun `energyFunction out of range is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            MorningMeasure.create(
                localDate = LocalDate.parse("2026-08-28"),
                now = 1_000L,
                mood = 3,
                anxiety = 3,
                angerUrge = 3,
                energyFunction = 0,
                sleepQuality = 3,
                sourceDeviceId = "d1",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            MorningMeasure.create(
                localDate = LocalDate.parse("2026-08-28"),
                now = 1_000L,
                mood = 3,
                anxiety = 3,
                angerUrge = 3,
                energyFunction = 6,
                sleepQuality = 3,
                sourceDeviceId = "d1",
            )
        }
    }

    @Test
    fun `sleepQuality out of range is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            MorningMeasure.create(
                localDate = LocalDate.parse("2026-08-28"),
                now = 1_000L,
                mood = 3,
                anxiety = 3,
                angerUrge = 3,
                energyFunction = 3,
                sleepQuality = 0,
                sourceDeviceId = "d1",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            MorningMeasure.create(
                localDate = LocalDate.parse("2026-08-28"),
                now = 1_000L,
                mood = 3,
                anxiety = 3,
                angerUrge = 3,
                energyFunction = 3,
                sleepQuality = 6,
                sourceDeviceId = "d1",
            )
        }
    }

    @Test
    fun `all values in range succeeds`() {
        val measure = MorningMeasure.create(
            localDate = LocalDate.parse("2026-08-28"),
            now = 1_000L,
            mood = 1,
            anxiety = 2,
            angerUrge = 3,
            energyFunction = 4,
            sleepQuality = 5,
            sourceDeviceId = "d1",
        )
        assertEquals("2026-08-28", measure.localDate)
        assertEquals(1_000L, measure.createdAt)
        assertEquals(1_000L, measure.updatedAt)
        assertEquals(1, measure.mood)
        assertEquals(2, measure.anxiety)
        assertEquals(3, measure.angerUrge)
        assertEquals(4, measure.energyFunction)
        assertEquals(5, measure.sleepQuality)
        assertEquals("morning-v1", measure.instrumentVersion)
        assertEquals("d1", measure.sourceDeviceId)
    }
}
