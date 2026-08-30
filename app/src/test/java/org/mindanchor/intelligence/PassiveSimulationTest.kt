package org.mindanchor.intelligence

import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PassiveSimulationTest {
    @Test fun `identical seeds produce byte-for-byte equal observation sequences`() {
        val repeated = evaluate(generateDays(PRIMARY_SEED))

        assertArrayEquals(observationBytes(unshiftedObservations), observationBytes(repeated))
    }

    @Test fun `no ineligible day emits a deviation`() {
        val target = generatedDays[EVALUATION_DAYS.first]
        val statuses = PassiveDataStatus.entries.filterNot { it.canEstimate }
        val ineligibleDays = statuses.map { target.copy(dataStatus = it) } + target.copy(
            excludedFeatures = target.features.keys,
        )

        ineligibleDays.forEach { day ->
            val observation = PassiveEstimator.observe(
                day = day,
                asOfTime = day.day.toEpochDay(),
                history = referenceDays,
                prior = emptyList(),
                seed = CALIBRATION_SEED,
            )
            assertEquals(PassiveObservationState.NO_OBSERVATION, observation.state)
            assertFalse(observation.crossed)
            assertNull(observation.threshold)
        }
    }

    @Test fun `baseline is absent before day sixty one`() {
        generatedDays.take(PassiveBaselineBuilder.MIN_DAYS).forEachIndexed { index, day ->
            val observation = PassiveEstimator.observe(
                day = day,
                asOfTime = day.day.toEpochDay(),
                history = generatedDays.take(index),
                prior = emptyList(),
                seed = CALIBRATION_SEED,
            )
            assertEquals(PassiveObservationState.NO_OBSERVATION, observation.state)
            assertNull(observation.threshold)
        }

        assertNotNull(unshiftedObservations.first().threshold)
    }

    @Test fun `unshifted multi-seed evaluation stays within the aggregate finite-sample allowance`() {
        val declaredBudgetPerSeed = EVALUATION_DAYS.count().toDouble() /
            BlockThresholdCalibrator.CALIBRATION_DAYS * BlockThresholdCalibrator.TARGET_EPISODES_PER_30
        val aggregateLimit = SEEDS.size * (declaredBudgetPerSeed + FINITE_SAMPLE_EPISODES)

        assertTrue(
            "false episodes $falseEpisodeCounts exceeded aggregate limit $aggregateLimit",
            falseEpisodeCounts.values.sum() <= aggregateLimit,
        )
    }

    @Test fun `shifted window has a seven-day unshifted zero-crossing control`() {
        assertEquals(0, unshiftedWindowCrossings)
    }

    @Test fun `seven-day two-scale shifts produce a crossing`() {
        val result = shiftResults.getValue(Shift(2.0, 7))

        assertTrue(result.crossings > 0)
        assertNotNull(result.firstCrossingDelay)
    }

    @Test fun `one-domain shift cannot produce an observation when all domains are available`() {
        assertEquals(0, oneDomainCrossings)
    }

    @Test fun `two corroborating shifted domains can produce an observation`() {
        assertTrue(twoDomainCrossings > 0)
    }

    @Test fun `simulation metrics are reproducible`() {
        val metrics = simulationMetrics
        val shifts = metrics.shifts.entries.joinToString(separator = ",") { (shift, result) ->
            "${shift.magnitude}x${shift.duration}=${result.crossings}/${result.firstCrossingDelay ?: "none"}"
        }
        val seedEpisodes = metrics.seedEpisodes.entries.joinToString(separator = ",") { (seed, episodes) ->
            "$seed:$episodes"
        }
        println(
            "PASSIVE_SIMULATION_METRICS seeds=$seedEpisodes primarySeed=${metrics.primarySeed} " +
                "calibrationSeed=${metrics.calibrationSeed} " +
                "injectionOffset=${metrics.injectionOffset} injectionDay=${metrics.injectionDay} " +
                "aggregateFalseEpisodes=${metrics.aggregateFalseEpisodes} " +
                "unshiftedWindowCrossings=${metrics.unshiftedWindowCrossings} " +
                "unshiftedWindowDomainCrossings=${metrics.unshiftedWindowDomainCrossings} " +
                "oneDomainCrossings=${metrics.oneDomainCrossings} " +
                "twoDomainCrossings=${metrics.twoDomainCrossings} shifts=$shifts",
        )

        assertEquals(EXPECTED_METRICS, metrics)
    }

    private data class Signal(
        val feature: PassiveFeature,
        val centre: Double,
        val unit: Double,
        val direction: Double,
    )

    private data class Shift(val magnitude: Double, val duration: Int)

    private data class ShiftResult(val crossings: Int, val firstCrossingDelay: Int?)

    private data class SimulationMetrics(
        val seedEpisodes: Map<Long, Int>,
        val primarySeed: Long,
        val calibrationSeed: Long,
        val injectionOffset: Int,
        val injectionDay: LocalDate,
        val aggregateFalseEpisodes: Int,
        val unshiftedWindowCrossings: Int,
        val unshiftedWindowDomainCrossings: Int,
        val oneDomainCrossings: Int,
        val twoDomainCrossings: Int,
        val shifts: Map<Shift, ShiftResult>,
    )

    companion object {
        private const val PRIMARY_SEED = 20_260_830L
        private const val CALIBRATION_SEED = 42L
        private const val DAYS = 240
        private const val REFERENCE_DAYS = 120
        private const val AR_COEFFICIENT = 0.65
        private const val FINITE_SAMPLE_EPISODES = 1.0
        private const val INJECTION_OFFSET = 0
        private const val SEGMENT = "simulation-device"
        private val START = LocalDate.parse("2026-01-01")
        private val SEEDS = listOf(1L, 7L, 42L, 2_026L, PRIMARY_SEED)
        private val MAGNITUDES = listOf(0.5, 1.0, 1.5, 2.0)
        private val DURATIONS = listOf(1, 2, 3, 7)
        private val SIGNALS = listOf(
            Signal(PassiveFeature.RESTING_HEART_RATE, centre = 62.0, unit = 3.0, direction = 1.0),
            Signal(PassiveFeature.SLEEP_MINUTES, centre = 430.0, unit = 35.0, direction = -1.0),
            Signal(PassiveFeature.STEPS, centre = 7_000.0, unit = 900.0, direction = -1.0),
            Signal(PassiveFeature.SCREEN_MINUTES, centre = 180.0, unit = 30.0, direction = 1.0),
        )
        private val EVALUATION_DAYS = REFERENCE_DAYS until DAYS
        private val generatedDays by lazy { generateDays(PRIMARY_SEED) }
        private val referenceDays by lazy { generatedDays.take(REFERENCE_DAYS) }
        private val unshiftedObservations by lazy { evaluate(generatedDays) }
        private val controlledDays by lazy {
            generatedDays.mapIndexed { index, day ->
                if (index !in REFERENCE_DAYS until REFERENCE_DAYS + DURATIONS.max()) return@mapIndexed day
                val baseline = requireNotNull(
                    PassiveBaselineBuilder.build(referenceDays, day.day, day.day.toEpochDay(), SEGMENT),
                )
                day.copy(features = day.features.mapValues { (feature, _) ->
                    baseline.features.getValue(feature).centre
                })
            }
        }
        private val controlledObservations by lazy { evaluate(controlledDays) }
        private val falseEpisodeCounts by lazy {
            SEEDS.associateWith { seed ->
                val observations = if (seed == PRIMARY_SEED) unshiftedObservations else evaluate(generateDays(seed))
                episodeCount(observations)
            }
        }
        private const val injectionOffset = INJECTION_OFFSET
        private val unshiftedWindowCrossings by lazy {
            controlledObservations.drop(injectionOffset).take(DURATIONS.max()).count { it.crossed }
        }
        private val unshiftedWindowDomainCrossings by lazy {
            controlledObservations.drop(injectionOffset).take(DURATIONS.max()).sumOf(::domainCrossings)
        }
        private val shiftResults by lazy {
            MAGNITUDES.flatMap { magnitude -> DURATIONS.map { Shift(magnitude, it) } }
                .associateWith { shift -> evaluateShift(shift, SIGNALS.map { it.feature }.toSet()) }
        }
        private val oneDomainCrossings by lazy {
            evaluateShift(
                shift = Shift(magnitude = 2.0, duration = 7),
                shiftedFeatures = setOf(PassiveFeature.RESTING_HEART_RATE),
            ).crossings
        }
        private val twoDomainCrossings by lazy {
            evaluateShift(
                shift = Shift(magnitude = 2.0, duration = 7),
                shiftedFeatures = setOf(PassiveFeature.RESTING_HEART_RATE, PassiveFeature.SLEEP_MINUTES),
            ).crossings
        }
        private val simulationMetrics by lazy {
            SimulationMetrics(
                seedEpisodes = falseEpisodeCounts,
                primarySeed = PRIMARY_SEED,
                calibrationSeed = CALIBRATION_SEED,
                injectionOffset = injectionOffset,
                injectionDay = generatedDays[REFERENCE_DAYS + injectionOffset].day,
                aggregateFalseEpisodes = falseEpisodeCounts.values.sum(),
                unshiftedWindowCrossings = unshiftedWindowCrossings,
                unshiftedWindowDomainCrossings = unshiftedWindowDomainCrossings,
                oneDomainCrossings = oneDomainCrossings,
                twoDomainCrossings = twoDomainCrossings,
                shifts = shiftResults,
            )
        }
        private val EXPECTED_METRICS = SimulationMetrics(
            seedEpisodes = mapOf(1L to 5, 7L to 8, 42L to 5, 2_026L to 3, PRIMARY_SEED to 4),
            primarySeed = PRIMARY_SEED,
            calibrationSeed = CALIBRATION_SEED,
            injectionOffset = 0,
            injectionDay = LocalDate.parse("2026-05-01"),
            aggregateFalseEpisodes = 25,
            unshiftedWindowCrossings = 0,
            unshiftedWindowDomainCrossings = 0,
            oneDomainCrossings = 0,
            twoDomainCrossings = 7,
            shifts = mapOf(
                Shift(0.5, 1) to ShiftResult(0, null),
                Shift(0.5, 2) to ShiftResult(0, null),
                Shift(0.5, 3) to ShiftResult(0, null),
                Shift(0.5, 7) to ShiftResult(0, null),
                Shift(1.0, 1) to ShiftResult(0, null),
                Shift(1.0, 2) to ShiftResult(0, null),
                Shift(1.0, 3) to ShiftResult(0, null),
                Shift(1.0, 7) to ShiftResult(0, null),
                Shift(1.5, 1) to ShiftResult(0, null),
                Shift(1.5, 2) to ShiftResult(0, null),
                Shift(1.5, 3) to ShiftResult(0, null),
                Shift(1.5, 7) to ShiftResult(0, null),
                Shift(2.0, 1) to ShiftResult(1, 0),
                Shift(2.0, 2) to ShiftResult(2, 0),
                Shift(2.0, 3) to ShiftResult(3, 0),
                Shift(2.0, 7) to ShiftResult(7, 0),
            ),
        )

        private fun generateDays(seed: Long): List<PassiveDay> {
            val random = Random(seed)
            val residuals = DoubleArray(SIGNALS.size)
            return List(DAYS) { index ->
                val features = SIGNALS.mapIndexed { signalIndex, signal ->
                    residuals[signalIndex] = AR_COEFFICIENT * residuals[signalIndex] + random.nextGaussian()
                    signal.feature to signal.centre + signal.unit * residuals[signalIndex]
                }.toMap()
                PassiveDay(
                    day = START.plusDays(index.toLong()),
                    dataStatus = PassiveDataStatus.AVAILABLE_FINAL,
                    features = features,
                    baselineSegment = SEGMENT,
                    sourceUpdatedTime = START.plusDays(index.toLong()).toEpochDay(),
                    ingestedAt = START.plusDays(index.toLong()).toEpochDay(),
                )
            }
        }

        private fun evaluate(days: List<PassiveDay>): List<PassiveObservation> {
            val history = days.take(REFERENCE_DAYS)
            val prior = mutableListOf<PassiveObservation>()
            return days.drop(REFERENCE_DAYS).map { day ->
                PassiveEstimator.observe(
                    day = day,
                    asOfTime = day.day.toEpochDay(),
                    history = history,
                    prior = prior,
                    seed = CALIBRATION_SEED,
                ).also { prior += it }
            }
        }

        private fun evaluateShift(
            shift: Shift,
            shiftedFeatures: Set<PassiveFeature>,
            availableFeatures: Set<PassiveFeature> = SIGNALS.map { it.feature }.toSet(),
        ): ShiftResult {
            val injectionStart = REFERENCE_DAYS + injectionOffset
            val shifted = controlledDays.mapIndexed { index, day ->
                if (index !in injectionStart until injectionStart + shift.duration) return@mapIndexed day
                val baseline = requireNotNull(
                    PassiveBaselineBuilder.build(referenceDays, day.day, day.day.toEpochDay(), SEGMENT),
                )
                val features = day.features.filterKeys { it in availableFeatures }.mapValues { (feature, value) ->
                    if (feature !in shiftedFeatures) return@mapValues value
                    val signal = SIGNALS.single { it.feature == feature }
                    value + signal.direction * shift.magnitude * baseline.features.getValue(feature).scale
                }
                day.copy(features = features)
            }
            val prior = controlledObservations.take(injectionOffset).toMutableList()
            val observations = shifted.subList(injectionStart, injectionStart + shift.duration).map { day ->
                PassiveEstimator.observe(
                    day = day,
                    asOfTime = day.day.toEpochDay(),
                    history = referenceDays,
                    prior = prior,
                    seed = CALIBRATION_SEED,
                ).also { prior += it }
            }
            val firstCrossing = observations.indexOfFirst { it.crossed }.takeIf { it >= 0 }
            return ShiftResult(observations.count { it.crossed }, firstCrossing)
        }

        private fun episodeCount(observations: List<PassiveObservation>): Int =
            BlockThresholdCalibrator.episodeCount(
                observations.map { if (it.crossed) 1.0 else 0.0 },
                threshold = 0.5,
            )

        private fun domainCrossings(observation: PassiveObservation): Int {
            val threshold = observation.threshold ?: return 0
            return observation.domains.count { it.score > threshold }
        }

        private fun observationBytes(observations: List<PassiveObservation>): ByteArray =
            observations.joinToString(separator = "\n").toByteArray(StandardCharsets.UTF_8)
    }
}
