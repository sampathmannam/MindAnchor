package org.mindanchor.journal

import java.util.UUID

/**
 * Derives context facts about a [JournalEntry]. The production
 * implementation is [StructuralContextExtractor]; this interface exists so
 * [JournalRepository] can be tested with a fake/failing extractor without
 * touching the real one.
 */
interface JournalContextExtractor {
    fun extract(entry: JournalEntry, now: Long): List<JournalContext>
}

/**
 * Derives structural metadata about a [JournalEntry] — the kind, the local
 * date, a word count, the user-chosen title. Nothing here reads meaning
 * into the body text: no sentiment, no diagnosis, no inferred emotion. This
 * is a hard product/ethics constraint of Program 0, not just a test-passing
 * concern — do not extend this class to parse mental-health state from the
 * body.
 */
class StructuralContextExtractor : JournalContextExtractor {

    override fun extract(entry: JournalEntry, now: Long): List<JournalContext> {
        val facts = mutableListOf<Pair<String, String>>()
        facts += "entry_kind" to entry.kind.name
        facts += "local_date" to entry.localDate
        val wordCount = entry.body.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
        facts += "word_count" to wordCount.toString()
        if (entry.title.isNotBlank()) {
            facts += "user_title" to entry.title
        }

        return facts.map { (key, value) ->
            JournalContext(
                id = deterministicId(entry.id, ContextRecordType.FACT, key),
                entryId = entry.id,
                recordType = ContextRecordType.FACT,
                key = key,
                value = value,
                sourceStart = null,
                sourceEnd = null,
                confidence = 1.0,
                extractorVersion = EXTRACTOR_VERSION,
                createdAt = now,
            )
        }
    }

    companion object {
        const val EXTRACTOR_VERSION = "structural-v1"

        /**
         * Every key this extractor can emit, in emission order. Named here
         * so the frozen data dictionary can describe the closed value set
         * without restating it — a second copy would drift silently, and
         * an export claiming a closed set the data violates is worse than
         * one claiming none.
         */
        val FACT_KEYS = listOf("entry_kind", "local_date", "word_count", "user_title")

        private fun deterministicId(entryId: String, recordType: ContextRecordType, key: String): String =
            UUID.nameUUIDFromBytes("$entryId|$recordType|$key|$EXTRACTOR_VERSION".toByteArray()).toString()
    }
}
