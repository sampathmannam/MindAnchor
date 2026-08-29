package org.mindanchor.continuity

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Program 1 Task 1 — the continuity content hash takes a format version,
 * and the version Program 0 shipped is frozen twice over: by the digest of
 * a fully populated fixture, and structurally by the field names and order
 * the version-1 projection serialises.
 *
 * The structural assertions matter more than the digest. The digest was
 * produced by running this code, so on its own it would freeze whatever
 * the projection happens to do — two transposed fields would simply have
 * produced a different constant and both tests would still pass, while
 * every Program 0 backup silently failed to verify. Asserting the element
 * names against a literal list is what makes the freeze independent of the
 * value it pins.
 */
@OptIn(ExperimentalSerializationApi::class)
class ContinuityHashVersionTest {

    /** Program 0's payload keys, in Program 0's order. This list is history; it does not change. */
    private val programZeroFields = listOf(
        "journalEntries",
        "contextRows",
        "morningMeasures",
        "notes",
        "letters",
        "readLetterDates",
        "frictionedApps",
        "alwaysOpenApps",
        "continuityChanges",
        "legacyBackupJson",
    )

    @Test
    fun `the program zero hash is frozen`() {
        assertEquals(
            "a9da88f8d627c266e994b3292002c84594d119ac552c76a10745e458eaf3f9af",
            ContinuityContentHasher.hash(
                ProgramZeroPayloadFixture.payload(),
                formatVersion = ContinuityContract.PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION,
            ),
        )
    }

    @Test
    fun `the version one projection serialises exactly program zero's fields`() {
        assertEquals(
            programZeroFields,
            serializer<ContinuityPayloadV1>().descriptor.elementNames.toList(),
        )
    }

    @Test
    fun `the live payload still begins with program zero's fields`() {
        assertEquals(
            programZeroFields,
            serializer<ContinuityPayload>().descriptor.elementNames.toList().take(programZeroFields.size),
        )
    }

    @Test
    fun `an unsupported format version is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ContinuityContentHasher.hash(ProgramZeroPayloadFixture.payload(), formatVersion = 99)
        }
    }

    @Test
    fun `the freeze covers the canonical sort order`() {
        val payload = ProgramZeroPayloadFixture.payload()
        val shuffled = payload.copy(
            journalEntries = payload.journalEntries.reversed(),
            contextRows = payload.contextRows.reversed(),
            morningMeasures = payload.morningMeasures.reversed(),
            notes = payload.notes.reversed(),
            letters = payload.letters.reversed(),
            readLetterDates = payload.readLetterDates.reversed(),
            frictionedApps = payload.frictionedApps.reversed(),
            alwaysOpenApps = payload.alwaysOpenApps.reversed(),
            continuityChanges = payload.continuityChanges.reversed(),
        )
        assertEquals(
            ContinuityContentHasher.hash(payload, ContinuityContract.PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION),
            ContinuityContentHasher.hash(shuffled, ContinuityContract.PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION),
        )
        assertNotEquals(
            "the fixture must actually exercise the sort keys",
            payload.journalEntries,
            payload.journalEntries.reversed(),
        )
    }
}
