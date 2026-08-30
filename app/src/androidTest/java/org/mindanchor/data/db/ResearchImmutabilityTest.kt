package org.mindanchor.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Program 1 Task 7 — the research tables refuse to be rewritten, at the
 * database level rather than by the discipline of whoever holds the DAO.
 *
 * `ResearchDaoAppendOnlyTest` proves the DAO declares no way to mutate a
 * row. These tests prove that even a raw SQL statement cannot: the
 * `BEFORE UPDATE` and `BEFORE DELETE` triggers `MIGRATION_6_7` installs
 * `RAISE(ABORT, ...)`. That difference matters because a research ledger
 * whose immutability rests on nobody writing the wrong query is not
 * immutable, it is merely unedited so far.
 *
 * A fresh in-memory database is used deliberately, built through the same
 * `withResearchImmutability` every production and test builder uses:
 * Room's generated `createAllTables` contains no triggers, so if these
 * pass, the callback a brand-new install depends on is working.
 */
@RunWith(AndroidJUnit4::class)
class ResearchImmutabilityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: AnchorDatabase

    private fun event(id: String, sequence: Long) = ResearchLedgerEventEntity(
        id = id,
        sequence = sequence,
        kind = "EXERCISE",
        occurredAt = 1_000L,
        recordedAt = 1_050L,
        localDate = "2026-08-29",
        studyPhaseId = "phase-0",
        sourceDeviceId = "device-a",
        note = "morning run",
        payloadJson = "{}",
        previousEventHash = "",
        eventHash = id,
    )

    private fun phase(id: String, ordinal: Int) = StudyPhaseEntity(
        id = id,
        ordinal = ordinal,
        startedAt = 1_000L + ordinal,
        reason = "INITIAL",
        appVersionCode = 95,
        appVersionName = "0.71.0",
        protocolCatalogSha256 = "catalogue",
        ruleSetVersion = "rule-set-none-v1",
        modelSetVersion = "model-set-none-v1",
        transformationSetVersion = "transformations",
        missingDataPolicyVersion = "missing-data-v1",
        instrumentVersion = "morning-v1",
        dictionaryVersion = "mindanchor-research-v1",
        sourceDeviceId = "device-a",
    )

    @Before
    fun open() {
        // The production callback, not a look-alike: a test that installs
        // its own triggers proves nothing about the builder every install
        // actually goes through.
        database = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java)
            .withResearchImmutability()
            .build()
    }

    @After
    fun close() = database.close()

    @Test
    fun insertingTheSameEventTwiceLeavesOneRow() = runBlocking {
        val dao = database.research()
        dao.insertLedgerEvents(listOf(event("event-1", 1L)))
        dao.insertLedgerEvents(listOf(event("event-1", 1L)))
        assertEquals(1, dao.ledgerEventCount())
    }

    @Test
    fun eventsComeBackInSequenceOrder() = runBlocking {
        val dao = database.research()
        dao.insertLedgerEvents(listOf(event("event-3", 3L), event("event-1", 1L), event("event-2", 2L)))
        assertEquals(listOf(1L, 2L, 3L), dao.ledgerEventsNow().map { it.sequence })
        assertEquals(3L, requireNotNull(dao.ledgerHead()).sequence)
    }

    @Test
    fun aRawUpdateOfALedgerEventIsRejected() = runBlocking {
        database.research().insertLedgerEvents(listOf(event("event-1", 1L)))
        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            database.openHelper.writableDatabase.execSQL(
                "UPDATE research_ledger_events SET note = 'rewritten'",
            )
        }
        assertEquals("morning run", database.research().ledgerEventsNow().single().note)
    }

    @Test
    fun aRawDeleteOfALedgerEventIsRejected() = runBlocking {
        database.research().insertLedgerEvents(listOf(event("event-1", 1L)))
        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            database.openHelper.writableDatabase.execSQL("DELETE FROM research_ledger_events")
        }
        assertEquals(1, database.research().ledgerEventCount())
    }

    @Test
    fun aRawUpdateOfAStudyPhaseIsRejected() = runBlocking {
        database.research().insertStudyPhase(phase("phase-0", 0))
        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            database.openHelper.writableDatabase.execSQL(
                "UPDATE study_phases SET reason = 'rewritten'",
            )
        }
        assertEquals("INITIAL", database.research().studyPhasesNow().single().reason)
    }

    @Test
    fun aRawDeleteOfAStudyPhaseIsRejected() = runBlocking {
        database.research().insertStudyPhase(phase("phase-0", 0))
        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            database.openHelper.writableDatabase.execSQL("DELETE FROM study_phases")
        }
        assertEquals(1, database.research().studyPhaseCount())
    }

    @Test
    fun phasesComeBackInOrdinalOrder() = runBlocking {
        val dao = database.research()
        dao.insertStudyPhase(phase("phase-1", 1))
        dao.insertStudyPhase(phase("phase-0", 0))
        assertEquals(listOf(0, 1), dao.studyPhasesNow().map { it.ordinal })
        assertEquals(1, requireNotNull(dao.latestStudyPhase()).ordinal)
    }

    @Test
    fun payloadsForKindReadsOnlyThatKind() = runBlocking {
        val dao = database.research()
        dao.insertLedgerEvents(
            listOf(
                event("event-1", 1L),
                event("event-2", 2L).copy(kind = "STUDY_PHASE_STARTED", payloadJson = """{"ordinal":0}"""),
            ),
        )
        assertEquals(listOf("""{"ordinal":0}"""), dao.payloadsForKind("STUDY_PHASE_STARTED"))
    }

    @Test
    fun allTwentyTriggersExistByName() {
        val cursor = database.openHelper.writableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type = 'trigger' ORDER BY name",
        )
        val names = mutableListOf<String>()
        cursor.use { while (it.moveToNext()) names += it.getString(0) }
        // Pinned by name so a future migration that re-creates either
        // table -- the 12-step dance MIGRATION_4_5 uses drops triggers
        // along with the table -- goes red instead of silently shipping
        // mutable research history.
        assertEquals(
            listOf(
                "passive_baseline_segments_no_delete",
                "passive_baseline_segments_no_update",
                "passive_daily_revisions_no_delete",
                "passive_daily_revisions_no_update",
                "passive_observation_decisions_no_delete",
                "passive_observation_decisions_no_update",
                "passive_pipeline_runs_no_delete",
                "passive_pipeline_runs_no_update",
                "passive_raw_provenance_no_delete",
                "passive_raw_provenance_no_update",
                "passive_source_lags_no_delete",
                "passive_source_lags_no_update",
                "passive_source_reads_no_delete",
                "passive_source_reads_no_update",
                "passive_window_revisions_no_delete",
                "passive_window_revisions_no_update",
                "research_ledger_events_no_delete",
                "research_ledger_events_no_update",
                "study_phases_no_delete",
                "study_phases_no_update",
            ),
            names,
        )
    }

    @Test
    fun operationalRevisionsRejectDirectUpdatesAndDeletes() {
        val sql = database.openHelper.writableDatabase
        sql.execSQL(
            "INSERT INTO passive_window_revisions VALUES " +
                "('window-1',0,900000,1000,'UTC',0,NULL,'segment','[]',1.0,1,0,'[]','[]','[]','window-v1',1000,1000,0,'INITIAL','hash')",
        )
        sql.execSQL(
            "INSERT INTO passive_daily_revisions VALUES " +
                "('daily-1','2026-08-30',1000,'OBSERVED','{}','{}','segment',1000,1000,'{}','{}','{}','{}','{}','window-v1','daily-v1',1000,'INITIAL','hash')",
        )
        sql.execSQL(
            "INSERT INTO passive_observation_decisions VALUES " +
                "('decision-1','2026-08-30',1000,'OBSERVED','NO_SIGNAL','segment',NULL,NULL,NULL,'{}','INITIAL','hash')",
        )
        listOf(
            "passive_window_revisions" to "contentHash",
            "passive_daily_revisions" to "contentHash",
            "passive_observation_decisions" to "contentHash",
        ).forEach { (table, column) ->
            assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
                sql.execSQL("UPDATE $table SET $column = 'rewritten'")
            }
            assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
                sql.execSQL("DELETE FROM $table")
            }
        }
    }

    @Test
    fun anInsertOrReplaceCannotOverwriteALedgerRow() = runBlocking {
        database.research().insertLedgerEvents(listOf(event("event-1", 1L)))
        // REPLACE is a delete followed by an insert. SQLite only fires
        // DELETE triggers for it when recursive triggers are on, which is
        // why the immutability callback turns them on -- without that this
        // would silently overwrite an immutable, hash-chained row.
        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            database.openHelper.writableDatabase.execSQL(
                "INSERT OR REPLACE INTO research_ledger_events " +
                    "(id, sequence, kind, occurredAt, recordedAt, localDate, studyPhaseId, " +
                    "sourceDeviceId, note, payloadJson, previousEventHash, eventHash) " +
                    "VALUES ('event-1', 1, 'EXERCISE', 1, 1, '2026-08-29', 'phase-0', " +
                    "'device-a', 'rewritten', '{}', '', 'event-1')",
            )
        }
        assertEquals("morning run", database.research().ledgerEventsNow().single().note)
    }

    @Test
    fun anIgnoredInsertReportsThatItWasIgnored() = runBlocking {
        val dao = database.research()
        assertEquals(listOf(1L), dao.insertLedgerEvents(listOf(event("event-1", 1L))).map { if (it > 0) 1L else it })
        // INSERT OR IGNORE treats a conflict as success, so the row id is
        // the only way a caller can tell a dropped row from a written one.
        assertEquals(listOf(-1L), dao.insertLedgerEvents(listOf(event("event-1", 1L))))
        assertTrue(dao.insertStudyPhase(phase("phase-0", 0)) > 0)
        assertEquals(-1L, dao.insertStudyPhase(phase("phase-0", 0)))
        // A different id colliding on the unique ordinal index is the
        // dangerous case: silently ignored, not an error.
        assertEquals(-1L, dao.insertStudyPhase(phase("phase-0-again", 0)))
    }
}
