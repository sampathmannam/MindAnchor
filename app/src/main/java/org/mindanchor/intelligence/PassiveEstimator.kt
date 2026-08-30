package org.mindanchor.intelligence

object PassiveEstimator {
    const val RULE_VERSION = "passive-observation-rules-v4"

    @Suppress("ReturnCount")
    fun observe(
        day: PassiveDay,
        asOfTime: Long,
        history: List<PassiveDay>,
        prior: List<PassiveObservation>,
        seed: Long,
    ): PassiveObservation {
        if (!day.dataStatus.canEstimate) return noObservation(day, asOfTime, 0)
        val effectiveHistory = PassiveHistory.effectiveFinalDays(history, day.day, asOfTime, day.baselineSegment)
        val frozenReference = PassiveBaselineBuilder.freeze(history, day.day, asOfTime, day.baselineSegment)
            ?: return noObservation(
                day.copy(dataStatus = PassiveDataStatus.BASELINE_BUILDING),
                asOfTime,
                PassiveBaselineBuilder.evaluate(history, day.day, asOfTime, day.baselineSegment).eligibleDays,
            )
        val baseline = PassiveBaselineBuilder.build(frozenReference, day.day)
        val current = PassiveScorer.score(day, baseline, asOfTime)
            ?: return noObservation(
                day.copy(dataStatus = PassiveDataStatus.INSUFFICIENT_DATA),
                asOfTime,
                baseline.referenceDays,
            )
        val historicalScores = effectiveHistory.mapNotNull { historicalDay ->
            val historicalBaseline = PassiveBaselineBuilder.build(frozenReference, historicalDay.day)
            PassiveScorer.score(historicalDay, historicalBaseline, asOfTime)?.score
        }
        val calibration = BlockThresholdCalibrator.calibrate(historicalScores, seed)
            ?: return noObservation(
                day.copy(dataStatus = PassiveDataStatus.BASELINE_BUILDING),
                asOfTime,
                baseline.referenceDays,
            )
        val crossingDomains = current.domains.count { it.score > calibration.threshold }
        val crossed = current.score > calibration.threshold &&
            crossingDomains >= PassiveScorer.MIN_CORROBORATING_DOMAINS
        val candidate = PassiveBaselineBuilder.buildTrailingCandidate(
            history,
            day.day,
            asOfTime,
            day.baselineSegment,
            baseline,
        )
        val baselineShift = candidate?.let { BaselineShiftDetector.assess(baseline, it) }
        val state = stateFor(day, asOfTime, prior, crossed, baselineShift)
        val draft = PassiveObservation(
            day.day,
            asOfTime,
            day.dataStatus,
            state,
            calibration.threshold,
            crossed,
            baseline.referenceDays,
            baseline.frozenAsOfTime,
            baseline.frozenThroughDay,
            day.baselineSegment,
            current.domains,
            calibration,
            baselineShift,
            "",
        )
        return draft.copy(explanation = PassiveExplanation.render(draft))
    }

    private fun stateFor(
        day: PassiveDay,
        asOfTime: Long,
        prior: List<PassiveObservation>,
        crossed: Boolean,
        baselineShift: BaselineShiftAssessment?,
    ): PassiveObservationState {
        val eligiblePrior = PassiveHistory.effectiveObservations(
            prior,
            day.day,
            asOfTime,
            day.baselineSegment,
        ).filter { it.dataStatus.canEstimate }
        val previousEligible = eligiblePrior.takeLast(2)
        val priorCandidateDays = eligiblePrior.takeLast(BaselineShiftDetector.PERSISTENCE_DAYS - 1)
        val persistentBaselineShift = baselineShift?.disagrees == true &&
            priorCandidateDays.size == BaselineShiftDetector.PERSISTENCE_DAYS - 1 &&
            priorCandidateDays.all { it.baselineShift?.disagrees == true }
        return when {
            persistentBaselineShift -> PassiveObservationState.BASELINE_SHIFT_CANDIDATE
            crossed && previousEligible.any { it.crossed } -> PassiveObservationState.SUSTAINED_DEVIATION
            crossed -> PassiveObservationState.TRANSIENT_DEVIATION
            requiresRangeReturnConfirmation(eligiblePrior) -> PassiveObservationState.RANGE_RETURN_PENDING
            else -> PassiveObservationState.WITHIN_PERSON_RANGE
        }
    }

    private fun requiresRangeReturnConfirmation(prior: List<PassiveObservation>): Boolean {
        val lastCrossing = prior.indexOfLast { it.crossed }
        return lastCrossing >= 0 && prior.size - lastCrossing == 1
    }

    private fun noObservation(day: PassiveDay, asOfTime: Long, baselineDays: Int) = PassiveObservation(
        day.day,
        asOfTime,
        day.dataStatus,
        PassiveObservationState.NO_OBSERVATION,
        null,
        false,
        baselineDays,
        null,
        null,
        day.baselineSegment,
        emptyList(),
        null,
        null,
        PassiveExplanation.noObservation(day.dataStatus, baselineDays),
    )
}

object PassiveExplanation {
    fun render(observation: PassiveObservation): String {
        if (observation.state == PassiveObservationState.BASELINE_SHIFT_CANDIDATE) {
            val domains = requireNotNull(observation.baselineShift).domains
                .joinToString(" and ") { it.domain.name.lowercase() }
            return "Trailing candidate baseline differed from the frozen reference baseline across $domains " +
                "for seven eligible days. This records baseline disagreement only."
        }
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
            PassiveObservationState.RANGE_RETURN_PENDING ->
                "One eligible in-range day was recorded; two consecutive eligible in-range days " +
                    "are required for within-person range."
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
