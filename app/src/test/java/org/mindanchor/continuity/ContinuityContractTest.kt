package org.mindanchor.continuity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuityContractTest {

    @Test
    fun `current wire constants stay stable`() {
        assertEquals(4, ContinuityContract.SNAPSHOT_FORMAT_VERSION)
        assertEquals(1, ContinuityContract.ENVELOPE_FORMAT_VERSION)
        assertEquals("MindAnchor-Continuity-Latest.mab", ContinuityContract.LATEST_FILE_NAME)
        assertEquals("mindanchor-research-v3", ContinuityContract.RESEARCH_DICTIONARY_VERSION)
    }

    @Test
    fun `program zero wire constants stay readable`() {
        assertEquals(1, ContinuityContract.PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION)
        assertEquals(2, ContinuityContract.PROGRAM_ONE_SNAPSHOT_FORMAT_VERSION)
        assertEquals("mindanchor-research-v1", ContinuityContract.PROGRAM_ZERO_RESEARCH_DICTIONARY_VERSION)
        assertEquals("mindanchor-research-v2", ContinuityContract.PROGRAM_ONE_RESEARCH_DICTIONARY_VERSION)
        assertEquals(setOf(1, 2, 3, 4), ContinuityContract.SUPPORTED_SNAPSHOT_FORMAT_VERSIONS)
        assertEquals(
            setOf("mindanchor-research-v1", "mindanchor-research-v2", "mindanchor-research-v3"),
            ContinuityContract.SUPPORTED_RESEARCH_DICTIONARY_VERSIONS,
        )
    }

    @Test
    fun `snapshot versions one through four remain supported`() {
        assertEquals(3, ContinuityContract.PROGRAM_TWO_SNAPSHOT_FORMAT_VERSION)
        assertEquals(4, ContinuityContract.SNAPSHOT_FORMAT_VERSION)
        assertEquals(setOf(1, 2, 3, 4), ContinuityContract.SUPPORTED_SNAPSHOT_FORMAT_VERSIONS)
    }

    @Test
    fun `program zero stays a supported snapshot format version`() {
        assertTrue(
            ContinuityContract.PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION
                in ContinuityContract.SUPPORTED_SNAPSHOT_FORMAT_VERSIONS,
        )
    }
}
