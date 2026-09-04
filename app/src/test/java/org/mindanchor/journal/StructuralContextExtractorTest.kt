package org.mindanchor.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuralContextExtractorTest {
    private val extractor = StructuralContextExtractor()

    @Test
    fun `extracts only structural facts`() {
        val entry = JournalEntry(
            id = "e1",
            createdAt = 1_000L,
            updatedAt = 1_000L,
            localDate = "2026-08-28",
            title = "Difficult shift",
            body = "I had an argument. I took a walk afterward.",
            kind = JournalKind.DAILY,
            sourceDeviceId = "d1",
            deletedAt = null,
        )
        val facts = extractor.extract(entry, now = 2_000L)
        assertEquals(setOf("entry_kind", "local_date", "word_count", "user_title"), facts.map { it.key }.toSet())
        assertTrue(facts.all { it.recordType == ContextRecordType.FACT })
        assertTrue(facts.none { it.value.contains("anxiety", ignoreCase = true) })
        assertTrue(facts.all { it.extractorVersion == "structural-v1" })
    }
}
