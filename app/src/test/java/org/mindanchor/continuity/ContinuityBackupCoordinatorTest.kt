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

    private fun sampleSnapshot(id: String = "snap-1", contentHash: String = "hash-$id"): ContinuitySnapshot =
        ContinuitySnapshot(
            formatVersion = ContinuitySnapshot.CURRENT_FORMAT_VERSION,
            snapshotId = id,
            createdAt = 5_000L,
            appVersionCode = 1,
            appVersionName = "test",
            sourceDeviceId = "device-a",
            payload = ContinuityPayload(),
            contentSha256 = contentHash,
        )

    /** A [RemoteBackupStore] fake: stores put() bytes verbatim; scriptable per-call overrides. */
    private class FakeRemoteBackupStore(
        private val putOverride: RemoteResult<RemoteObject>? = null,
        private val getOverride: RemoteResult<ByteArray?>? = null,
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
            return RemoteResult.Ok(stored[name])
        }

        override suspend fun list(prefix: String): RemoteResult<List<RemoteObject>> = RemoteResult.Ok(emptyList())
    }

    /** Tracks every [acknowledgePending] / [recordError] / [recordVerified] call the coordinator makes. */
    private class Recorder {
        val acknowledged = mutableListOf<String>()
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
        acknowledgePending = { id -> recorder.acknowledged += id },
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
        val snapshot = sampleSnapshot(id = "snap-42", contentHash = "hash-42")

        val result = coordinator(recorder, store, snapshot = snapshot).runCheckpoint()

        assertTrue(result is CheckpointResult.Verified)
        val verified = result as CheckpointResult.Verified
        assertEquals("snap-42", verified.snapshotId)
        assertEquals("hash-42", verified.contentSha256)
        assertEquals(1, store.putCalls)
        assertEquals(1, store.getCalls)
        assertEquals(listOf("snap-42"), recorder.acknowledged)
        assertEquals(Triple(5_000L, "snap-42", "hash-42"), recorder.verifiedCall)
        assertTrue("no error should be recorded on the happy path", recorder.errorsRecorded.isEmpty())
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
}
