package org.mindanchor.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Program 3 Task 2 — reads and inserts, and nothing else.
 *
 * There is no `@Update`, no `@Delete`, and no SQL that writes to an
 * existing row. The database triggers make mutation impossible; this
 * interface makes it unexpressible, so a later change cannot reach for a
 * mutation method and discover the refusal only at runtime.
 * `AdvisoryDaoAppendOnlyTest` fails the build if that ever stops being
 * true.
 *
 * Every insert is `IGNORE` on conflict because identifiers are content
 * hashes: re-materializing the same opportunity or replaying the same
 * event is a no-op rather than a duplicate or an overwrite.
 */
@Dao
interface AdvisoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOpportunity(row: AdvisoryOpportunityEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvents(rows: List<InterventionEpisodeEventEntity>): List<Long>

    @Query("SELECT * FROM advisory_opportunities ORDER BY presentedAt, id")
    suspend fun opportunitiesNow(): List<AdvisoryOpportunityEntity>

    @Query("SELECT * FROM advisory_opportunities WHERE id = :id LIMIT 1")
    suspend fun opportunity(id: String): AdvisoryOpportunityEntity?

    @Query("SELECT * FROM intervention_episode_events ORDER BY occurredAt, episodeId, sequence, id")
    suspend fun eventsNow(): List<InterventionEpisodeEventEntity>

    @Query("SELECT * FROM intervention_episode_events WHERE episodeId = :episodeId ORDER BY sequence, id")
    suspend fun eventsForEpisode(episodeId: String): List<InterventionEpisodeEventEntity>

    @Query(
        "SELECT * FROM intervention_episode_events WHERE opportunityId = :opportunityId " +
            "ORDER BY occurredAt, episodeId, sequence, id",
    )
    suspend fun eventsForOpportunity(opportunityId: String): List<InterventionEpisodeEventEntity>

    @Query("SELECT * FROM advisory_opportunities ORDER BY presentedAt, id")
    fun observeOpportunities(): Flow<List<AdvisoryOpportunityEntity>>

    @Query("SELECT * FROM intervention_episode_events ORDER BY occurredAt, episodeId, sequence, id")
    fun observeEvents(): Flow<List<InterventionEpisodeEventEntity>>
}
