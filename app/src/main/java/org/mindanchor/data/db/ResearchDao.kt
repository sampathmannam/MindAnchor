package org.mindanchor.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Reads and appends. Nothing here updates or deletes, and nothing ever
 * will: `research_ledger_events` and `study_phases` carry the research
 * history a later report rests on, and the design's rule is that
 * historical evidence is never rewritten.
 *
 * Every insert is `OnConflictStrategy.IGNORE`, never `REPLACE`. That is
 * not a preference — SQLite implements `REPLACE` as a delete followed by
 * an insert, so a `REPLACE` here would trip the tables' own
 * `BEFORE DELETE` triggers at runtime. `ResearchDaoAppendOnlyTest` checks
 * this file's source text for exactly that mistake, because Room's
 * annotations have binary retention and cannot be inspected at runtime.
 *
 * Kept to ten functions on purpose: detekt's default TooManyFunctions
 * threshold for an interface is eleven, and a DAO that keeps growing is a
 * sign the caller wants a query it should be composing itself.
 */
@Dao
interface ResearchDao {

    /**
     * Returns one row id per event, or -1 for an event the table already
     * held. The caller must look: `INSERT OR IGNORE` treats a conflict as
     * success, so a dropped row is otherwise indistinguishable from a
     * written one.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLedgerEvents(events: List<ResearchLedgerEventEntity>): List<Long>

    /**
     * Returns the new row id, or -1 if the insert was ignored. Two
     * conflict sources exist — the content-addressed `id`, and the unique
     * index on `ordinal` — and a silently dropped phase would leave every
     * event of that phase pointing at a row that does not exist.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStudyPhase(phase: StudyPhaseEntity): Long

    @Query("SELECT * FROM research_ledger_events ORDER BY sequence")
    fun ledgerEvents(): Flow<List<ResearchLedgerEventEntity>>

    @Query("SELECT * FROM research_ledger_events ORDER BY sequence")
    suspend fun ledgerEventsNow(): List<ResearchLedgerEventEntity>

    /** The highest-sequence row, for linking the next append without loading the whole ledger. */
    @Query("SELECT * FROM research_ledger_events ORDER BY sequence DESC LIMIT 1")
    suspend fun ledgerHead(): ResearchLedgerEventEntity?

    @Query("SELECT COUNT(*) FROM research_ledger_events")
    suspend fun ledgerEventCount(): Int

    /** The payloads already recorded for [kind], so a registration is not repeated. */
    @Query("SELECT payloadJson FROM research_ledger_events WHERE kind = :kind")
    suspend fun payloadsForKind(kind: String): List<String>

    @Query("SELECT * FROM study_phases ORDER BY ordinal")
    suspend fun studyPhasesNow(): List<StudyPhaseEntity>

    @Query("SELECT * FROM study_phases ORDER BY ordinal DESC LIMIT 1")
    suspend fun latestStudyPhase(): StudyPhaseEntity?

    @Query("SELECT COUNT(*) FROM study_phases")
    suspend fun studyPhaseCount(): Int
}
