package org.mindanchor.intelligence

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class PassiveCalibrationTest {
    @Test fun `score requires evidence from two domains`() {
        val baseline = PassiveBaseline("a", 60, mapOf(
            PassiveFeature.STEPS to FeatureBaseline(PassiveFeature.STEPS, 5_000.0, 500.0, 60, false)))
        val day = PassiveDay(LocalDate.parse("2026-08-30"), PassiveDataStatus.AVAILABLE_FINAL,
            mapOf(PassiveFeature.STEPS to 8_000.0), baselineSegment = "a")
        assertNull(PassiveScorer.score(day, baseline))
    }

    @Test fun `exercise-excluded physiology cannot contribute`() {
        val baseline = PassiveBaseline("a", 60, mapOf(
            PassiveFeature.RESTING_HEART_RATE to FeatureBaseline(PassiveFeature.RESTING_HEART_RATE, 60.0, 5.0, 60, false),
            PassiveFeature.SLEEP_MINUTES to FeatureBaseline(PassiveFeature.SLEEP_MINUTES, 450.0, 30.0, 60, false)))
        val day = PassiveDay(LocalDate.parse("2026-08-30"), PassiveDataStatus.AVAILABLE_FINAL,
            mapOf(PassiveFeature.RESTING_HEART_RATE to 100.0, PassiveFeature.SLEEP_MINUTES to 300.0),
            excludedFeatures = setOf(PassiveFeature.RESTING_HEART_RATE), baselineSegment = "a")
        assertNull(PassiveScorer.score(day, baseline))
    }

    @Test fun `calibration is deterministic and respects the episode budget`() {
        val scores = List(60) { i -> 0.5 + (i % 10) * 0.1 }
        val first = BlockThresholdCalibrator.calibrate(scores, seed = 42L)!!
        val second = BlockThresholdCalibrator.calibrate(scores, seed = 42L)!!
        assertEquals(first, second)
        assertTrue(first.expectedEpisodesPer30 <= 1.0)
    }

    @Test fun `nearby crossings form one episode`() {
        assertEquals(2, BlockThresholdCalibrator.episodeCount(listOf(4.0, 0.0, 4.0, 0.0, 0.0, 4.0), 3.0))
    }
}
