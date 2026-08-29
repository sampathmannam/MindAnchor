package org.mindanchor.continuity

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.backup.BackupRepository
import org.mindanchor.continuity.crypto.BackupEnvelopeCodec
import org.mindanchor.continuity.crypto.RecoveryKey
import org.mindanchor.continuity.crypto.RecoveryKeyCodec
import org.mindanchor.data.FrictionPrefs
import org.mindanchor.data.LauncherPrefs
import org.mindanchor.data.NotesPrefs
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.data.db.withResearchImmutability
import org.mindanchor.data.db.SafetyPlan
import org.mindanchor.data.mergeRestored
import org.mindanchor.data.replaceAlwaysOpen
import org.mindanchor.data.replaceFlagged
import org.mindanchor.journal.DeviceIdentityStore
import org.mindanchor.journal.JournalRepository
import org.mindanchor.journal.StructuralContextExtractor
import org.mindanchor.letters.Letter
import org.mindanchor.letters.LetterStore
import org.mindanchor.letters.mergeRestored
import org.mindanchor.model.Note
import org.mindanchor.research.LedgerChain
import org.mindanchor.research.LedgerEventKind
import org.mindanchor.research.LedgerIntegrity
import org.mindanchor.research.toDomain
import org.mindanchor.research.MorningMeasureRepository
import org.mindanchor.research.testLedgerRepository

/**
 * Task 14, Step 1: the end-to-end proof that everything Tasks 3, 7, 8, and
 * 11 built individually actually composes — capture a real snapshot from a
 * fully-populated set of stores, encrypt it exactly the way the on-device
 * backup path does, wipe the destination stores as if this were a
 * replacement phone, restore through the real [RestoreCoordinator] state
 * machine, and prove the result matches the original both by content hash
 * *and* by field-by-field equality.
 *
 * This is the full version of what [ContinuitySnapshotRepositoryTest] (the
 * capture half) and [RestoreResumeTest] (the restore half) already prove
 * piece-by-piece — same repository/store classes, same in-memory-Room /
 * real-on-device-DataStore idiom, wired together for the first time.
 *
 * ## Why "destination" reuses the same DataStores instead of new instances
 *
 * [NotesPrefs], [LetterStore], and [FrictionPrefs] each wrap a
 * `preferencesDataStore(name = ...)` delegate on [Context] — a process-wide
 * singleton keyed by name. A second in-process instance of any of them
 * would read and write the exact same on-disk file as the first, so there
 * is no way to construct a genuinely separate "destination phone" instance
 * of these stores inside one JVM process. [ContinuitySnapshotRepository]'s
 * `legacyBackupJson` field reaches the same constraint one level deeper: it
 * is produced by [BackupRepository.export], which reads the app's real
 * on-disk [AnchorDatabase.get] singleton for the safety plan, crisis
 * contacts, and pulses — not the injectable [AnchorDatabase] this test
 * otherwise uses for Journal/context/morning-measure rows.
 *
 * [RestoreResumeTest] already established the pattern this test follows:
 * wipe those stores back to empty (`reset()` / `replaceAll(emptyList())` /
 * `replaceFlaggedApps(emptySet())` / clearing the real singleton's safety
 * plan and contacts) rather than trying to construct new instances, so a
 * wiped store is what stands in for "the replacement phone" for anything
 * that is not a fresh, independently-constructible Room database. Room
 * *is* independently constructible — `sourceDb` and `destDb` below are two
 * genuinely separate [Room.inMemoryDatabaseBuilder] instances — so the
 * Journal/context/morning-measure half of this test is a real two-database
 * round trip, not just a wipe-and-reuse of one database.
 */
@RunWith(AndroidJUnit4::class)
class ContinuityRoundTripTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var sourceDb: AnchorDatabase
    private lateinit var notesPrefs: NotesPrefs
    private lateinit var letterStore: LetterStore
    private lateinit var frictionPrefs: FrictionPrefs
    private lateinit var launcherPrefs: LauncherPrefs
    private lateinit var backupRepository: BackupRepository
    private lateinit var continuityPrefs: ContinuityPrefs
    private lateinit var restoreStateStore: RestoreStateStore
    private lateinit var deviceIdentity: DeviceIdentityStore
    private lateinit var stagingFile: File
    private lateinit var stagingTmpFile: File

    private val key: RecoveryKey = RecoveryKeyCodec.generate { ByteArray(32) { i -> (i * 7).toByte() } }

    @Before
    fun setUp() = runBlocking {
        sourceDb = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java)
            .withResearchImmutability()
            .build()
        notesPrefs = NotesPrefs(context)
        letterStore = LetterStore(context)
        frictionPrefs = FrictionPrefs(context)
        launcherPrefs = LauncherPrefs(context)
        backupRepository = BackupRepository(context)
        continuityPrefs = ContinuityPrefs(context)
        restoreStateStore = RestoreStateStore(context)
        deviceIdentity = DeviceIdentityStore(context)
        val stagingDir = File(context.filesDir, "continuity")
        stagingFile = File(stagingDir, "restore-staged.mab")
        stagingTmpFile = File(stagingDir, "restore-staged.mab.tmp")
        clearEverything()
    }

    @After
    fun tearDown() = runBlocking {
        sourceDb.close()
        clearEverything()
    }

    /** Mirrors [RestoreResumeTest.clearEverything] — see this class's KDoc for why. */
    private suspend fun clearEverything() {
        letterStore.reset()
        notesPrefs.replaceAll(emptyList())
        frictionPrefs.replaceFlaggedApps(emptySet())
        frictionPrefs.replaceAlwaysOpenApps(emptySet())
        launcherPrefs.replaceFavorites(emptyList())
        launcherPrefs.replaceHidden(emptySet())
        launcherPrefs.replaceRenames(emptyMap())
        restoreStateStore.reset()
        stagingFile.delete()
        stagingTmpFile.delete()
        val realDb = AnchorDatabase.get(context)
        realDb.safety().savePlan(SafetyPlan())
        realDb.safety().contactsNow().forEach { realDb.safety().removeContact(it) }
    }

    @Test
    fun aFullCaptureEncryptRestoreCycleReproducesEveryFieldAndIsIdempotent() = runBlocking {
        // --- Steps 1/2: one multiline Journal entry, verify it and its four
        // structural facts land as five separate rows ---
        val ledgerRepository = testLedgerRepository(context, sourceDb)
        val journalRepository = JournalRepository(
            context,
            sourceDb,
            deviceIdentity,
            StructuralContextExtractor(),
            ledgerRepository.provenance,
        )
        val multilineBody = "Line one of the day.\nLine two, a different thought entirely.\nLine three, still going."
        val entry = journalRepository.create(
            title = "Round trip day",
            body = multilineBody,
            now = 1_000L,
            localDate = LocalDate.of(2026, 8, 27),
        )
        assertEquals(multilineBody, sourceDb.journal().entry(entry.id)?.body)
        val sourceContextRows = sourceDb.journal().allContext().filter { it.entryId == entry.id }
        assertEquals(4, sourceContextRows.size)
        val sourceFacts = sourceContextRows.associate { it.key to it.value }
        assertEquals(setOf("entry_kind", "local_date", "word_count", "user_title"), sourceFacts.keys)
        assertEquals("DAILY", sourceFacts["entry_kind"])
        assertEquals("2026-08-27", sourceFacts["local_date"])
        assertEquals("Round trip day", sourceFacts["user_title"])

        // --- Step 3: one morning measure ---
        val measureRepository = MorningMeasureRepository(
            context,
            sourceDb,
            deviceIdentity,
            ledgerRepository.provenance,
        )
        val measure = measureRepository.save(
            localDate = LocalDate.of(2026, 8, 27),
            now = 1_500L,
            mood = 4,
            anxiety = 2,
            angerUrge = 1,
            energyFunction = 3,
            sleepQuality = 5,
        )

        // --- Step 3b: one research-log event, so the snapshot carries a
        // self-reported ledger row as well as the provenance rows the
        // Journal write already opened ---
        ledgerRepository.record(
            kind = LedgerEventKind.EXERCISE,
            occurredAt = 1_600L,
            note = "a walk before the rain",
            now = 1_600L,
        )
        val sourceLedger = sourceDb.research().ledgerEventsNow()
        val sourcePhases = sourceDb.research().studyPhasesNow()
        assertEquals(1, sourcePhases.size)
        assertEquals(
            LedgerIntegrity.VERIFIED,
            LedgerChain.verify(sourceLedger.map { it.toDomain() }),
        )
        assertTrue(sourceLedger.any { it.kind == LedgerEventKind.EXERCISE.name })

        // --- Step 4: a Quick Note, a Letter (marked read), a frictioned app,
        // an always-open app ---
        notesPrefs.add(Note(id = 1L, body = "A quick note about the day", createdAt = 2_000L, updatedAt = 2_000L))
        val letterDate = LocalDate.of(2026, 8, 26)
        letterStore.save(Letter(date = letterDate, body = "A letter to remember"))
        letterStore.setRead(letterDate, true)
        frictionPrefs.setFlagged("com.example.social", true)
        frictionPrefs.setAlwaysOpen("com.example.emergency", true)

        // --- Step 5: capture + encrypt the source snapshot ---
        val sourceSnapshotRepository = ContinuitySnapshotRepository(
            context = context,
            database = sourceDb,
            notesPrefs = notesPrefs,
            letterStore = letterStore,
            frictionPrefs = frictionPrefs,
            deviceIdentity = deviceIdentity,
            backupRepository = backupRepository,
        )
        val originalSnapshot = sourceSnapshotRepository.capture(now = 5_000L)
        val envelope = BackupEnvelopeCodec.encrypt(
            ContinuitySnapshotCodec.encode(originalSnapshot),
            key,
            now = 5_000L,
        )
        val envelopeBytes = BackupEnvelopeCodec.encode(envelope).encodeToByteArray()

        // --- Step 6: wipe the destination — a genuinely separate Room
        // database standing in for the replacement phone's own store, plus
        // the shared DataStores/singleton reset to empty (see class KDoc) ---
        val destDb = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java)
            .withResearchImmutability()
            .build()
        try {
            letterStore.reset()
            notesPrefs.replaceAll(emptyList())
            frictionPrefs.replaceFlaggedApps(emptySet())
            frictionPrefs.replaceAlwaysOpenApps(emptySet())
            val realDb = AnchorDatabase.get(context)
            realDb.safety().savePlan(SafetyPlan())
            realDb.safety().contactsNow().forEach { realDb.safety().removeContact(it) }
            restoreStateStore.reset()

            val destSnapshotRepository = ContinuitySnapshotRepository(
                context = context,
                database = destDb,
                notesPrefs = notesPrefs,
                letterStore = letterStore,
                frictionPrefs = frictionPrefs,
                deviceIdentity = deviceIdentity,
                backupRepository = backupRepository,
            )
            val destDao = destDb.journal()

            // --- Step 7: restore through a real RestoreCoordinator, wired
            // by hand the same way RestoreResumeTest's realCoordinator()
            // does — same collaborator shape RestoreCoordinator.build wires
            // in production, bound to the wiped destination database ---
            val coordinator = RestoreCoordinator(
                currentStageInfo = { restoreStateStore.currentInfo() },
                persistDownloaded = { name, sha, hash, version ->
                    restoreStateStore.markDownloaded(name, sha, hash, version)
                },
                persistDecrypted = { hash, version -> restoreStateStore.markDecrypted(hash, version) },
                persistRoomMerged = { restoreStateStore.markRoomMerged() },
                persistDataStoresMerged = { restoreStateStore.markDataStoresMerged() },
                persistVerified = { restoreStateStore.markVerified() },
                resetState = { restoreStateStore.reset() },
                readStagedBytes = { if (stagingFile.exists()) stagingFile.readBytes() else null },
                writeStagedBytesAtomically = { bytes ->
                    stagingFile.parentFile?.mkdirs()
                    stagingTmpFile.outputStream().use { it.write(bytes); it.flush() }
                    if (stagingFile.exists()) stagingFile.delete()
                    stagingTmpFile.renameTo(stagingFile)
                },
                deleteStagedFile = { stagingFile.delete() },
                currentVerifiedKey = { key },
                preflightIsLocalDataEmpty = {
                    destDao.entriesNow().isEmpty() &&
                        destDao.allContext().isEmpty() &&
                        destDao.morningMeasuresNow().isEmpty() &&
                        notesPrefs.notes.first().notes.isEmpty() &&
                        letterStore.letters.first().isEmpty() &&
                        launcherPrefs.favorites.first().isEmpty() &&
                        launcherPrefs.hidden.first().isEmpty() &&
                        launcherPrefs.renames.first().isEmpty() &&
                        destDb.research().ledgerEventCount() == 0 &&
                        destDb.research().studyPhaseCount() == 0
                },
                mergeRoom = { payload ->
                    destDb.withTransaction {
                        destDao.upsertEntries(payload.journalEntries.map { it.toEntity() })
                        destDao.upsertContext(payload.contextRows.map { it.toEntity() })
                        destDao.upsertMorningMeasures(payload.morningMeasures.map { it.toEntity() })
                        payload.continuityChanges.forEach { destDao.insertChange(it.toEntity()) }
                        // The production function, not a copy of it.
                        mergeResearchRows(destDb, payload)
                    }
                },
                mergeDataStores = { payload ->
                    backupRepository.import(payload.legacyBackupJson, System.currentTimeMillis())
                    notesPrefs.mergeRestored(payload.notes.map { it.toDomain() })
                    letterStore.mergeRestored(
                        payload.letters.mapNotNull { it.toDomain() },
                        payload.readLetterDates.mapNotNull { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() }.toSet(),
                    )
                    frictionPrefs.replaceFlagged(payload.frictionedApps.toSet())
                    frictionPrefs.replaceAlwaysOpen(payload.alwaysOpenApps.toSet())
                },
                recapture = { destSnapshotRepository.capture(System.currentTimeMillis()) },
                recordRestoreVerified = { at, hash -> continuityPrefs.recordRestore(at, hash) },
                recordVerifyFailed = { continuityPrefs.recordError(ContinuityErrorCode.RESTORE_VERIFY_FAILED) },
            )

            val result = coordinator.beginRestore(
                "round-trip.mab",
                envelopeBytes,
                originalSnapshot.contentSha256,
                originalSnapshot.formatVersion,
            )
            assertTrue("expected a Verified result, got $result", result is RestoreResult.Verified)
            assertEquals(originalSnapshot.contentSha256, (result as RestoreResult.Verified).contentHash)

            // --- Steps 8/9: recapture from the destination; the content
            // hash must match the original exactly ---
            val recapturedSnapshot = destSnapshotRepository.capture(now = 9_000L)
            assertEquals(originalSnapshot.contentSha256, recapturedSnapshot.contentSha256)

            // The research history crossed intact: same rows, same chain,
            // same head. A hash match alone would not show the chain still
            // links, and a chain that verified against itself would not
            // show it is the same chain.
            // The merge has to survive being re-run: mergeRoom commits
            // before ROOM_MERGED is persisted, so an interruption in that
            // window makes the next resume do exactly this. Before the
            // post-condition replaced per-insert row-id checks, this threw
            // — and resumeIfPending runs on app start, so it would have
            // crashed the launcher on every cold start.
            destDb.withTransaction { mergeResearchRows(destDb, originalSnapshot.payload) }
            assertEquals(
                originalSnapshot.payload.researchLedgerEvents.size,
                destDb.research().ledgerEventCount(),
            )
            assertEquals(originalSnapshot.payload.studyPhases.size, destDb.research().studyPhaseCount())

            val restoredLedger = destDb.research().ledgerEventsNow()
            assertEquals(sourceLedger.map { it.id }, restoredLedger.map { it.id })
            assertEquals(sourcePhases.map { it.id }, destDb.research().studyPhasesNow().map { it.id })
            assertEquals(
                LedgerIntegrity.VERIFIED,
                LedgerChain.verify(
                    restoredLedger.map { it.toDomain() },
                    LedgerChain.anchorOf(sourceLedger.map { it.toDomain() }),
                ),
            )

            // --- Field-level equality, not just the hash ---
            val restoredEntries = destDao.entriesNow()
            assertEquals(1, restoredEntries.size)
            assertEquals(multilineBody, restoredEntries.single().body)
            assertEquals("Round trip day", restoredEntries.single().title)

            val restoredContext = destDao.allContext().filter { it.entryId == entry.id }
            assertEquals(4, restoredContext.size)
            assertEquals(sourceFacts, restoredContext.associate { it.key to it.value })

            val restoredMeasures = destDao.morningMeasuresNow()
            assertEquals(1, restoredMeasures.size)
            val restoredMeasure = restoredMeasures.single()
            assertEquals(measure.mood, restoredMeasure.mood)
            assertEquals(measure.anxiety, restoredMeasure.anxiety)
            assertEquals(measure.angerUrge, restoredMeasure.angerUrge)
            assertEquals(measure.energyFunction, restoredMeasure.energyFunction)
            assertEquals(measure.sleepQuality, restoredMeasure.sleepQuality)

            val restoredNotes = notesPrefs.notes.first().notes
            assertEquals(1, restoredNotes.size)
            assertEquals("A quick note about the day", restoredNotes.single().body)

            val restoredLetters = letterStore.letters.first()
            assertEquals(1, restoredLetters.size)
            assertEquals("A letter to remember", restoredLetters.single().body)
            assertTrue(letterStore.readDates.first().contains(letterDate))

            assertEquals(setOf("com.example.social"), frictionPrefs.flaggedApps.first())
            assertEquals(setOf("com.example.emergency"), frictionPrefs.alwaysOpen.first())

            // --- Step 10: run the same restore a second time — idempotency,
            // no duplicate rows anywhere ---
            val second = coordinator.beginRestore(
                "round-trip.mab",
                envelopeBytes,
                originalSnapshot.contentSha256,
                originalSnapshot.formatVersion,
            )
            assertEquals(RestoreResult.AlreadyVerified, second)
            assertEquals(1, destDao.entriesNow().size)
            assertEquals(4, destDao.allContext().filter { it.entryId == entry.id }.size)
            assertEquals(1, destDao.morningMeasuresNow().size)
            assertEquals(1, notesPrefs.notes.first().notes.size)
            assertEquals(1, letterStore.letters.first().size)
            assertEquals(1, letterStore.readDates.first().size)
        } finally {
            destDb.close()
        }
    }
}
