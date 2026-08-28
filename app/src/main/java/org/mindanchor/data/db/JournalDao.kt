package org.mindanchor.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntry(entry: JournalEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntries(entries: List<JournalEntryEntity>)

    @Query("SELECT * FROM journal_entries WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun entries(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE id = :id LIMIT 1")
    suspend fun entry(id: String): JournalEntryEntity?

    // Program 0 must never physically delete Journal content: this
    // tombstones the entry (deletedAt set) rather than removing the row.
    @Query("UPDATE journal_entries SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun tombstone(id: String, deletedAt: Long, updatedAt: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContext(rows: List<JournalContextEntity>)

    @Query("SELECT * FROM journal_context ORDER BY createdAt, id")
    suspend fun allContext(): List<JournalContextEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMorningMeasure(measure: MorningMeasureEntity)

    @Query("SELECT * FROM morning_measures ORDER BY localDate DESC")
    fun morningMeasures(): Flow<List<MorningMeasureEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertChange(change: ContinuityChangeEntity)

    @Query("SELECT * FROM continuity_changes WHERE acknowledgedSnapshotId IS NULL ORDER BY occurredAt, id")
    suspend fun pendingChanges(): List<ContinuityChangeEntity>

    @Query("UPDATE continuity_changes SET acknowledgedSnapshotId = :snapshotId WHERE acknowledgedSnapshotId IS NULL")
    suspend fun acknowledgePending(snapshotId: String)

    // One-shot sorted queries for snapshot export (Task 7), so a
    // point-in-time capture does not call .first() on a UI Flow.
    @Query("SELECT * FROM journal_entries WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    suspend fun entriesNow(): List<JournalEntryEntity>

    @Query("SELECT * FROM morning_measures ORDER BY localDate DESC")
    suspend fun morningMeasuresNow(): List<MorningMeasureEntity>
}
