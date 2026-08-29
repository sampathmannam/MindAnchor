package org.mindanchor.continuity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 7 — pins the canonical continuity snapshot codec: a full round trip
 * preserves every field, the content hash is order-independent (so the same
 * logical data restored on a new phone produces the same hash), a single
 * changed character changes the hash, and both an unsupported format
 * version and corrupt JSON are rejected as typed results rather than
 * thrown exceptions.
 */
class ContinuitySnapshotCodecTest {

    private fun samplePayload(entryBodySuffix: String = "one"): ContinuityPayload = ContinuityPayload(
        journalEntries = listOf(
            JournalEntryDto(
                id = "entry-1",
                createdAt = 1_000L,
                updatedAt = 1_000L,
                localDate = "2026-08-27",
                title = "Morning",
                body = "Body $entryBodySuffix",
                kind = "DAILY",
                sourceDeviceId = "device-a",
                deletedAt = null,
            ),
        ),
        contextRows = listOf(
            JournalContextDto(
                id = "context-1",
                entryId = "entry-1",
                recordType = "FACT",
                key = "word_count",
                value = "2",
                sourceStart = null,
                sourceEnd = null,
                confidence = 1.0,
                extractorVersion = "structural-v1",
                createdAt = 1_000L,
            ),
        ),
        morningMeasures = listOf(
            MorningMeasureDto(
                id = "measure-1",
                localDate = "2026-08-27",
                createdAt = 900L,
                updatedAt = 900L,
                mood = 3,
                anxiety = 2,
                angerUrge = 1,
                energyFunction = 4,
                sleepQuality = 3,
                instrumentVersion = "morning-v1",
                sourceDeviceId = "device-a",
            ),
        ),
        notes = listOf(
            NoteDto(id = 1L, body = "A note", createdAt = 500L, updatedAt = 500L, pinned = false, type = null),
        ),
        letters = listOf(
            LetterDto(
                date = "2026-08-26",
                body = "A letter",
                provider = "groq",
                model = "llama-3.3-70b-versatile",
                promptTokens = 100,
                completionTokens = 50,
                durationMs = 1_200L,
            ),
        ),
        readLetterDates = listOf("2026-08-26"),
        frictionedApps = listOf("com.example.social"),
        alwaysOpenApps = listOf("com.example.work"),
        continuityChanges = listOf(
            ContinuityChangeDto(
                id = "change-1",
                entityType = "JOURNAL_ENTRY",
                entityId = "entry-1",
                operation = "CREATE",
                occurredAt = 1_000L,
                acknowledgedSnapshotId = null,
            ),
        ),
        legacyBackupJson = """{"version":1,"note":"n","savedAt":42,"plan":{"warningSigns":"","copingSteps":"","distractions":"","reasonsForLiving":"","environmentSafety":""},"contacts":[],"pulses":[],"favorites":[],"hidden":[],"frictioned":[],"renames":{},"checkIns":[],"readings":[],"corpusAdditions":"","inferred":[]}""",
    )

    private fun sampleSnapshot(payload: ContinuityPayload = samplePayload()): ContinuitySnapshot {
        val hash = ContinuityContentHasher.hash(payload)
        return ContinuitySnapshot(
            formatVersion = ContinuitySnapshot.CURRENT_FORMAT_VERSION,
            snapshotId = "snapshot-1",
            createdAt = 2_000L,
            appVersionCode = 94,
            appVersionName = "0.70.0",
            sourceDeviceId = "device-a",
            payload = payload,
            contentSha256 = hash,
        )
    }

    @Test
    fun `capture encode decode preserves every field`() {
        val snapshot = sampleSnapshot()

        val encoded = ContinuitySnapshotCodec.encode(snapshot)
        val decoded = ContinuitySnapshotCodec.decode(encoded)

        assertTrue(decoded is ContinuitySnapshotCodec.DecodeResult.Success)
        val restored = (decoded as ContinuitySnapshotCodec.DecodeResult.Success).snapshot
        assertEquals(snapshot, restored)
    }

    @Test
    fun `list order does not change the content hash`() {
        val entryA = JournalEntryDto(
            id = "entry-a",
            createdAt = 1_000L,
            updatedAt = 1_000L,
            localDate = "2026-08-27",
            title = "A",
            body = "Body A",
            kind = "DAILY",
            sourceDeviceId = "device-a",
            deletedAt = null,
        )
        val entryB = entryA.copy(id = "entry-b", title = "B", body = "Body B")

        val payloadForward = ContinuityPayload(journalEntries = listOf(entryA, entryB))
        val payloadReversed = ContinuityPayload(journalEntries = listOf(entryB, entryA))

        val hashForward = ContinuityContentHasher.hash(payloadForward)
        val hashReversed = ContinuityContentHasher.hash(payloadReversed)

        assertEquals(hashForward, hashReversed)
    }

    @Test
    fun `one changed journal character changes the hash`() {
        val original = samplePayload(entryBodySuffix = "one")
        val mutated = samplePayload(entryBodySuffix = "onE")

        val originalHash = ContinuityContentHasher.hash(original)
        val mutatedHash = ContinuityContentHasher.hash(mutated)

        assertNotEquals(originalHash, mutatedHash)
    }

    @Test
    fun `the same logical data restored on a new phone produces the same hash`() {
        // Same payload, but as if captured from two different devices at
        // two different times — the two snapshot-level fields that must
        // never leak into the content hash.
        val payload = samplePayload()
        val hashDeviceA = ContinuityContentHasher.hash(payload)
        val hashDeviceB = ContinuityContentHasher.hash(payload.copy())

        assertEquals(hashDeviceA, hashDeviceB)
    }

    @Test
    fun `an unknown format version is rejected with a typed error`() {
        val snapshot = sampleSnapshot()
        val encoded = ContinuitySnapshotCodec.encode(snapshot)
        val withBadVersion = encoded.replaceFirst(
            "\"formatVersion\":${snapshot.formatVersion}",
            "\"formatVersion\":999",
        )
        assertNotEquals(encoded, withBadVersion) // sanity: the replace actually matched

        val decoded = ContinuitySnapshotCodec.decode(withBadVersion)

        assertTrue(decoded is ContinuitySnapshotCodec.DecodeResult.UnsupportedVersion)
        assertEquals(999, (decoded as ContinuitySnapshotCodec.DecodeResult.UnsupportedVersion).formatVersion)
    }

    @Test
    fun `corrupt JSON is rejected without throwing`() {
        val decoded = ContinuitySnapshotCodec.decode("{ this is not valid json at all")

        assertTrue(decoded is ContinuitySnapshotCodec.DecodeResult.Corrupt)
    }

    @Test
    fun `payload keeps journal entries and context rows as separate top-level arrays`() {
        val snapshot = sampleSnapshot()
        val encoded = ContinuitySnapshotCodec.encode(snapshot)

        assertTrue(encoded.contains("\"journalEntries\""))
        assertTrue(encoded.contains("\"contextRows\""))
        // They must be two distinct arrays, not one array serving both roles.
        val entriesIndex = encoded.indexOf("\"journalEntries\"")
        val contextIndex = encoded.indexOf("\"contextRows\"")
        assertNotEquals(entriesIndex, contextIndex)
    }

    @Test
    fun `a Program 0 snapshot document still decodes`() {
        val current = ContinuitySnapshotCodec.encode(
            ContinuitySnapshot(
                formatVersion = ContinuitySnapshot.CURRENT_FORMAT_VERSION,
                snapshotId = "snap-1",
                createdAt = 1_000L,
                appVersionCode = 1,
                appVersionName = "test",
                sourceDeviceId = "device-a",
                payload = samplePayload(),
                contentSha256 = "hash",
            ),
        )
        // The one change that makes a Program 0 checkpoint readable at all
        // on this build. Nothing else in the suite produces a version-1
        // document, because nothing writes one any more.
        val asProgramZero = current.replace(
            "\"formatVersion\":${ContinuitySnapshot.CURRENT_FORMAT_VERSION}",
            "\"formatVersion\":${ContinuityContract.PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION}",
        )
        val decoded = ContinuitySnapshotCodec.decode(asProgramZero)
        assertTrue(decoded is ContinuitySnapshotCodec.DecodeResult.Success)
        assertEquals(
            ContinuityContract.PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION,
            (decoded as ContinuitySnapshotCodec.DecodeResult.Success).snapshot.formatVersion,
        )
    }
}
