package org.mindanchor.continuity

import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Builds and (de)serializes a [ResearchExport].
 *
 * [ResearchExport.contentSha256] deliberately excludes `exportedAt`,
 * `appVersionCode`, and `appVersionName` — two devices with identical
 * Journal content must produce the identical hash even when exported at
 * different times or from different app builds, so a researcher (or the
 * person themselves) can tell "did anything actually change" apart from
 * "was this exported again."
 */
object ResearchExportCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    sealed class DecodeResult {
        data class Success(val export: ResearchExport) : DecodeResult()
        data object Corrupt : DecodeResult()
    }

    fun encode(export: ResearchExport): String = json.encodeToString(export)

    fun decode(text: String): DecodeResult {
        val parsed = runCatching { json.decodeFromString<ResearchExport>(text) }
            .getOrElse { return DecodeResult.Corrupt }
        return DecodeResult.Success(parsed)
    }

    /**
     * Splits [context] into facts and inferences by [JournalContextDto.recordType]
     * and computes [ResearchExport.contentSha256] over the four content
     * lists only, canonically sorted the same way as
     * [ContinuityContentHasher].
     */
    fun buildFrom(
        entries: List<JournalEntryDto>,
        context: List<JournalContextDto>,
        measures: List<MorningMeasureDto>,
        now: Long,
        appVersionCode: Int,
        appVersionName: String,
    ): ResearchExport {
        val sortedEntries = entries.sortedBy { it.id }
        val sortedFacts = context.filter { it.recordType == "FACT" }.sortedBy { it.id }
        val sortedInferences = context.filter { it.recordType == "INFERENCE" }.sortedBy { it.id }
        val sortedMeasures = measures.sortedBy { it.id }
        return ResearchExport(
            exportedAt = now,
            appVersionCode = appVersionCode,
            appVersionName = appVersionName,
            contentSha256 = hashContent(sortedEntries, sortedFacts, sortedInferences, sortedMeasures),
            journalEntries = sortedEntries,
            contextFacts = sortedFacts,
            contextInferences = sortedInferences,
            morningMeasures = sortedMeasures,
        )
    }

    private fun hashContent(
        entries: List<JournalEntryDto>,
        facts: List<JournalContextDto>,
        inferences: List<JournalContextDto>,
        measures: List<MorningMeasureDto>,
    ): String {
        val content = HashedContent(entries, facts, inferences, measures)
        val bytes = json.encodeToString(content).encodeToByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    @Serializable
    private data class HashedContent(
        val journalEntries: List<JournalEntryDto>,
        val contextFacts: List<JournalContextDto>,
        val contextInferences: List<JournalContextDto>,
        val morningMeasures: List<MorningMeasureDto>,
    )
}
