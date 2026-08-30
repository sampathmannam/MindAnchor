package org.mindanchor.continuity

import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.backup.RemoteBackupStore
import org.mindanchor.backup.RemoteObject
import org.mindanchor.backup.RemoteResult
import org.mindanchor.continuity.crypto.BackupEnvelopeCodec
import org.mindanchor.continuity.crypto.RecoveryKey
import org.mindanchor.continuity.crypto.RecoveryKeyCodec

/**
 * The Task 10 verify-then-acknowledge algorithm, exercised as plain JVM
 * logic — every collaborator is a fake or a lambda, no Room, no Context,
 * no Robolectric. Confirms the ordering the plan requires: upload success
 * alone is never enough — a byte-exact download and a decrypted
 * content-hash match must BOTH pass before [Recorder.acknowledged] or
 * [Recorder.verifiedCall] ever receive anything, and the previous
 * verified state is left untouched on any failure.
 */
class ContinuityBackupCoordinatorTest {

    private fun sampleKey(seed: Int = 1): RecoveryKey =
        RecoveryKeyCodec.generate { ByteArray(32) { i -> ((seed + i) and 0xFF).toByte() } }

    /**
     * [contentHash] defaults to the payload's real digest, because the
     * coordinator now re-derives it: a snapshot whose stamped hash does not
     * match its own payload is exactly the mis-stamped file the verify step
     * exists to catch, and a fixture carrying a made-up string would be one.
     * Tests that want a *different* snapshot vary the payload, not the hash.
     */
    private fun sampleSnapshot(
        id: String = "snap-1",
        payload: ContinuityPayload = ContinuityPayload(),
        contentHash: String = ContinuityContentHasher.hash(payload, ContinuitySnapshot.CURRENT_FORMAT_VERSION),
    ): ContinuitySnapshot =
        ContinuitySnapshot(
            formatVersion = ContinuitySnapshot.CURRENT_FORMAT_VERSION,
            snapshotId = id,
            createdAt = 5_000L,
            appVersionCode = 1,
            appVersionName = "test",
            sourceDeviceId = "device-a",
            payload = payload,
            contentSha256 = contentHash,
        )

    /** A [RemoteBackupStore] fake: stores put() bytes verbatim; scriptable per-call overrides. */
    private class FakeRemoteBackupStore(
        private val putOverride: RemoteResult<RemoteObject>? = null,
        private val getOverride: RemoteResult<ByteArray?>? = null,
        // Per-name get() override, checked after [getOverride]. Lets a test
        // script one file's get() (e.g. a corrupted LATEST refresh) while
        // another name (e.g. the already-verified versioned upload) still
        // echoes back whatever was put().
        private val getOverrideForName: Map<String, RemoteResult<ByteArray?>> = emptyMap(),
    ) : RemoteBackupStore {
        val stored = mutableMapOf<String, ByteArray>()
        var putCalls = 0
        var getCalls = 0

        override suspend fun put(name: String, bytes: ByteArray): RemoteResult<RemoteObject> {
            putCalls++
            putOverride?.let { return it }
            stored[name] = bytes
            return RemoteResult.Ok(RemoteObject(id = "id-$name", name = name, size = bytes.size.toLong(), modifiedTime = Instant.EPOCH))
        }

        override suspend fun get(name: String): RemoteResult<ByteArray?> {
            getCalls++
            getOverride?.let { return it }
            getOverrideForName[name]?.let { return it }
            return RemoteResult.Ok(stored[name])
        }

        override suspend fun list(prefix: String): RemoteResult<List<RemoteObject>> = RemoteResult.Ok(emptyList())
    }

    /** Tracks every [acknowledgePending] / [recordError] / [recordVerified] call the coordinator makes. */
    private class Recorder {
        val acknowledged = mutableListOf<Pair<String, List<String>>>()
        val errorsRecorded = mutableListOf<ContinuityErrorCode>()
        var verifiedCall: Triple<Long, String, String>? = null
    }

    private fun coordinator(
        recorder: Recorder,
        remoteBackupStore: RemoteBackupStore,
        backupEnabled: Boolean = true,
        key: RecoveryKey? = sampleKey(),
        snapshot: ContinuitySnapshot = sampleSnapshot(),
    ): ContinuityBackupCoordinator = ContinuityBackupCoordinator(
        isBackupEnabled = { backupEnabled },
        currentVerifiedKey = { key },
        remoteBackupStore = remoteBackupStore,
        captureSnapshot = { snapshot },
        acknowledgePending = { snapshotId, changeIds -> recorder.acknowledged += snapshotId to changeIds },
        recordError = { code -> recorder.errorsRecorded += code },
        recordVerified = { at, id, hash -> recorder.verifiedCall = Triple(at, id, hash) },
        now = { 5_000L },
    )

    @Test
    fun `backup disabled exits before any remote call and acknowledges nothing`() = runBlocking {
        val store = FakeRemoteBackupStore()
        val recorder = Recorder()
        val result = coordinator(recorder, store, backupEnabled = false).runCheckpoint()

        assertEquals(CheckpointResult.BackupDisabled, result)
        assertEquals(0, store.putCalls)
        assertEquals(0, store.getCalls)
        assertTrue(recorder.acknowledged.isEmpty())
        assertNull(recorder.verifiedCall)
    }

    @Test
    fun `missing recovery key records KEY_MISSING and makes no remote call`() = runBlocking {
        val store = FakeRemoteBackupStore()
        val recorder = Recorder()
        val result = coordinator(recorder, store, key = null).runCheckpoint()

        assertEquals(CheckpointResult.KeyMissing, result)
        assertEquals(listOf(ContinuityErrorCode.KEY_MISSING), recorder.errorsRecorded)
        assertEquals(0, store.putCalls)
        assertTrue(recorder.acknowledged.isEmpty())
        assertNull(recorder.verifiedCall)
    }

    @Test
    fun `upload auth-expired records AUTH and acknowledges nothing`() = runBlocking {
        val store = FakeRemoteBackupStore(putOverride = RemoteResult.AuthExpired)
        val recorder = Recorder()
        val result = coordinator(recorder, store).runCheckpoint()

        assertEquals(CheckpointResult.AuthExpired, result)
        assertEquals(listOf(ContinuityErrorCode.AUTH), recorder.errorsRecorded)
        assertTrue(recorder.acknowledged.isEmpty())
        assertNull(recorder.verifiedCall)
    }

    @Test
    fun `upload retryable records NETWORK and yields a Retryable result`() = runBlocking {
        val store = FakeRemoteBackupStore(putOverride = RemoteResult.Retryable("network_error"))
        val recorder = Recorder()
        val result = coordinator(recorder, store).runCheckpoint()

        assertEquals(CheckpointResult.Retryable(ContinuityErrorCode.NETWORK), result)
        assertEquals(listOf(ContinuityErrorCode.NETWORK), recorder.errorsRecorded)
        assertTrue(recorder.acknowledged.isEmpty())
    }

    @Test
    fun `upload permanent failure does not acknowledge or retry-loop`() = runBlocking {
        val store = FakeRemoteBackupStore(putOverride = RemoteResult.Permanent("http_400"))
        val recorder = Recorder()
        val result = coordinator(recorder, store).runCheckpoint()

        assertEquals(CheckpointResult.PermanentFailure(ContinuityErrorCode.NETWORK), result)
        assertTrue(recorder.acknowledged.isEmpty())
        assertNull(recorder.verifiedCall)
    }

    @Test
    fun `download auth-expired after a successful upload still records AUTH and acknowledges nothing`() = runBlocking {
        // put() succeeds (default), get() reports AuthExpired.
        val store = FakeRemoteBackupStore(getOverride = RemoteResult.AuthExpired)
        val recorder = Recorder()
        val result = coordinator(recorder, store).runCheckpoint()

        assertEquals(1, store.putCalls)
        assertEquals(1, store.getCalls)
        assertEquals(CheckpointResult.AuthExpired, result)
        assertTrue(recorder.acknowledged.isEmpty())
        assertNull(recorder.verifiedCall)
    }

    @Test
    fun `download returning null bytes is VERIFY_FAILED, not acknowledged`() = runBlocking {
        val store = FakeRemoteBackupStore(getOverride = RemoteResult.Ok(null))
        val recorder = Recorder()
        val result = coordinator(recorder, store).runCheckpoint()

        assertEquals(CheckpointResult.VerificationFailed(ContinuityErrorCode.VERIFY_FAILED), result)
        assertEquals(listOf(ContinuityErrorCode.VERIFY_FAILED), recorder.errorsRecorded)
        assertTrue("a byte mismatch must never acknowledge pending changes", recorder.acknowledged.isEmpty())
        assertNull("a byte mismatch must never update the verified checkpoint fields", recorder.verifiedCall)
    }

    @Test
    fun `downloaded bytes that do not match the uploaded bytes are VERIFY_FAILED`() = runBlocking {
        val store = FakeRemoteBackupStore(getOverride = RemoteResult.Ok("corrupted-not-the-same-bytes".encodeToByteArray()))
        val recorder = Recorder()
        val result = coordinator(recorder, store).runCheckpoint()

        assertEquals(CheckpointResult.VerificationFailed(ContinuityErrorCode.VERIFY_FAILED), result)
        assertTrue(recorder.acknowledged.isEmpty())
        assertNull(recorder.verifiedCall)
    }

    @Test
    fun `a store that returns bytes not byte-identical to what was uploaded is caught before decrypt is even tried`() = runBlocking {
        // The coordinator compares the downloaded bytes against the exact
        // bytes it just uploaded (step 7) BEFORE attempting decode/decrypt
        // (step 8). A store that returns anything other than an exact echo
        // — corrupted, truncated, or (as here) a *different but otherwise
        // well-formed* envelope entirely — is rejected at the byte-compare
        // stage. This is what makes the DECODE_FAILED branch defensive
        // dead code on the checkpoint path: byte-identical output from an
        // honest encoder always decodes, so decode failure can only ever
        // follow a byte mismatch, which is already caught first.
        val foreignEnvelopeBytes = BackupEnvelopeCodec.encode(
            BackupEnvelopeCodec.encrypt("{\"not\":\"the same snapshot\"}", sampleKey(seed = 99), 1L),
        ).encodeToByteArray()
        val store = FakeRemoteBackupStore(getOverride = RemoteResult.Ok(foreignEnvelopeBytes))
        val recorder = Recorder()

        val result = coordinator(recorder, store).runCheckpoint()

        assertEquals(CheckpointResult.VerificationFailed(ContinuityErrorCode.VERIFY_FAILED), result)
        assertTrue(recorder.acknowledged.isEmpty())
        assertNull(recorder.verifiedCall)
    }

    @Test
    fun `full success uploads, verifies, acknowledges, and records verified state exactly once`() = runBlocking {
        val store = FakeRemoteBackupStore()
        val recorder = Recorder()
        val snapshot = sampleSnapshot(
            id = "snap-42",
            payload = ContinuityPayload(
                continuityChanges = listOf(
                    ContinuityChangeDto(
                        id = "captured-change",
                        entityType = "journal_entry",
                        entityId = "entry-1",
                        operation = "CREATE",
                        occurredAt = 1_000L,
                        acknowledgedSnapshotId = null,
                    ),
                ),
            ),
        )

        val result = coordinator(recorder, store, snapshot = snapshot).runCheckpoint()

        assertTrue(result is CheckpointResult.Verified)
        val verified = result as CheckpointResult.Verified
        assertEquals("snap-42", verified.snapshotId)
        assertEquals(snapshot.contentSha256, verified.contentSha256)
        assertEquals(1, store.putCalls)
        assertEquals(1, store.getCalls)
        assertEquals(listOf("snap-42" to listOf("captured-change")), recorder.acknowledged)
        assertEquals(Triple(5_000L, "snap-42", snapshot.contentSha256), recorder.verifiedCall)
        assertTrue("no error should be recorded on the happy path", recorder.errorsRecorded.isEmpty())
    }

    @Test
    fun `runCheckpoint uploads to and verifies against a custom targetFileName, not LATEST`() = runBlocking {
        // Finding 1's fix: NightlySnapshotWorker points runCheckpoint at a
        // versioned name instead of LATEST, so the SAME upload-download-
        // byte-compare-decrypt-hash sequence verifies the nightly file too.
        val store = FakeRemoteBackupStore()
        val recorder = Recorder()
        val snapshot = sampleSnapshot(id = "snap-nightly")

        val result = coordinator(recorder, store, snapshot = snapshot)
            .runCheckpoint(targetFileName = { "custom-versioned-name.mab" })

        assertTrue(result is CheckpointResult.Verified)
        assertEquals(1, store.putCalls)
        assertEquals(1, store.getCalls)
        assertTrue("the custom name must be the one actually written to", store.stored.containsKey("custom-versioned-name.mab"))
        assertTrue("LATEST must not be touched by runCheckpoint itself", !store.stored.containsKey(ContinuityFiles.LATEST))
        assertEquals(listOf("snap-nightly" to emptyList<String>()), recorder.acknowledged)
    }

    @Test
    fun `a versioned upload whose downloaded bytes do not match is VERIFY_FAILED and never acknowledged`() = runBlocking {
        // Mirrors the LATEST-path byte-mismatch test above, but for the
        // versioned-file target NightlySnapshotWorker uses: a corrupted
        // round-trip on the versioned name must fail exactly the same way
        // — nothing acknowledged, no verified state recorded, so
        // NightlySnapshotWorker.doWork() bails out via `toWorkResult()`
        // before ever touching LATEST.
        val store = FakeRemoteBackupStore(getOverride = RemoteResult.Ok("corrupted-not-the-same-bytes".encodeToByteArray()))
        val recorder = Recorder()

        val result = coordinator(recorder, store)
            .runCheckpoint(targetFileName = { "custom-versioned-name.mab" })

        assertEquals(CheckpointResult.VerificationFailed(ContinuityErrorCode.VERIFY_FAILED), result)
        assertTrue(recorder.acknowledged.isEmpty())
        assertNull(recorder.verifiedCall)
    }

    @Test
    fun `a save that never runs the worker leaves the change unacknowledged (Task 3-2 guarantee holds)`() = runBlocking {
        // Re-confirms Task 3/2's guarantee: an offline (or never-executed)
        // checkpoint attempt must never acknowledge a pending change. This
        // coordinator-level fake stands in for "CheckpointBackupWorker
        // cannot run" (e.g. no CONNECTED network) — the acknowledge
        // callback here is exactly org.mindanchor.data.db.JournalDao.acknowledgePending,
        // wired by CheckpointBackupWorker in production; it is never
        // invoked unless steps 6-9 (download, byte-compare, decrypt,
        // hash-compare) all pass.
        val store = FakeRemoteBackupStore(putOverride = RemoteResult.Retryable("network_error"))
        val recorder = Recorder()

        coordinator(recorder, store).runCheckpoint()

        assertTrue(
            "a pending continuity_changes row must stay unacknowledged when the checkpoint cannot complete",
            recorder.acknowledged.isEmpty(),
        )
    }

    @Test
    fun `putAndVerifyBytes succeeds when the downloaded bytes match exactly and records no error`() = runBlocking {
        val store = FakeRemoteBackupStore()
        val recorder = Recorder()
        val bytes = "already-verified-envelope-bytes".encodeToByteArray()

        val result = coordinator(recorder, store).putAndVerifyBytes(ContinuityFiles.LATEST, bytes)

        assertEquals(PutAndVerifyResult.Verified, result)
        assertTrue(recorder.errorsRecorded.isEmpty())
        assertTrue("putAndVerifyBytes never itself records a verified state", recorder.acknowledged.isEmpty())
        assertNull(recorder.verifiedCall)
    }

    @Test
    fun `putAndVerifyBytes on a corrupted round-trip records VERIFY_FAILED, not Verified`() = runBlocking {
        val store = FakeRemoteBackupStore(getOverride = RemoteResult.Ok("corrupted-refresh-bytes".encodeToByteArray()))
        val recorder = Recorder()
        val bytes = "already-verified-envelope-bytes".encodeToByteArray()

        val result = coordinator(recorder, store).putAndVerifyBytes(ContinuityFiles.LATEST, bytes)

        assertEquals(PutAndVerifyResult.VerificationFailed(ContinuityErrorCode.VERIFY_FAILED), result)
        assertEquals(listOf(ContinuityErrorCode.VERIFY_FAILED), recorder.errorsRecorded)
    }

    @Test
    fun `a versioned nightly checkpoint that verifies, followed by a LATEST refresh whose round-trip is corrupted, does not report a clean success for the refresh`() = runBlocking {
        // Reproduces the post-review finding: NightlySnapshotWorker runs
        // runCheckpoint() against the versioned name (verifies cleanly),
        // then calls putAndVerifyBytes() to refresh LATEST with those same
        // already-verified bytes. A fault that corrupts ONLY the LATEST
        // put/get (a genuinely separate network operation from the
        // versioned upload) must surface as an honest VerificationFailed
        // for that refresh, not be silently folded into the versioned
        // checkpoint's success.
        val store = FakeRemoteBackupStore(
            getOverrideForName = mapOf(ContinuityFiles.LATEST to RemoteResult.Ok("corrupted-latest-refresh".encodeToByteArray())),
        )
        val recorder = Recorder()
        val snapshot = sampleSnapshot(id = "snap-nightly")
        val coordinator = coordinator(recorder, store, snapshot = snapshot)

        val versionedResult = coordinator.runCheckpoint(targetFileName = { "custom-versioned-name.mab" })
        assertTrue("the versioned upload itself must verify cleanly", versionedResult is CheckpointResult.Verified)
        val verified = versionedResult as CheckpointResult.Verified
        val versionedVerifiedCall = recorder.verifiedCall
        assertEquals(Triple(5_000L, "snap-nightly", snapshot.contentSha256), versionedVerifiedCall)

        val latestRefreshResult = coordinator.putAndVerifyBytes(ContinuityFiles.LATEST, verified.envelopeBytes)

        assertEquals(
            "a corrupted LATEST refresh must surface as VerificationFailed, not as a silent success",
            PutAndVerifyResult.VerificationFailed(ContinuityErrorCode.VERIFY_FAILED),
            latestRefreshResult,
        )
        assertEquals(
            "the versioned checkpoint's own verified-state recording must be untouched by the later LATEST failure",
            versionedVerifiedCall,
            recorder.verifiedCall,
        )
        assertEquals(
            "the failed LATEST refresh must leave an honest VERIFY_FAILED error signal " +
                "(the versioned run itself recorded no error, since it verified cleanly)",
            listOf(ContinuityErrorCode.VERIFY_FAILED),
            recorder.errorsRecorded,
        )
    }
}
