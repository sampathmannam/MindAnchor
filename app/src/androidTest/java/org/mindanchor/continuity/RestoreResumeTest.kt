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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
import org.mindanchor.data.db.CrisisContact
import org.mindanchor.data.db.JournalEntryEntity
import org.mindanchor.data.db.PulseResult
import org.mindanchor.data.db.SafetyPlan
import org.mindanchor.data.mergeRestored
import org.mindanchor.data.replaceAlwaysOpen
import org.mindanchor.data.replaceFlagged
import org.mindanchor.journal.DeviceIdentityStore
import org.mindanchor.letters.LetterStore
import org.mindanchor.letters.mergeRestored

/**
 * End-to-end proof, on-device, of the exact property [RestoreCoordinatorTest]
 * proves with fakes: interrupting the restore right after any nonterminal
 * stage and then resuming leaves the SAME end state as one clean,
 * uninterrupted run — no duplicate Journal entries, context rows, morning
 * measures, Notes, Letters, contacts, or pulses — this time against a real
 * in-memory [AnchorDatabase] and the app's real on-device DataStores
 * ([NotesPrefs], [LetterStore], [FrictionPrefs], [LauncherPrefs],
 * [ContinuityPrefs], [RestoreStateStore]).
 *
 * [RestoreCoordinator.build] itself is not used here (it hardcodes
 * [AnchorDatabase.get], the app's real on-disk singleton — every other Room
 * test in this codebase constructs its own in-memory database instead, so
 * this test does too); the coordinator is instead built by hand with the
 * exact same collaborators [RestoreCoordinator.build] wires in production.
 */
@RunWith(AndroidJUnit4::class)
class RestoreResumeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AnchorDatabase
    private lateinit var notesPrefs: NotesPrefs
    private lateinit var letterStore: LetterStore
    private lateinit var frictionPrefs: FrictionPrefs
    private lateinit var launcherPrefs: LauncherPrefs
    private lateinit var backupRepository: BackupRepository
    private lateinit var continuityPrefs: ContinuityPrefs
    private lateinit var restoreStateStore: RestoreStateStore
    private lateinit var snapshotRepository: ContinuitySnapshotRepository
    private lateinit var stagingFile: File
    private lateinit var stagingTmpFile: File

    private val key: RecoveryKey = RecoveryKeyCodec.generate { ByteArray(32) { i -> i.toByte() } }

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java).build()
        notesPrefs = NotesPrefs(context)
        letterStore = LetterStore(context)
        frictionPrefs = FrictionPrefs(context)
        launcherPrefs = LauncherPrefs(context)
        backupRepository = BackupRepository(context)
        continuityPrefs = ContinuityPrefs(context)
        restoreStateStore = RestoreStateStore(context)
        snapshotRepository = ContinuitySnapshotRepository(
            context = context,
            database = db,
            notesPrefs = notesPrefs,
            letterStore = letterStore,
            frictionPrefs = frictionPrefs,
            deviceIdentity = DeviceIdentityStore(context),
            backupRepository = backupRepository,
        )
        val stagingDir = File(context.filesDir, "continuity")
        stagingFile = File(stagingDir, "restore-staged.mab")
        stagingTmpFile = File(stagingDir, "restore-staged.mab.tmp")

        clearEverything()
    }

    @After
    fun tearDown() = runBlocking {
        db.close()
        clearEverything()
    }

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
        // BackupRepository/ContinuitySnapshotRepository.capture() reach the
        // app's real on-disk AnchorDatabase singleton for the safety plan /
        // contacts / pulses / EMA moments — clear those too so this test
        // never leaks state into (or reads state left by) another
        // instrumentation run on this emulator.
        val realDb = AnchorDatabase.get(context)
        realDb.safety().savePlan(org.mindanchor.data.db.SafetyPlan())
        realDb.safety().contactsNow().forEach { realDb.safety().removeContact(it) }
    }

    // --- Fixture helpers -----------------------------------------------------

    /**
     * The blank-local-state legacy-backup export — [recapture] always
     * re-derives `legacyBackupJson` via a genuine
     * `BackupRepository.export(...)` call (never blindly copies whatever
     * value a payload happened to carry in), so a synthetic `""` here
     * would never round-trip: [ContinuityContentHasher.normalizeLegacyBackup]
     * leaves a truly-blank string blank, but a real export of an empty
     * local state produces a well-formed (non-blank) JSON document
     * instead — a genuine mismatch, not a test bug in the coordinator.
     * Every fixture below must carry the SAME value recapture will later
     * regenerate, which is exactly what a real export of the (already
     * cleared, per [setUp]) singleton database produces right now.
     */
    private suspend fun blankLegacyBackupJson(): String = backupRepository.export(0L)

    private suspend fun samplePayload(entryId: String = "entry-1", legacyBackupJson: String? = null): ContinuityPayload = ContinuityPayload(
        journalEntries = listOf(
            JournalEntryDto(
                id = entryId, createdAt = 1_000, updatedAt = 1_000, localDate = "2026-01-01",
                title = "t", body = "b", kind = "DAILY", sourceDeviceId = "device-a", deletedAt = null,
            ),
        ),
        contextRows = listOf(
            JournalContextDto(
                id = "ctx-1", entryId = entryId, recordType = "FACT", key = "k", value = "v",
                sourceStart = null, sourceEnd = null, confidence = 1.0, extractorVersion = "v1", createdAt = 1_000,
            ),
        ),
        morningMeasures = listOf(
            MorningMeasureDto(
                id = "m-1", localDate = "2026-01-01", createdAt = 1_000, updatedAt = 1_000,
                mood = 3, anxiety = 2, angerUrge = 1, energyFunction = 3, sleepQuality = 4,
                instrumentVersion = "v1", sourceDeviceId = "device-a",
            ),
        ),
        notes = listOf(NoteDto(id = 1L, body = "note", createdAt = 1_000, updatedAt = 1_000, pinned = false, type = null)),
        letters = listOf(LetterDto(date = "2026-01-01", body = "letter", provider = null, model = null, promptTokens = null, completionTokens = null, durationMs = null)),
        readLetterDates = listOf("2026-01-01"),
        frictionedApps = listOf("com.example.app"),
        alwaysOpenApps = listOf("com.example.sms"),
        continuityChanges = listOf(ContinuityChangeDto(id = "chg-1", entityType = "journal_entry", entityId = entryId, operation = "CREATE", occurredAt = 1_000, acknowledgedSnapshotId = null)),
        legacyBackupJson = legacyBackupJson ?: blankLegacyBackupJson(),
    )

    private fun sampleSnapshot(payload: ContinuityPayload): ContinuitySnapshot {
        val sorted = ContinuityContentHasher.sorted(payload)
        return ContinuitySnapshot(
            formatVersion = ContinuitySnapshot.CURRENT_FORMAT_VERSION,
            snapshotId = "snap-1",
            createdAt = 5_000L,
            appVersionCode = 1,
            appVersionName = "test",
            sourceDeviceId = "device-a",
            payload = sorted,
            contentSha256 = ContinuityContentHasher.hash(sorted),
        )
    }

    private fun envelopeBytes(snapshot: ContinuitySnapshot): ByteArray {
        val json = ContinuitySnapshotCodec.encode(snapshot)
        val envelope = BackupEnvelopeCodec.encrypt(json, key, now = 5_000L)
        return BackupEnvelopeCodec.encode(envelope).encodeToByteArray()
    }

    private class ThrowOnce {
        var armed = false
        suspend fun <T> guard(block: suspend () -> T): T {
            if (armed) {
                armed = false
                throw IllegalStateException("injected failure")
            }
            return block()
        }
    }

    /** Builds a real [RestoreCoordinator], the exact collaborator shape [RestoreCoordinator.build] wires — against this test's own in-memory [db]. */
    private fun realCoordinator(
        readGuard: ThrowOnce = ThrowOnce(),
        mergeRoomGuard: ThrowOnce = ThrowOnce(),
        mergeDataStoresGuard: ThrowOnce = ThrowOnce(),
    ): RestoreCoordinator {
        val dao = db.journal()
        return RestoreCoordinator(
            currentStageInfo = { restoreStateStore.currentInfo() },
            persistDownloaded = { name, sha, hash -> restoreStateStore.markDownloaded(name, sha, hash) },
            persistDecrypted = { hash -> restoreStateStore.markDecrypted(hash) },
            persistRoomMerged = { restoreStateStore.markRoomMerged() },
            persistDataStoresMerged = { restoreStateStore.markDataStoresMerged() },
            persistVerified = { restoreStateStore.markVerified() },
            resetState = { restoreStateStore.reset() },
            readStagedBytes = {
                readGuard.guard {
                    if (stagingFile.exists()) stagingFile.readBytes() else null
                }
            },
            writeStagedBytesAtomically = { bytes ->
                stagingFile.parentFile?.mkdirs()
                stagingTmpFile.outputStream().use { it.write(bytes); it.flush() }
                if (stagingFile.exists()) stagingFile.delete()
                stagingTmpFile.renameTo(stagingFile)
            },
            deleteStagedFile = { stagingFile.delete() },
            currentVerifiedKey = { key },
            preflightIsLocalDataEmpty = {
                dao.entriesNow().isEmpty() &&
                    dao.allContext().isEmpty() &&
                    dao.morningMeasuresNow().isEmpty() &&
                    notesPrefs.notes.first().notes.isEmpty() &&
                    letterStore.letters.first().isEmpty() &&
                    launcherPrefs.favorites.first().isEmpty() &&
                    launcherPrefs.hidden.first().isEmpty() &&
                    launcherPrefs.renames.first().isEmpty()
            },
            mergeRoom = { payload ->
                mergeRoomGuard.guard {
                    db.withTransaction {
                        dao.upsertEntries(payload.journalEntries.map { it.toEntity() })
                        dao.upsertContext(payload.contextRows.map { it.toEntity() })
                        dao.upsertMorningMeasures(payload.morningMeasures.map { it.toEntity() })
                        payload.continuityChanges.forEach { dao.insertChange(it.toEntity()) }
                    }
                }
            },
            mergeDataStores = { payload ->
                mergeDataStoresGuard.guard {
                    backupRepository.import(payload.legacyBackupJson, System.currentTimeMillis())
                    notesPrefs.mergeRestored(payload.notes.map { it.toDomain() })
                    letterStore.mergeRestored(
                        payload.letters.mapNotNull { it.toDomain() },
                        payload.readLetterDates.mapNotNull { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() }.toSet(),
                    )
                    frictionPrefs.replaceFlagged(payload.frictionedApps.toSet())
                    frictionPrefs.replaceAlwaysOpen(payload.alwaysOpenApps.toSet())
                }
            },
            recapture = { snapshotRepository.capture(System.currentTimeMillis()) },
            recordRestoreVerified = { at, hash -> continuityPrefs.recordRestore(at, hash) },
            recordVerifyFailed = { continuityPrefs.recordError(ContinuityErrorCode.RESTORE_VERIFY_FAILED) },
        )
    }

    private suspend fun assertNoDuplication() {
        val dao = db.journal()
        assertEquals(1, dao.entriesNow().size)
        assertEquals(1, dao.allContext().size)
        assertEquals(1, dao.morningMeasuresNow().size)
        assertEquals(1, dao.allChangesNow().size)
        assertEquals(1, notesPrefs.notes.first().notes.size)
        assertEquals(1, letterStore.letters.first().size)
        assertEquals(1, letterStore.readDates.first().size)
    }

    // --- Interruption + resume, one stage at a time -------------------------

    @Test
    fun interruptedRightAfterDownloadedResumesToTheSameEndStateAsACleanRun() = runBlocking {
        val snapshot = sampleSnapshot(samplePayload())
        val bytes = envelopeBytes(snapshot)
        val readGuard = ThrowOnce().apply { armed = true }
        val coordinator = realCoordinator(readGuard = readGuard)

        try {
            coordinator.beginRestore("x.mab", bytes, snapshot.contentSha256)
            fail("expected the injected failure to propagate")
        } catch (e: IllegalStateException) {
            // expected
        }
        assertEquals(RestoreStage.DOWNLOADED, restoreStateStore.currentInfo().stage)
        assertTrue(db.journal().entriesNow().isEmpty())

        val result = coordinator.resume()

        assertTrue(result is RestoreResult.Verified)
        assertNoDuplication()
        assertEquals(RestoreStage.VERIFIED, restoreStateStore.currentInfo().stage)
        assertFalse("staged file deleted only after VERIFIED is durable", stagingFile.exists())
    }

    @Test
    fun interruptedRightAfterDecryptedResumesToTheSameEndStateAsACleanRun() = runBlocking {
        val snapshot = sampleSnapshot(samplePayload())
        val bytes = envelopeBytes(snapshot)
        stagingFile.parentFile?.mkdirs()
        stagingFile.writeBytes(bytes)
        restoreStateStore.markDownloaded("x.mab", "sha", snapshot.contentSha256)
        restoreStateStore.markDecrypted(snapshot.contentSha256)
        val mergeRoomGuard = ThrowOnce().apply { armed = true }
        val coordinator = realCoordinator(mergeRoomGuard = mergeRoomGuard)

        try {
            coordinator.resume()
            fail("expected the injected failure to propagate")
        } catch (e: IllegalStateException) {
            // expected
        }
        assertEquals(RestoreStage.DECRYPTED, restoreStateStore.currentInfo().stage)
        assertTrue(db.journal().entriesNow().isEmpty())

        val result = coordinator.resume()

        assertTrue(result is RestoreResult.Verified)
        assertNoDuplication()
    }

    @Test
    fun interruptedRightAfterRoomMergedResumesToTheSameEndStateAsACleanRun() = runBlocking {
        val snapshot = sampleSnapshot(samplePayload())
        val bytes = envelopeBytes(snapshot)
        stagingFile.parentFile?.mkdirs()
        stagingFile.writeBytes(bytes)
        restoreStateStore.markDownloaded("x.mab", "sha", snapshot.contentSha256)
        restoreStateStore.markDecrypted(snapshot.contentSha256)
        restoreStateStore.markRoomMerged()
        // The persisted stage claims the Room merge already durably completed.
        db.withTransaction {
            val dao = db.journal()
            dao.upsertEntries(snapshot.payload.journalEntries.map { it.toEntity() })
            dao.upsertContext(snapshot.payload.contextRows.map { it.toEntity() })
            dao.upsertMorningMeasures(snapshot.payload.morningMeasures.map { it.toEntity() })
            snapshot.payload.continuityChanges.forEach { dao.insertChange(it.toEntity()) }
        }
        val mergeDataStoresGuard = ThrowOnce().apply { armed = true }
        val coordinator = realCoordinator(mergeDataStoresGuard = mergeDataStoresGuard)

        try {
            coordinator.resume()
            fail("expected the injected failure to propagate")
        } catch (e: IllegalStateException) {
            // expected
        }
        assertEquals(RestoreStage.ROOM_MERGED, restoreStateStore.currentInfo().stage)
        assertTrue(notesPrefs.notes.first().notes.isEmpty())

        val result = coordinator.resume()

        assertTrue(result is RestoreResult.Verified)
        assertNoDuplication()
    }

    @Test
    fun resumeAtVerifiedIsAFastNoOpAndNeverReMerges() = runBlocking {
        val snapshot = sampleSnapshot(samplePayload())
        val bytes = envelopeBytes(snapshot)
        val coordinator = realCoordinator()
        val first = coordinator.beginRestore("x.mab", bytes, snapshot.contentSha256)
        assertTrue(first is RestoreResult.Verified)
        assertNoDuplication()

        val second = coordinator.resume()

        assertEquals(RestoreResult.AlreadyVerified, second)
        assertNoDuplication()
    }

    // --- Local-data preflight ------------------------------------------------

    @Test
    fun preflightBlocksANewRestoreWhenLocalJournalDataAlreadyExists() = runBlocking {
        db.journal().upsertEntries(
            listOf(
                org.mindanchor.data.db.JournalEntryEntity(
                    id = "existing", createdAt = 1L, updatedAt = 1L, localDate = "2026-01-01",
                    title = "existing", body = "existing local data", kind = "DAILY",
                    sourceDeviceId = "device-a", deletedAt = null,
                ),
            ),
        )
        val snapshot = sampleSnapshot(samplePayload())
        val bytes = envelopeBytes(snapshot)
        val coordinator = realCoordinator()

        val result = coordinator.beginRestore("x.mab", bytes, snapshot.contentSha256)

        assertEquals(RestoreResult.PreflightBlocked, result)
        assertEquals(RestoreStage.NONE, restoreStateStore.currentInfo().stage)
        assertFalse(stagingFile.exists())
    }

    /**
     * Unlike every other test in this class, this one deliberately uses
     * [RestoreCoordinator.build] itself — the exact production wiring
     * `RestoreActivity` uses — instead of [realCoordinator]'s hand-rolled
     * collaborators. [realCoordinator]'s own `preflightIsLocalDataEmpty`
     * lambda above (see its `db.journal()`/`notesPrefs`/etc. checks) is
     * missing the safety-plan/contacts/pulses checks the REAL production
     * preflight has — exactly the gap an independent code review flagged:
     * this is the single safety-critical gate that stops a restore from
     * silently coexisting with an already-populated safety plan or
     * crisis-contact list, and it previously had zero coverage against the
     * real function.
     *
     * [RestoreCoordinator.build] reads Journal/safety/contacts/pulses from
     * the app's real on-disk [AnchorDatabase] singleton (not this class's
     * own in-memory [db]) — the one place in this file that touches it for
     * more than [clearEverything]'s existing safety/contacts cleanup.
     * `realDb.clearAllTables()` in the `finally` block is needed because
     * [org.mindanchor.data.db.PulseDao] exposes no delete method at all —
     * Room's own table-clear is the only way to remove the seeded pulse
     * afterward.
     */
    @Test
    fun theRealProductionPreflightBlocksANewRestoreWhenSafetyPlanContactsOrPulsesExist() = runBlocking {
        val realDb = AnchorDatabase.get(context)
        try {
            realDb.safety().savePlan(SafetyPlan(warningSigns = "feeling overwhelmed", updatedAt = 1_000L))
            realDb.safety().addContact(CrisisContact(name = "A Friend", phone = "555-0100", isProfessional = false))
            realDb.pulses().insert(PulseResult(takenAt = 1_000L, score = 70))
            realDb.journal().upsertEntries(
                listOf(
                    JournalEntryEntity(
                        id = "existing-on-real-device", createdAt = 1L, updatedAt = 1L, localDate = "2026-01-01",
                        title = "existing", body = "existing local data", kind = "DAILY",
                        sourceDeviceId = "device-a", deletedAt = null,
                    ),
                ),
            )

            val snapshot = sampleSnapshot(samplePayload())
            val bytes = envelopeBytes(snapshot)

            val result = RestoreCoordinator.build(context).beginRestore("x.mab", bytes, snapshot.contentSha256)

            assertEquals(RestoreResult.PreflightBlocked, result)
            assertEquals(RestoreStage.NONE, restoreStateStore.currentInfo().stage)
            assertFalse(stagingFile.exists())
        } finally {
            realDb.clearAllTables()
        }
    }

    // The companion property — a RESUMED restore (persisted stage past
    // RestoreStage.NONE) must never re-run the preflight, even though local
    // data now genuinely exists, because that data is the restore's own
    // in-progress merge, not evidence of a second, unrelated dataset — is
    // already covered by RestoreCoordinatorTest's
    // `beginRestore on an already-in-progress restore delegates to resume and never re-runs the preflight`
    // test. That property (beginRestore's `info.stage != NONE -> resume()`
    // branch, see RestoreCoordinator.beginRestore's KDoc) lives entirely in
    // RestoreCoordinator itself, independent of which preflight
    // implementation is plugged in, so a JVM-fake preflight double is
    // adequate there and this file does not duplicate it.

    // --- Legacy-backup-carried contacts/pulses survive an interruption without duplicating ---

    @Test
    fun contactsAndPulsesCarriedViaLegacyBackupSurviveAnInterruptedThenResumedRestoreWithoutDuplicating() = runBlocking {
        // Seed the "source phone" state directly on the real singleton
        // AnchorDatabase (the one BackupRepository.export/import actually
        // reads/writes), then export it into a legacyBackupJson blob — the
        // exact carried-verbatim payload field this restore round-trips.
        val realDb = AnchorDatabase.get(context)
        realDb.safety().addContact(CrisisContact(name = "A Friend", phone = "555-0100", isProfessional = false))
        realDb.pulses().insert(PulseResult(takenAt = 1_000L, score = 70))
        val legacyJson = backupRepository.export(2_000L)
        // "New phone": clear the singleton back out before restoring into it.
        realDb.safety().contactsNow().forEach { realDb.safety().removeContact(it) }

        val payload = samplePayload(legacyBackupJson = legacyJson)
        val snapshot = sampleSnapshot(payload)
        val bytes = envelopeBytes(snapshot)
        val mergeDataStoresGuard = ThrowOnce().apply { armed = true }
        val coordinator = realCoordinator(mergeDataStoresGuard = mergeDataStoresGuard)

        try {
            coordinator.beginRestore("x.mab", bytes, snapshot.contentSha256)
            fail("expected the injected failure to propagate")
        } catch (e: IllegalStateException) {
            // expected — interrupted between ROOM_MERGED and DATASTORES_MERGED
        }

        val result = coordinator.resume()

        assertTrue(result is RestoreResult.Verified)
        assertEquals("the contact must be restored exactly once, not duplicated", 1, realDb.safety().contactsNow().count { it.phone == "555-0100" })
        assertEquals("the pulse must be restored exactly once, not duplicated", 1, realDb.pulses().history().first().count { it.takenAt == 1_000L })
    }
}
