package org.mindanchor.continuity

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mindanchor.continuity.crypto.BackupEnvelopeCodec
import org.mindanchor.continuity.crypto.RecoveryKey
import org.mindanchor.continuity.crypto.RecoveryKeyCodec

/**
 * The Task 11 staged-restore algorithm, exercised as plain JVM logic —
 * every Room/DataStore collaborator is a fake in-memory lambda, no Room, no
 * Context, no Robolectric — mirroring [ContinuityBackupCoordinatorTest]'s
 * style. Real [BackupEnvelopeCodec]/[ContinuitySnapshotCodec]/
 * [RecoveryKeyCodec] are used for the crypto layer, so the wrong-key and
 * corrupt-envelope paths are exercised for real, not stubbed.
 *
 * The core property every "interrupted, then resumed" test proves: calling
 * the full DOWNLOADED→DECRYPTED→ROOM_MERGED→DATASTORES_MERGED→VERIFIED
 * sequence twice (once interrupted right after a given stage, once to
 * completion) leaves the exact same end state as one clean, uninterrupted
 * run — no duplicate Journal entries, context rows, morning measures,
 * Notes, Letters, or continuity-change rows, and each merge collaborator is
 * invoked exactly once for the data it actually applied.
 */
class RestoreCoordinatorTest {

    private fun sampleKey(seed: Int = 1): RecoveryKey =
        RecoveryKeyCodec.generate { ByteArray(32) { i -> ((seed + i) and 0xFF).toByte() } }

    private fun samplePayload(entryId: String = "entry-1"): ContinuityPayload = ContinuityPayload(
        journalEntries = listOf(
            JournalEntryDto(
                id = entryId, createdAt = 1000, updatedAt = 1000, localDate = "2026-01-01",
                title = "t", body = "b", kind = "DAILY", sourceDeviceId = "device-a", deletedAt = null,
            ),
        ),
        contextRows = listOf(
            JournalContextDto(
                id = "ctx-1", entryId = entryId, recordType = "FACT", key = "k", value = "v",
                sourceStart = null, sourceEnd = null, confidence = 1.0, extractorVersion = "v1", createdAt = 1000,
            ),
        ),
        morningMeasures = listOf(
            MorningMeasureDto(
                id = "m-1", localDate = "2026-01-01", createdAt = 1000, updatedAt = 1000,
                mood = 3, anxiety = 2, angerUrge = 1, energyFunction = 3, sleepQuality = 4,
                instrumentVersion = "v1", sourceDeviceId = "device-a",
            ),
        ),
        notes = listOf(NoteDto(id = 1L, body = "note", createdAt = 1000, updatedAt = 1000, pinned = false, type = null)),
        letters = listOf(LetterDto(date = "2026-01-01", body = "letter", provider = null, model = null, promptTokens = null, completionTokens = null, durationMs = null)),
        readLetterDates = listOf("2026-01-01"),
        frictionedApps = listOf("com.example.app"),
        alwaysOpenApps = listOf("com.example.sms"),
        continuityChanges = listOf(ContinuityChangeDto(id = "chg-1", entityType = "journal_entry", entityId = entryId, operation = "CREATE", occurredAt = 1000, acknowledgedSnapshotId = null)),
        legacyBackupJson = "",
    )

    private fun sampleSnapshot(payload: ContinuityPayload = samplePayload()): ContinuitySnapshot {
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

    private fun envelopeBytes(snapshot: ContinuitySnapshot, key: RecoveryKey): ByteArray {
        val json = ContinuitySnapshotCodec.encode(snapshot)
        val envelope = BackupEnvelopeCodec.encrypt(json, key, now = 5_000L)
        return BackupEnvelopeCodec.encode(envelope).encodeToByteArray()
    }

    // --- Fakes ------------------------------------------------------------

    private class FakeStateStore {
        var stage: RestoreStage = RestoreStage.NONE
        var remoteName: String? = null
        var envelopeSha256: String? = null
        var expectedContentHash: String? = null
        var expectedFormatVersion: Int? = null

        fun currentInfo(): RestoreStageInfo =
            RestoreStageInfo(stage, remoteName, envelopeSha256, expectedContentHash, expectedFormatVersion)

        fun markDownloaded(name: String, sha256: String, hash: String, formatVersion: Int) {
            stage = RestoreStage.DOWNLOADED
            remoteName = name
            envelopeSha256 = sha256
            expectedContentHash = hash
            expectedFormatVersion = formatVersion
        }

        fun markDecrypted(hash: String, formatVersion: Int) {
            stage = RestoreStage.DECRYPTED
            expectedContentHash = hash
            expectedFormatVersion = formatVersion
        }

        fun markRoomMerged() { stage = RestoreStage.ROOM_MERGED }
        fun markDataStoresMerged() { stage = RestoreStage.DATASTORES_MERGED }
        fun markVerified() { stage = RestoreStage.VERIFIED }
        fun reset() {
            stage = RestoreStage.NONE
            expectedFormatVersion = null
            remoteName = null
            envelopeSha256 = null
            expectedContentHash = null
        }
    }

    private class FakeStagedFile {
        var bytes: ByteArray? = null
        fun read(): ByteArray? = bytes
        fun write(b: ByteArray) { bytes = b }
        fun delete() { bytes = null }
    }

    /** A fake Room-DAO-shaped + DataStore-shaped local store, replicating the REPLACE/IGNORE/dedup semantics the real collaborators use. */
    private class FakeLocalStore {
        val entries = LinkedHashMap<String, JournalEntryDto>()
        val context = LinkedHashMap<String, JournalContextDto>()
        val measures = LinkedHashMap<String, MorningMeasureDto>()
        val changes = LinkedHashMap<String, ContinuityChangeDto>()
        val notes = LinkedHashMap<Long, NoteDto>()
        val letters = LinkedHashMap<String, LetterDto>()
        val readLetterDates = mutableSetOf<String>()
        var frictionedApps = mutableSetOf<String>()
        var alwaysOpenApps = mutableSetOf<String>()
        var legacyBackupJson = ""

        var roomMergeCalls = 0
        var dataStoreMergeCalls = 0

        /** Mirrors upsertEntries/upsertContext/upsertMorningMeasures (REPLACE) + insertChange (IGNORE). */
        fun mergeRoom(payload: ContinuityPayload) {
            roomMergeCalls++
            payload.journalEntries.forEach { entries[it.id] = it }
            payload.contextRows.forEach { context[it.id] = it }
            payload.morningMeasures.forEach { measures[it.id] = it }
            payload.continuityChanges.forEach { changes.putIfAbsent(it.id, it) }
        }

        /** Mirrors BackupRepository.import + NotesPrefs/LetterStore.mergeRestored + FrictionPrefs.replace*. */
        fun mergeDataStores(payload: ContinuityPayload) {
            dataStoreMergeCalls++
            legacyBackupJson = payload.legacyBackupJson
            payload.notes.forEach { incoming ->
                val existing = notes[incoming.id]
                if (existing == null || incoming.updatedAt > existing.updatedAt) notes[incoming.id] = incoming
            }
            payload.letters.forEach { incoming -> letters.putIfAbsent(incoming.date, incoming) }
            readLetterDates += payload.readLetterDates
            frictionedApps = payload.frictionedApps.toMutableSet()
            alwaysOpenApps = payload.alwaysOpenApps.toMutableSet()
        }

        fun snapshotPayload(): ContinuityPayload = ContinuityPayload(
            journalEntries = entries.values.toList(),
            contextRows = context.values.toList(),
            morningMeasures = measures.values.toList(),
            notes = notes.values.toList(),
            letters = letters.values.toList(),
            readLetterDates = readLetterDates.toList(),
            frictionedApps = frictionedApps.toList(),
            alwaysOpenApps = alwaysOpenApps.toList(),
            continuityChanges = changes.values.toList(),
            legacyBackupJson = legacyBackupJson,
        )
    }

    /** Throws once (the injected mid-stage "crash"), then behaves normally on every subsequent call. */
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

    private fun coordinator(
        stateStore: FakeStateStore,
        stagedFile: FakeStagedFile,
        localStore: FakeLocalStore,
        key: RecoveryKey?,
        preflightEmpty: Boolean = true,
        preflightCalls: MutableList<Unit> = mutableListOf(),
        readGuard: ThrowOnce = ThrowOnce(),
        mergeRoomGuard: ThrowOnce = ThrowOnce(),
        mergeDataStoresGuard: ThrowOnce = ThrowOnce(),
        recaptureGuard: ThrowOnce = ThrowOnce(),
        verifyFailedCalls: MutableList<Unit> = mutableListOf(),
        restoreVerifiedCalls: MutableList<Pair<Long, String>> = mutableListOf(),
        // Lets a test simulate the re-captured local content actually
        // disagreeing with the snapshot's own contentSha256 (e.g. a
        // concurrent local write racing the restore) — by default,
        // recapture faithfully reflects whatever localStore currently
        // holds, which is what every idempotency test needs.
        recaptureOverride: (suspend () -> ContinuitySnapshot)? = null,
    ): RestoreCoordinator = RestoreCoordinator(
        currentStageInfo = { stateStore.currentInfo() },
        persistDownloaded = { name, sha, hash, version ->
            stateStore.markDownloaded(name, sha, hash, version)
        },
        persistDecrypted = { hash, version -> stateStore.markDecrypted(hash, version) },
        persistRoomMerged = { stateStore.markRoomMerged() },
        persistDataStoresMerged = { stateStore.markDataStoresMerged() },
        persistVerified = { stateStore.markVerified() },
        resetState = { stateStore.reset() },
        readStagedBytes = { readGuard.guard { stagedFile.read() } },
        writeStagedBytesAtomically = { bytes -> stagedFile.write(bytes) },
        deleteStagedFile = { stagedFile.delete() },
        currentVerifiedKey = { key },
        preflightIsLocalDataEmpty = { preflightCalls.add(Unit); preflightEmpty },
        mergeRoom = { payload -> mergeRoomGuard.guard { localStore.mergeRoom(payload) } },
        mergeDataStores = { payload -> mergeDataStoresGuard.guard { localStore.mergeDataStores(payload) } },
        recapture = {
            recaptureGuard.guard {
                recaptureOverride?.invoke() ?: ContinuitySnapshot(
                    formatVersion = ContinuitySnapshot.CURRENT_FORMAT_VERSION,
                    snapshotId = "recaptured",
                    createdAt = 9_000L,
                    appVersionCode = 1,
                    appVersionName = "test",
                    sourceDeviceId = "device-a",
                    payload = localStore.snapshotPayload(),
                    contentSha256 = "unused-recomputed-by-coordinator",
                )
            }
        },
        recordRestoreVerified = { at, hash -> restoreVerifiedCalls += at to hash },
        recordVerifyFailed = { verifyFailedCalls += Unit },
        now = { 9_000L },
    )

    // --- Happy path ---------------------------------------------------------

    @Test
    fun `a clean uninterrupted run reaches VERIFIED, merges once, and deletes the staged file`() = runBlocking {
        val key = sampleKey()
        val snapshot = sampleSnapshot()
        val bytes = envelopeBytes(snapshot, key)
        val stateStore = FakeStateStore()
        val stagedFile = FakeStagedFile()
        val localStore = FakeLocalStore()
        val restoreVerifiedCalls = mutableListOf<Pair<Long, String>>()

        val result = coordinator(stateStore, stagedFile, localStore, key, restoreVerifiedCalls = restoreVerifiedCalls)
            .beginRestore(
                "MindAnchor-Continuity-Latest.mab",
                bytes,
                snapshot.contentSha256,
                ContinuitySnapshot.CURRENT_FORMAT_VERSION,
            )

        assertTrue(result is RestoreResult.Verified)
        assertEquals(snapshot.contentSha256, (result as RestoreResult.Verified).contentHash)
        assertEquals(RestoreStage.VERIFIED, stateStore.stage)
        assertNull("the staged file must be deleted only after VERIFIED is durable", stagedFile.bytes)
        assertEquals(1, localStore.roomMergeCalls)
        assertEquals(1, localStore.dataStoreMergeCalls)
        assertEquals(1, localStore.entries.size)
        assertEquals(listOf(9_000L to snapshot.contentSha256), restoreVerifiedCalls)
    }

    // --- Interruption + resume, one stage at a time -------------------------

    @Test
    fun `interrupted right after DOWNLOADED resumes to the same end state as a clean run`() = runBlocking {
        val key = sampleKey()
        val snapshot = sampleSnapshot()
        val bytes = envelopeBytes(snapshot, key)

        val stateStore = FakeStateStore()
        val stagedFile = FakeStagedFile()
        val localStore = FakeLocalStore()
        val readGuard = ThrowOnce().apply { armed = true }
        val coord = coordinator(stateStore, stagedFile, localStore, key, readGuard = readGuard)

        try {
            coord.beginRestore(
                "MindAnchor-Continuity-Latest.mab",
                bytes,
                snapshot.contentSha256,
                ContinuitySnapshot.CURRENT_FORMAT_VERSION,
            )
            fail("expected the injected failure to propagate")
        } catch (e: IllegalStateException) {
            // expected — the crash happened between DOWNLOADED and DECRYPTED
        }
        assertEquals("stage must not advance past DOWNLOADED", RestoreStage.DOWNLOADED, stateStore.stage)
        assertTrue("the staged file itself is written before decrypt is attempted", stagedFile.bytes != null)
        assertTrue("nothing was merged before the crash", localStore.entries.isEmpty())

        val result = coord.resume()

        assertEquals(cleanRunPayload(snapshot, key), localStore.snapshotPayload().let { ContinuityContentHasher.sorted(it) })
        assertTrue(result is RestoreResult.Verified)
        assertEquals("mergeRoom must have run exactly once total, not zero or twice", 1, localStore.roomMergeCalls)
        assertEquals("mergeDataStores must have run exactly once total, not zero or twice", 1, localStore.dataStoreMergeCalls)
        assertEquals(1, localStore.entries.size)
        assertEquals(1, localStore.notes.size)
        assertEquals(1, localStore.changes.size)
    }

    @Test
    fun `interrupted right after DECRYPTED resumes to the same end state as a clean run`() = runBlocking {
        val key = sampleKey()
        val snapshot = sampleSnapshot()
        val bytes = envelopeBytes(snapshot, key)

        val stateStore = FakeStateStore().apply {
            stage = RestoreStage.DECRYPTED
            expectedContentHash = snapshot.contentSha256
            expectedFormatVersion = snapshot.formatVersion
        }
        val stagedFile = FakeStagedFile().apply { write(bytes) }
        val localStore = FakeLocalStore()
        val mergeRoomGuard = ThrowOnce().apply { armed = true }
        val coord = coordinator(stateStore, stagedFile, localStore, key, mergeRoomGuard = mergeRoomGuard)

        try {
            coord.resume()
            fail("expected the injected failure to propagate")
        } catch (e: IllegalStateException) {
            // expected
        }
        assertEquals("stage must not advance past DECRYPTED", RestoreStage.DECRYPTED, stateStore.stage)
        assertTrue("mergeRoom's guard threw before applying anything", localStore.entries.isEmpty())

        val result = coord.resume()

        assertTrue(result is RestoreResult.Verified)
        assertEquals(cleanRunPayload(snapshot, key), ContinuityContentHasher.sorted(localStore.snapshotPayload()))
        assertEquals(1, localStore.roomMergeCalls)
        assertEquals(1, localStore.dataStoreMergeCalls)
        assertEquals(1, localStore.entries.size)
    }

    @Test
    fun `interrupted right after ROOM_MERGED resumes to the same end state as a clean run`() = runBlocking {
        val key = sampleKey()
        val snapshot = sampleSnapshot()
        val bytes = envelopeBytes(snapshot, key)

        val stateStore = FakeStateStore().apply {
            stage = RestoreStage.ROOM_MERGED
            expectedContentHash = snapshot.contentSha256
            expectedFormatVersion = snapshot.formatVersion
        }
        val stagedFile = FakeStagedFile().apply { write(bytes) }
        // The persisted stage ROOM_MERGED means the Room merge already
        // durably completed in the (simulated) prior, interrupted run.
        val localStore = FakeLocalStore().apply { mergeRoom(snapshot.payload) }
        val mergeDataStoresGuard = ThrowOnce().apply { armed = true }
        val coord = coordinator(stateStore, stagedFile, localStore, key, mergeDataStoresGuard = mergeDataStoresGuard)

        try {
            coord.resume()
            fail("expected the injected failure to propagate")
        } catch (e: IllegalStateException) {
            // expected
        }
        assertEquals("stage must not advance past ROOM_MERGED", RestoreStage.ROOM_MERGED, stateStore.stage)
        assertTrue("mergeDataStores's guard threw before applying anything", localStore.notes.isEmpty())

        val result = coord.resume()

        assertTrue(result is RestoreResult.Verified)
        assertEquals(cleanRunPayload(snapshot, key), ContinuityContentHasher.sorted(localStore.snapshotPayload()))
        assertEquals(1, localStore.dataStoreMergeCalls)
        assertEquals(1, localStore.notes.size)
        assertEquals(1, localStore.letters.size)
    }

    @Test
    fun `interrupted right after DATASTORES_MERGED resumes to the same end state as a clean run`() = runBlocking {
        val key = sampleKey()
        val snapshot = sampleSnapshot()
        val bytes = envelopeBytes(snapshot, key)

        val stateStore = FakeStateStore().apply {
            stage = RestoreStage.DATASTORES_MERGED
            expectedContentHash = snapshot.contentSha256
            expectedFormatVersion = snapshot.formatVersion
        }
        val stagedFile = FakeStagedFile().apply { write(bytes) }
        val localStore = FakeLocalStore().apply {
            // Simulate the merges having already happened in the interrupted prior run.
            mergeRoom(snapshot.payload)
            mergeDataStores(snapshot.payload)
        }
        val recaptureGuard = ThrowOnce().apply { armed = true }
        val coord = coordinator(stateStore, stagedFile, localStore, key, recaptureGuard = recaptureGuard)

        try {
            coord.resume()
            fail("expected the injected failure to propagate")
        } catch (e: IllegalStateException) {
            // expected
        }
        assertEquals("stage must not advance to VERIFIED", RestoreStage.DATASTORES_MERGED, stateStore.stage)
        assertTrue("the staged file is kept while not yet VERIFIED", stagedFile.bytes != null)

        val result = coord.resume()

        assertTrue(result is RestoreResult.Verified)
        assertNull("staged file deleted only now, after VERIFIED is durable", stagedFile.bytes)
        // The merges from the interrupted run were NOT re-applied a second time.
        assertEquals(1, localStore.roomMergeCalls)
        assertEquals(1, localStore.dataStoreMergeCalls)
        assertEquals(1, localStore.entries.size)
    }

    private fun cleanRunPayload(snapshot: ContinuitySnapshot, key: RecoveryKey): ContinuityPayload = runBlocking {
        val stateStore = FakeStateStore()
        val stagedFile = FakeStagedFile()
        val localStore = FakeLocalStore()
        coordinator(stateStore, stagedFile, localStore, key)
            .beginRestore(
                "clean-run.mab",
                envelopeBytes(snapshot, key),
                snapshot.contentSha256,
                snapshot.formatVersion,
            )
        ContinuityContentHasher.sorted(localStore.snapshotPayload())
    }

    // --- resume() at terminal / empty stages is a safe no-op ---------------

    @Test
    fun `resume at NONE is a no-op`() = runBlocking {
        val stateStore = FakeStateStore()
        val stagedFile = FakeStagedFile()
        val localStore = FakeLocalStore()

        val result = coordinator(stateStore, stagedFile, localStore, sampleKey()).resume()

        assertEquals(RestoreResult.NothingStaged, result)
        assertEquals(0, localStore.roomMergeCalls)
    }

    @Test
    fun `resume at VERIFIED is a fast no-op and never re-merges`() = runBlocking {
        val key = sampleKey()
        val snapshot = sampleSnapshot()
        val stateStore = FakeStateStore().apply { stage = RestoreStage.VERIFIED }
        val stagedFile = FakeStagedFile() // already deleted, per the VERIFIED contract
        val localStore = FakeLocalStore().apply {
            mergeRoom(snapshot.payload)
            mergeDataStores(snapshot.payload)
        }

        val result = coordinator(stateStore, stagedFile, localStore, key).resume()

        assertEquals(RestoreResult.AlreadyVerified, result)
        assertEquals("resume() at VERIFIED must not re-run the Room merge", 1, localStore.roomMergeCalls)
        assertEquals("resume() at VERIFIED must not re-run the DataStore merge", 1, localStore.dataStoreMergeCalls)
    }

    // --- Wrong key vs. corruption are distinct, and neither mutates anything ---

    @Test
    fun `a wrong recovery key is reported distinctly from corruption and mutates nothing`() = runBlocking {
        val rightKey = sampleKey(seed = 1)
        val wrongKey = sampleKey(seed = 99)
        val snapshot = sampleSnapshot()
        val bytes = envelopeBytes(snapshot, rightKey)

        val stateStore = FakeStateStore()
        val stagedFile = FakeStagedFile()
        val localStore = FakeLocalStore()

        val result = coordinator(stateStore, stagedFile, localStore, wrongKey)
            .beginRestore(
                "MindAnchor-Continuity-Latest.mab",
                bytes,
                snapshot.contentSha256,
                ContinuitySnapshot.CURRENT_FORMAT_VERSION,
            )

        assertEquals(RestoreResult.WrongRecoveryKey, result)
        assertEquals("a wrong key must not advance the stage past DOWNLOADED", RestoreStage.DOWNLOADED, stateStore.stage)
        assertTrue("nothing was merged", localStore.entries.isEmpty())
        assertTrue("the staged file is kept for a retry with the correct key", stagedFile.bytes != null)
    }

    @Test
    fun `a corrupt staged envelope is reported distinctly from a wrong key and mutates nothing`() = runBlocking {
        val key = sampleKey()
        val stateStore = FakeStateStore()
        val stagedFile = FakeStagedFile().apply { write("not a valid envelope at all".encodeToByteArray()) }
        stateStore.markDownloaded(
            "MindAnchor-Continuity-Latest.mab",
            "irrelevant-sha",
            "irrelevant-hash",
            ContinuitySnapshot.CURRENT_FORMAT_VERSION,
        )
        val localStore = FakeLocalStore()

        val result = coordinator(stateStore, stagedFile, localStore, key).resume()

        assertEquals(RestoreResult.StagedFileCorrupt, result)
        assertEquals(RestoreStage.DOWNLOADED, stateStore.stage)
        assertTrue(localStore.entries.isEmpty())
    }

    @Test
    fun `no verified recovery key on this device is reported as KeyMissing before any decrypt attempt`() = runBlocking {
        val snapshot = sampleSnapshot()
        val stateStore = FakeStateStore()
        val stagedFile = FakeStagedFile().apply { write(envelopeBytes(snapshot, sampleKey())) }
        stateStore.markDownloaded("x.mab", "sha", "hash", ContinuitySnapshot.CURRENT_FORMAT_VERSION)
        val localStore = FakeLocalStore()

        val result = coordinator(stateStore, stagedFile, localStore, key = null).resume()

        assertEquals(RestoreResult.KeyMissing, result)
        assertEquals(RestoreStage.DOWNLOADED, stateStore.stage)
    }

    // --- Preflight gate ------------------------------------------------------

    @Test
    fun `beginRestore refuses to start when local data is not empty and mutates nothing`() = runBlocking {
        val key = sampleKey()
        val snapshot = sampleSnapshot()
        val bytes = envelopeBytes(snapshot, key)
        val stateStore = FakeStateStore()
        val stagedFile = FakeStagedFile()
        val localStore = FakeLocalStore()

        val result = coordinator(stateStore, stagedFile, localStore, key, preflightEmpty = false)
            .beginRestore("x.mab", bytes, snapshot.contentSha256, ContinuitySnapshot.CURRENT_FORMAT_VERSION)

        assertEquals(RestoreResult.PreflightBlocked, result)
        assertEquals("the stage must stay NONE — nothing was staged", RestoreStage.NONE, stateStore.stage)
        assertNull("no staging file was written", stagedFile.bytes)
    }

    @Test
    fun `beginRestore on an already-in-progress restore delegates to resume and never re-runs the preflight`() = runBlocking {
        val key = sampleKey()
        val snapshot = sampleSnapshot()
        val bytes = envelopeBytes(snapshot, key)
        val stateStore = FakeStateStore().apply {
            stage = RestoreStage.ROOM_MERGED
            expectedContentHash = snapshot.contentSha256
            expectedFormatVersion = snapshot.formatVersion
        }
        val stagedFile = FakeStagedFile().apply { write(bytes) }
        val localStore = FakeLocalStore().apply { mergeRoom(snapshot.payload) }
        val preflightCalls = mutableListOf<Unit>()

        // preflightEmpty = false would normally refuse — but since a restore
        // is already in progress, beginRestore must delegate to resume() and
        // never even check the preflight.
        val result = coordinator(stateStore, stagedFile, localStore, key, preflightEmpty = false, preflightCalls = preflightCalls)
            .beginRestore(
                "a-different-candidate.mab",
                "different-bytes".encodeToByteArray(),
                "a-different-hash",
                ContinuitySnapshot.CURRENT_FORMAT_VERSION,
            )

        assertTrue(result is RestoreResult.Verified)
        assertTrue("the preflight must never run for an in-progress restore", preflightCalls.isEmpty())
    }

    // --- Verify mismatch: never roll back, never delete to force a match ----

    @Test
    fun `a content hash mismatch at the final stage keeps the merged data and the staged file, and never claims VERIFIED`() = runBlocking {
        val key = sampleKey()
        val snapshot = sampleSnapshot()
        val bytes = envelopeBytes(snapshot, key)
        val stateStore = FakeStateStore()
        val stagedFile = FakeStagedFile()
        val localStore = FakeLocalStore()
        val verifyFailedCalls = mutableListOf<Unit>()

        // Simulates the final re-capture disagreeing with what was actually
        // merged (e.g. a concurrent local write racing the restore):
        // recapture returns a payload that was never part of this
        // envelope's own content, so it cannot hash-match snapshot.contentSha256
        // no matter how faithfully the merge phases themselves ran.
        val mismatchedSnapshot = sampleSnapshot(samplePayload(entryId = "a-completely-different-entry"))
        val recaptureOverride: suspend () -> ContinuitySnapshot = { mismatchedSnapshot }

        val result = coordinator(
            stateStore, stagedFile, localStore, key,
            verifyFailedCalls = verifyFailedCalls, recaptureOverride = recaptureOverride,
        ).beginRestore("x.mab", bytes, snapshot.contentSha256, ContinuitySnapshot.CURRENT_FORMAT_VERSION)

        assertTrue(result is RestoreResult.VerifyMismatch)
        assertEquals(RestoreStage.DATASTORES_MERGED, stateStore.stage)
        assertTrue("VERIFIED must never be persisted on a mismatch", stateStore.stage != RestoreStage.VERIFIED)
        assertTrue("the staged file must be kept, not deleted, on a mismatch", stagedFile.bytes != null)
        assertEquals("the merged Room data must NOT be rolled back", 1, localStore.entries.size)
        assertEquals("the merged DataStore data must NOT be rolled back", 1, localStore.notes.size)
        assertEquals(1, verifyFailedCalls.size)

        // Calling resume() again must not wipe or re-merge from scratch —
        // it just retries the same final check (which fails again, honestly,
        // since the underlying condition this test simulates hasn't changed).
        val secondResult = coordinator(
            stateStore, stagedFile, localStore, key,
            verifyFailedCalls = verifyFailedCalls, recaptureOverride = recaptureOverride,
        ).resume()
        assertTrue(secondResult is RestoreResult.VerifyMismatch)
        assertEquals("the merge phases were not re-run a second time", 1, localStore.roomMergeCalls)
        assertEquals(1, localStore.dataStoreMergeCalls)
    }

    // --- Corrupted local state (staged file missing while stage says otherwise) ---

    @Test
    fun `a missing staged file while the stage says otherwise resets to NONE without crashing`() = runBlocking {
        val stateStore = FakeStateStore().apply {
            stage = RestoreStage.DOWNLOADED
            remoteName = "x.mab"
            envelopeSha256 = "sha"
            expectedContentHash = "hash"
        }
        val stagedFile = FakeStagedFile() // bytes == null: missing
        val localStore = FakeLocalStore()

        val result = coordinator(stateStore, stagedFile, localStore, sampleKey()).resume()

        assertEquals(RestoreResult.LocalStateReset, result)
        assertEquals(RestoreStage.NONE, stateStore.stage)
        assertFalse(localStore.entries.isNotEmpty())
    }

    // --- Restoring a Program 0 checkpoint onto a Program 1 build ----------

    /** A snapshot stamped and hashed exactly as a Program 0 build would have written it. */
    private fun programZeroSnapshot(payload: ContinuityPayload = samplePayload()): ContinuitySnapshot {
        val sorted = ContinuityContentHasher.sorted(payload)
        return ContinuitySnapshot(
            formatVersion = ContinuityContract.PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION,
            snapshotId = "snap-program-zero",
            createdAt = 5_000L,
            appVersionCode = 1,
            appVersionName = "test",
            sourceDeviceId = "device-a",
            payload = sorted,
            contentSha256 = ContinuityContentHasher.hash(
                sorted,
                ContinuityContract.PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION,
            ),
        )
    }

    @Test
    fun `a Program 0 checkpoint still verifies on a build whose payload has grown`() = runBlocking {
        val key = sampleKey()
        val snapshot = programZeroSnapshot()
        val stateStore = FakeStateStore()
        val stagedFile = FakeStagedFile()
        val localStore = FakeLocalStore()

        val result = coordinator(stateStore, stagedFile, localStore, key).beginRestore(
            "MindAnchor-Continuity-Latest.mab",
            envelopeBytes(snapshot, key),
            snapshot.contentSha256,
            snapshot.formatVersion,
        )

        // The recaptured payload is version-2 shaped -- it carries the two
        // research lists, empty -- so verifying it against the snapshot's
        // own version-1 hash is the only thing that can succeed here.
        assertTrue("a Program 0 backup must still restore: $result", result is RestoreResult.Verified)
        assertEquals(RestoreStage.VERIFIED, stateStore.stage)
        assertEquals(
            "verifying with the current version would have failed",
            ContinuityContentHasher.hash(
                localStore.snapshotPayload(),
                ContinuityContract.PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION,
            ),
            (result as RestoreResult.Verified).contentHash,
        )
        assertNotEquals(
            ContinuityContentHasher.hash(localStore.snapshotPayload()),
            result.contentHash,
        )
    }

    @Test
    fun `a resume past the merges uses the persisted format version`() = runBlocking {
        val key = sampleKey()
        val snapshot = programZeroSnapshot()
        val stagedFile = FakeStagedFile().apply { write(envelopeBytes(snapshot, key)) }
        val localStore = FakeLocalStore().apply {
            mergeRoom(snapshot.payload)
            mergeDataStores(snapshot.payload)
        }

        val correct = FakeStateStore().apply {
            stage = RestoreStage.DATASTORES_MERGED
            expectedContentHash = snapshot.contentSha256
            expectedFormatVersion = ContinuityContract.PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION
        }
        assertTrue(coordinator(correct, stagedFile, localStore, key).resume() is RestoreResult.Verified)

        // The same staged restore, resumed as if it were a current-version
        // snapshot, must not quietly claim success.
        val mislabelled = FakeStateStore().apply {
            stage = RestoreStage.DATASTORES_MERGED
            expectedContentHash = snapshot.contentSha256
            expectedFormatVersion = ContinuityContract.SNAPSHOT_FORMAT_VERSION
        }
        assertTrue(
            coordinator(mislabelled, FakeStagedFile().apply { write(envelopeBytes(snapshot, key)) }, localStore, key)
                .resume() is RestoreResult.VerifyMismatch,
        )
    }

    @Test
    fun `a restore staged before the version was recorded is treated as Program 0`() = runBlocking {
        val key = sampleKey()
        val snapshot = programZeroSnapshot()
        val stagedFile = FakeStagedFile().apply { write(envelopeBytes(snapshot, key)) }
        val localStore = FakeLocalStore().apply {
            mergeRoom(snapshot.payload)
            mergeDataStores(snapshot.payload)
        }
        // expectedFormatVersion deliberately left null: a restore staged by
        // a build that predates the field can only be a Program 0 snapshot,
        // because the constant was 1 until the payload actually grew.
        val stateStore = FakeStateStore().apply {
            stage = RestoreStage.DATASTORES_MERGED
            expectedContentHash = snapshot.contentSha256
        }

        assertTrue(coordinator(stateStore, stagedFile, localStore, key).resume() is RestoreResult.Verified)
    }

    @Test
    fun `passive DTO mappings preserve every long-term field without a raw sample`() {
        val payload = ProgramTwoPayloadFixture.payload()
        assertEquals(payload.passiveRawProvenance, payload.passiveRawProvenance.map { it.toEntity().toDto() })
        assertEquals(payload.passiveSourceReads, payload.passiveSourceReads.map { it.toEntity().toDto() })
        assertEquals(payload.passiveSourceLags, payload.passiveSourceLags.map { it.toEntity().toDto() })
        assertEquals(payload.passiveBaselineSegments, payload.passiveBaselineSegments.map { it.toEntity().toDto() })
        assertEquals(payload.passivePipelineRuns, payload.passivePipelineRuns.map { it.toEntity().toDto() })
        assertEquals(payload.passiveWindowRevisions, payload.passiveWindowRevisions.map { it.toEntity().toDto() })
        assertEquals(payload.passiveDailyRevisions, payload.passiveDailyRevisions.map { it.toEntity().toDto() })
        assertEquals(
            payload.passiveObservationDecisions,
            payload.passiveObservationDecisions.map { it.toEntity().toDto() },
        )
    }

    @Test
    fun `large restore membership builds one stored id set and reports genuinely missing ids`() {
        val count = 50_000
        val ids = List(count) { "passive-row-$it" }
        val stored = object : AbstractCollection<String>() {
            override val size: Int = ids.size
            override fun iterator(): Iterator<String> = ids.iterator()
            override fun contains(element: String): Boolean = error("linear Collection.contains must not be used")
        }

        assertTrue(missingRestoredIds(stored, ids).isEmpty())
        assertEquals(listOf("passive-row-missing"), missingRestoredIds(stored, ids + "passive-row-missing"))
    }

    @Test
    fun `production restore preflight and merge cover all long-term passive tables but no raw values`() {
        val source = File("src/main/java/org/mindanchor/continuity/RestoreCoordinator.kt").readText()
        listOf(
            "rawProvenanceNow",
            "sourceReadsNow",
            "sourceLagsNow",
            "baselineSegmentsNow",
            "pipelineRunsNow",
            "windowRevisionsNow",
            "dailyRevisionsNow",
            "observationDecisionsNow",
        ).forEach { query ->
            assertTrue("preflight must inspect $query", source.contains("passive.$query().isEmpty()"))
        }
        val mergeRoomSource = source.substringAfter("mergeRoom =").substringBefore("mergeDataStores =")
        assertTrue(mergeRoomSource.contains("database.withTransaction"))
        assertTrue(mergeRoomSource.contains("mergePassiveRows(database, payload)"))
        assertFalse(source.contains("insertRawSamples"))
        assertFalse(source.contains("rawRecords("))
    }
}
