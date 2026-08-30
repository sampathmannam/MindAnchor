package org.mindanchor.intelligence

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil
import org.mindanchor.usage.ScreenEvent
import org.mindanchor.usage.ScreenEventKind
import org.mindanchor.usage.ScreenRhythm

object PassiveWindowAggregator {
    const val WINDOW_MILLIS = 15L * 60_000L
    const val TRANSFORMATION_VERSION = "passive-window-v1"
    private const val MINUTE_MILLIS = 60_000L
    private const val HEART_RATE_BINS_PER_WINDOW = 15.0
    private const val MIN_HEART_RATE_COVERAGE = 0.5
    private const val EXERCISE_EXCLUSION = "EXERCISE_OVERLAP"
    private const val COVERAGE_EXCLUSION = "INSUFFICIENT_HEART_RATE_COVERAGE"

    fun aggregate(
        records: List<PassiveSourceRecord>,
        range: PassiveReadRange,
        zone: ZoneId,
        wakeTimeMillis: Long?,
    ): List<PassiveFeatureWindow> {
        val first = Math.floorDiv(range.startInclusive, WINDOW_MILLIS) * WINDOW_MILLIS
        val last = Math.floorDiv(range.endExclusive - 1L, WINDOW_MILLIS) * WINDOW_MILLIS
        return generateSequence(first) { it + WINDOW_MILLIS }
            .takeWhile { it <= last }
            .map { start -> window(records, start, zone, wakeTimeMillis) }
            .toList()
    }

    private fun window(
        records: List<PassiveSourceRecord>,
        start: Long,
        zone: ZoneId,
        wakeTimeMillis: Long?,
    ): PassiveFeatureWindow {
        val end = start + WINDOW_MILLIS
        val overlapping = records.filter {
            it.eventStart < end && it.eventEnd.coerceAtLeast(it.eventStart + 1L) > start
        }
        val hrBins = overlapping.filter { it.kind == PassiveRecordKind.HEART_RATE_SAMPLE }
            .map { Math.floorDiv(it.eventStart - start, MINUTE_MILLIS) }
            .filter { it in 0L..14L }
            .distinct()
            .size
        val coverage = hrBins / HEART_RATE_BINS_PER_WINDOW
        val exerciseMillis = overlapping.filter { it.kind == PassiveRecordKind.EXERCISE_SESSION }
            .sumOf { overlapMillis(it.eventStart, it.eventEnd, start, end) }
        val physiologyEligible = coverage >= MIN_HEART_RATE_COVERAGE && exerciseMillis == 0L
        val offset = zone.rules.getOffset(Instant.ofEpochMilli(start)).totalSeconds
        return PassiveFeatureWindow(
            startInclusive = start,
            endExclusive = end,
            zoneId = zone.id,
            zoneOffsetSeconds = offset,
            quality = PassiveWindowQuality(
                heartRateCoverage = coverage,
                physiologyEligible = physiologyEligible,
                exerciseOverlapMillis = exerciseMillis,
                wakeRelativeMinute = wakeTimeMillis?.let { ((start - it) / MINUTE_MILLIS).toInt() },
            ),
            features = featureRows(overlapping, start, end, physiologyEligible, coverage),
            provenanceRecordIds = overlapping.map { it.recordId }.distinct().sorted(),
        )
    }

    private fun featureRows(
        records: List<PassiveSourceRecord>,
        start: Long,
        end: Long,
        physiologyEligible: Boolean,
        heartRateCoverage: Double,
    ): List<PassiveWindowFeature> = buildList {
        instantPhysiology(
            records,
            PassiveRecordKind.RESTING_HEART_RATE,
            PassiveFeature.RESTING_HEART_RATE,
            physiologyEligible,
            heartRateCoverage,
        )?.let(::add)
        instantPhysiology(
            records,
            PassiveRecordKind.HRV_RMSSD,
            PassiveFeature.HRV_RMSSD,
            physiologyEligible,
            heartRateCoverage,
        )?.let(::add)
        instantContext(records, PassiveRecordKind.SPO2, PassiveFeature.SPO2_PERCENT)?.let(::add)
        intervalValue(records, PassiveRecordKind.STEPS_INTERVAL, PassiveFeature.STEPS, start, end)?.let(::add)
        activeMinutes(records, start, end)?.let(::add)
    }

    private fun instantPhysiology(
        records: List<PassiveSourceRecord>,
        kind: PassiveRecordKind,
        feature: PassiveFeature,
        eligible: Boolean,
        coverage: Double,
    ): PassiveWindowFeature? {
        val matching = records.filter { it.kind == kind && it.value != null }
        if (matching.isEmpty()) return null
        return PassiveWindowFeature(
            feature = feature,
            value = matching.mapNotNull { it.value }.average(),
            unit = matching.first().unit,
            coverage = coverage,
            eligible = eligible,
            exclusion = when {
                eligible -> null
                records.any { it.kind == PassiveRecordKind.EXERCISE_SESSION } -> EXERCISE_EXCLUSION
                else -> COVERAGE_EXCLUSION
            },
        )
    }

    private fun instantContext(
        records: List<PassiveSourceRecord>,
        kind: PassiveRecordKind,
        feature: PassiveFeature,
    ): PassiveWindowFeature? {
        val matching = records.filter { it.kind == kind && it.value != null }
        if (matching.isEmpty()) return null
        return PassiveWindowFeature(
            feature = feature,
            value = matching.mapNotNull { it.value }.average(),
            unit = matching.first().unit,
            coverage = 1.0,
            eligible = true,
            exclusion = null,
        )
    }

    private fun intervalValue(
        records: List<PassiveSourceRecord>,
        kind: PassiveRecordKind,
        feature: PassiveFeature,
        start: Long,
        end: Long,
    ): PassiveWindowFeature? {
        val matching = records.filter { it.kind == kind && it.value != null && it.eventEnd > it.eventStart }
        if (matching.isEmpty()) return null
        val overlaps = matching.map { overlapMillis(it.eventStart, it.eventEnd, start, end) }
        val value = matching.zip(overlaps).sumOf { (record, overlap) ->
            requireNotNull(record.value) * overlap.toDouble() / (record.eventEnd - record.eventStart)
        }
        return PassiveWindowFeature(
            feature = feature,
            value = value,
            unit = matching.first().unit,
            coverage = (overlaps.sum().toDouble() / WINDOW_MILLIS).coerceAtMost(1.0),
            eligible = true,
            exclusion = null,
        )
    }

    private fun activeMinutes(
        records: List<PassiveSourceRecord>,
        start: Long,
        end: Long,
    ): PassiveWindowFeature? {
        val matching = records.filter { it.kind == PassiveRecordKind.EXERCISE_SESSION }
        if (matching.isEmpty()) return null
        val overlap = matching.sumOf { overlapMillis(it.eventStart, it.eventEnd, start, end) }
        return PassiveWindowFeature(
            feature = PassiveFeature.ACTIVE_MINUTES,
            value = overlap.toDouble() / MINUTE_MILLIS,
            unit = "min",
            coverage = (overlap.toDouble() / WINDOW_MILLIS).coerceAtMost(1.0),
            eligible = true,
            exclusion = null,
        )
    }

    internal fun overlapMillis(recordStart: Long, recordEnd: Long, start: Long, end: Long): Long =
        (minOf(recordEnd, end) - maxOf(recordStart, start)).coerceAtLeast(0L)
}

object PassiveFinality {
    const val BOOTSTRAP_LAG_MILLIS = 48L * 3_600_000L
    const val MIN_OBSERVED_LAGS = 30
    const val MIN_LAG_MILLIS = 6L * 3_600_000L
    const val MAX_LAG_MILLIS = 7L * 24L * 3_600_000L
    private const val P99_QUANTILE = 0.99
    private const val FIRST_RANK = 1

    fun watermark(
        localDayEnd: Long,
        configuredFamilies: Set<PassiveSourceFamily>,
        observations: List<SourceLag>,
        asOfTime: Long,
    ): PassiveFinalityDecision {
        val perSource = configuredFamilies.associateWith { family ->
            val sorted = observations.filter { it.sourceFamily == family }.map { it.lagMillis }.sorted()
            if (sorted.size < MIN_OBSERVED_LAGS) {
                BOOTSTRAP_LAG_MILLIS
            } else {
                val rank = ceil(P99_QUANTILE * sorted.size).toInt().coerceAtLeast(FIRST_RANK)
                sorted[rank - 1].coerceIn(MIN_LAG_MILLIS, MAX_LAG_MILLIS)
            }
        }
        val watermark = localDayEnd + (perSource.values.maxOrNull() ?: BOOTSTRAP_LAG_MILLIS)
        return PassiveFinalityDecision(watermark, asOfTime >= watermark, perSource)
    }
}

object PassiveDailyAggregator {
    const val TRANSFORMATION_VERSION = "passive-daily-v1"
    private const val MINUTE_MILLIS = 60_000L
    private const val SIX_PM_HOUR = 18

    @Suppress("LongParameterList")
    fun aggregate(
        date: LocalDate,
        zone: ZoneId,
        windows: List<PassiveFeatureWindow>,
        records: List<PassiveSourceRecord>,
        reads: List<PassiveSourceRead>,
        baselineSegment: String,
        asOfTime: Long,
        finality: PassiveFinalityDecision,
    ): PassiveDailyAggregate {
        val readStates = reads.associate { it.sourceFamily to it.state }
        val features = dailyFeatures(date, zone, windows, records, readStates, asOfTime)
        val excluded = dailyExclusions(windows)
        val domainCount = features.keys.filter { it.scored && it !in excluded }
            .mapNotNull { it.domain }
            .distinct()
            .size
        val exercisePreventedSecondDomain = domainCount < 2 && windows.any {
            it.quality.exerciseOverlapMillis > 0L &&
                it.features.any { row -> row.feature.domain == PassiveDomain.PHYSIOLOGY }
        }
        val status = when {
            !finality.final -> PassiveDataStatus.AVAILABLE_PROVISIONAL
            domainCount >= 2 -> PassiveDataStatus.AVAILABLE_FINAL
            exercisePreventedSecondDomain -> PassiveDataStatus.SUPPRESSED_EXERCISE
            else -> PassiveDataStatus.INSUFFICIENT_DATA
        }
        val updateTimes = records.mapNotNull { it.sourceUpdatedTime }
        return PassiveDailyAggregate(
            passiveDay = PassiveDay(
                date,
                status,
                features,
                excluded,
                baselineSegment,
                sourceUpdatedTime = updateTimes.maxOrNull() ?: records.maxOfOrNull { it.ingestedAt } ?: asOfTime,
                ingestedAt = records.maxOfOrNull { it.ingestedAt } ?: asOfTime,
            ),
            windows = windows,
            readStates = readStates,
            coverageByFeature = coverageByFeature(windows),
            missingFeatures = PassiveFeature.entries.filter { it !in features }.toSet(),
            exclusions = excluded.associateWith { "EXERCISE_OVERLAP" },
            finality = finality,
        )
    }

    private fun dailyFeatures(
        date: LocalDate,
        zone: ZoneId,
        windows: List<PassiveFeatureWindow>,
        records: List<PassiveSourceRecord>,
        readStates: Map<PassiveSourceFamily, PassiveReadState>,
        asOfTime: Long,
    ): Map<PassiveFeature, Double> = buildMap {
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val sleep = records.filter {
            it.kind == PassiveRecordKind.SLEEP_SESSION &&
                Instant.ofEpochMilli(it.eventEnd).atZone(zone).toLocalDate() == date
        }
        if (sleep.isNotEmpty()) {
            put(PassiveFeature.SLEEP_MINUTES, sleep.sumOf { (it.eventEnd - it.eventStart).toDouble() } / MINUTE_MILLIS)
            val sixPm = date.minusDays(1).atTime(SIX_PM_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
            put(
                PassiveFeature.SLEEP_ONSET_AFTER_SIX_PM,
                (sleep.minOf { it.eventStart } - sixPm).toDouble() / MINUTE_MILLIS,
            )
        }
        clippedIntervalValue(records, PassiveRecordKind.STEPS_INTERVAL, dayStart, dayEnd)?.let {
            put(PassiveFeature.STEPS, it)
        }
        val exercise = records.filter {
            it.kind == PassiveRecordKind.EXERCISE_SESSION &&
                PassiveWindowAggregator.overlapMillis(it.eventStart, it.eventEnd, dayStart, dayEnd) > 0L
        }
        if (exercise.isNotEmpty()) {
            val overlap = exercise.sumOf {
                PassiveWindowAggregator.overlapMillis(it.eventStart, it.eventEnd, dayStart, dayEnd)
            }
            put(PassiveFeature.ACTIVE_MINUTES, overlap.toDouble() / MINUTE_MILLIS)
        }
        listOf(
            PassiveFeature.RESTING_HEART_RATE,
            PassiveFeature.HRV_RMSSD,
            PassiveFeature.SPO2_PERCENT,
        ).forEach { feature ->
            val values = windows.asSequence()
                .filter { it.startInclusive < dayEnd && it.endExclusive > dayStart }
                .flatMap { it.features.asSequence() }
                .filter { it.feature == feature && it.eligible }
                .mapNotNull { it.value }
                .toList()
            if (values.isNotEmpty()) put(feature, values.average())
        }
        if (readStates[PassiveSourceFamily.USAGE_STATS] == PassiveReadState.SUCCESS) {
            val events = records.mapNotNull { it.toScreenEvent() }
            val rhythm = ScreenRhythm.days(events, listOf(date), zone, minOf(asOfTime, dayEnd)).getValue(date)
            rhythm.firstUnlockMinute?.let { put(PassiveFeature.FIRST_UNLOCK_MINUTE, it.toDouble()) }
            rhythm.screenMinutes?.let { put(PassiveFeature.SCREEN_MINUTES, it.toDouble()) }
        }
    }

    private fun clippedIntervalValue(
        records: List<PassiveSourceRecord>,
        kind: PassiveRecordKind,
        start: Long,
        end: Long,
    ): Double? {
        val matching = records.filter {
            it.kind == kind && it.value != null && it.eventEnd > it.eventStart &&
                PassiveWindowAggregator.overlapMillis(it.eventStart, it.eventEnd, start, end) > 0L
        }
        if (matching.isEmpty()) return null
        return matching.sumOf { record ->
            val overlap = PassiveWindowAggregator.overlapMillis(record.eventStart, record.eventEnd, start, end)
            requireNotNull(record.value) * overlap.toDouble() / (record.eventEnd - record.eventStart)
        }
    }

    private fun PassiveSourceRecord.toScreenEvent(): ScreenEvent? {
        val eventKind = when (kind) {
            PassiveRecordKind.SCREEN_INTERACTIVE -> ScreenEventKind.INTERACTIVE
            PassiveRecordKind.SCREEN_NON_INTERACTIVE -> ScreenEventKind.NON_INTERACTIVE
            PassiveRecordKind.SCREEN_UNLOCKED -> ScreenEventKind.UNLOCKED
            else -> return null
        }
        return ScreenEvent(eventKind, eventStart)
    }

    private fun dailyExclusions(windows: List<PassiveFeatureWindow>): Set<PassiveFeature> =
        windows.flatMap { it.features }
            .groupBy { it.feature }
            .filterValues { rows -> rows.none { it.eligible } && rows.any { it.exclusion == "EXERCISE_OVERLAP" } }
            .keys

    private fun coverageByFeature(windows: List<PassiveFeatureWindow>): Map<PassiveFeature, Double> =
        windows.flatMap { it.features }
            .groupBy { it.feature }
            .mapValues { (_, rows) -> rows.map { it.coverage }.average() }
}
