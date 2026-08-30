package org.mindanchor.intelligence

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PassiveEstimatorTest {
    private val start = LocalDate.parse("2026-01-01")

    @Test fun `nonfinal and otherwise ineligible inputs produce no observation`() {
        val nonFinal = PassiveDataStatus.entries.filterNot { it.canEstimate }
        assertEquals(4, nonFinal.size)

        val nonFinalObservations = nonFinal.map { status ->
            PassiveEstimator.observe(day(start, status), 1_000L, emptyList(), emptyList(), 42L)
        }
        val finalButIneligible = PassiveEstimator.observe(
            day(start.plusDays(60), features = mapOf(PassiveFeature.RESTING_HEART_RATE to 61.0)),
            1_000L,
            history(),
            emptyList(),
            42L,
        )

        (nonFinalObservations + finalButIneligible).forEach { observation ->
            assertEquals(PassiveObservationState.NO_OBSERVATION, observation.state)
            assertNull(observation.threshold)
            assertFalse(observation.crossed)
            assertTrue(observation.domains.isEmpty())
        }
        assertEquals(PassiveDataStatus.INSUFFICIENT_DATA, finalButIneligible.dataStatus)
    }

    @Test fun `fifty nine days cannot activate a baseline or threshold`() {
        val observation = PassiveEstimator.observe(
            day(start.plusDays(59)),
            2_000L,
            history(59),
            emptyList(),
            42L,
        )

        assertEquals(PassiveDataStatus.BASELINE_BUILDING, observation.dataStatus)
        assertEquals(PassiveObservationState.NO_OBSERVATION, observation.state)
        assertEquals(59, observation.baselineDays)
        assertNull(observation.threshold)
    }

    @Test fun `first crossing is transient once baseline and calibration are available`() {
        val observation = crossingObservation()

        assertEquals(PassiveObservationState.TRANSIENT_DEVIATION, observation.state)
        assertTrue(observation.crossed)
        assertNotNull(observation.threshold)
        assertEquals(
            "Recorded physiology and sleep signals differed from your calibrated personal range. " +
                "This describes recorded data only.",
            observation.explanation,
        )
    }

    @Test fun `second crossing among three eligible observations is sustained`() {
        val targetDay = start.plusDays(60)
        val prior = listOf(
            priorObservation(targetDay.minusDays(2), crossed = true),
            priorObservation(targetDay.minusDays(1), crossed = false),
        )

        val observation = crossingObservation(prior)

        assertEquals(PassiveObservationState.SUSTAINED_DEVIATION, observation.state)
        assertTrue(observation.crossed)
        assertNotNull(observation.threshold)
    }

    @Test fun `fixed explanations are observation only and exclude banned terms`() {
        val reference = history()
        val targetDay = start.plusDays(60)
        val noObservations = PassiveDataStatus.entries.filterNot { it.canEstimate }.map { status ->
            PassiveEstimator.observe(day(targetDay, status), 3_000L, reference, emptyList(), 42L)
        }
        val baselineBuilding = PassiveEstimator.observe(
            day(start.plusDays(59)), 3_000L, history(59), emptyList(), 42L,
        )
        val insufficient = PassiveEstimator.observe(
            day(targetDay, features = mapOf(PassiveFeature.RESTING_HEART_RATE to 61.0)),
            3_000L,
            reference,
            emptyList(),
            42L,
        )
        val inRange = PassiveEstimator.observe(
            day(targetDay), 3_000L, reference, emptyList(), 42L,
        )
        val transient = crossingObservation()
        val sustained = crossingObservation(listOf(priorObservation(targetDay.minusDays(1), crossed = true)))
        val observations = noObservations + baselineBuilding + insufficient + inRange + transient + sustained

        assertEquals("Available signals were within your calibrated personal range.", inRange.explanation)
        assertNotNull(inRange.threshold)
        observations.filter { it.state == PassiveObservationState.NO_OBSERVATION }
            .forEach { assertNull(it.threshold) }

        val banned = listOf("anxiety", "depression", "bpd", "panic", "anger", "diagnosis", "illness", "disorder")
        observations.forEach { observation ->
            assertTrue(observation.explanation.isNotBlank())
            val explanation = observation.explanation.lowercase()
            banned.forEach { term ->
                assertFalse("${observation.dataStatus}: $term in $explanation", explanation.contains(term))
            }
        }
    }

    private fun crossingObservation(prior: List<PassiveObservation> = emptyList()): PassiveObservation =
        PassiveEstimator.observe(
            day(
                start.plusDays(60),
                features = mapOf(
                    PassiveFeature.RESTING_HEART_RATE to 80.0,
                    PassiveFeature.SLEEP_MINUTES to 300.0,
                ),
            ),
            4_000L,
            history(),
            prior,
            42L,
        )

    private fun history(count: Int = 60): List<PassiveDay> = List(count) { index ->
        val even = index % 2 == 0
        day(
            start.plusDays(index.toLong()),
            features = mapOf(
                PassiveFeature.RESTING_HEART_RATE to if (even) 60.0 else 62.0,
                PassiveFeature.SLEEP_MINUTES to if (even) 420.0 else 440.0,
            ),
        )
    }

    private fun day(
        date: LocalDate,
        status: PassiveDataStatus = PassiveDataStatus.AVAILABLE_FINAL,
        features: Map<PassiveFeature, Double> = mapOf(
            PassiveFeature.RESTING_HEART_RATE to 60.0,
            PassiveFeature.SLEEP_MINUTES to 420.0,
        ),
    ) = PassiveDay(date, status, features, baselineSegment = "device-a")

    private fun priorObservation(date: LocalDate, crossed: Boolean) = PassiveObservation(
        day = date,
        asOfTime = 500L,
        dataStatus = PassiveDataStatus.AVAILABLE_FINAL,
        state = if (crossed) {
            PassiveObservationState.TRANSIENT_DEVIATION
        } else {
            PassiveObservationState.WITHIN_PERSON_RANGE
        },
        threshold = 1.0,
        crossed = crossed,
        baselineDays = 60,
        baselineSegment = "device-a",
        domains = emptyList(),
        explanation = "prior observation",
    )
}
