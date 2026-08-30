package org.mindanchor.intelligence

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PassiveContractsTest {
    @Test fun `only final data is estimator eligible`() {
        assertEquals(listOf(PassiveDataStatus.AVAILABLE_FINAL),
            PassiveDataStatus.entries.filter { it.canEstimate })
    }

    @Test fun `SpO2 is context and never a scored feature`() {
        assertFalse(PassiveFeature.SPO2_PERCENT.scored)
    }

    @Test fun `excluded feature is unavailable without deleting its value`() {
        val day = PassiveDay(
            day = LocalDate.parse("2026-08-30"),
            dataStatus = PassiveDataStatus.AVAILABLE_FINAL,
            features = mapOf(PassiveFeature.RESTING_HEART_RATE to 80.0),
            excludedFeatures = setOf(PassiveFeature.RESTING_HEART_RATE),
            baselineSegment = "device-a",
            sourceUpdatedTime = 1_000L,
            ingestedAt = 1_000L,
        )
        assertEquals(80.0, day.features[PassiveFeature.RESTING_HEART_RATE])
        assertFalse(day.isEligible(PassiveFeature.RESTING_HEART_RATE))
    }
}
