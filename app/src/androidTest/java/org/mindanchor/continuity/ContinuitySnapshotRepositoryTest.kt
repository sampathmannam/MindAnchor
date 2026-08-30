package org.mindanchor.continuity

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.backup.BackupRepository
import org.mindanchor.data.FrictionPrefs
import org.mindanchor.data.NotesPrefs
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.data.db.PassiveBaselineSegmentEntity
import org.mindanchor.data.db.PassiveDailyRevisionEntity
import org.mindanchor.data.db.PassiveObservationDecisionEntity
import org.mindanchor.data.db.PassivePipelineRunEntity
import org.mindanchor.data.db.PassiveRawProvenanceEntity
import org.mindanchor.data.db.PassiveRawSampleEntity
import org.mindanchor.data.db.PassiveSourceLagEntity
import org.mindanchor.data.db.PassiveSourceReadEntity
import org.mindanchor.data.db.PassiveWindowRevisionEntity
import org.mindanchor.research.testLedgerRepository
import org.mindanchor.data.db.withResearchImmutability
import org.mindanchor.journal.DeviceIdentityStore
import org.mindanchor.journal.JournalRepository
import org.mindanchor.journal.StructuralContextExtractor
import org.mindanchor.letters.Letter
import org.mindanchor.letters.LetterStore
import org.mindanchor.model.Note
import org.mindanchor.research.toEntity

internal object PassiveContinuityFixture {
    val rawProvenance = listOf(
        PassiveRawProvenanceEntity(
            id = "raw-1",
            sourceFamily = "HEART_RATE",
            recordKind = "HeartRateRecord",
            eventStart = 1_000L,
            eventEnd = 2_000L,
            unit = "bpm",
            dataOriginPackage = "com.example.health",
            deviceManufacturer = "Example",
            deviceModel = "Watch",
            deviceType = "WATCH",
            sourceUpdatedTime = 2_100L,
            ingestedAt = 2_200L,
            zoneId = "Asia/Calcutta",
            zoneOffsetSeconds = 19_800,
            recordId = "record-1",
            recordVersion = 1L,
        ),
    )
    val rawSamples = listOf(PassiveRawSampleEntity(provenanceId = "raw-1", value = 97.0, ingestedAt = 2_200L))
    val sourceReads = listOf(
        PassiveSourceReadEntity(
            id = "read-1",
            runId = "run-1",
            sourceFamily = "HEART_RATE",
            state = "AVAILABLE",
            rangeStart = 1_000L,
            rangeEnd = 2_000L,
            zoneId = "Asia/Calcutta",
            attemptedAt = 2_200L,
            recordCount = 1,
            errorCode = null,
        ),
    )
    val sourceLags = listOf(
        PassiveSourceLagEntity(
            id = "lag-1",
            sourceFamily = "HEART_RATE",
            eventEnd = 2_000L,
            observedUpdatedAt = 2_100L,
            ingestedAt = 2_200L,
            lagMillis = 100L,
            usedIngestedAtFallback = false,
            observedAt = 2_200L,
        ),
    )
    val baselineSegments = listOf(
        PassiveBaselineSegmentEntity(
            id = "segment-1",
            openedAt = 500L,
            fingerprintsJson = "{}",
            windowTransformationVersion = "window-v1",
            dailyTransformationVersion = "daily-v1",
        ),
    )
    val pipelineRuns = listOf(
        PassivePipelineRunEntity(
            id = "run-1",
            startedAt = 2_000L,
            completedAt = 2_300L,
            scanStart = 1_000L,
            scanEnd = 2_000L,
            zoneId = "Asia/Calcutta",
            historyPermissionGranted = true,
            firstSuccessfulPermissionedRun = true,
            result = "SUCCESS_PERMISSIONED",
            sourceStatesJson = "{}",
        ),
    )
    val windowRevisions = listOf(
        windowRevision(id = "window-provisional", asOfTime = 2_200L, final = false, reason = "INITIAL"),
        windowRevision(id = "window-final", asOfTime = 2_400L, final = true, reason = "WATERMARK_FINAL"),
    )
    val dailyRevisions = listOf(
        dailyRevision(
            id = "daily-provisional",
            asOfTime = 2_200L,
            status = "AVAILABLE_PROVISIONAL",
            reason = "INITIAL",
        ),
        dailyRevision(
            id = "daily-final",
            asOfTime = 2_400L,
            status = "AVAILABLE_FINAL",
            reason = "WATERMARK_FINAL",
        ),
    )
    val observationDecisions = listOf(
        observationDecision(
            id = "decision-provisional",
            asOfTime = 2_200L,
            status = "AVAILABLE_PROVISIONAL",
            reason = "INITIAL",
        ),
        observationDecision(
            id = "decision-final",
            asOfTime = 2_400L,
            status = "AVAILABLE_FINAL",
            reason = "WATERMARK_FINAL",
        ),
    )

    suspend fun insertInto(database: AnchorDatabase, includeRawSamples: Boolean = true) {
        val dao = database.passive()
        dao.insertRawProvenance(rawProvenance)
        if (includeRawSamples) dao.insertRawSamples(rawSamples)
        dao.insertSourceReads(sourceReads)
        dao.insertSourceLags(sourceLags)
        baselineSegments.forEach { dao.insertBaselineSegment(it) }
        pipelineRuns.forEach { dao.insertPipelineRun(it) }
        dao.insertWindowRevisions(windowRevisions)
        dao.insertDailyRevisions(dailyRevisions)
        dao.insertObservationDecisions(observationDecisions)
    }

    private fun windowRevision(
        id: String,
        asOfTime: Long,
        final: Boolean,
        reason: String,
    ) = PassiveWindowRevisionEntity(
        id = id,
        windowStart = 1_000L,
        windowEnd = 1_900L,
        asOfTime = asOfTime,
        zoneId = "Asia/Calcutta",
        zoneOffsetSeconds = 19_800,
        wakeRelativeMinute = 15,
        baselineSegment = "segment-1",
        featureRowsJson = "[]",
        heartRateCoverage = 1.0,
        physiologyEligible = true,
        exerciseOverlapMillis = 0L,
        provenanceRecordIdsJson = "[\"raw-1\"]",
        missingnessJson = "[]",
        exclusionsJson = "[]",
        transformationVersion = "window-v1",
        sourceUpdatedTime = 2_100L,
        ingestedAt = asOfTime,
        final = final,
        revisionReason = reason,
        contentHash = "$id-hash",
    )

    private fun dailyRevision(
        id: String,
        asOfTime: Long,
        status: String,
        reason: String,
    ) = PassiveDailyRevisionEntity(
        id = id,
        localDate = "2026-08-30",
        asOfTime = asOfTime,
        dataStatus = status,
        featuresJson = "{}",
        excludedFeaturesJson = "[]",
        baselineSegment = "segment-1",
        sourceUpdatedTime = 2_100L,
        ingestedAt = asOfTime,
        sourceReadStatesJson = "{}",
        coverageJson = "{}",
        missingnessJson = "[]",
        exclusionsJson = "[]",
        provenanceJson = "[\"raw-1\"]",
        windowTransformationVersion = "window-v1",
        dailyTransformationVersion = "daily-v1",
        watermark = 2_000L,
        revisionReason = reason,
        contentHash = "$id-hash",
    )

    private fun observationDecision(
        id: String,
        asOfTime: Long,
        status: String,
        reason: String,
    ) = PassiveObservationDecisionEntity(
        id = id,
        localDate = "2026-08-30",
        asOfTime = asOfTime,
        dataStatus = status,
        observationState = "NO_OBSERVATION",
        baselineSegment = "segment-1",
        calibrationSeed = null,
        frozenBaselineAsOfTime = null,
        frozenBaselineThroughDay = null,
        decisionJson = "{}",
        revisionReason = reason,
        contentHash = "$id-hash",
    )
}

/**
 * Proves the Task 7 capture guarantee: [ContinuitySnapshotRepository.capture]
 * produces a snapshot whose [ContinuitySnapshot.contentSha256] matches an
 * independently-recomputed hash of the same seeded data, and that capturing
 * twice against identical underlying content produces the same
 * `contentSha256` both times even though `snapshotId` and `createdAt`
 * necessarily differ.
 */
@RunWith(AndroidJUnit4::class)
class ContinuitySnapshotRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AnchorDatabase
    private lateinit var deviceIdentity: DeviceIdentityStore
    private lateinit var notesPrefs: NotesPrefs
    private lateinit var letterStore: LetterStore
    private lateinit var frictionPrefs: FrictionPrefs
    private lateinit var repository: ContinuitySnapshotRepository

    @Before
    fun setUp() = runBlocking {
        ContinuityPrefs(context).reset()
        db = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java)
            .withResearchImmutability()
            .build()
        deviceIdentity = DeviceIdentityStore(context)
        notesPrefs = NotesPrefs(context)
        letterStore = LetterStore(context)
        frictionPrefs = FrictionPrefs(context)
        repository = ContinuitySnapshotRepository(
            context = context,
            database = db,
            notesPrefs = notesPrefs,
            letterStore = letterStore,
            frictionPrefs = frictionPrefs,
            deviceIdentity = deviceIdentity,
            backupRepository = BackupRepository(context),
        )

        // These DataStores are real, on-device, process-wide singletons —
        // clear them so a previous instrumentation run on this emulator
        // cannot leak state into this test.
        letterStore.reset()
        clearNotes()
        clearFriction()
        ContinuityPrefs(context).reset()
    }

    @After
    fun tearDown() = runBlocking {
        db.close()
        letterStore.reset()
        clearNotes()
        clearFriction()
    }

    private suspend fun clearNotes() {
        notesPrefs.replaceAll(emptyList())
    }

    private suspend fun clearFriction() {
        frictionPrefs.replaceFlaggedApps(emptySet())
        frictionPrefs.replaceAlwaysOpenApps(emptySet())
    }

    private suspend fun seed() {
        val journalRepository = JournalRepository(
            context,
            db,
            deviceIdentity,
            StructuralContextExtractor(),
            testLedgerRepository(context, db).provenance,
        )
        journalRepository.create(
            title = "A day",
            body = "Something happened today.",
            now = 1_000L,
            localDate = LocalDate.of(2026, 8, 27),
        )
        db.journal().upsertMorningMeasure(
            org.mindanchor.research.MorningMeasure.create(
                localDate = LocalDate.of(2026, 8, 27),
                now = 1_000L,
                mood = 3,
                anxiety = 2,
                angerUrge = 1,
                energyFunction = 4,
                sleepQuality = 3,
                sourceDeviceId = "device-a",
            ).toEntity(),
        )
        notesPrefs.add(Note(id = 1L, body = "A quick note", createdAt = 500L, updatedAt = 500L))
        letterStore.save(Letter(date = LocalDate.of(2026, 8, 26), body = "A letter"))
        letterStore.setRead(LocalDate.of(2026, 8, 26), true)
        frictionPrefs.setFlagged("com.example.social", true)
        frictionPrefs.setAlwaysOpen("com.example.work", true)
    }

    @Test
    fun captureProducesAHashMatchingAnIndependentRecomputation() = runBlocking {
        seed()

        val snapshot = repository.capture(now = 5_000L)

        val recomputed = ContinuityContentHasher.hash(snapshot.payload)
        assertEquals(recomputed, snapshot.contentSha256)

        // Sanity: the payload actually carries the seeded rows, not an
        // accidentally-empty capture that would make the hash comparison
        // vacuous.
        assertTrue(snapshot.payload.journalEntries.isNotEmpty())
        assertTrue(snapshot.payload.morningMeasures.isNotEmpty())
        assertTrue(snapshot.payload.notes.isNotEmpty())
        assertTrue(snapshot.payload.letters.isNotEmpty())
        assertTrue(snapshot.payload.readLetterDates.isNotEmpty())
        assertTrue(snapshot.payload.frictionedApps.isNotEmpty())
        assertTrue(snapshot.payload.alwaysOpenApps.isNotEmpty())
        assertFalse(snapshot.payload.legacyBackupJson.isBlank())
    }

    @Test
    fun capturingTwiceWithIdenticalContentProducesTheSameContentHash() = runBlocking {
        seed()

        val first = repository.capture(now = 5_000L)
        val second = repository.capture(now = 6_000L)

        assertEquals(first.contentSha256, second.contentSha256)
        // The two captures are still genuinely distinct snapshots.
        assertNotEquals(first.snapshotId, second.snapshotId)
        assertNotEquals(first.createdAt, second.createdAt)
    }

    @Test
    fun roomRowsComeFromOneTransactionallyConsistentPointInTime() = runBlocking {
        val ledger = testLedgerRepository(context, db)
        lateinit var writer: Deferred<org.mindanchor.research.ResearchLedgerEvent>
        val racingRepository = ContinuitySnapshotRepository(
            context = context,
            database = db,
            notesPrefs = notesPrefs,
            letterStore = letterStore,
            frictionPrefs = frictionPrefs,
            deviceIdentity = deviceIdentity,
            backupRepository = BackupRepository(context),
            afterResearchLedgerRead = {
                writer = async(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                    ledger.record(
                        org.mindanchor.research.LedgerEventKind.EXERCISE,
                        occurredAt = 1_000L,
                        note = "racing write",
                        now = 1_000L,
                    )
                }
                // UNDISPATCHED runs through the writer's first suspension.
                // record() has no suspend point before database.withTransaction,
                // so this proves the competing write reached transaction
                // acquisition and is blocked by the capture transaction.
                assertTrue(writer.isActive && !writer.isCompleted)
            },
        )

        val snapshot = racingRepository.capture(now = 5_000L)
        writer.await()

        assertTrue("the racing write must commit after capture", db.research().studyPhaseCount() > 0)
        assertEquals(emptyList<ResearchLedgerEventDto>(), snapshot.payload.researchLedgerEvents)
        assertEquals(emptyList<StudyPhaseDto>(), snapshot.payload.studyPhases)
    }

    @Test
    fun captureCarriesEveryLongTermPassiveRowButNeverRawSampleValues() = runBlocking {
        PassiveContinuityFixture.insertInto(db)

        val snapshot = repository.capture(now = 5_000L)
        val encoded = ContinuitySnapshotCodec.encode(snapshot)

        assertEquals(PassiveContinuityFixture.rawProvenance.map { it.toDto() }, snapshot.payload.passiveRawProvenance)
        assertEquals(PassiveContinuityFixture.sourceReads.map { it.toDto() }, snapshot.payload.passiveSourceReads)
        assertEquals(PassiveContinuityFixture.sourceLags.map { it.toDto() }, snapshot.payload.passiveSourceLags)
        assertEquals(
            PassiveContinuityFixture.baselineSegments.map { it.toDto() },
            snapshot.payload.passiveBaselineSegments,
        )
        assertEquals(PassiveContinuityFixture.pipelineRuns.map { it.toDto() }, snapshot.payload.passivePipelineRuns)
        assertEquals(PassiveContinuityFixture.windowRevisions.map { it.toDto() }, snapshot.payload.passiveWindowRevisions)
        assertEquals(PassiveContinuityFixture.dailyRevisions.map { it.toDto() }, snapshot.payload.passiveDailyRevisions)
        assertEquals(
            PassiveContinuityFixture.observationDecisions.map { it.toDto() },
            snapshot.payload.passiveObservationDecisions,
        )
        assertFalse(encoded.contains("passiveRawSamples"))
        assertFalse(encoded.contains("\"value\":97.0"))
        assertFalse(encoded.contains("97.0"))
    }
}
