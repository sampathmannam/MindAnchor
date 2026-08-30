package org.mindanchor.intelligence

object PassiveEstimator {
    const val RULE_VERSION = "passive-observation-rules-v2"

    @Suppress("ReturnCount")
    fun observe(
        day: PassiveDay,
        asOfTime: Long,
        history: List<PassiveDay>,
        prior: List<PassiveObservation>,
        seed: Long,
    ): PassiveObservation {
        if (!day.dataStatus.canEstimate) return noObservation(day, asOfTime, 0)
        val baseline = PassiveBaselineBuilder.build(history, day.day, day.baselineSegment)
            ?: return noObservation(
                day.copy(dataStatus = PassiveDataStatus.BASELINE_BUILDING),
                asOfTime,
                PassiveBaselineBuilder.evaluate(history, day.baselineSegment).eligibleDays,
            )
        val current = PassiveScorer.score(day, baseline)
            ?: return noObservation(
                day.copy(dataStatus = PassiveDataStatus.INSUFFICIENT_DATA),
                asOfTime,
                baseline.referenceDays,
            )
        val historicalScores = history.filter { it.day.isBefore(day.day) }
            .mapNotNull { PassiveScorer.score(it, baseline)?.score }
        val calibration = BlockThresholdCalibrator.calibrate(historicalScores, seed)
            ?: return noObservation(
                day.copy(dataStatus = PassiveDataStatus.BASELINE_BUILDING),
                asOfTime,
                baseline.referenceDays,
            )
        val crossingDomains = current.domains.count { it.score > calibration.threshold }
        val crossed = current.score > calibration.threshold &&
            crossingDomains >= PassiveScorer.MIN_CORROBORATING_DOMAINS
        val previousEligible = prior.filter { it.dataStatus.canEstimate }
            .sortedByDescending { it.day }
            .take(2)
        val state = when {
            crossed && previousEligible.any { it.crossed } -> PassiveObservationState.SUSTAINED_DEVIATION
            crossed -> PassiveObservationState.TRANSIENT_DEVIATION
            else -> PassiveObservationState.WITHIN_PERSON_RANGE
        }
        val draft = PassiveObservation(
            day.day,
            asOfTime,
            day.dataStatus,
            state,
            calibration.threshold,
            crossed,
            baseline.referenceDays,
            day.baselineSegment,
            current.domains,
            "",
        )
        return draft.copy(explanation = PassiveExplanation.render(draft))
    }

    private fun noObservation(day: PassiveDay, asOfTime: Long, baselineDays: Int) = PassiveObservation(
        day.day,
        asOfTime,
        day.dataStatus,
        PassiveObservationState.NO_OBSERVATION,
        null,
        false,
        baselineDays,
        day.baselineSegment,
        emptyList(),
        PassiveExplanation.noObservation(day.dataStatus, baselineDays),
    )
}

object PassiveExplanation {
    fun render(observation: PassiveObservation): String {
        if (observation.crossed) {
            val threshold = requireNotNull(observation.threshold)
            val domains = observation.domains.filter { it.score > threshold }
                .joinToString(" and ") { it.domain.name.lowercase() }
            return "Recorded $domains signals differed from your calibrated personal range. " +
                "This describes recorded data only."
        }
        return when (observation.state) {
            PassiveObservationState.WITHIN_PERSON_RANGE ->
                "Available signals were within your calibrated personal range."
            PassiveObservationState.NO_OBSERVATION ->
                noObservation(observation.dataStatus, observation.baselineDays)
            else -> "Recorded signals were within your calibrated personal range."
        }
    }

    fun noObservation(status: PassiveDataStatus, baselineDays: Int): String = when (status) {
        PassiveDataStatus.AVAILABLE_FINAL ->
            "No observation: eligible signal coverage could not be scored."
        PassiveDataStatus.AVAILABLE_PROVISIONAL ->
            "No observation: the day's data is not final."
        PassiveDataStatus.INSUFFICIENT_DATA ->
            "No observation: eligible data is insufficient."
        PassiveDataStatus.SUPPRESSED_EXERCISE ->
            "No observation: physiology signals overlapping exercise were excluded."
        PassiveDataStatus.BASELINE_BUILDING ->
            "No observation: the personal baseline is building ($baselineDays of 60 eligible days)."
    }
}
