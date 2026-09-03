package org.mindanchor.continuity

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.advisory.AdvisoryCodec
import org.mindanchor.advisory.AdvisoryPolicy
import org.mindanchor.advisory.EpisodeEventType
import org.mindanchor.advisory.EventChainVerdict
import org.mindanchor.advisory.OutcomeWindowOpenedPayloadV1
import org.mindanchor.advisory.RoomAdvisoryOutcomeReconciler
import org.mindanchor.advisory.TerminalPayloadV1
import org.mindanchor.data.db.AdvisoryOpportunityEntity
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.data.db.InterventionEpisodeEventEntity
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
import org.mindanchor.research.TransformationRegistry
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
    fun open() = runBlocking {
        ContinuityPrefs(context).reset()
        database = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java)
            .withResearchImmutability()
            .build()
    }

    @After
    fun close() = runBlocking {
        database.close()
        ContinuityPrefs(context).reset()
    }

    private suspend fun build(
        now: Long = dayThree,
        highWater: ContinuityPrefs.LedgerHighWater? = null,
        afterLedgerRead: suspend () -> Unit = {},
    ) = ResearchExportBuilder.build(
        database = database,
        highWater = highWater,
        now = now,
        zone = ZoneOffset.UTC,
        appVersionCode = 95,
        appVersionName = "0.71.0",
        afterLedgerRead = afterLedgerRead,
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
        // Empty is not the same as perfect: with nothing recorded there is
        // no window, and the file says so.
        assertEquals(null, export.missingDataWindowStart)
        assertEquals(null, export.missingDataWindowThrough)
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
        assertEquals(TransformationRegistry.transformations.sortedBy { it.id }, export.transformations)
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
    fun aClockBeforeEveryRecordReportsNoAbsenceRatherThanInventingOne() = runBlocking {
        seedADayOfRecords()
        testLedgerRepository(context, database)
            .record(LedgerEventKind.ILLNESS, occurredAt = dayThree, note = "", now = dayThree)

        // "now" is days before every record, as it would be after timezone
        // travel or a manual clock change.
        //
        // This test previously asserted the opposite: that the window ran
        // on to the newest record, so nothing was missed. That produced
        // absences dated *after* the export date -- days that had not
        // happened when the file was written -- which is the one thing
        // this policy exists to never do. Under-reporting is the smaller
        // wrong, and the statement carried in the file says the window
        // ends on the export date, so the document does not overclaim.
        val export = build(now = dayOne - 2 * 86_400_000L)

        assertTrue(
            "no absence may be dated after the export date",
            export.missingData.none { it.localDate > "2026-08-25" },
        )
        // The records themselves are untouched: only the derived report
        // declines to describe a window it cannot vouch for.
        assertTrue("the journal entry must still be exported", export.journalEntries.isNotEmpty())
        assertTrue(
            "the illness the person logged must still be exported",
            export.ledgerEvents.any { it.kind == LedgerEventKind.ILLNESS.name },
        )
        assertTrue(
            "the file must say the window ends on the export date",
            export.missingDataStatement.contains("export date"),
        )
        // The document has to say it reported on nothing, rather than
        // leaving an empty list to read as perfect adherence. A phone with
        // no network time boots to its build date, so restore-then-export
        // before the clock syncs is the realistic way to land here.
        assertEquals(null, export.missingDataWindowStart)
        assertEquals(null, export.missingDataWindowThrough)
        assertTrue(ResearchExportCodec.verify(export))
    }

    @Test
    fun anOrdinaryExportStatesTheWindowItReportedOn() = runBlocking {
        seedADayOfRecords()

        val export = build()

        assertEquals("2026-08-27", export.missingDataWindowStart)
        assertEquals("2026-08-29", export.missingDataWindowThrough)
        assertTrue(
            "no absence may fall outside the stated window",
            export.missingData.all {
                it.localDate >= export.missingDataWindowStart!! &&
                    it.localDate <= export.missingDataWindowThrough!!
            },
        )
    }

    /** Generous for any fixture here, and orders of magnitude below the runaway reports the window rule exists to prevent. */
    private val MAX_PLAUSIBLE_REPORT_ROWS = 400

    @Test
    fun anAbsurdStoredDateDoesNotCrashTheExport() = runBlocking {
        seedADayOfRecords()
        // One row stamped a thousand years ago -- a corrupt restore, or a
        // clock that was wrong when it was written. Before the window rule
        // this asked for four hundred thousand records and threw, and an
        // export that throws leaves a zero-byte file behind.
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
        // The row itself is still exported verbatim -- only the derived
        // report ignores it. Nothing about the record is altered.
        assertTrue(
            "the impossible row must still appear in the data",
            export.journalEntries.any { it.localDate == "1000-01-01" },
        )
        // The previous version of this assertion was `first().localDate >
        // "1900-01-01"`, which was true of a 36,600-row report starting in
        // 1926 -- it could not tell a sane window from a three-megabyte
        // one. Bounding the count is what actually distinguishes them.
        assertTrue(
            "one corrupt row must not expand the report: ${export.missingData.size} rows",
            export.missingData.size <= MAX_PLAUSIBLE_REPORT_ROWS,
        )
        assertTrue(
            "the report must be about the dates actually recorded",
            export.missingData.all { it.localDate.startsWith("2026") },
        )
    }

    @Test
    fun aFutureDatedRowDoesNotReplaceTheWholeReport() = runBlocking {
        seedADayOfRecords()
        // The mirror image, and the worse one: a row stamped a thousand
        // years *ahead* used to drag the window forward with it, so the
        // report covered the thirtieth century and dropped every date the
        // person had lived -- while still claiming every absence was
        // listed. A confidently wrong report is worse than a crash.
        database.journal().upsertEntries(
            listOf(
                org.mindanchor.data.db.JournalEntryEntity(
                    id = "entry-from-the-year-3026",
                    createdAt = dayOne,
                    updatedAt = dayOne,
                    localDate = "3026-01-01",
                    title = "",
                    body = "A row from a clock set to the wrong millennium",
                    kind = "DAILY",
                    sourceDeviceId = "device-a",
                    deletedAt = null,
                ),
            ),
        )

        val export = build()

        assertTrue("the export must still be produced", ResearchExportCodec.verify(export))
        assertEquals("2026-08-27", export.missingDataWindowStart)
        assertEquals("2026-08-29", export.missingDataWindowThrough)
        assertEquals(
            listOf("2026-08-28", "2026-08-29"),
            export.missingData
                .filter { it.variable == MissingDataPolicy.VARIABLE_MORNING_MEASURE }
                .map { it.localDate },
        )
        assertTrue(
            "one corrupt row must not expand the report: ${export.missingData.size} rows",
            export.missingData.size <= MAX_PLAUSIBLE_REPORT_ROWS,
        )
        assertTrue(
            "the report must be about the dates actually recorded",
            export.missingData.all { it.localDate.startsWith("2026") },
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

    @Test
    fun aRejectedWriteReturnsWriteFailedRatherThanSuccess() = runBlocking {
        val outcome = ResearchExportBuilder.export(
            context = context,
            database = database,
            uri = android.net.Uri.parse("content://provider/rejected"),
            now = dayThree,
            zone = ZoneOffset.UTC,
            writeExport = { _, _, _ -> false },
        )

        assertEquals(ResearchExportBuilder.ExportOutcome.WriteFailed, outcome)
    }

    @Test
    fun everyRoomDatasetComesFromOneTransactionallyConsistentPointInTime() = runBlocking {
        val ledger = testLedgerRepository(context, database)
        lateinit var writer: Deferred<org.mindanchor.research.ResearchLedgerEvent>

        val export = build(
            afterLedgerRead = {
                writer = async(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                    ledger.record(
                        LedgerEventKind.EXERCISE,
                        occurredAt = dayOne,
                        note = "racing write",
                        now = dayOne,
                    )
                }
                // The writer starts synchronously through its first
                // suspension, and record() reaches database.withTransaction
                // before any suspension. It is therefore waiting on this
                // export transaction, not merely queued to start later.
                assertTrue(writer.isActive && !writer.isCompleted)
            },
        )
        writer.await()

        assertTrue("the racing write must commit after export", database.research().studyPhaseCount() > 0)
        assertEquals(emptyList<ResearchLedgerEventDto>(), export.ledgerEvents)
        assertEquals(emptyList<StudyPhaseDto>(), export.studyPhases)
        assertTrue(ResearchExportCodec.verify(export))
    }

    @Test
    fun passiveHistoryIsExportedButRawSampleValuesAreExcluded() = runBlocking {
        PassiveContinuityFixture.insertInto(database)

        val export = build()
        val encoded = ResearchExportCodec.encode(export)

        assertEquals(PassiveContinuityFixture.rawProvenance.map { it.toDto() }, export.passiveRawProvenance)
        assertEquals(PassiveContinuityFixture.sourceReads.map { it.toDto() }, export.passiveSourceReads)
        assertEquals(PassiveContinuityFixture.sourceLags.map { it.toDto() }, export.passiveSourceLags)
        assertEquals(PassiveContinuityFixture.baselineSegments.map { it.toDto() }, export.passiveBaselineSegments)
        assertEquals(PassiveContinuityFixture.pipelineRuns.map { it.toDto() }, export.passivePipelineRuns)
        assertEquals(PassiveContinuityFixture.windowRevisions.map { it.toDto() }, export.passiveWindowRevisions)
        assertEquals(PassiveContinuityFixture.dailyRevisions.map { it.toDto() }, export.passiveDailyRevisions)
        assertEquals(
            PassiveContinuityFixture.observationDecisions.map { it.toDto() },
            export.passiveObservationDecisions,
        )
        assertFalse(encoded.contains("passiveRawSamples"))
        assertFalse(encoded.contains("173.25"))
        assertTrue(ResearchExportCodec.verify(export))
    }

    private fun advisoryOpportunity(id: String): AdvisoryOpportunityEntity {
        val unsealed = AdvisoryOpportunityEntity(
            id = id,
            presentedAt = 1_000L,
            localDate = "2026-09-03",
            zoneId = "UTC",
            sourceDecisionId = "decision-$id",
            sourceDecisionContentHash = "decision-hash",
            sourceLocalDate = "2026-09-02",
            sourceAsOfTime = 900L,
            sourceDataStatus = "AVAILABLE_FINAL",
            sourceObservationState = "SUSTAINED_DEVIATION",
            sourceExplanation = "fixture explanation",
            sourceBaselineSegment = "segment-1",
            sourcePassiveRuleVersion = "passive-observation-rules-v6",
            sourcePassiveModelVersion = "personal-robust-baseline-v4",
            sourceStudyPhaseId = "phase-1",
            protocolId = "cyclic-sighing",
            protocolVersion = 1,
            protocolDefinitionSha256 = "definition-hash",
            protocolCatalogSha256 = "catalog-hash",
            protocolClinicalReviewStatus = "NOT_REVIEWED",
            advisoryRuleVersion = AdvisoryPolicy.RULE_VERSION,
            buildMode = "PERSONAL_RESEARCH",
            operationalEvidenceApproved = true,
            masterAdvisoryEnabled = true,
            deliveryAllowedAtPresentation = true,
            studyPhaseId = "phase-1",
            sourceDeviceId = "device-a",
            contentHash = "",
        )
        return unsealed.copy(contentHash = AdvisoryCodec.opportunityContentHash(unsealed))
    }

    private fun episodeEvent(
        episodeId: String,
        sequence: Long,
        type: EpisodeEventType,
        occurredAt: Long,
        previous: String,
        payload: String = AdvisoryCodec.EMPTY_PAYLOAD,
    ) = AdvisoryCodec.seal(
        InterventionEpisodeEventEntity(
            id = "",
            episodeId = episodeId,
            opportunityId = "opportunity-$episodeId",
            sequence = sequence,
            eventType = type.name,
            occurredAt = occurredAt,
            localDate = "2026-09-03",
            zoneId = "UTC",
            studyPhaseId = "phase-1",
            sourceDeviceId = "device-a",
            protocolId = "cyclic-sighing",
            protocolVersion = 1,
            protocolDefinitionSha256 = "definition-hash",
            protocolCatalogSha256 = "catalog-hash",
            advisoryRuleVersion = AdvisoryPolicy.RULE_VERSION,
            buildMode = "PERSONAL_RESEARCH",
            operationalEvidenceApproved = true,
            masterAdvisoryEnabled = true,
            deliveryAllowed = true,
            payloadSchemaVersion = AdvisoryCodec.EVENT_PAYLOAD_SCHEMA_VERSION,
            payloadJson = payload,
            previousEventHash = previous,
            eventHash = "",
        ),
    )

    /** A completed episode with an open outcome window, chained correctly — mirrors AdvisoryOutcomeReconcilerTest's fixture. */
    private fun completedChain(episodeId: String, startedAt: Long, outcomeWindowSeconds: Long): List<InterventionEpisodeEventEntity> {
        val attested = episodeEvent(episodeId, 1L, EpisodeEventType.ELIGIBILITY_ATTESTED, startedAt, "")
        val started = episodeEvent(episodeId, 2L, EpisodeEventType.STARTED, startedAt, attested.eventHash)
        val terminalPayload = AdvisoryCodec.json.encodeToString(TerminalPayloadV1(300_000L, 33))
        val completed = episodeEvent(
            episodeId, 3L, EpisodeEventType.COMPLETED_MAX_DURATION,
            startedAt + 300_000L, started.eventHash, terminalPayload,
        )
        val windowPayload = AdvisoryCodec.json.encodeToString(
            OutcomeWindowOpenedPayloadV1(
                opensAt = startedAt + 300_000L,
                closesAt = startedAt + 300_000L + outcomeWindowSeconds * 1_000L,
            ),
        )
        val opened = episodeEvent(
            episodeId, 4L, EpisodeEventType.OUTCOME_WINDOW_OPENED,
            startedAt + 300_000L, completed.eventHash, windowPayload,
        )
        return listOf(attested, started, completed, opened)
    }

    /** A user-stopped episode: no COMPLETED_MAX_DURATION, so no outcome window is ever opened. */
    private fun stoppedChain(episodeId: String, startedAt: Long): List<InterventionEpisodeEventEntity> {
        val attested = episodeEvent(episodeId, 1L, EpisodeEventType.ELIGIBILITY_ATTESTED, startedAt, "")
        val started = episodeEvent(episodeId, 2L, EpisodeEventType.STARTED, startedAt, attested.eventHash)
        val terminalPayload = AdvisoryCodec.json.encodeToString(TerminalPayloadV1(60_000L, 6))
        val stopped = episodeEvent(
            episodeId, 3L, EpisodeEventType.STOPPED_BY_USER,
            startedAt + 60_000L, started.eventHash, terminalPayload,
        )
        return listOf(attested, started, stopped)
    }

    @Test
    fun productionExportIncludesAReconciledCompletedEpisodeWithMissingOutcomeClosure() = runBlocking {
        val opportunity = advisoryOpportunity("opportunity-episode-1")
        database.advisory().insertOpportunity(opportunity)
        database.advisory().insertEvents(completedChain("episode-1", startedAt = 1_000L, outcomeWindowSeconds = 86_400L))
        val dueAt = 1_000L + 300_000L + 86_400_000L
        val reconciler = RoomAdvisoryOutcomeReconciler(context, database, testLedgerRepository(context, database))

        val export = ResearchExportBuilder.build(
            database = database,
            highWater = null,
            now = dueAt,
            zone = ZoneOffset.UTC,
            appVersionCode = 95,
            appVersionName = "0.71.0",
            advisoryOutcomeReconciler = reconciler,
        )

        assertEquals(listOf(opportunity.id), export.advisoryOpportunities.map { it.id })
        val events = export.interventionEpisodeEvents
        assertEquals(5, events.size)
        assertTrue(events.any { it.eventType == EpisodeEventType.OUTCOME_WINDOW_CLOSED_MISSING.name })
        assertEquals(
            EventChainVerdict.VALID,
            AdvisoryCodec.verifyEpisodeChain(database.advisory().eventsForEpisode("episode-1")),
        )
        assertEquals(events.sortedBy { it.sequence }.map { it.sequence }, events.map { it.sequence })
        assertTrue(ResearchExportCodec.verify(export))
    }

    @Test
    fun productionExportOfStoppedOnlyHistoryHasNoOutcomeWindowRows() = runBlocking {
        val opportunity = advisoryOpportunity("opportunity-episode-2")
        database.advisory().insertOpportunity(opportunity)
        database.advisory().insertEvents(stoppedChain("episode-2", startedAt = 2_000L))

        val export = ResearchExportBuilder.build(
            database = database,
            highWater = null,
            now = 2_000L + 60_000L,
            zone = ZoneOffset.UTC,
            appVersionCode = 95,
            appVersionName = "0.71.0",
        )

        assertEquals(3, export.interventionEpisodeEvents.size)
        assertTrue(
            export.interventionEpisodeEvents.none {
                it.eventType == EpisodeEventType.OUTCOME_WINDOW_OPENED.name ||
                    it.eventType == EpisodeEventType.OUTCOME_WINDOW_CLOSED_MISSING.name
            },
        )
        assertTrue(ResearchExportCodec.verify(export))
    }
}
