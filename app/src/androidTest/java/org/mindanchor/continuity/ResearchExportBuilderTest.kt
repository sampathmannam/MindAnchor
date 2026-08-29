package org.mindanchor.continuity

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.data.db.withResearchImmutability
import org.mindanchor.journal.DeviceIdentityStore
import org.mindanchor.journal.JournalContext
import org.mindanchor.journal.JournalContextExtractor
import org.mindanchor.journal.JournalEntry
import org.mindanchor.journal.JournalRepository
import org.mindanchor.journal.StructuralContextExtractor
import org.mindanchor.research.LedgerEventKind
import org.mindanchor.research.LedgerIntegrity
import org.mindanchor.research.MissingDataPolicy
import org.mindanchor.research.MissingDataReason
import org.mindanchor.research.MorningMeasureRepository
import org.mindanchor.research.ResearchDataDictionary
import org.mindanchor.research.testLedgerRepository

/**
 * Program 1 Task 11 — the export a person actually gets, built from real
 * Room rows rather than hand-assembled DTOs.
 *
 * The point of doing it on a device is the joins: which entries have no
 * context, which days have no measure, which phase covers which record.
 * Those are the parts a unit test with a hand-built fixture cannot get
 * wrong, and therefore cannot prove right.
 */
@RunWith(AndroidJUnit4::class)
class ResearchExportBuilderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: AnchorDatabase

    /** 2026-08-27T00:00Z, so a UTC zone puts every fixture on a predictable date. */
    private val dayOne = LocalDate.of(2026, 8, 27).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    private val dayThree = dayOne + 2 * 86_400_000L

    @Before
    fun open() {
        database = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java)
            .withResearchImmutability()
            .build()
    }

    @After
    fun close() = database.close()

    private suspend fun build(now: Long = dayThree) = ResearchExportBuilder.build(
        database = database,
        now = now,
        zone = ZoneOffset.UTC,
        appVersionCode = 95,
        appVersionName = "0.71.0",
    )

    private suspend fun seedADayOfRecords() {
        val ledger = testLedgerRepository(context, database)
        val deviceIdentity = DeviceIdentityStore(context)
        JournalRepository(
            context,
            database,
            deviceIdentity,
            StructuralContextExtractor(),
            ledger.provenance,
        ).create(title = "A day", body = "Two words", now = dayOne, localDate = LocalDate.of(2026, 8, 27))
        MorningMeasureRepository(context, database, deviceIdentity, ledger.provenance).save(
            localDate = LocalDate.of(2026, 8, 27),
            now = dayOne,
            mood = 4,
            anxiety = 2,
            angerUrge = 1,
            energyFunction = 3,
            sleepQuality = 5,
        )
        ledger.record(LedgerEventKind.EXERCISE, occurredAt = dayOne, note = "a walk", now = dayOne)
    }

    @Test
    fun anEmptyDatabaseProducesAnEmptyButValidExport() = runBlocking {
        val export = build()

        assertTrue(ResearchExportCodec.verify(export))
        assertEquals(ContinuityContract.RESEARCH_DICTIONARY_VERSION, export.dataDictionaryVersion)
        assertEquals(emptyList<JournalEntryDto>(), export.journalEntries)
        // Nothing recorded means nothing absent: absences are only
        // meaningful once there is a first record to count from.
        assertEquals(emptyList<org.mindanchor.research.MissingDataRecord>(), export.missingData)
        assertEquals(LedgerIntegrity.VERIFIED, export.ledgerIntegrity)
    }

    @Test
    fun aRealDayIsExportedWholeAndVerifies() = runBlocking {
        seedADayOfRecords()

        val export = build()

        assertTrue("a freshly built export must verify: $export", ResearchExportCodec.verify(export))
        assertEquals(1, export.journalEntries.size)
        assertEquals(4, export.contextFacts.size)
        assertEquals(emptyList<JournalContextDto>(), export.contextInferences)
        assertEquals(1, export.morningMeasures.size)
        assertEquals(1, export.studyPhases.size)
        assertTrue(export.ledgerEvents.any { it.kind == LedgerEventKind.EXERCISE.name })
        assertEquals(export.ledgerEvents.size, export.ledgerEventCount)
        assertEquals(export.ledgerEvents.last().eventHash, export.ledgerHeadHash)
        assertEquals(LedgerIntegrity.VERIFIED, export.ledgerIntegrity)
    }

    @Test
    fun theExportCarriesEnoughToBeReadWithoutTheApp() = runBlocking {
        seedADayOfRecords()

        val export = build()

        assertNotNull("an export must describe its own columns", export.dataDictionary)
        assertEquals(ResearchDataDictionary.sha256, export.dataDictionarySha256)
        assertEquals(1, export.protocolRegistry.size)
        assertTrue(export.protocolCatalogSha256.isNotBlank())
        assertEquals(2, export.transformations.size)
        assertEquals(MissingDataPolicy.VERSION, export.missingDataPolicyVersion)
        assertEquals(MissingDataPolicy.STATEMENT, export.missingDataStatement)
    }

    @Test
    fun everyDayWithoutAMeasureIsListedWithAReason() = runBlocking {
        seedADayOfRecords()

        val export = build(now = dayThree)

        // Day one has a measure; days two and three do not, and both fall
        // after the first measure, so neither is "hadn't started yet".
        assertEquals(
            listOf("2026-08-28", "2026-08-29"),
            export.missingData
                .filter { it.variable == MissingDataPolicy.VARIABLE_MORNING_MEASURE }
                .map { it.localDate },
        )
        assertTrue(
            export.missingData.all { it.reason == MissingDataReason.NOT_RECORDED },
        )
    }

    @Test
    fun anEntryWithNoContextIsReportedRatherThanHidden() = runBlocking {
        val ledger = testLedgerRepository(context, database)
        // A repository whose extractor derives nothing stands in for the
        // kill switch being off, or a derivation that failed.
        JournalRepository(
            context,
            database,
            DeviceIdentityStore(context),
            object : JournalContextExtractor {
                override fun extract(entry: JournalEntry, now: Long): List<JournalContext> = emptyList()
            },
            ledger.provenance,
        ).create(title = "A day", body = "Two words", now = dayOne, localDate = LocalDate.of(2026, 8, 27))

        val export = build(now = dayOne)

        assertEquals(emptyList<JournalContextDto>(), export.contextFacts)
        assertEquals(
            MissingDataReason.CONTEXT_NOT_DERIVED,
            export.missingData.single { it.variable == MissingDataPolicy.VARIABLE_JOURNAL_CONTEXT }.reason,
        )
        // The same day also has no morning measure, and it precedes the
        // first one ever taken, so it is reported as "hadn't started" and
        // not as a skipped day.
        assertEquals(
            MissingDataReason.BEFORE_FIRST_RECORD,
            export.missingData.single { it.variable == MissingDataPolicy.VARIABLE_MORNING_MEASURE }.reason,
        )
    }

    @Test
    fun exportingTwiceProducesTheSameContentHash() = runBlocking {
        seedADayOfRecords()

        val first = build(now = dayThree)
        val second = build(now = dayThree + 60_000L)

        // Different export times, same content: the hash answers "did the
        // data change", not "was this exported again".
        assertEquals(first.contentSha256, second.contentSha256)
        assertTrue(first.exportedAt != second.exportedAt)
    }

    @Test
    fun theWholeDocumentSurvivesAWriteAndAReadBack() = runBlocking {
        seedADayOfRecords()
        val original = build()

        val decoded = ResearchExportCodec.decode(ResearchExportCodec.encode(original))

        assertTrue("$decoded", decoded is ResearchExportCodec.DecodeResult.Success)
        val roundTripped = (decoded as ResearchExportCodec.DecodeResult.Success).export
        assertEquals(original, roundTripped)
        assertTrue(ResearchExportCodec.verify(roundTripped))
    }
}
