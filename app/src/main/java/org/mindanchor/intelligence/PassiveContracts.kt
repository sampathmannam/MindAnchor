package org.mindanchor.intelligence

import java.time.LocalDate

enum class PassiveDomain { PHYSIOLOGY, SLEEP, ACTIVITY, ROUTINE }

enum class PassiveFeature(val domain: PassiveDomain?, val scored: Boolean = true) {
    RESTING_HEART_RATE(PassiveDomain.PHYSIOLOGY),
    HRV_RMSSD(PassiveDomain.PHYSIOLOGY),
    SLEEP_MINUTES(PassiveDomain.SLEEP),
    SLEEP_ONSET_AFTER_SIX_PM(PassiveDomain.SLEEP),
    STEPS(PassiveDomain.ACTIVITY),
    FIRST_UNLOCK_MINUTE(PassiveDomain.ROUTINE),
    SCREEN_MINUTES(PassiveDomain.ROUTINE),
    SPO2_PERCENT(null, scored = false),
}

enum class PassiveDataStatus(val canEstimate: Boolean) {
    AVAILABLE_FINAL(true),
    AVAILABLE_PROVISIONAL(false),
    INSUFFICIENT_DATA(false),
    SUPPRESSED_EXERCISE(false),
    BASELINE_BUILDING(false),
}

enum class PassiveObservationState {
    WITHIN_PERSON_RANGE,
    TRANSIENT_DEVIATION,
    SUSTAINED_DEVIATION,
    BASELINE_SHIFT_CANDIDATE,
    NO_OBSERVATION,
}

data class PassiveDay(
    val day: LocalDate,
    val dataStatus: PassiveDataStatus,
    val features: Map<PassiveFeature, Double>,
    val excludedFeatures: Set<PassiveFeature> = emptySet(),
    val baselineSegment: String,
) {
    fun isEligible(feature: PassiveFeature): Boolean =
        dataStatus.canEstimate && feature.scored && feature !in excludedFeatures && features[feature]?.isFinite() == true
}

data class FeatureEvidence(
    val feature: PassiveFeature,
    val value: Double,
    val centre: Double,
    val scale: Double,
    val zScore: Double,
    val referenceCount: Int,
    val pooledStratum: Boolean,
)

data class DomainEvidence(
    val domain: PassiveDomain,
    val score: Double,
    val features: List<FeatureEvidence>,
)

data class PassiveObservation(
    val day: LocalDate,
    val asOfTime: Long,
    val dataStatus: PassiveDataStatus,
    val state: PassiveObservationState,
    val threshold: Double?,
    val crossed: Boolean,
    val baselineDays: Int,
    val baselineSegment: String,
    val domains: List<DomainEvidence>,
    val explanation: String,
)
