package org.mindanchor.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
 * A fresh in-memory database is used deliberately: Room's generated
 * `createAllTables` contains no triggers, so if these pass here, the
 * `onCreate` callback that installs them on a brand-new install is
 * working.
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
        database = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java)
            .addCallback(
                object : androidx.room.RoomDatabase.Callback() {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        AnchorDatabase.installResearchImmutability(db)
                    }
                },
            )
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
}
