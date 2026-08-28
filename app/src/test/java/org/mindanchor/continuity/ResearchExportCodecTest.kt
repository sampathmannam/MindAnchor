package org.mindanchor.continuity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 7 — pins the structured research export: facts and inferences are
 * genuinely separate arrays (Program 0 never produces inferences, but the
 * shape must support them), the content hash ignores export-time metadata
 * so two devices with identical Journal content produce identical hashes,
 * and the codec round trips.
 */
class ResearchExportCodecTest {

    private val entry = JournalEntryDto(
        id = "entry-1",
        createdAt = 1_000L,
        updatedAt = 1_000L,
        localDate = "2026-08-27",
        title = "Morning",
        body = "Body text",
        kind = "DAILY",
        sourceDeviceId = "device-a",
        deletedAt = null,
    )

    private val fact = JournalContextDto(
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
    )

    private val inference = JournalContextDto(
        id = "context-2",
        entryId = "entry-1",
        recordType = "INFERENCE",
        key = "sentiment",
        value = "neutral",
        sourceStart = null,
        sourceEnd = null,
        confidence = 0.5,
        extractorVersion = "inference-v1",
        createdAt = 1_000L,
    )

    private val measure = MorningMeasureDto(
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
    )

    @Test
    fun `buildFrom splits context rows into facts and inferences by record type`() {
        val export = ResearchExportCodec.buildFrom(
            entries = listOf(entry),
            context = listOf(fact, inference),
            measures = listOf(measure),
            now = 5_000L,
            appVersionCode = 94,
            appVersionName = "0.70.0",
        )

        assertEquals(listOf(fact), export.contextFacts)
        assertEquals(listOf(inference), export.contextInferences)
    }

    @Test
    fun `program 0 produces no inferences so contextInferences is empty`() {
        val export = ResearchExportCodec.buildFrom(
            entries = listOf(entry),
            context = listOf(fact),
            measures = listOf(measure),
            now = 5_000L,
            appVersionCode = 94,
            appVersionName = "0.70.0",
        )

        assertTrue(export.contextInferences.isEmpty())
    }

    @Test
    fun `encode decode round trips exactly`() {
        val export = ResearchExportCodec.buildFrom(
            entries = listOf(entry),
            context = listOf(fact, inference),
            measures = listOf(measure),
            now = 5_000L,
            appVersionCode = 94,
            appVersionName = "0.70.0",
        )

        val encoded = ResearchExportCodec.encode(export)
        val decoded = ResearchExportCodec.decode(encoded)

        assertTrue(decoded is ResearchExportCodec.DecodeResult.Success)
        assertEquals(export, (decoded as ResearchExportCodec.DecodeResult.Success).export)
    }

    @Test
    fun `corrupt JSON is rejected without throwing`() {
        val decoded = ResearchExportCodec.decode("not json")

        assertTrue(decoded is ResearchExportCodec.DecodeResult.Corrupt)
    }

    @Test
    fun `content hash ignores exportedAt and app version so two devices agree`() {
        val exportFromDeviceA = ResearchExportCodec.buildFrom(
            entries = listOf(entry),
            context = listOf(fact),
            measures = listOf(measure),
            now = 1_000L,
            appVersionCode = 90,
            appVersionName = "0.69.0",
        )
        val exportFromDeviceB = ResearchExportCodec.buildFrom(
            entries = listOf(entry),
            context = listOf(fact),
            measures = listOf(measure),
            now = 9_999_999L,
            appVersionCode = 94,
            appVersionName = "0.70.0",
        )

        assertEquals(exportFromDeviceA.contentSha256, exportFromDeviceB.contentSha256)
    }

    @Test
    fun `changing a journal entry changes the content hash`() {
        val export = ResearchExportCodec.buildFrom(
            entries = listOf(entry),
            context = listOf(fact),
            measures = listOf(measure),
            now = 1_000L,
            appVersionCode = 90,
            appVersionName = "0.69.0",
        )
        val mutatedExport = ResearchExportCodec.buildFrom(
            entries = listOf(entry.copy(body = "Different body")),
            context = listOf(fact),
            measures = listOf(measure),
            now = 1_000L,
            appVersionCode = 90,
            appVersionName = "0.69.0",
        )

        assertNotEquals(export.contentSha256, mutatedExport.contentSha256)
    }

    @Test
    fun `journal entries facts and inferences are separate top-level arrays in the export JSON`() {
        val export = ResearchExportCodec.buildFrom(
            entries = listOf(entry),
            context = listOf(fact, inference),
            measures = listOf(measure),
            now = 5_000L,
            appVersionCode = 94,
            appVersionName = "0.70.0",
        )
        val encoded = ResearchExportCodec.encode(export)

        assertTrue(encoded.contains("\"journalEntries\""))
        assertTrue(encoded.contains("\"contextFacts\""))
        assertTrue(encoded.contains("\"contextInferences\""))
    }
}
