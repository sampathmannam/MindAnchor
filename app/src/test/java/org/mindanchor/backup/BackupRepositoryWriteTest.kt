package org.mindanchor.backup

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRepositoryWriteTest {

    @Test
    fun `a null provider stream is a failed write`() {
        assertFalse(BackupRepository.writeTo(null, "research export"))
    }

    @Test
    fun `a real stream receives the complete text`() {
        val output = ByteArrayOutputStream()

        assertTrue(BackupRepository.writeTo(output, "research export"))
        assertEquals("research export", output.toByteArray().decodeToString())
    }
}
