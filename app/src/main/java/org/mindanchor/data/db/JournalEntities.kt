package org.mindanchor.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "journal_entries", indices = [Index("localDate"), Index("createdAt")])
data class JournalEntryEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val localDate: String,
    val title: String,
    val body: String,
    val kind: String,
    val sourceDeviceId: String,
    val deletedAt: Long?,
)

@Entity(
    tableName = "journal_context",
    foreignKeys = [
        ForeignKey(
            entity = JournalEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("entryId"), Index("recordType")],
)
data class JournalContextEntity(
    @PrimaryKey val id: String,
    val entryId: String,
    val recordType: String,
    val key: String,
    val value: String,
    val sourceStart: Int?,
    val sourceEnd: Int?,
    val confidence: Double,
    val extractorVersion: String,
    val createdAt: Long,
)

@Entity(tableName = "morning_measures", indices = [Index(value = ["localDate"], unique = true)])
data class MorningMeasureEntity(
    @PrimaryKey val id: String,
    val localDate: String,
    val createdAt: Long,
    val updatedAt: Long,
    val mood: Int,
    val anxiety: Int,
    val angerUrge: Int,
    val energyFunction: Int,
    val sleepQuality: Int,
    val instrumentVersion: String,
    val sourceDeviceId: String,
)

@Entity(tableName = "continuity_changes", indices = [Index("occurredAt"), Index("acknowledgedSnapshotId")])
data class ContinuityChangeEntity(
    @PrimaryKey val id: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val occurredAt: Long,
    val acknowledgedSnapshotId: String?,
)
