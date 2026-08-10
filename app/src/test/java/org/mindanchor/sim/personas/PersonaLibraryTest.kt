package org.mindanchor.sim.personas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Smoke tests for the persona library.
 *
 * The persona library is the input to the simulation runner
 * (WP-4), so any failure here blocks WP-3 and WP-4 too. The
 * tests check the *shape* of the output, not the values:
 *
 *  - Every persona produces exactly 14 days.
 *  - The dates are contiguous, starting at the requested [start].
 *  - The same persona with the same seed produces the same
 *    schedule (determinism).
 *  - Different personas produce different schedules.
 *  - All five personas are present in the library.
 */
class PersonaLibraryTest {

    private val start: LocalDate = LocalDate.of(2026, 1, 5)
    private val seed: Long = 42L

    @Test
    fun `library contains all 5 personas`() {
        assertEquals(5, PersonaLibrary.all.size)
        val ids = PersonaLibrary.all.map { it.id }
        assertTrue(ids.contains("morning_lark_healthy"))
        assertTrue(ids.contains("night_owl_healthy"))
        assertTrue(ids.contains("shift_worker_rotating"))
        assertTrue(ids.contains("insomnia_anxious"))
        assertTrue(ids.contains("depression_low_motivation"))
    }

    @Test
    fun `each persona produces 14 contiguous days starting at the given start`() {
        PersonaLibrary.all.forEach { persona ->
            val schedule = persona.schedule(start, seed)
            assertEquals("$persona produced ${schedule.size} days, not 14", 14, schedule.size)
            for (i in 0 until 14) {
                val expected = start.plusDays(i.toLong())
                val actual = schedule[i].date
                assertEquals(
                    "$persona day $i: expected $expected got $actual",
                    expected,
                    actual,
                )
            }
        }
    }

    @Test
    fun `same persona and seed produces identical schedules`() {
        // Determinism is what makes the simulation output reproducible.
        val p = MorningLarkPersona()
        val a = p.schedule(start, seed)
        val b = p.schedule(start, seed)
        assertEquals(a, b)
    }

    @Test
    fun `different seeds produce different schedules`() {
        val p = MorningLarkPersona()
        val a = p.schedule(start, 1L)
        val b = p.schedule(start, 2L)
        assertTrue(
            "Same persona with different seeds should differ on at least one field",
            a.zip(b).any { (x, y) -> x != y },
        )
    }

    @Test
    fun `different personas produce different schedules`() {
        val a = MorningLarkPersona().schedule(start, seed)
        val b = NightOwlPersona().schedule(start, seed)
        val c = InsomniacPersona().schedule(start, seed)
        // Morning lark should have a meaningfully different mean sleep
        // minutes than the insomniac persona. (5+ hours difference.)
        val morningSleep = a.mapNotNull { it.sleepMinutes }.average()
        val insomniacSleep = c.mapNotNull { it.sleepMinutes }.average()
        assertTrue(
            "Morning lark mean sleep (${morningSleep}m) should be " +
                "at least 60 min more than insomniac (${insomniacSleep}m)",
            morningSleep - insomniacSleep > 60.0,
        )
        // Night owl and morning lark should differ on mean sleep onset.
        val morningOnset = a.mapNotNull { it.sleepOnset }.average()
        val nightOnset = b.mapNotNull { it.sleepOnset }.average()
        assertTrue(
            "Night owl mean sleep onset ($nightOnset) should be " +
                "later than morning lark ($morningOnset)",
            nightOnset > morningOnset,
        )
    }

    @Test
    fun `every persona has non-empty id, name and description`() {
        PersonaLibrary.all.forEach { persona ->
            assertTrue(
                "$persona.id is blank",
                persona.id.isNotBlank(),
            )
            assertTrue(
                "$persona.name is blank",
                persona.name.isNotBlank(),
            )
            assertTrue(
                "$persona.description is blank",
                persona.description.isNotBlank(),
            )
        }
    }

    @Test
    fun `byId returns the same instance as in the library list`() {
        val id = "morning_lark_healthy"
        val fromList = PersonaLibrary.all.first { it.id == id }
        val fromById = PersonaLibrary.byId(id)
        assertNotNull("PersonaLibrary.byId(\"$id\") returned null", fromById)
        assertEquals(fromList, fromById)
    }
}
