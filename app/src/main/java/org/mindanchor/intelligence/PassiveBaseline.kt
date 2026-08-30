package org.mindanchor.intelligence

import java.time.LocalDate

data class BaselineEligibility(val eligibleDays: Int, val weekdays: Int, val weekendDays: Int) {
    val ready get() = eligibleDays >= PassiveBaselineBuilder.MIN_DAYS &&
        weekdays >= PassiveBaselineBuilder.MIN_WEEKDAY_DAYS &&
        weekendDays >= PassiveBaselineBuilder.MIN_WEEKEND_DAYS
}

data class FeatureBaseline(
    val feature: PassiveFeature,
    val centre: Double,
    val scale: Double,
    val sampleCount: Int,
    val pooledStratum: Boolean,
)

data class PassiveBaseline(
    val segment: String,
    val frozenAsOfTime: Long,
    val frozenThroughDay: LocalDate,
    val referenceDays: Int,
    val features: Map<PassiveFeature, FeatureBaseline>,
)

data class FrozenPassiveReference(
    val segment: String,
    val frozenAsOfTime: Long,
    val frozenThroughDay: LocalDate,
    val days: List<PassiveDay>,
)

data class BaselineShiftDomainEvidence(
    val domain: PassiveDomain,
    val standardizedDisagreement: Double,
    val features: List<PassiveFeature>,
)

data class BaselineShiftAssessment(
    val candidateDays: Int,
    val standardizedDisagreementThreshold: Double,
    val minimumCorroboratingDomains: Int,
    val persistenceDays: Int,
    val domains: List<BaselineShiftDomainEvidence>,
    val disagrees: Boolean,
)

object PassiveBaselineBuilder {
    const val MIN_DAYS = 60
    const val MIN_WEEKDAY_DAYS = 8
    const val MIN_WEEKEND_DAYS = 8
    const val MIN_STRATUM_VALUES = 14
    const val TRAILING_CANDIDATE_DAYS = 56
    private const val WEEKEND_START_DAY = 6
    private const val MAD_SCALE = 1.4826
    private const val IQR_SCALE = 1.349
    private const val FIRST_QUARTILE = 0.25
    private const val MEDIAN_QUANTILE = 0.5
    private const val THIRD_QUARTILE = 0.75

    fun evaluate(
        history: List<PassiveDay>,
        targetDay: LocalDate,
        asOfTime: Long,
        segment: String,
    ): BaselineEligibility {
        val eligible = PassiveHistory.effectiveFinalDays(history, targetDay, asOfTime, segment)
        return eligibility(eligible)
    }

    private fun eligibility(eligible: List<PassiveDay>): BaselineEligibility {
        val weekend = eligible.count { it.day.dayOfWeek.value >= WEEKEND_START_DAY }
        return BaselineEligibility(eligible.size, eligible.size - weekend, weekend)
    }

    fun build(
        history: List<PassiveDay>,
        targetDay: LocalDate,
        asOfTime: Long,
        segment: String,
    ): PassiveBaseline? {
        val reference = freeze(history, targetDay, asOfTime, segment) ?: return null
        return build(reference, targetDay)
    }

    fun freeze(
        history: List<PassiveDay>,
        targetDay: LocalDate,
        asOfTime: Long,
        segment: String,
    ): FrozenPassiveReference? {
        val cutoffs = history.asSequence()
            .filter { it.day.isBefore(targetDay) }
            .filter { it.ingestedAt <= asOfTime }
            .filter { it.baselineSegment == segment && it.dataStatus.canEstimate }
            .map { it.ingestedAt }
            .distinct()
            .sorted()
        cutoffs.forEach { cutoff ->
            val eligible = PassiveHistory.effectiveFinalDays(history, targetDay, cutoff, segment)
            val prefix = (MIN_DAYS..eligible.size).asSequence()
                .map { eligible.take(it) }
                .firstOrNull { eligibility(it).ready }
                ?: return@forEach
            return FrozenPassiveReference(
                segment = segment,
                frozenAsOfTime = cutoff,
                frozenThroughDay = prefix.last().day,
                days = prefix,
            )
        }
        return null
    }

    fun build(reference: FrozenPassiveReference, stratumDay: LocalDate): PassiveBaseline =
        buildBaseline(reference, stratumDay)

    fun buildTrailingCandidate(
        history: List<PassiveDay>,
        targetDay: LocalDate,
        asOfTime: Long,
        segment: String,
        reference: PassiveBaseline,
    ): PassiveBaseline? {
        val eligible = PassiveHistory.effectiveFinalDays(history, targetDay, asOfTime, segment)
        if (eligible.size < TRAILING_CANDIDATE_DAYS) return null
        val candidateDays = eligible.takeLast(TRAILING_CANDIDATE_DAYS)
        val targetWeekend = targetDay.dayOfWeek.value >= WEEKEND_START_DAY
        val features = reference.features.mapNotNull { (feature, referenceFeature) ->
            val population = candidateDays.filter { day ->
                day.isEligible(feature) && (
                    referenceFeature.pooledStratum ||
                        (day.day.dayOfWeek.value >= WEEKEND_START_DAY) == targetWeekend
                    )
            }.mapNotNull { it.features[feature] }
            statistics(feature, population, referenceFeature.pooledStratum)?.let { feature to it }
        }.toMap()
        return PassiveBaseline(
            segment = segment,
            frozenAsOfTime = reference.frozenAsOfTime,
            frozenThroughDay = reference.frozenThroughDay,
            referenceDays = candidateDays.size,
            features = features,
        )
    }

    private fun buildBaseline(
        reference: FrozenPassiveReference,
        targetDay: LocalDate,
    ): PassiveBaseline {
        val eligible = reference.days
        val targetWeekend = targetDay.dayOfWeek.value >= WEEKEND_START_DAY
        val baselines = PassiveFeature.entries.filter { it.scored }.mapNotNull { feature ->
            val all = eligible.filter { it.isEligible(feature) }.mapNotNull { it.features[feature] }
            val stratum = eligible.filter {
                (it.day.dayOfWeek.value >= WEEKEND_START_DAY) == targetWeekend && it.isEligible(feature)
            }
                .mapNotNull { it.features[feature] }
            val pooled = stratum.size < MIN_STRATUM_VALUES
            val values = if (pooled) all else stratum
            statistics(feature, values, pooled)?.let { feature to it }
        }.toMap()
        return PassiveBaseline(
            segment = reference.segment,
            frozenAsOfTime = reference.frozenAsOfTime,
            frozenThroughDay = reference.frozenThroughDay,
            referenceDays = eligible.size,
            features = baselines,
        )
    }

    private fun statistics(feature: PassiveFeature, values: List<Double>, pooled: Boolean): FeatureBaseline? {
        if (values.size < MIN_STRATUM_VALUES) return null
        val centre = median(values)
        val mad = median(values.map { kotlin.math.abs(it - centre) })
        val q1 = quantile(values, FIRST_QUARTILE)
        val q3 = quantile(values, THIRD_QUARTILE)
        val scale = if (mad > 0.0) MAD_SCALE * mad else (q3 - q1) / IQR_SCALE
        if (!scale.isFinite() || scale <= 0.0) return null
        return FeatureBaseline(feature, centre, scale, values.size, pooled)
    }

    internal fun median(values: List<Double>): Double = quantile(values, MEDIAN_QUANTILE)

    internal fun quantile(values: List<Double>, probability: Double): Double {
        require(values.isNotEmpty())
        val sorted = values.sorted()
        val position = probability * (sorted.lastIndex)
        val lower = position.toInt()
        val upper = kotlin.math.ceil(position).toInt()
        if (lower == upper) return sorted[lower]
        return sorted[lower] + (sorted[upper] - sorted[lower]) * (position - lower)
    }
}

object BaselineShiftDetector {
    const val STANDARDIZED_DISAGREEMENT = 1.0
    const val MIN_CORROBORATING_DOMAINS = 2
    const val PERSISTENCE_DAYS = 7

    fun assess(reference: PassiveBaseline, candidate: PassiveBaseline): BaselineShiftAssessment {
        if (reference.segment != candidate.segment) {
            return assessment(candidate.referenceDays, emptyList())
        }
        val featureDisagreements = candidate.features.mapNotNull { (feature, candidateFeature) ->
            val referenceFeature = reference.features[feature] ?: return@mapNotNull null
            val disagreement = kotlin.math.abs(candidateFeature.centre - referenceFeature.centre) /
                referenceFeature.scale
            if (disagreement < STANDARDIZED_DISAGREEMENT) return@mapNotNull null
            Triple(requireNotNull(feature.domain), feature, disagreement)
        }
        val domains = featureDisagreements.groupBy { it.first }.map { (domain, disagreements) ->
            BaselineShiftDomainEvidence(
                domain = domain,
                standardizedDisagreement = disagreements.maxOf { it.third },
                features = disagreements.map { it.second }.sortedBy { it.name },
            )
        }.sortedBy { it.domain.name }
        return assessment(candidate.referenceDays, domains)
    }

    private fun assessment(
        candidateDays: Int,
        domains: List<BaselineShiftDomainEvidence>,
    ) = BaselineShiftAssessment(
        candidateDays = candidateDays,
        standardizedDisagreementThreshold = STANDARDIZED_DISAGREEMENT,
        minimumCorroboratingDomains = MIN_CORROBORATING_DOMAINS,
        persistenceDays = PERSISTENCE_DAYS,
        domains = domains,
        disagrees = domains.size >= MIN_CORROBORATING_DOMAINS,
    )
}
