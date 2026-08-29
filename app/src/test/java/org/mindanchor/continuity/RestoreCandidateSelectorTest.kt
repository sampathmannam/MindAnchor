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
 * Task 14 QA-runbook gap: [RestoreCandidateSelector.select] must fall back
 * from a corrupted [ContinuityFiles.LATEST] to the newest decryptable
 * versioned snapshot, and [RestoreScreen] must be told a fallback happened
 * via [CandidateSelectionResult.Found.usedFallbackFrom] — pure
 * selection/decrypt logic, no Room/DataStore I/O, so a fake
 * [RemoteBackupStore] is sufficient here (mirroring [RestoreCoordinatorTest]'s
 * JVM-fake style) rather than an androidTest.
 */
class RestoreCandidateSelectorTest {

    private val key: RecoveryKey = RecoveryKeyCodec.generate { ByteArray(32) { i -> i.toByte() } }

    private fun samplePayload(entryId: String): ContinuityPayload = ContinuityPayload(
        journalEntries = listOf(
            JournalEntryDto(
                id = entryId, createdAt = 1000, updatedAt = 1000, localDate = "2026-01-01",
                title = "t", body = "b", kind = "DAILY", sourceDeviceId = "device-a", deletedAt = null,
            ),
        ),
        contextRows = emptyList(),
        morningMeasures = emptyList(),
        notes = emptyList(),
        letters = emptyList(),
        readLetterDates = emptyList(),
        frictionedApps = emptyList(),
        alwaysOpenApps = emptyList(),
        continuityChanges = emptyList(),
        legacyBackupJson = "",
    )

    private fun sampleSnapshot(snapshotId: String, createdAt: Long): ContinuitySnapshot {
        val sorted = ContinuityContentHasher.sorted(samplePayload(entryId = snapshotId))
        return ContinuitySnapshot(
            formatVersion = ContinuitySnapshot.CURRENT_FORMAT_VERSION,
            snapshotId = snapshotId,
            createdAt = createdAt,
            appVersionCode = 1,
            appVersionName = "test",
            sourceDeviceId = "device-a",
            payload = sorted,
            contentSha256 = ContinuityContentHasher.hash(sorted),
        )
    }

    private fun envelopeBytes(snapshot: ContinuitySnapshot): ByteArray {
        val json = ContinuitySnapshotCodec.encode(snapshot)
        val envelope = BackupEnvelopeCodec.encrypt(json, key, now = snapshot.createdAt)
        return BackupEnvelopeCodec.encode(envelope).encodeToByteArray()
    }

    /** A fake, in-memory-only remote store — no network call is ever made. Mirrors RestoreScreenTest's own fake. */
    private class FakeRemoteBackupStore(private val objects: Map<String, ByteArray>) : RemoteBackupStore {
        override suspend fun put(name: String, bytes: ByteArray): RemoteResult<RemoteObject> =
            RemoteResult.Ok(RemoteObject(id = name, name = name, size = bytes.size.toLong(), modifiedTime = Instant.EPOCH))
        override suspend fun get(name: String): RemoteResult<ByteArray?> = RemoteResult.Ok(objects[name])
        override suspend fun list(prefix: String): RemoteResult<List<RemoteObject>> =
            RemoteResult.Ok(
                objects.keys.filter { it.contains(prefix) }
                    .map { RemoteObject(id = it, name = it, size = objects.getValue(it).size.toLong(), modifiedTime = Instant.EPOCH) },
            )
    }

    @Test
    fun `a corrupted Latest falls back to the newest decryptable versioned snapshot and reports the fallback`() = runBlocking {
        val olderVersioned = sampleSnapshot("older-snap", createdAt = 1_000L)
        val olderName = ContinuityFiles.versioned(Instant.ofEpochMilli(1_000L), "older-snap")
        val newerVersioned = sampleSnapshot("newer-snap", createdAt = 2_000L)
        val newerName = ContinuityFiles.versioned(Instant.ofEpochMilli(2_000L), "newer-snap")

        val store = FakeRemoteBackupStore(
            mapOf(
                // Corrupted/garbage bytes at the LATEST slot — fails to even decode as an envelope.
                ContinuityFiles.LATEST to "not a valid envelope at all".encodeToByteArray(),
                olderName to envelopeBytes(olderVersioned),
                newerName to envelopeBytes(newerVersioned),
            ),
        )

        val result = RestoreCandidateSelector.select(store, key)

        assertTrue("a decryptable versioned snapshot must still be found despite the corrupted Latest", result is CandidateSelectionResult.Found)
        val found = result as CandidateSelectionResult.Found
        assertEquals("a corrupted Latest must be reported as the fallback source", ContinuityFiles.LATEST, found.usedFallbackFrom)
        assertEquals("the newest decryptable versioned snapshot must be selected, not just any decryptable one", newerName, found.candidate.remoteName)
        assertEquals(newerVersioned.snapshotId, found.candidate.snapshot.snapshotId)
    }

    @Test
    fun `Latest present and decryptable is used directly with no fallback reported`() = runBlocking {
        val latestSnapshot = sampleSnapshot("latest-snap", createdAt = 3_000L)
        val store = FakeRemoteBackupStore(mapOf(ContinuityFiles.LATEST to envelopeBytes(latestSnapshot)))

        val result = RestoreCandidateSelector.select(store, key)

        assertTrue(result is CandidateSelectionResult.Found)
        val found = result as CandidateSelectionResult.Found
        assertNull("a readable Latest must not be reported as a fallback", found.usedFallbackFrom)
        assertEquals(ContinuityFiles.LATEST, found.candidate.remoteName)
    }

    @Test
    fun `Latest missing falls back to the newest decryptable versioned snapshot and reports the fallback`() = runBlocking {
        val olderVersioned = sampleSnapshot("older-snap", createdAt = 1_000L)
        val olderName = ContinuityFiles.versioned(Instant.ofEpochMilli(1_000L), "older-snap")
        val newerVersioned = sampleSnapshot("newer-snap", createdAt = 2_000L)
        val newerName = ContinuityFiles.versioned(Instant.ofEpochMilli(2_000L), "newer-snap")

        // No ContinuityFiles.LATEST entry at all — remoteBackupStore.get(LATEST) resolves to Ok(null).
        val store = FakeRemoteBackupStore(
            mapOf(
                olderName to envelopeBytes(olderVersioned),
                newerName to envelopeBytes(newerVersioned),
            ),
        )

        val result = RestoreCandidateSelector.select(store, key)

        assertTrue("a decryptable versioned snapshot must still be found when Latest is simply missing", result is CandidateSelectionResult.Found)
        val found = result as CandidateSelectionResult.Found
        assertEquals("a missing Latest must be reported as the fallback source", ContinuityFiles.LATEST, found.usedFallbackFrom)
        assertEquals("the newest decryptable versioned snapshot must be selected", newerName, found.candidate.remoteName)
    }

    @Test
    fun `when both Latest and the newest versioned snapshot are corrupt, selection falls back further to an older decryptable snapshot`() = runBlocking {
        val oldestVersioned = sampleSnapshot("oldest-snap", createdAt = 1_000L)
        val oldestName = ContinuityFiles.versioned(Instant.ofEpochMilli(1_000L), "oldest-snap")
        val middleVersioned = sampleSnapshot("middle-snap", createdAt = 2_000L)
        val middleName = ContinuityFiles.versioned(Instant.ofEpochMilli(2_000L), "middle-snap")
        val newestName = ContinuityFiles.versioned(Instant.ofEpochMilli(3_000L), "newest-snap")

        val store = FakeRemoteBackupStore(
            mapOf(
                ContinuityFiles.LATEST to "not a valid envelope at all".encodeToByteArray(),
                newestName to "also not a valid envelope".encodeToByteArray(),
                middleName to envelopeBytes(middleVersioned),
                oldestName to envelopeBytes(oldestVersioned),
            ),
        )

        val result = RestoreCandidateSelector.select(store, key)

        assertTrue("selection must keep trying older candidates until one decrypts", result is CandidateSelectionResult.Found)
        val found = result as CandidateSelectionResult.Found
        assertEquals(ContinuityFiles.LATEST, found.usedFallbackFrom)
        assertEquals(
            "the newest DECRYPTABLE snapshot (middle, not the corrupt newest or the older oldest) must win — proving newest-to-oldest try order",
            middleName,
            found.candidate.remoteName,
        )
    }

    @Test
    fun `a wrong recovery key on Latest is reported immediately without trying any versioned snapshot`() = runBlocking {
        val wrongKey = RecoveryKeyCodec.generate { ByteArray(32) { i -> (i + 1).toByte() } }
        val latestSnapshot = sampleSnapshot("latest-snap", createdAt = 3_000L)
        val versionedSnapshot = sampleSnapshot("versioned-snap", createdAt = 1_000L)
        val versionedName = ContinuityFiles.versioned(Instant.ofEpochMilli(1_000L), "versioned-snap")

        // Both Latest and a versioned snapshot are genuinely decryptable — but only with `key`
        // (envelopeBytes always encrypts with this test class's own `key`).
        // If the selector wrongly fell back to the versioned snapshot on a wrong-key failure, it
        // would fail identically there too, but this test proves it never even tries: the result
        // must be WrongRecoveryKey, not NoneAvailable.
        val store = FakeRemoteBackupStore(
            mapOf(
                ContinuityFiles.LATEST to envelopeBytes(latestSnapshot),
                versionedName to envelopeBytes(versionedSnapshot),
            ),
        )

        val result = RestoreCandidateSelector.select(store, wrongKey)

        assertEquals(
            "a wrong key must stop selection immediately, distinct from NoneAvailable/corruption",
            CandidateSelectionResult.WrongRecoveryKey,
            result,
        )
    }
}
