package org.mindanchor.continuity

import org.junit.Assert.assertEquals
import org.junit.Test

class ContinuityContractTest {
    @Test
    fun `program zero wire constants stay stable`() {
        assertEquals(1, ContinuityContract.SNAPSHOT_FORMAT_VERSION)
        assertEquals(1, ContinuityContract.ENVELOPE_FORMAT_VERSION)
        assertEquals("MindAnchor-Continuity-Latest.mab", ContinuityContract.LATEST_FILE_NAME)
        assertEquals("mindanchor-research-v1", ContinuityContract.RESEARCH_DICTIONARY_VERSION)
    }
}
