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

    private suspend fun build(
        now: Long = dayThree,
        highWater: ContinuityPrefs.LedgerHighWater? = null,
    ) = ResearchExportBuilder.build(
        database = database,
        highWater = highWater,
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

    @Test
    fun aLedgerThatHasShrunkBelowItsHighWaterMarkIsReportedBroken() = runBlocking {
        seedADayOfRecords()
        val actual = database.research().ledgerEventCount()

        // The chain itself still verifies -- what remains after a
        // truncation is a shorter but perfectly self-consistent history --
        // so only a count recorded elsewhere can notice the loss.
        val intact = build(highWater = ContinuityPrefs.LedgerHighWater(actual, "irrelevant"))
        assertEquals(LedgerIntegrity.VERIFIED, intact.ledgerIntegrity)

        val shrunk = build(highWater = ContinuityPrefs.LedgerHighWater(actual + 1, "irrelevant"))
        assertEquals(LedgerIntegrity.BROKEN, shrunk.ledgerIntegrity)
    }

    @Test
    fun aHighWaterMarkBehindTheLedgerRaisesNoAlarm() = runBlocking {
        seedADayOfRecords()

        // The mark only ever rises, so being behind means a write that did
        // not refresh it, or a ledger restored onto a phone that has not
        // written since. Neither is evidence of loss.
        val export = build(highWater = ContinuityPrefs.LedgerHighWater(1, "an older head"))

        assertEquals(LedgerIntegrity.VERIFIED, export.ledgerIntegrity)
    }

    @Test
    fun aClockBehindTheNewestRecordStillReportsEveryAbsence() = runBlocking {
        seedADayOfRecords()
        // A later record with no measure of its own, so there is a genuine
        // absence to miss.
        testLedgerRepository(context, database)
            .record(LedgerEventKind.ILLNESS, occurredAt = dayThree, note = "", now = dayThree)

        // "now" is days before the newest record, as it would be after
        // timezone travel or a manual clock change. Reporting nothing here
        // while the document still says every absence is listed would be a
        // lie indistinguishable from a person who had missed nothing.
        val export = build(now = dayOne - 2 * 86_400_000L)

        assertEquals(
            "the window must run to the newest record, not to a clock that is behind it",
            listOf("2026-08-28", "2026-08-29"),
            export.missingData
                .filter { it.variable == MissingDataPolicy.VARIABLE_MORNING_MEASURE }
                .map { it.localDate },
        )
        assertTrue(ResearchExportCodec.verify(export))
    }

    @Test
    fun anAbsurdStoredDateDoesNotCrashTheExport() = runBlocking {
        seedADayOfRecords()
        // One row stamped a thousand years ago -- a corrupt restore, or a
        // clock that was wrong when it was written. Without the window
        // clamp this asks for four hundred thousand records and throws,
        // and an export that throws leaves a zero-byte file behind.
        database.journal().upsertEntries(
            listOf(
                org.mindanchor.data.db.JournalEntryEntity(
                    id = "entry-from-the-year-1000",
                    createdAt = dayOne,
                    updatedAt = dayOne,
                    localDate = "1000-01-01",
                    title = "",
                    body = "A row with an impossible date",
                    kind = "DAILY",
                    sourceDeviceId = "device-a",
                    deletedAt = null,
                ),
            ),
        )

        val export = build()

        assertTrue("the export must still be produced", ResearchExportCodec.verify(export))
        assertEquals(2, export.journalEntries.size)
        // The clamp is visible rather than silent: the earliest absence
        // listed is far later than the earliest record.
        assertTrue(export.missingData.isNotEmpty())
        assertTrue(
            "the window must be clamped, not run from the year 1000",
            export.missingData.first().localDate > "1900-01-01",
        )
    }

    @Test
    fun anExportOutcomeIsReturnedRatherThanThrown() = runBlocking {
        // A closed database is the simplest way to make the Room reads
        // fail. The person is mid-export with a file already created by
        // the picker; a typed outcome is what the caller can show them.
        database.close()

        val outcome = ResearchExportBuilder.export(
            context = context,
            database = database,
            uri = android.net.Uri.parse("content://invalid/nothing"),
            now = dayThree,
            zone = ZoneOffset.UTC,
        )

        assertTrue(
            "a failure must be a typed outcome, not an exception: $outcome",
            outcome is ResearchExportBuilder.ExportOutcome.BuildFailed ||
                outcome is ResearchExportBuilder.ExportOutcome.WriteFailed,
        )
        // Re-open so @After's close() is harmless.
        database = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java)
            .withResearchImmutability()
            .build()
    }
}
