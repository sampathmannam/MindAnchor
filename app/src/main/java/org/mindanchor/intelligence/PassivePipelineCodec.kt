@file:Suppress("TooManyFunctions")

package org.mindanchor.intelligence

import java.security.MessageDigest
import java.time.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.mindanchor.data.db.PassiveDailyRevisionEntity
import org.mindanchor.data.db.PassiveObservationDecisionEntity
import org.mindanchor.data.db.PassiveSourceLagEntity
import org.mindanchor.data.db.PassiveSourceReadEntity
import org.mindanchor.data.db.PassiveWindowRevisionEntity

enum class RevisionReason { INITIAL, CONTENT_CHANGED, FINALITY, BACKFILL }

@OptIn(ExperimentalSerializationApi::class)
object PassivePipelineCodec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        prettyPrint = false
    }

    fun contentHash(canonicalJson: String): String = MessageDigest.getInstance("SHA-256")
        .digest(canonicalJson.encodeToByteArray())
        .joinToString("") { "%02x".format(it) }

    fun rawIdentity(record: PassiveSourceRecord): String = contentHash(
        json.encodeToString(RawIdentity.from(record)),
    )

    fun calibrationSeed(segment: String, frozenAsOfTime: Long, calibrationVersion: String): Long =
        PassiveSeed.firstSigned64Bits("$segment|$frozenAsOfTime|$calibrationVersion")

    fun shouldAppend(previousContentHash: String?, nextContentHash: String, reason: RevisionReason): Boolean =
        previousContentHash != nextContentHash || reason == RevisionReason.FINALITY || reason == RevisionReason.BACKFILL

    fun sourceReadEntity(read: PassiveSourceRead, runId: String): PassiveSourceReadEntity {
        val identity = SourceReadIdentity(
            runId = runId,
            sourceFamily = read.sourceFamily.name,
            state = read.state.name,
            rangeStart = read.range.startInclusive,
            rangeEnd = read.range.endExclusive,
            zoneId = read.range.zoneId,
            attemptedAt = read.attemptedAt,
            recordCount = read.records.size,
            errorCode = read.errorCode,
        )
        return PassiveSourceReadEntity(
            id = contentHash(json.encodeToString(identity)),
            runId = runId,
            sourceFamily = read.sourceFamily.name,
            state = read.state.name,
            rangeStart = read.range.startInclusive,
            rangeEnd = read.range.endExclusive,
            zoneId = read.range.zoneId,
            attemptedAt = read.attemptedAt,
            recordCount = read.records.size,
            errorCode = read.errorCode,
        )
    }

    fun sourceLagEntity(record: PassiveSourceRecord, observedAt: Long): PassiveSourceLagEntity {
        val observedUpdatedAt = record.sourceUpdatedTime ?: record.ingestedAt
        val usedFallback = record.sourceUpdatedTime == null
        val identity = SourceLagIdentity(rawProvenanceId = rawIdentity(record))
        return PassiveSourceLagEntity(
            id = contentHash(json.encodeToString(identity)),
            sourceFamily = record.sourceFamily.name,
            eventEnd = record.eventEnd,
            observedUpdatedAt = observedUpdatedAt,
            ingestedAt = record.ingestedAt,
            lagMillis = (observedUpdatedAt - record.eventEnd).coerceAtLeast(0L),
            usedIngestedAtFallback = usedFallback,
            observedAt = observedAt,
        )
    }

    fun sortedFingerprintJson(fingerprints: Set<PassiveSourceFingerprint>): String = json.encodeToString(
        fingerprints.sortedBy(PassiveSourceFingerprint::canonical).map(FingerprintDto::from),
    )

    fun decodeFingerprints(canonicalJson: String): Set<PassiveSourceFingerprint> =
        json.decodeFromString<List<FingerprintDto>>(canonicalJson).map(FingerprintDto::toDomain).toSet()

    @Suppress("LongParameterList")
    fun windowEntity(
        window: PassiveFeatureWindow,
        baselineSegment: String,
        sourceUpdatedTime: Long,
        ingestedAt: Long,
        final: Boolean,
        reason: RevisionReason,
        asOfTime: Long,
    ): PassiveWindowRevisionEntity {
        val content = WindowContentDto.from(
            window,
            baselineSegment,
            sourceUpdatedTime,
            ingestedAt,
            final,
        )
        val canonical = json.encodeToString(content)
        val hash = contentHash(canonical)
        return PassiveWindowRevisionEntity(
            id = revisionId(window.startInclusive.toString(), asOfTime, hash, reason),
            windowStart = window.startInclusive,
            windowEnd = window.endExclusive,
            asOfTime = asOfTime,
            zoneId = window.zoneId,
            zoneOffsetSeconds = window.zoneOffsetSeconds,
            wakeRelativeMinute = window.quality.wakeRelativeMinute,
            baselineSegment = baselineSegment,
            featureRowsJson = json.encodeToString(content.featureRows),
            heartRateCoverage = window.quality.heartRateCoverage,
            physiologyEligible = window.quality.physiologyEligible,
            exerciseOverlapMillis = window.quality.exerciseOverlapMillis,
            provenanceRecordIdsJson = json.encodeToString(content.provenanceRecordIds),
            missingnessJson = json.encodeToString(content.missingFeatures),
            exclusionsJson = json.encodeToString(content.exclusions),
            transformationVersion = PassiveWindowAggregator.TRANSFORMATION_VERSION,
            sourceUpdatedTime = sourceUpdatedTime,
            ingestedAt = ingestedAt,
            final = final,
            revisionReason = reason.name,
            contentHash = hash,
        )
    }

    fun dailyEntity(
        aggregate: PassiveDailyAggregate,
        provenanceIds: Set<String>,
        reason: RevisionReason,
        asOfTime: Long,
    ): PassiveDailyRevisionEntity {
        val content = DailyContentDto.from(aggregate, provenanceIds)
        val canonical = json.encodeToString(content)
        val hash = contentHash(canonical)
        val day = aggregate.passiveDay
        return PassiveDailyRevisionEntity(
            id = revisionId(day.day.toString(), asOfTime, hash, reason),
            localDate = day.day.toString(),
            asOfTime = asOfTime,
            dataStatus = day.dataStatus.name,
            featuresJson = encodeFeatureMap(day.features),
            excludedFeaturesJson = encodeFeatureSet(day.excludedFeatures),
            baselineSegment = day.baselineSegment,
            sourceUpdatedTime = day.sourceUpdatedTime,
            ingestedAt = day.ingestedAt,
            sourceReadStatesJson = encodeReadStateMap(aggregate.readStates),
            coverageJson = encodeFeatureMap(aggregate.coverageByFeature),
            missingnessJson = encodeFeatureSet(aggregate.missingFeatures),
            exclusionsJson = encodeFeatureStringMap(aggregate.exclusions),
            provenanceJson = json.encodeToString(provenanceIds.sorted()),
            windowTransformationVersion = PassiveWindowAggregator.TRANSFORMATION_VERSION,
            dailyTransformationVersion = PassiveDailyAggregator.TRANSFORMATION_VERSION,
            watermark = aggregate.finality.watermark,
            revisionReason = reason.name,
            contentHash = hash,
        )
    }

    fun dailyToDomain(entity: PassiveDailyRevisionEntity): PassiveDay = PassiveDay(
        day = LocalDate.parse(entity.localDate),
        dataStatus = PassiveDataStatus.valueOf(entity.dataStatus),
        features = decodeFeatureMap(entity.featuresJson),
        excludedFeatures = decodeFeatureSet(entity.excludedFeaturesJson),
        baselineSegment = entity.baselineSegment,
        sourceUpdatedTime = entity.sourceUpdatedTime,
        ingestedAt = entity.ingestedAt,
    )

    fun decisionEntity(
        observation: PassiveObservation,
        reason: RevisionReason,
    ): PassiveObservationDecisionEntity {
        val content = ObservationDto.from(observation)
        val canonical = json.encodeToString(content)
        val hash = contentHash(canonical)
        return PassiveObservationDecisionEntity(
            id = revisionId(observation.day.toString(), observation.asOfTime, hash, reason),
            localDate = observation.day.toString(),
            asOfTime = observation.asOfTime,
            dataStatus = observation.dataStatus.name,
            observationState = observation.state.name,
            baselineSegment = observation.baselineSegment,
            calibrationSeed = observation.calibration?.seed,
            frozenBaselineAsOfTime = observation.frozenBaselineAsOfTime,
            frozenBaselineThroughDay = observation.frozenBaselineThroughDay?.toString(),
            decisionJson = canonical,
            revisionReason = reason.name,
            contentHash = hash,
        )
    }

    fun decisionToDomain(entity: PassiveObservationDecisionEntity): PassiveObservation {
        PassiveDataStatus.valueOf(entity.dataStatus)
        PassiveObservationState.valueOf(entity.observationState)
        return json.decodeFromString<ObservationDto>(entity.decisionJson).toDomain()
    }

    private fun revisionId(logicalKey: String, asOfTime: Long, hash: String, reason: RevisionReason): String =
        contentHash("$logicalKey|$asOfTime|$hash|${reason.name}")

    private fun encodeFeatureMap(values: Map<PassiveFeature, Double>): String = json.encodeToString(
        values.entries.sortedBy { it.key.name }.associate { it.key.name to it.value },
    )

    private fun decodeFeatureMap(encoded: String): Map<PassiveFeature, Double> =
        json.decodeFromString<Map<String, Double>>(encoded).entries.associate { (key, value) ->
            PassiveFeature.valueOf(key) to value
        }

    private fun encodeFeatureSet(values: Set<PassiveFeature>): String =
        json.encodeToString(values.map { it.name }.sorted())

    private fun decodeFeatureSet(encoded: String): Set<PassiveFeature> =
        json.decodeFromString<List<String>>(encoded).map(PassiveFeature::valueOf).toSet()

    private fun encodeFeatureStringMap(values: Map<PassiveFeature, String>): String = json.encodeToString(
        values.entries.sortedBy { it.key.name }.associate { it.key.name to it.value },
    )

    private fun encodeReadStateMap(values: Map<PassiveSourceFamily, PassiveReadState>): String = json.encodeToString(
        values.entries.sortedBy { it.key.name }.associate { it.key.name to it.value.name },
    )
}

@Serializable
private data class SourceReadIdentity(
    val runId: String,
    val sourceFamily: String,
    val state: String,
    val rangeStart: Long,
    val rangeEnd: Long,
    val zoneId: String,
    val attemptedAt: Long,
    val recordCount: Int,
    val errorCode: String?,
)

@Serializable
private data class RawIdentity(
    val sourceFamily: String,
    val recordKind: String,
    val eventStart: Long,
    val eventEnd: Long,
    val value: Double?,
    val unit: String,
    val dataOriginPackage: String,
    val deviceManufacturer: String?,
    val deviceModel: String?,
    val deviceType: String?,
    val sourceUpdatedTime: Long?,
    val zoneId: String,
    val zoneOffsetSeconds: Int,
    val recordId: String,
    val recordVersion: Long,
) {
    companion object {
        fun from(record: PassiveSourceRecord) = RawIdentity(
            sourceFamily = record.sourceFamily.name,
            recordKind = record.kind.name,
            eventStart = record.eventStart,
            eventEnd = record.eventEnd,
            value = record.value,
            unit = record.unit,
            dataOriginPackage = record.dataOriginPackage,
            deviceManufacturer = record.deviceManufacturer,
            deviceModel = record.deviceModel,
            deviceType = record.deviceType,
            sourceUpdatedTime = record.sourceUpdatedTime,
            zoneId = record.zoneId,
            zoneOffsetSeconds = record.zoneOffsetSeconds,
            recordId = record.recordId,
            recordVersion = record.recordVersion,
        )
    }
}

@Serializable
private data class SourceLagIdentity(val rawProvenanceId: String)

@Serializable
private data class FingerprintDto(
    val sourceFamily: String,
    val dataOriginPackage: String,
    val deviceManufacturer: String?,
    val deviceModel: String?,
    val deviceType: String?,
) {
    fun toDomain() = PassiveSourceFingerprint(
        PassiveSourceFamily.valueOf(sourceFamily),
        dataOriginPackage,
        deviceManufacturer,
        deviceModel,
        deviceType,
    )

    companion object {
        fun from(value: PassiveSourceFingerprint) = FingerprintDto(
            value.sourceFamily.name,
            value.dataOriginPackage,
            value.deviceManufacturer,
            value.deviceModel,
            value.deviceType,
        )
    }
}

@Serializable
private data class WindowFeatureDto(
    val feature: String,
    val value: Double?,
    val unit: String,
    val coverage: Double,
    val eligible: Boolean,
    val exclusion: String?,
) {
    companion object {
        fun from(value: PassiveWindowFeature) = WindowFeatureDto(
            value.feature.name,
            value.value,
            value.unit,
            value.coverage,
            value.eligible,
            value.exclusion,
        )
    }
}

private val windowFeatureComparator = compareBy<WindowFeatureDto> { it.feature }
    .thenBy { it.unit }
    .thenBy { it.value }
    .thenBy { it.coverage }
    .thenBy { it.eligible }
    .thenBy { it.exclusion }

@Serializable
private data class StringValueDto(val name: String, val value: String)

@Serializable
private data class StringDoubleDto(val name: String, val value: Double)

@Serializable
private data class StringLongDto(val name: String, val value: Long)

@Serializable
private data class WindowContentDto(
    val startInclusive: Long,
    val endExclusive: Long,
    val zoneId: String,
    val zoneOffsetSeconds: Int,
    val wakeRelativeMinute: Int?,
    val baselineSegment: String,
    val featureRows: List<WindowFeatureDto>,
    val heartRateCoverage: Double,
    val physiologyEligible: Boolean,
    val exerciseOverlapMillis: Long,
    val provenanceRecordIds: List<String>,
    val missingFeatures: List<String>,
    val exclusions: List<StringValueDto>,
    val transformationVersion: String,
    val sourceUpdatedTime: Long,
    val ingestedAt: Long,
    val final: Boolean,
) {
    companion object {
        fun from(
            window: PassiveFeatureWindow,
            baselineSegment: String,
            sourceUpdatedTime: Long,
            ingestedAt: Long,
            final: Boolean,
        ): WindowContentDto {
            val rows = window.features.map(WindowFeatureDto::from).sortedWith(windowFeatureComparator)
            val present = rows.map { PassiveFeature.valueOf(it.feature) }.toSet()
            return WindowContentDto(
                window.startInclusive,
                window.endExclusive,
                window.zoneId,
                window.zoneOffsetSeconds,
                window.quality.wakeRelativeMinute,
                baselineSegment,
                rows,
                window.quality.heartRateCoverage,
                window.quality.physiologyEligible,
                window.quality.exerciseOverlapMillis,
                window.provenanceRecordIds.distinct().sorted(),
                PassiveFeature.entries.filter { it !in present }.map { it.name }.sorted(),
                rows.mapNotNull { row -> row.exclusion?.let { StringValueDto(row.feature, it) } }
                    .sortedBy { it.name },
                PassiveWindowAggregator.TRANSFORMATION_VERSION,
                sourceUpdatedTime,
                ingestedAt,
                final,
            )
        }
    }
}

@Serializable
private data class DailyWindowDto(
    val startInclusive: Long,
    val endExclusive: Long,
    val zoneId: String,
    val zoneOffsetSeconds: Int,
    val quality: WindowQualityDto,
    val featureRows: List<WindowFeatureDto>,
    val provenanceRecordIds: List<String>,
) {
    companion object {
        fun from(window: PassiveFeatureWindow) = DailyWindowDto(
            window.startInclusive,
            window.endExclusive,
            window.zoneId,
            window.zoneOffsetSeconds,
            WindowQualityDto.from(window.quality),
            window.features.map(WindowFeatureDto::from).sortedWith(windowFeatureComparator),
            window.provenanceRecordIds.distinct().sorted(),
        )
    }
}

@Serializable
private data class WindowQualityDto(
    val heartRateCoverage: Double,
    val physiologyEligible: Boolean,
    val exerciseOverlapMillis: Long,
    val wakeRelativeMinute: Int?,
) {
    companion object {
        fun from(value: PassiveWindowQuality) = WindowQualityDto(
            value.heartRateCoverage,
            value.physiologyEligible,
            value.exerciseOverlapMillis,
            value.wakeRelativeMinute,
        )
    }
}

@Serializable
private data class SourceLagDto(
    val sourceFamily: String,
    val lagMillis: Long,
    val usedIngestedAtFallback: Boolean,
) {
    companion object {
        fun from(value: SourceLag) = SourceLagDto(
            value.sourceFamily.name,
            value.lagMillis,
            value.usedIngestedAtFallback,
        )
    }
}

@Serializable
private data class DailyContentDto(
    val localDate: String,
    val dataStatus: String,
    val features: List<StringDoubleDto>,
    val excludedFeatures: List<String>,
    val baselineSegment: String,
    val sourceUpdatedTime: Long,
    val ingestedAt: Long,
    val windows: List<DailyWindowDto>,
    val readStates: List<StringValueDto>,
    val coverageByFeature: List<StringDoubleDto>,
    val missingFeatures: List<String>,
    val exclusions: List<StringValueDto>,
    val finalityWatermark: Long,
    val final: Boolean,
    val perSourceLagMillis: List<StringLongDto>,
    val sourceLags: List<SourceLagDto>,
    val provenanceIds: List<String>,
    val windowTransformationVersion: String,
    val dailyTransformationVersion: String,
    val watermark: Long,
) {
    companion object {
        fun from(aggregate: PassiveDailyAggregate, provenanceIds: Set<String>): DailyContentDto {
            val day = aggregate.passiveDay
            return DailyContentDto(
                day.day.toString(),
                day.dataStatus.name,
                day.features.entries.map { StringDoubleDto(it.key.name, it.value) }.sortedBy { it.name },
                day.excludedFeatures.map { it.name }.sorted(),
                day.baselineSegment,
                day.sourceUpdatedTime,
                day.ingestedAt,
                aggregate.windows.map(DailyWindowDto::from).sortedBy { it.startInclusive },
                aggregate.readStates.entries.map { StringValueDto(it.key.name, it.value.name) }.sortedBy { it.name },
                aggregate.coverageByFeature.entries.map { StringDoubleDto(it.key.name, it.value) }.sortedBy { it.name },
                aggregate.missingFeatures.map { it.name }.sorted(),
                aggregate.exclusions.entries.map { StringValueDto(it.key.name, it.value) }.sortedBy { it.name },
                aggregate.finality.watermark,
                aggregate.finality.final,
                aggregate.finality.perSourceLagMillis.entries.map { StringLongDto(it.key.name, it.value) }
                    .sortedBy { it.name },
                aggregate.sourceLags.map(SourceLagDto::from).sortedWith(
                    compareBy<SourceLagDto> { it.sourceFamily }
                        .thenBy { it.lagMillis }
                        .thenBy { it.usedIngestedAtFallback },
                ),
                provenanceIds.sorted(),
                PassiveWindowAggregator.TRANSFORMATION_VERSION,
                PassiveDailyAggregator.TRANSFORMATION_VERSION,
                aggregate.finality.watermark,
            )
        }
    }
}

@Serializable
private data class FeatureEvidenceDto(
    val feature: String,
    val value: Double,
    val centre: Double,
    val scale: Double,
    val zScore: Double,
    val referenceCount: Int,
    val pooledStratum: Boolean,
) {
    fun toDomain() = FeatureEvidence(
        PassiveFeature.valueOf(feature), value, centre, scale, zScore, referenceCount, pooledStratum,
    )

    companion object {
        fun from(value: FeatureEvidence) = FeatureEvidenceDto(
            value.feature.name,
            value.value,
            value.centre,
            value.scale,
            value.zScore,
            value.referenceCount,
            value.pooledStratum,
        )
    }
}

@Serializable
private data class DomainEvidenceDto(
    val domain: String,
    val score: Double,
    val features: List<FeatureEvidenceDto>,
) {
    fun toDomain() = DomainEvidence(
        PassiveDomain.valueOf(domain), score, features.map(FeatureEvidenceDto::toDomain),
    )

    companion object {
        fun from(value: DomainEvidence) = DomainEvidenceDto(
            value.domain.name,
            value.score,
            value.features.map(FeatureEvidenceDto::from).sortedBy { it.feature },
        )
    }
}

@Serializable
private data class CalibrationConfigurationDto(
    val blockDays: Int,
    val calibrationDays: Int,
    val simulations: Int,
    val targetEpisodesPer30: Double,
    val refractoryDays: Int,
) {
    fun toDomain() = CalibrationConfiguration(
        blockDays, calibrationDays, simulations, targetEpisodesPer30, refractoryDays,
    )

    companion object {
        fun from(value: CalibrationConfiguration) = CalibrationConfigurationDto(
            value.blockDays,
            value.calibrationDays,
            value.simulations,
            value.targetEpisodesPer30,
            value.refractoryDays,
        )
    }
}

@Serializable
private data class CalibrationResultDto(
    val threshold: Double,
    val expectedEpisodesPer30: Double,
    val simulations: Int,
    val seed: Long,
    val configuration: CalibrationConfigurationDto,
) {
    fun toDomain() = CalibrationResult(
        threshold, expectedEpisodesPer30, simulations, seed, configuration.toDomain(),
    )

    companion object {
        fun from(value: CalibrationResult) = CalibrationResultDto(
            value.threshold,
            value.expectedEpisodesPer30,
            value.simulations,
            value.seed,
            CalibrationConfigurationDto.from(value.configuration),
        )
    }
}

@Serializable
private data class ShiftDomainDto(
    val domain: String,
    val standardizedDisagreement: Double,
    val features: List<String>,
) {
    fun toDomain() = BaselineShiftDomainEvidence(
        PassiveDomain.valueOf(domain), standardizedDisagreement, features.map(PassiveFeature::valueOf),
    )

    companion object {
        fun from(value: BaselineShiftDomainEvidence) = ShiftDomainDto(
            value.domain.name,
            value.standardizedDisagreement,
            value.features.map { it.name }.sorted(),
        )
    }
}

@Serializable
private data class BaselineShiftDto(
    val candidateDays: Int,
    val standardizedDisagreementThreshold: Double,
    val minimumCorroboratingDomains: Int,
    val persistenceDays: Int,
    val comparisonPopulation: String,
    val domains: List<ShiftDomainDto>,
    val disagrees: Boolean,
) {
    fun toDomain() = BaselineShiftAssessment(
        candidateDays,
        standardizedDisagreementThreshold,
        minimumCorroboratingDomains,
        persistenceDays,
        BaselineComparisonPopulation.valueOf(comparisonPopulation),
        domains.map(ShiftDomainDto::toDomain),
        disagrees,
    )

    companion object {
        fun from(value: BaselineShiftAssessment) = BaselineShiftDto(
            value.candidateDays,
            value.standardizedDisagreementThreshold,
            value.minimumCorroboratingDomains,
            value.persistenceDays,
            value.comparisonPopulation.name,
            value.domains.map(ShiftDomainDto::from).sortedBy { it.domain },
            value.disagrees,
        )
    }
}

@Serializable
private data class ObservationDto(
    val day: String,
    val asOfTime: Long,
    val dataStatus: String,
    val state: String,
    val threshold: Double?,
    val crossed: Boolean,
    val baselineDays: Int,
    val frozenBaselineAsOfTime: Long?,
    val frozenBaselineThroughDay: String?,
    val baselineSegment: String,
    val domains: List<DomainEvidenceDto>,
    val calibration: CalibrationResultDto?,
    val baselineShift: BaselineShiftDto?,
    val explanation: String,
) {
    fun toDomain() = PassiveObservation(
        LocalDate.parse(day),
        asOfTime,
        PassiveDataStatus.valueOf(dataStatus),
        PassiveObservationState.valueOf(state),
        threshold,
        crossed,
        baselineDays,
        frozenBaselineAsOfTime,
        frozenBaselineThroughDay?.let(LocalDate::parse),
        baselineSegment,
        domains.map(DomainEvidenceDto::toDomain),
        calibration?.toDomain(),
        baselineShift?.toDomain(),
        explanation,
    )

    companion object {
        fun from(value: PassiveObservation) = ObservationDto(
            value.day.toString(),
            value.asOfTime,
            value.dataStatus.name,
            value.state.name,
            value.threshold,
            value.crossed,
            value.baselineDays,
            value.frozenBaselineAsOfTime,
            value.frozenBaselineThroughDay?.toString(),
            value.baselineSegment,
            value.domains.map(DomainEvidenceDto::from).sortedBy { it.domain },
            value.calibration?.let(CalibrationResultDto::from),
            value.baselineShift?.let(BaselineShiftDto::from),
            value.explanation,
        )
    }
}
