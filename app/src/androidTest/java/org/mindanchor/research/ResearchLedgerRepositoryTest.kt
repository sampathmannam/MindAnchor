package org.mindanchor.research

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.data.db.withResearchImmutability
import org.mindanchor.continuity.ContinuityPrefs

/**
 * Program 1 Task 8 — the ledger repository over real Room.
 *
 * The point of doing this on a device rather than in memory-fakes is the
 * transaction: `record` opens a study phase and appends an event in one
 * write, and only a real database can show that a failure part-way leaves
 * nothing behind.
 */
@RunWith(AndroidJUnit4::class)
class ResearchLedgerRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: AnchorDatabase
    private lateinit var repository: ResearchLedgerRepository

    private fun localDate(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    @Before
    fun open() = runBlocking {
        ContinuityPrefs(context).reset()
        database = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java)
            .withResearchImmutability()
            .build()
        repository = ResearchLedgerRepository(
            context = context,
            database = database,
            currentVector = { ProvenanceVersions.vector(95, "0.71.0", "device-a") },
        )
    }

    @After
    fun close() = runBlocking {
        database.close()
        ContinuityPrefs(context).reset()
    }

    @Test
    fun recordingAnEventOpensPhaseZeroFirst() = runBlocking {
        val event = repository.record(LedgerEventKind.EXERCISE, occurredAt = 1_000L, note = "morning run", now = 1_050L)

        assertEquals(1, database.research().studyPhaseCount())
        val phase = requireNotNull(database.research().latestStudyPhase())
        assertEquals(0, phase.ordinal)
        assertEquals(StudyPhaseReason.INITIAL.name, phase.reason)
        assertEquals(phase.id, event.studyPhaseId)
        assertEquals(LedgerIntegrity.VERIFIED, LedgerChain.verify(repository.events().first()))
        val highWater = requireNotNull(ContinuityPrefs(context).ledgerHighWater.first())
        assertEquals(database.research().ledgerEventCount(), highWater.eventCount)
        assertEquals(database.research().ledgerHead()?.eventHash, highWater.headHash)
    }

    @Test
    fun theNoteIsStoredExactlyAsWritten() = runBlocking {
        val event = repository.record(
            LedgerEventKind.LIFE_EVENT,
            occurredAt = 1_000L,
            note = "  moved house — everything is in boxes  ",
            now = 1_000L,
        )
        assertEquals("  moved house — everything is in boxes  ", event.note)
        assertEquals(event.note, repository.events().first().last().note)
    }

    @Test
    fun aWhitespaceOnlyNoteIsStillStoredExactly() = runBlocking {
        val event = repository.record(LedgerEventKind.ILLNESS, occurredAt = 1_000L, note = "   ", now = 1_000L)
        assertEquals("   ", event.note)
    }

    @Test
    fun anOverLongNoteWritesNothing() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.record(
                    LedgerEventKind.CAFFEINE,
                    occurredAt = 1_000L,
                    note = "x".repeat(MAX_LEDGER_NOTE_LENGTH + 1),
                    now = 1_000L,
                )
            }
        }
        assertEquals(0, database.research().ledgerEventCount())
        assertEquals(0, database.research().studyPhaseCount())
    }

    @Test
    fun aSystemRecordedKindCannotBeSelfReported() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.record(LedgerEventKind.SENSOR_GAP, occurredAt = 1_000L, note = "", now = 1_000L)
            }
        }
        assertEquals(0, database.research().ledgerEventCount())
    }

    @Test
    fun aMedicationChangeRecordsNothingDerived() = runBlocking {
        val event = repository.record(
            LedgerEventKind.MEDICATION_CHANGE,
            occurredAt = 1_000L,
            note = "dose changed",
            now = 1_000L,
        )
        assertEquals("{}", event.payloadJson)
        assertEquals("dose changed", event.note)
    }

    @Test
    fun everyResearchWriteIsQueuedForTheNextCheckpoint() = runBlocking {
        repository.record(LedgerEventKind.EXERCISE, occurredAt = 1_000L, note = "", now = 1_000L)
        val types = database.journal().allChangesNow().map { it.entityType }.toSet()
        assertTrue("STUDY_PHASE" in types)
        assertTrue("RESEARCH_LEDGER_EVENT" in types)
    }

    @Test
    fun sequencesRunWithoutGaps() = runBlocking {
        repository.record(LedgerEventKind.EXERCISE, occurredAt = 1_000L, note = "one", now = 1_000L)
        repository.record(LedgerEventKind.CAFFEINE, occurredAt = 2_000L, note = "two", now = 2_000L)
        repository.record(LedgerEventKind.SHIFT_SCHEDULE, occurredAt = 3_000L, note = "three", now = 3_000L)

        val events = repository.events().first()
        assertEquals((1L..events.size.toLong()).toList(), events.map { it.sequence })
        assertEquals(LedgerIntegrity.VERIFIED, LedgerChain.verify(events, LedgerChain.anchorOf(events)))
    }

    @Test
    fun anEventIsFiledUnderTheDayItHappened() = runBlocking {
        val yesterday = 1_000L
        val today = yesterday + 86_400_000L
        repository.record(LedgerEventKind.ILLNESS, occurredAt = yesterday, note = "", now = today)

        val event = repository.events().first().last()
        assertEquals(localDate(yesterday), event.localDate)
        assertEquals(yesterday, event.occurredAt)
        assertEquals(today, event.recordedAt)
    }

    @Test
    fun theDayViewShowsOnlyWhatThePersonRecordedThatDay() = runBlocking {
        repository.record(LedgerEventKind.EXERCISE, occurredAt = 1_000L, note = "a run", now = 1_000L)

        val today = repository.selfReportedOn(localDate(1_000L)).first()
        assertEquals(listOf(LedgerEventKind.EXERCISE), today.map { it.kind })
        assertTrue(repository.selfReportedOn("1999-01-01").first().isEmpty())
        // The provenance events MindAnchor wrote about itself are on the
        // same day but are not the person's record of it.
        assertTrue(repository.events().first().size > today.size)
    }

    @Test
    fun aDeviceChangeAfterARestoreOpensItsOwnPhase() = runBlocking {
        repository.record(LedgerEventKind.EXERCISE, occurredAt = 1_000L, note = "", now = 1_000L)

        val onAReplacementPhone = ResearchLedgerRepository(
            context = context,
            database = database,
            currentVector = { ProvenanceVersions.vector(95, "0.71.0", "device-b") },
        )
        onAReplacementPhone.record(LedgerEventKind.EXERCISE, occurredAt = 2_000L, note = "", now = 2_000L)

        val phases = database.research().studyPhasesNow()
        assertEquals(listOf(0, 1), phases.map { it.ordinal })
        assertEquals(StudyPhaseReason.DEVICE_CHANGE.name, phases.last().reason)
        assertEquals(LedgerIntegrity.VERIFIED, LedgerChain.verify(repository.events().first()))
    }

    @Test
    fun aClockRollbackCannotRecordAnEventBeforeItsAssignedPhase() = runBlocking {
        var vector = ProvenanceVersions.vector(95, "0.71.0", "device-a")
        val rollbackRepository = ResearchLedgerRepository(
            context = context,
            database = database,
            currentVector = { vector },
        )
        rollbackRepository.record(
            LedgerEventKind.EXERCISE,
            occurredAt = 1_700_000_000_000L,
            note = "before rollback",
            now = 1_700_000_000_000L,
        )
        vector = vector.copy(appVersionCode = 96)

        val event = rollbackRepository.record(
            LedgerEventKind.CAFFEINE,
            occurredAt = 1_000L,
            note = "after rollback",
            now = 1_000L,
        )
        val phase = requireNotNull(database.research().latestStudyPhase()?.toDomain())

        assertEquals(phase.startedAt, event.recordedAt)
        assertEquals(phase.id, StudyPhaseDecision.phaseAt(database.research().studyPhasesNow().map { it.toDomain() }, event.recordedAt)?.id)
    }

    /**
     * A repository whose append fails after the phase insert has already
     * succeeded — the torn write the shared transaction exists to prevent.
     */
    private class FailingAppendRepository(
        context: Context,
        database: AnchorDatabase,
    ) : ResearchLedgerRepository(
        context = context,
        database = database,
        currentVector = { ProvenanceVersions.vector(95, "0.71.0", "device-a") },
    ) {
        override suspend fun appendEvents(events: List<ResearchLedgerEvent>) {
            error("the disk filled up between the phase insert and the append")
        }
    }

    @Test
    fun aFailureBetweenThePhaseInsertAndTheAppendLeavesNothingBehind() = runBlocking {
        val failing = FailingAppendRepository(context, database)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { failing.record(LedgerEventKind.EXERCISE, occurredAt = 1_000L, note = "", now = 1_000L) }
        }

        // Room's withTransaction is re-entrant from the same coroutine, so
        // the coordinator's inner transaction and record's outer one are
        // one unit of work. Without that, a phase would have committed here
        // with no STUDY_PHASE_STARTED event to explain it, in tables that
        // can never be repaired.
        assertEquals(0, database.research().studyPhaseCount())
        assertEquals(0, database.research().ledgerEventCount())
        assertTrue(database.journal().allChangesNow().isEmpty())
    }
}
