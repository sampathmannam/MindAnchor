package org.mindanchor.data.db

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PassiveRoomTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun duplicateRowsAreIgnoredAndOnlyRawValuesCanBePruned() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java)
            .withResearchImmutability()
            .build()
        try {
            val dao = db.passive()
            val provenance = rawProvenance()
            val sample = PassiveRawSampleEntity(provenance.id, 72.0, 1_000L)
            val sourceRead = PassiveSourceReadEntity("read-1", "run-1", "PHYSIOLOGY", "SUCCESS", 0L, 2_000L, "UTC", 2_000L, 1, null)
            val sourceLag = PassiveSourceLagEntity("lag-1", "PHYSIOLOGY", 1_000L, 1_100L, 1_200L, 100L, false, 1_200L)
            val segment = PassiveBaselineSegmentEntity("segment-1", 1_000L, "{}", "window-v1", "daily-v1")
            val run = PassivePipelineRunEntity("run-1", 1_000L, 2_000L, 0L, 2_000L, "UTC", true, true, "SUCCESS_PERMISSIONED", "{}")
            val window = windowRevision()
            val daily = dailyRevision()
            val decision = observationDecision()

            assertTrue(dao.insertRawProvenance(listOf(provenance)).single() > 0L)
            assertTrue(dao.insertRawSamples(listOf(sample)).single() > 0L)
            assertTrue(dao.insertSourceReads(listOf(sourceRead)).single() > 0L)
            assertTrue(dao.insertSourceLags(listOf(sourceLag)).single() > 0L)
            assertTrue(dao.insertBaselineSegment(segment) > 0L)
            assertTrue(dao.insertPipelineRun(run) > 0L)
            assertTrue(dao.insertWindowRevisions(listOf(window)).single() > 0L)
            assertTrue(dao.insertDailyRevisions(listOf(daily)).single() > 0L)
            assertTrue(dao.insertObservationDecisions(listOf(decision)).single() > 0L)

            assertEquals(-1L, dao.insertRawProvenance(listOf(provenance)).single())
            assertEquals(-1L, dao.insertRawSamples(listOf(sample)).single())
            assertEquals(-1L, dao.insertSourceReads(listOf(sourceRead)).single())
            assertEquals(-1L, dao.insertSourceLags(listOf(sourceLag)).single())
            assertEquals(-1L, dao.insertBaselineSegment(segment))
            assertEquals(-1L, dao.insertPipelineRun(run))
            assertEquals(-1L, dao.insertWindowRevisions(listOf(window)).single())
            assertEquals(-1L, dao.insertDailyRevisions(listOf(daily)).single())
            assertEquals(-1L, dao.insertObservationDecisions(listOf(decision)).single())

            assertEquals(1, dao.rawRecords(0L, 10_000L).size)
            assertEquals(1, dao.sourceReadsNow().size)
            assertEquals(1, dao.sourceLagsNow().size)
            assertEquals(1, dao.baselineSegmentsNow().size)
            assertEquals(1, dao.pipelineRunsNow().size)
            assertEquals(1, dao.successfulPermissionedRunCount())
            assertEquals(1, dao.windowRevisionsNow().size)
            assertEquals(1, dao.dailyRevisionsNow().size)
            assertEquals(1, dao.observationDecisionsNow().size)

            assertEquals(1, dao.pruneRawSamples(2_000L))
            assertEquals(1, dao.rawProvenanceNow().size)
            assertTrue(dao.rawRecords(0L, 10_000L).isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun immutableRowsRejectMutationButRawValuesCanBePruned() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java)
            .withResearchImmutability()
            .build()
        try {
            val dao = db.passive()
            dao.insertRawProvenance(listOf(rawProvenance()))
            dao.insertRawSamples(listOf(PassiveRawSampleEntity("raw-1", 72.0, 1_000L)))
            dao.insertWindowRevisions(listOf(windowRevision()))
            assertThrows(SQLiteConstraintException::class.java) {
                db.openHelper.writableDatabase.execSQL(
                    "UPDATE passive_window_revisions SET contentHash = 'rewritten' WHERE id = 'window-1'",
                )
            }
            assertEquals(1, dao.pruneRawSamples(2_000L))
            assertEquals(1, dao.rawProvenanceNow().size)
            assertTrue(dao.rawRecords(0L, 10_000L).isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun equalContentRevisionsAppendAndSameMillisecondHistoryUsesInsertionOrder() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java)
            .withResearchImmutability()
            .build()
        try {
            val dao = db.passive()
            val firstWindow = windowRevision(id = "z-window-first", reason = "INITIAL")
            val secondWindow = windowRevision(id = "a-window-second", reason = "BACKFILL")
            val firstDaily = dailyRevision(id = "z-daily-first", reason = "INITIAL")
            val secondDaily = dailyRevision(id = "a-daily-second", reason = "BACKFILL")
            val firstDecision = observationDecision(id = "z-decision-first", reason = "INITIAL")
            val secondDecision = observationDecision(id = "a-decision-second", reason = "BACKFILL")
            val firstSegment = PassiveBaselineSegmentEntity("z-segment-first", 1_000L, "{}", "window-v1", "daily-v1")
            val secondSegment = PassiveBaselineSegmentEntity("a-segment-second", 1_000L, "{}", "window-v2", "daily-v2")

            assertTrue(dao.insertWindowRevisions(listOf(firstWindow, secondWindow)).all { it > 0L })
            assertTrue(dao.insertDailyRevisions(listOf(firstDaily, secondDaily)).all { it > 0L })
            assertTrue(dao.insertObservationDecisions(listOf(firstDecision, secondDecision)).all { it > 0L })
            assertTrue(dao.insertBaselineSegment(firstSegment) > 0L)
            assertTrue(dao.insertBaselineSegment(secondSegment) > 0L)

            assertEquals(listOf("z-window-first", "a-window-second"), dao.windowRevisionsNow().map { it.id })
            assertEquals("a-window-second", dao.latestWindowRevision(0L)?.id)
            assertEquals(listOf("z-daily-first", "a-daily-second"), dao.dailyRevisionsNow().map { it.id })
            assertEquals("a-daily-second", dao.latestDailyRevision("2026-08-30")?.id)
            assertEquals(
                listOf("z-daily-first", "a-daily-second"),
                dao.dailyHistory("2026-08-31", 2_000L).map { it.id },
            )
            assertEquals(
                listOf("z-decision-first", "a-decision-second"),
                dao.observationDecisionsNow().map { it.id },
            )
            assertEquals("a-decision-second", dao.latestObservationDecision("2026-08-30")?.id)
            assertEquals(
                listOf("z-decision-first", "a-decision-second"),
                dao.priorDecisions("2026-08-31", 2_000L).map { it.id },
            )
            assertEquals(listOf("z-segment-first", "a-segment-second"), dao.baselineSegmentsNow().map { it.id })
            assertEquals("a-segment-second", dao.latestBaselineSegment()?.id)
        } finally {
            db.close()
        }
    }

    private fun rawProvenance() = PassiveRawProvenanceEntity(
        "raw-1", "PHYSIOLOGY", "HEART_RATE", 1_000L, 1_000L, "bpm", "watch",
        "COROS", "Pace", "WATCH", 1_100L, 1_200L, "UTC", 0, "record-1", 1L,
    )

    private fun windowRevision(
        id: String = "window-1",
        reason: String = "INITIAL",
    ) = PassiveWindowRevisionEntity(
        id, 0L, 900_000L, 2_000L, "UTC", 0, null, "segment-1", "[]",
        1.0, true, 0L, "[]", "[]", "[]", "window-v1", 1_100L, 1_200L,
        false, reason, "window-hash",
    )

    private fun dailyRevision(
        id: String = "daily-1",
        reason: String = "INITIAL",
    ) = PassiveDailyRevisionEntity(
        id, "2026-08-30", 2_000L, "OBSERVED", "{}", "{}", "segment-1",
        1_100L, 1_200L, "{}", "{}", "{}", "{}", "{}", "window-v1", "daily-v1",
        1_100L, reason, "daily-hash",
    )

    private fun observationDecision(
        id: String = "decision-1",
        reason: String = "INITIAL",
    ) = PassiveObservationDecisionEntity(
        id, "2026-08-30", 2_000L, "OBSERVED", "NO_SIGNAL", "segment-1",
        42L, 1_500L, "2026-08-29", "{}", reason, "decision-hash",
    )
}
