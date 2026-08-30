package org.mindanchor.intelligence

import java.time.LocalDate

data class BaselineEligibility(val eligibleDays: Int, val weekdays: Int, val weekendDays: Int) {
    val ready get() = eligibleDays >= 60 && weekdays >= 8 && weekendDays >= 8
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
    val referenceDays: Int,
    val features: Map<PassiveFeature, FeatureBaseline>,
)

object PassiveBaselineBuilder {
    const val MIN_DAYS = 60
    const val MIN_WEEKDAY_DAYS = 8
    const val MIN_WEEKEND_DAYS = 8
    const val MIN_STRATUM_VALUES = 14
    private const val MAD_SCALE = 1.4826
    private const val IQR_SCALE = 1.349

    fun evaluate(history: List<PassiveDay>, segment: String): BaselineEligibility {
        val eligible = history.filter { it.baselineSegment == segment && it.dataStatus.canEstimate }
        val weekend = eligible.count { it.day.dayOfWeek.value >= 6 }
        return BaselineEligibility(eligible.size, eligible.size - weekend, weekend)
    }

    fun build(history: List<PassiveDay>, targetDay: LocalDate, segment: String): PassiveBaseline? {
        val eligible = history.filter { it.day.isBefore(targetDay) && it.baselineSegment == segment && it.dataStatus.canEstimate }
        if (!evaluate(eligible, segment).ready) return null
        val targetWeekend = targetDay.dayOfWeek.value >= 6
        val baselines = PassiveFeature.entries.filter { it.scored }.mapNotNull { feature ->
            val all = eligible.filter { it.isEligible(feature) }.mapNotNull { it.features[feature] }
            val stratum = eligible.filter { (it.day.dayOfWeek.value >= 6) == targetWeekend && it.isEligible(feature) }
                .mapNotNull { it.features[feature] }
            val pooled = stratum.size < MIN_STRATUM_VALUES
            val values = if (pooled) all else stratum
            statistics(feature, values, pooled)?.let { feature to it }
        }.toMap()
        return PassiveBaseline(segment, eligible.size, baselines)
    }

    private fun statistics(feature: PassiveFeature, values: List<Double>, pooled: Boolean): FeatureBaseline? {
        if (values.isEmpty()) return null
        val centre = median(values)
        val mad = median(values.map { kotlin.math.abs(it - centre) })
        val q1 = quantile(values, 0.25)
        val q3 = quantile(values, 0.75)
        val scale = if (mad > 0.0) MAD_SCALE * mad else (q3 - q1) / IQR_SCALE
        if (!scale.isFinite() || scale <= 0.0) return null
        return FeatureBaseline(feature, centre, scale, values.size, pooled)
    }

    internal fun median(values: List<Double>): Double = quantile(values, 0.5)

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
