package org.mindanchor.intelligence

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PassiveCalibrationTest {
    @Test fun `score requires evidence from two domains`() {
        val baseline = PassiveBaseline("a", 1L, LocalDate.parse("2026-03-01"), 60, mapOf(
            PassiveFeature.STEPS to FeatureBaseline(PassiveFeature.STEPS, 5_000.0, 500.0, 60, false)))
        val day = PassiveDay(LocalDate.parse("2026-08-30"), PassiveDataStatus.AVAILABLE_FINAL,
            mapOf(PassiveFeature.STEPS to 8_000.0), baselineSegment = "a",
            sourceUpdatedTime = 1_000L, ingestedAt = 1_000L)
        assertNull(PassiveScorer.score(day, baseline, asOfTime = 1_000L))
    }

    @Test fun `exercise-excluded physiology cannot contribute`() {
        val baseline = PassiveBaseline("a", 1L, LocalDate.parse("2026-03-01"), 60, mapOf(
            PassiveFeature.RESTING_HEART_RATE to FeatureBaseline(
                PassiveFeature.RESTING_HEART_RATE, 60.0, 5.0, 60, false,
            ),
            PassiveFeature.SLEEP_MINUTES to FeatureBaseline(PassiveFeature.SLEEP_MINUTES, 450.0, 30.0, 60, false)))
        val day = PassiveDay(LocalDate.parse("2026-08-30"), PassiveDataStatus.AVAILABLE_FINAL,
            mapOf(PassiveFeature.RESTING_HEART_RATE to 100.0, PassiveFeature.SLEEP_MINUTES to 300.0),
            excludedFeatures = setOf(PassiveFeature.RESTING_HEART_RATE), baselineSegment = "a",
            sourceUpdatedTime = 1_000L, ingestedAt = 1_000L)
        assertNull(PassiveScorer.score(day, baseline, asOfTime = 1_000L))
    }

    @Test fun `day score is the second strongest domain magnitude`() {
        val baseline = PassiveBaseline(
            "a",
            1L,
            LocalDate.parse("2026-03-01"),
            60,
            mapOf(
                PassiveFeature.RESTING_HEART_RATE to FeatureBaseline(
                    PassiveFeature.RESTING_HEART_RATE, 60.0, 5.0, 60, false,
                ),
                PassiveFeature.SLEEP_MINUTES to FeatureBaseline(
                    PassiveFeature.SLEEP_MINUTES, 420.0, 20.0, 60, false,
                ),
                PassiveFeature.STEPS to FeatureBaseline(PassiveFeature.STEPS, 5_000.0, 500.0, 60, false),
            ),
        )
        val day = PassiveDay(
            LocalDate.parse("2026-08-30"),
            PassiveDataStatus.AVAILABLE_FINAL,
            mapOf(
                PassiveFeature.RESTING_HEART_RATE to 100.0,
                PassiveFeature.SLEEP_MINUTES to 480.0,
                PassiveFeature.STEPS to 5_500.0,
            ),
            baselineSegment = "a",
            sourceUpdatedTime = 1_000L,
            ingestedAt = 1_000L,
        )

        val score = PassiveScorer.score(day, baseline, asOfTime = 1_000L)!!

        assertEquals(3.0, score.score, 0.0)
        assertEquals(3, score.domains.size)
    }

    @Test fun `all four non-final statuses are unscorable`() {
        val baseline = twoDomainBaseline()
        val statuses = listOf(
            PassiveDataStatus.AVAILABLE_PROVISIONAL,
            PassiveDataStatus.INSUFFICIENT_DATA,
            PassiveDataStatus.SUPPRESSED_EXERCISE,
            PassiveDataStatus.BASELINE_BUILDING,
        )

        statuses.forEach { status ->
            val day = twoDomainDay(status = status)
            assertNull(status.name, PassiveScorer.score(day, baseline, asOfTime = 1_000L))
        }
    }

    @Test fun `a revision ingested after the cutoff is unscorable`() {
        val day = twoDomainDay(ingestedAt = 2_000L)

        assertNull(PassiveScorer.score(day, twoDomainBaseline(), asOfTime = 1_000L))
    }

    @Test fun `calibration is deterministic and respects the episode budget`() {
        val scores = List(60) { i -> 0.5 + (i % 10) * 0.1 }
        val first = BlockThresholdCalibrator.calibrate(scores, seed = 42L)!!
        val second = BlockThresholdCalibrator.calibrate(scores, seed = 42L)!!
        assertEquals(first, second)
        assertTrue(first.expectedEpisodesPer30 <= 1.0)
        assertEquals(42L, first.seed)
        assertEquals(
            CalibrationConfiguration(
                blockDays = 7,
                calibrationDays = 30,
                simulations = 512,
                targetEpisodesPer30 = 1.0,
                refractoryDays = 2,
            ),
            first.configuration,
        )
    }

    @Test fun `connected safe threshold stops before a violation and rejects a lower safe island`() {
        val expectedEpisodes = mapOf(
            5.0 to 0.0,
            4.0 to 0.75,
            3.0 to 1.25,
            2.0 to 0.8,
            1.0 to 0.2,
        )

        val threshold = BlockThresholdCalibrator.selectConnectedSafeThreshold(expectedEpisodes)

        assertEquals(4.0, threshold, 0.0)
    }

    @Test fun `nearby crossings form one episode`() {
        assertEquals(2, BlockThresholdCalibrator.episodeCount(listOf(4.0, 0.0, 4.0, 0.0, 0.0, 4.0), 3.0))
    }

    private fun twoDomainBaseline() = PassiveBaseline(
        "a",
        1L,
        LocalDate.parse("2026-03-01"),
        60,
        mapOf(
            PassiveFeature.RESTING_HEART_RATE to FeatureBaseline(
                PassiveFeature.RESTING_HEART_RATE,
                60.0,
                5.0,
                60,
                false,
            ),
            PassiveFeature.SLEEP_MINUTES to FeatureBaseline(
                PassiveFeature.SLEEP_MINUTES,
                450.0,
                30.0,
                60,
                false,
            ),
        ),
    )

    private fun twoDomainDay(
        status: PassiveDataStatus = PassiveDataStatus.AVAILABLE_FINAL,
        ingestedAt: Long = 1_000L,
    ) = PassiveDay(
        day = LocalDate.parse("2026-08-30"),
        dataStatus = status,
        features = mapOf(
            PassiveFeature.RESTING_HEART_RATE to 80.0,
            PassiveFeature.SLEEP_MINUTES to 300.0,
        ),
        baselineSegment = "a",
        sourceUpdatedTime = 1_000L,
        ingestedAt = ingestedAt,
    )
}
