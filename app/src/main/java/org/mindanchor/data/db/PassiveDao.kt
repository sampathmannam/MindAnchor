package org.mindanchor.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Suppress("TooManyFunctions", "MaxLineLength")
@Dao
interface PassiveDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRawProvenance(rows: List<PassiveRawProvenanceEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRawSamples(rows: List<PassiveRawSampleEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSourceReads(rows: List<PassiveSourceReadEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSourceLags(rows: List<PassiveSourceLagEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBaselineSegment(row: PassiveBaselineSegmentEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPipelineRun(row: PassivePipelineRunEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWindowRevisions(rows: List<PassiveWindowRevisionEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDailyRevisions(rows: List<PassiveDailyRevisionEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertObservationDecisions(rows: List<PassiveObservationDecisionEntity>): List<Long>

    @Query("SELECT p.*, s.value AS rawValue FROM passive_raw_provenance p " +
        "JOIN passive_raw_samples s ON s.provenanceId = p.id " +
        "WHERE p.eventStart < :endExclusive AND p.eventEnd >= :startInclusive ORDER BY p.eventStart, p.rowid")
    suspend fun rawRecords(startInclusive: Long, endExclusive: Long): List<PassiveStoredRecord>

    @Query("DELETE FROM passive_raw_samples WHERE ingestedAt < :cutoff")
    suspend fun pruneRawSamples(cutoff: Long): Int

    @Query("SELECT * FROM passive_raw_provenance ORDER BY eventStart, rowid")
    suspend fun rawProvenanceNow(): List<PassiveRawProvenanceEntity>

    @Query("SELECT * FROM passive_source_reads ORDER BY attemptedAt, sourceFamily, rowid")
    suspend fun sourceReadsNow(): List<PassiveSourceReadEntity>

    @Query("SELECT * FROM passive_source_lags ORDER BY observedAt, sourceFamily, rowid")
    suspend fun sourceLagsNow(): List<PassiveSourceLagEntity>

    @Query("SELECT * FROM passive_source_lags WHERE sourceFamily = :family ORDER BY observedAt, rowid")
    suspend fun sourceLags(family: String): List<PassiveSourceLagEntity>

    @Query("SELECT * FROM passive_baseline_segments ORDER BY openedAt, rowid")
    suspend fun baselineSegmentsNow(): List<PassiveBaselineSegmentEntity>

    @Query("SELECT * FROM passive_baseline_segments ORDER BY openedAt DESC, rowid DESC LIMIT 1")
    suspend fun latestBaselineSegment(): PassiveBaselineSegmentEntity?

    @Query("SELECT * FROM passive_pipeline_runs ORDER BY completedAt, rowid")
    suspend fun pipelineRunsNow(): List<PassivePipelineRunEntity>

    @Query("SELECT COUNT(*) FROM passive_pipeline_runs WHERE result = 'SUCCESS_PERMISSIONED'")
    suspend fun successfulPermissionedRunCount(): Int

    @Query("SELECT * FROM passive_window_revisions ORDER BY windowStart, asOfTime, rowid")
    suspend fun windowRevisionsNow(): List<PassiveWindowRevisionEntity>

    @Query("SELECT * FROM passive_window_revisions WHERE windowStart = :windowStart ORDER BY asOfTime DESC, rowid DESC LIMIT 1")
    suspend fun latestWindowRevision(windowStart: Long): PassiveWindowRevisionEntity?

    @Query("SELECT * FROM passive_daily_revisions ORDER BY localDate, asOfTime, rowid")
    suspend fun dailyRevisionsNow(): List<PassiveDailyRevisionEntity>

    @Query("SELECT * FROM passive_daily_revisions WHERE localDate = :localDate ORDER BY asOfTime DESC, rowid DESC LIMIT 1")
    suspend fun latestDailyRevision(localDate: String): PassiveDailyRevisionEntity?

    @Query("SELECT * FROM passive_daily_revisions WHERE localDate < :targetDate AND asOfTime <= :asOfTime ORDER BY localDate, asOfTime, rowid")
    suspend fun dailyHistory(targetDate: String, asOfTime: Long): List<PassiveDailyRevisionEntity>

    @Query("SELECT * FROM passive_observation_decisions ORDER BY localDate, asOfTime, rowid")
    suspend fun observationDecisionsNow(): List<PassiveObservationDecisionEntity>

    @Query("SELECT * FROM passive_observation_decisions WHERE localDate = :localDate ORDER BY asOfTime DESC, rowid DESC LIMIT 1")
    suspend fun latestObservationDecision(localDate: String): PassiveObservationDecisionEntity?

    @Query("SELECT * FROM passive_observation_decisions WHERE localDate < :targetDate AND asOfTime <= :asOfTime ORDER BY localDate, asOfTime, rowid")
    suspend fun priorDecisions(targetDate: String, asOfTime: Long): List<PassiveObservationDecisionEntity>
}
