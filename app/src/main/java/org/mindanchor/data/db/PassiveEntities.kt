package org.mindanchor.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "passive_raw_provenance", indices = [Index("eventStart"), Index("eventEnd"), Index("sourceFamily")])
data class PassiveRawProvenanceEntity(
    @PrimaryKey val id: String,
    val sourceFamily: String,
    val recordKind: String,
    val eventStart: Long,
    val eventEnd: Long,
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
)

@Entity(
    tableName = "passive_raw_samples",
    foreignKeys = [ForeignKey(
        entity = PassiveRawProvenanceEntity::class,
        parentColumns = ["id"],
        childColumns = ["provenanceId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("ingestedAt")],
)
data class PassiveRawSampleEntity(
    @PrimaryKey val provenanceId: String,
    val value: Double?,
    val ingestedAt: Long,
)

data class PassiveStoredRecord(
    @Embedded val provenance: PassiveRawProvenanceEntity,
    @ColumnInfo(name = "rawValue") val value: Double?,
)

@Entity(tableName = "passive_source_reads", indices = [Index("attemptedAt"), Index("sourceFamily")])
data class PassiveSourceReadEntity(
    @PrimaryKey val id: String,
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

@Entity(tableName = "passive_source_lags", indices = [Index("sourceFamily"), Index("observedAt")])
data class PassiveSourceLagEntity(
    @PrimaryKey val id: String,
    val sourceFamily: String,
    val eventEnd: Long,
    val observedUpdatedAt: Long,
    val ingestedAt: Long,
    val lagMillis: Long,
    val usedIngestedAtFallback: Boolean,
    val observedAt: Long,
)

@Entity(tableName = "passive_baseline_segments", indices = [Index("openedAt")])
data class PassiveBaselineSegmentEntity(
    @PrimaryKey val id: String,
    val openedAt: Long,
    val fingerprintsJson: String,
    val windowTransformationVersion: String,
    val dailyTransformationVersion: String,
)

@Entity(tableName = "passive_pipeline_runs", indices = [Index("startedAt"), Index("completedAt")])
data class PassivePipelineRunEntity(
    @PrimaryKey val id: String,
    val startedAt: Long,
    val completedAt: Long,
    val scanStart: Long,
    val scanEnd: Long,
    val zoneId: String,
    val historyPermissionGranted: Boolean,
    val firstSuccessfulPermissionedRun: Boolean,
    val result: String,
    val sourceStatesJson: String,
)

@Entity(tableName = "passive_window_revisions", indices = [
    Index("windowStart"),
    Index("baselineSegment"),
    Index(value = ["windowStart", "contentHash"]),
])
data class PassiveWindowRevisionEntity(
    @PrimaryKey val id: String,
    val windowStart: Long,
    val windowEnd: Long,
    val asOfTime: Long,
    val zoneId: String,
    val zoneOffsetSeconds: Int,
    val wakeRelativeMinute: Int?,
    val baselineSegment: String,
    val featureRowsJson: String,
    val heartRateCoverage: Double,
    val physiologyEligible: Boolean,
    val exerciseOverlapMillis: Long,
    val provenanceRecordIdsJson: String,
    val missingnessJson: String,
    val exclusionsJson: String,
    val transformationVersion: String,
    val sourceUpdatedTime: Long,
    val ingestedAt: Long,
    val final: Boolean,
    val revisionReason: String,
    val contentHash: String,
)

@Entity(tableName = "passive_daily_revisions", indices = [
    Index("localDate"),
    Index("baselineSegment"),
    Index(value = ["localDate", "contentHash"]),
])
data class PassiveDailyRevisionEntity(
    @PrimaryKey val id: String,
    val localDate: String,
    val asOfTime: Long,
    val dataStatus: String,
    val featuresJson: String,
    val excludedFeaturesJson: String,
    val baselineSegment: String,
    val sourceUpdatedTime: Long,
    val ingestedAt: Long,
    val sourceReadStatesJson: String,
    val coverageJson: String,
    val missingnessJson: String,
    val exclusionsJson: String,
    val provenanceJson: String,
    val windowTransformationVersion: String,
    val dailyTransformationVersion: String,
    val watermark: Long,
    val revisionReason: String,
    val contentHash: String,
)

@Entity(tableName = "passive_observation_decisions", indices = [
    Index("localDate"),
    Index("baselineSegment"),
    Index(value = ["localDate", "contentHash"]),
])
data class PassiveObservationDecisionEntity(
    @PrimaryKey val id: String,
    val localDate: String,
    val asOfTime: Long,
    val dataStatus: String,
    val observationState: String,
    val baselineSegment: String,
    val calibrationSeed: Long?,
    val frozenBaselineAsOfTime: Long?,
    val frozenBaselineThroughDay: String?,
    val decisionJson: String,
    val revisionReason: String,
    val contentHash: String,
)
