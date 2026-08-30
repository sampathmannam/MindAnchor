package org.mindanchor.intelligence

import java.util.Random
import kotlin.math.abs

data class DayScore(val score: Double, val domains: List<DomainEvidence>)

data class CalibrationResult(
    val threshold: Double,
    val expectedEpisodesPer30: Double,
    val simulations: Int,
)

object PassiveScorer {
    const val MIN_CORROBORATING_DOMAINS = 2

    fun score(day: PassiveDay, baseline: PassiveBaseline): DayScore? {
        if (!day.dataStatus.canEstimate || day.baselineSegment != baseline.segment) return null
        val evidence = baseline.features.values.mapNotNull { reference ->
            if (!day.isEligible(reference.feature)) return@mapNotNull null
            val value = day.features[reference.feature] ?: return@mapNotNull null
            FeatureEvidence(
                feature = reference.feature,
                value = value,
                centre = reference.centre,
                scale = reference.scale,
                zScore = (value - reference.centre) / reference.scale,
                referenceCount = reference.sampleCount,
                pooledStratum = reference.pooledStratum,
            )
        }
        val domains = evidence.groupBy { requireNotNull(it.feature.domain) }.map { (domain, features) ->
            DomainEvidence(domain, features.maxOf { abs(it.zScore) }, features.sortedBy { it.feature.name })
        }.sortedBy { it.domain.name }
        if (domains.size < MIN_CORROBORATING_DOMAINS) return null
        val corroboratedScore = domains.map { it.score }.sortedDescending()[MIN_CORROBORATING_DOMAINS - 1]
        return DayScore(corroboratedScore, domains)
    }
}

object BlockThresholdCalibrator {
    const val BLOCK_DAYS = 7
    const val CALIBRATION_DAYS = 30
    const val SIMULATIONS = 512
    const val TARGET_EPISODES_PER_30 = 1.0
    const val REFRACTORY_DAYS = 2

    fun calibrate(scores: List<Double>, seed: Long): CalibrationResult? {
        if (scores.size < CALIBRATION_DAYS || scores.any { !it.isFinite() }) return null
        val random = Random(seed)
        val samples = List(SIMULATIONS) { circularBlockSample(scores, random) }
        val expectedEpisodes = scores.distinct().associateWith { candidate ->
            expectedEpisodeCount(samples, candidate)
        }
        val threshold = selectConnectedSafeThreshold(expectedEpisodes)
        return CalibrationResult(threshold, expectedEpisodes.getValue(threshold), SIMULATIONS)
    }

    internal fun selectConnectedSafeThreshold(expectedEpisodes: Map<Double, Double>): Double {
        require(expectedEpisodes.isNotEmpty())
        val candidates = expectedEpisodes.keys.sortedDescending()
        var threshold = candidates.first()
        require(expectedEpisodes.getValue(threshold) <= TARGET_EPISODES_PER_30)
        for (candidate in candidates.drop(1)) {
            if (expectedEpisodes.getValue(candidate) > TARGET_EPISODES_PER_30) break
            threshold = candidate
        }
        return threshold
    }

    fun episodeCount(scores: List<Double>, threshold: Double): Int {
        var episodes = 0
        var previousCrossing: Int? = null
        scores.forEachIndexed { index, score ->
            if (score > threshold) {
                if (previousCrossing == null || index - previousCrossing!! > REFRACTORY_DAYS) episodes++
                previousCrossing = index
            }
        }
        return episodes
    }

    private fun circularBlockSample(scores: List<Double>, random: Random): List<Double> {
        val sample = ArrayList<Double>(CALIBRATION_DAYS)
        while (sample.size < CALIBRATION_DAYS) {
            val start = random.nextInt(scores.size)
            repeat(BLOCK_DAYS) { offset ->
                if (sample.size < CALIBRATION_DAYS) sample += scores[(start + offset) % scores.size]
            }
        }
        return sample
    }

    private fun expectedEpisodeCount(samples: List<List<Double>>, threshold: Double): Double =
        samples.sumOf { episodeCount(it, threshold) }.toDouble() / samples.size
}
