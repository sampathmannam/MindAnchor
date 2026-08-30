package org.mindanchor.intelligence

import java.nio.ByteBuffer
import java.security.MessageDigest

private fun canonicalPart(value: String?): String = value?.let { "${it.length}:$it" } ?: "null:"

enum class PassiveSourceFamily {
    HEART_RATE,
    RESTING_HEART_RATE,
    HRV_RMSSD,
    SLEEP,
    STEPS,
    EXERCISE,
    OXYGEN_SATURATION,
    USAGE_STATS,

    ;

    internal fun accepts(kind: PassiveRecordKind): Boolean = when (this) {
        HEART_RATE -> kind == PassiveRecordKind.HEART_RATE_SAMPLE
        RESTING_HEART_RATE -> kind == PassiveRecordKind.RESTING_HEART_RATE
        HRV_RMSSD -> kind == PassiveRecordKind.HRV_RMSSD
        SLEEP -> kind == PassiveRecordKind.SLEEP_SESSION
        STEPS -> kind == PassiveRecordKind.STEPS_INTERVAL
        EXERCISE -> kind == PassiveRecordKind.EXERCISE_SESSION
        OXYGEN_SATURATION -> kind == PassiveRecordKind.SPO2
        USAGE_STATS -> kind in setOf(
            PassiveRecordKind.SCREEN_INTERACTIVE,
            PassiveRecordKind.SCREEN_NON_INTERACTIVE,
            PassiveRecordKind.SCREEN_UNLOCKED,
        )
    }
}

enum class PassiveRecordKind {
    HEART_RATE_SAMPLE,
    RESTING_HEART_RATE,
    HRV_RMSSD,
    SLEEP_SESSION,
    STEPS_INTERVAL,
    EXERCISE_SESSION,
    SPO2,
    SCREEN_INTERACTIVE,
    SCREEN_NON_INTERACTIVE,
    SCREEN_UNLOCKED,
}

enum class PassiveReadState {
    SUCCESS,
    UNAVAILABLE,
    PERMISSION_DENIED,
    READ_FAILURE_TRANSIENT,
    READ_FAILURE_PERMANENT,
}

data class PassiveReadRange(
    val startInclusive: Long,
    val endExclusive: Long,
    val zoneId: String,
) {
    init {
        require(startInclusive < endExclusive)
    }
}

data class PassiveSourceRecord(
    val sourceFamily: PassiveSourceFamily,
    val kind: PassiveRecordKind,
    val eventStart: Long,
    val eventEnd: Long,
    val value: Double?,
    val unit: String,
    val dataOriginPackage: String,
    val deviceManufacturer: String?,
    val deviceModel: String?,
    val deviceType: String?,
    val sourceUpdatedTime: Long?,
    val ingestedAt: Long,
    val zoneId: String,
    val zoneOffsetSeconds: Int,
    val recordId: String,
    val recordVersion: Long,
) {
    init {
        require(sourceFamily.accepts(kind))
        require(eventStart <= eventEnd)
        require(value == null || value.isFinite())
        require(unit.isNotBlank())
        require(dataOriginPackage.isNotBlank())
        require(zoneId.isNotBlank())
        require(recordId.isNotBlank())
        require(recordVersion >= 0L)
    }
}

data class PassiveSourceRead(
    val sourceFamily: PassiveSourceFamily,
    val state: PassiveReadState,
    val range: PassiveReadRange,
    val attemptedAt: Long,
    val records: List<PassiveSourceRecord> = emptyList(),
    val errorCode: String? = null,
) {
    init {
        require(state == PassiveReadState.SUCCESS || records.isEmpty())
        require(state == PassiveReadState.SUCCESS || !errorCode.isNullOrBlank())
        require(records.all { it.sourceFamily == sourceFamily })
    }
}

interface PassiveRecordSource {
    suspend fun read(range: PassiveReadRange): List<PassiveSourceRead>
}

data class PassiveSourceFingerprint(
    val sourceFamily: PassiveSourceFamily,
    val dataOriginPackage: String,
    val deviceManufacturer: String?,
    val deviceModel: String?,
    val deviceType: String?,
) {
    fun canonical(): String = listOf(
        sourceFamily.name,
        dataOriginPackage,
        deviceManufacturer,
        deviceModel,
        deviceType,
    ).joinToString("") { canonicalPart(it) }
}

data class PassiveWindowQuality(
    val heartRateCoverage: Double,
    val physiologyEligible: Boolean,
    val exerciseOverlapMillis: Long,
    val wakeRelativeMinute: Int?,
)

data class PassiveWindowFeature(
    val feature: PassiveFeature,
    val value: Double?,
    val unit: String,
    val coverage: Double,
    val eligible: Boolean,
    val exclusion: String?,
)

data class PassiveFeatureWindow(
    val startInclusive: Long,
    val endExclusive: Long,
    val zoneId: String,
    val zoneOffsetSeconds: Int,
    val quality: PassiveWindowQuality,
    val features: List<PassiveWindowFeature>,
    val provenanceRecordIds: List<String>,
)

data class PassiveFinalityDecision(
    val watermark: Long,
    val final: Boolean,
    val perSourceLagMillis: Map<PassiveSourceFamily, Long>,
)

data class PassiveDailyAggregate(
    val passiveDay: PassiveDay,
    val windows: List<PassiveFeatureWindow>,
    val readStates: Map<PassiveSourceFamily, PassiveReadState>,
    val coverageByFeature: Map<PassiveFeature, Double>,
    val missingFeatures: Set<PassiveFeature>,
    val exclusions: Map<PassiveFeature, String>,
    val finality: PassiveFinalityDecision,
    val sourceLags: List<SourceLag>,
)

data class SourceLag(
    val sourceFamily: PassiveSourceFamily,
    val lagMillis: Long,
    val usedIngestedAtFallback: Boolean,
)

object PassiveSeed {
    fun digest(material: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(material.encodeToByteArray())

    fun sha256(material: String): String =
        digest(material).joinToString("") { "%02x".format(it) }

    fun firstSigned64Bits(material: String): Long = ByteBuffer.wrap(digest(material)).long
}

object PassiveBaselineSegment {
    fun id(
        configuredFingerprints: Set<PassiveSourceFingerprint>,
        windowTransformationVersion: String,
        dailyTransformationVersion: String,
    ): String {
        val fingerprints = configuredFingerprints.map { it.canonical() }.sorted()
        val material = buildList {
            add(fingerprints.size.toString())
            addAll(fingerprints)
            add(windowTransformationVersion)
            add(dailyTransformationVersion)
        }.joinToString("") { canonicalPart(it) }
        return PassiveSeed.sha256(material)
    }
}
