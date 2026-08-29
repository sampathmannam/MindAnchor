package org.mindanchor.continuity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Program 1 Task 1 — the continuity content hash becomes version-aware, and
 * the version Program 0 shipped is frozen at the value Program 0's own code
 * produced before any Program 1 field was appended.
 */
class ContinuityHashVersionTest {

    @Test
    fun `the program zero hash is frozen`() {
        assertEquals(
            "0425b07482520c0e3841b45b6f576540ba57d012d4023c5c5ebfd9395aac9b7c",
            ContinuityContentHasher.hash(
                ProgramZeroPayloadFixture.payload(),
                formatVersion = ContinuityContract.PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION,
            ),
        )
    }

    @Test
    fun `the default hash is the current format version`() {
        val payload = ProgramZeroPayloadFixture.payload()
        assertEquals(
            ContinuityContentHasher.hash(payload, ContinuityContract.SNAPSHOT_FORMAT_VERSION),
            ContinuityContentHasher.hash(payload),
        )
    }

    @Test
    fun `an unsupported format version is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ContinuityContentHasher.hash(ProgramZeroPayloadFixture.payload(), formatVersion = 99)
        }
    }

    @Test
    fun `program zero remains a supported snapshot format version`() {
        assertEquals(
            setOf(ContinuityContract.PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION, ContinuityContract.SNAPSHOT_FORMAT_VERSION),
            ContinuityContract.SUPPORTED_SNAPSHOT_FORMAT_VERSIONS,
        )
    }
}
