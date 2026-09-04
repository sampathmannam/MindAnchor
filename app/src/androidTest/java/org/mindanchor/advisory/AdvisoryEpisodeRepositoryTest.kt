package org.mindanchor.advisory

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.continuity.ContinuityPrefs
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.data.db.AdvisoryOpportunityEntity
import org.mindanchor.data.db.withResearchImmutability
import org.mindanchor.intelligence.PassiveDataStatus
import org.mindanchor.intelligence.PassiveObservation
import org.mindanchor.intelligence.PassiveObservationState
import org.mindanchor.intelligence.PassivePipelineCodec
import org.mindanchor.intelligence.RevisionReason
import org.mindanchor.research.EvidenceProtocolCatalog
import org.mindanchor.research.EvidenceProtocolRegistry
import org.mindanchor.research.ResearchLedgerRepository
import org.mindanchor.research.testLedgerRepository

/**
 * Program 3 Task 4 — the manual-start event stream, its terminal events,
 * and dismissal, all against real Room and real EpisodeTransitions
 * enforcement.
 *
 * The property under test throughout is that a person's own single Start
 * tap is the only source of the four attestation facts, every
 * independent gate on that Start is checked fresh (not trusted from
 * presentation time), and every terminal outcome — completion,
 * interruption, or a person's own stop — is exactly one event that can
 * never be followed by another.
 */
@RunWith(AndroidJUnit4::class)
class AdvisoryEpisodeRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zoneId: ZoneId = ZoneId.of("UTC")

    private lateinit var database: AnchorDatabase
    private lateinit var researchLedger: ResearchLedgerRepository
    private lateinit var repository: RoomAdvisoryRepository

    private val phaseStartedAt = LocalDate.parse("2026-09-01").atStartOfDay(zoneId).toInstant().toEpochMilli()
    private val cyclicSighing = EvidenceProtocolCatalog.registry.latest("cyclic-sighing")!!
    private val cyclicDefinitionHash = EvidenceProtocolRegistry.definitionSha256(cyclicSighing)

    private var settings = AdvisorySettings(masterAdvisoryEnabled = true, deliveryAllowed = true)

    @Before
    fun open() = runBlocking {
        ContinuityPrefs(context).reset()
        database = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java)
            .withResearchImmutability()
            .build()
        researchLedger = testLedgerRepository(context, database)
        researchLedger.provenance.ensureCurrentPhase(phaseStartedAt)
        repository = RoomAdvisoryRepository(
            context = context,
            database = database,
            researchLedger = researchLedger,
            settingsProvider = { settings },
            buildAuthorization = {
                AdvisoryBuildAuthorization.forFlags(personalResearchBuild = true, operationalEvidenceApproved = true)
            },
            setCurrentEpisodeId = { id -> settings = settings.copy(currentEpisodeId = id) },
        )
    }

    @After
    fun close() {
        database.close()
    }

    private fun observation(
        asOfTime: Long = phaseStartedAt + 1_000L,
        day: LocalDate = LocalDate.parse("2026-09-01"),
    ) = PassiveObservation(
        day = day,
        asOfTime = asOfTime,
        dataStatus = PassiveDataStatus.AVAILABLE_FINAL,
        state = PassiveObservationState.SUSTAINED_DEVIATION,
        threshold = 1.5,
        crossed = true,
        baselineDays = 30,
        frozenBaselineAsOfTime = null,
        frozenBaselineThroughDay = null,
        baselineSegment = "segment-1",
        domains = emptyList(),
        calibration = null,
        baselineShift = null,
        explanation = "Sustained deviation across two features.",
    )

    private suspend fun createOpportunity(
        asOfTime: Long = phaseStartedAt + 1_000L,
        day: LocalDate = LocalDate.parse("2026-09-01"),
        reason: RevisionReason = RevisionReason.INITIAL,
        now: Long = phaseStartedAt + 5_000L,
    ): String {
        val entity = PassivePipelineCodec.decisionEntity(observation(asOfTime, day), reason)
        database.passive().insertObservationDecisions(listOf(entity))
        val result = repository.refreshOpportunity(now = now, zoneId = zoneId) as AdvisoryRefreshResult.Created
        return result.opportunityId
    }

    @Test
    fun `start re-evaluates all current gates and appends attested then started atomically`() = runBlocking {
        val opportunityId = createOpportunity()
        val result = repository.start(opportunityId, now = phaseStartedAt + 10_000L, zoneId = zoneId)

        assertTrue(result is AdvisoryStartResult.Started)
        val episodeId = (result as AdvisoryStartResult.Started).episodeId
        val events = database.advisory().eventsForEpisode(episodeId)
        assertEquals(
            listOf(EpisodeEventType.ELIGIBILITY_ATTESTED.name, EpisodeEventType.STARTED.name),
            events.map { it.eventType },
        )
        assertEquals(listOf(1L, 2L), events.map { it.sequence })
        assertEquals(EventChainVerdict.VALID, AdvisoryCodec.verifyEpisodeChain(events))
        assertEquals(episodeId, settings.currentEpisodeId)
        assertTrue(AdvisoryProcessSessionRegistry.contains(episodeId))
        AdvisoryProcessSessionRegistry.unregister(episodeId)
    }

    @Test
    fun `the attestation payload names all four facts true and no free text`() = runBlocking {
        val opportunityId = createOpportunity()
        val episodeId =
            (repository.start(opportunityId, now = phaseStartedAt + 10_000L, zoneId = zoneId) as AdvisoryStartResult.Started)
                .episodeId
        val attested = database.advisory().eventsForEpisode(episodeId).first { it.eventType == EpisodeEventType.ELIGIBILITY_ATTESTED.name }
        val payload = AdvisoryCodec.json.decodeFromString<EligibilityAttestedPayloadV1>(attested.payloadJson)
        assertTrue(payload.currentlySelfNoticesTensionOrArousal)
        assertTrue(payload.choosesProtocol)
        assertTrue(payload.exclusionsAndContraindicationsClear)
        assertTrue(payload.notDrivingOperatingMachineryOrExerting)
        AdvisoryProcessSessionRegistry.unregister(episodeId)
    }

    @Test
    fun `a disabled delivery switch appends zero rows`() = runBlocking {
        val opportunityId = createOpportunity()
        settings = settings.copy(deliveryAllowed = false)
        val result = repository.start(opportunityId, now = phaseStartedAt + 10_000L, zoneId = zoneId)
        assertEquals(AdvisoryStartResult.NotStarted(AdvisoryIneligibleReason.DELIVERY_DISABLED), result)
        assertTrue(database.advisory().eventsForOpportunity(opportunityId).isEmpty())
    }

    @Test
    fun `a stale superseded source decision appends zero rows`() = runBlocking {
        val opportunityId = createOpportunity()
        // Program 2 corrects the reading after presentation: the
        // opportunity's own frozen source is no longer the standing
        // decision, so starting it must refuse rather than act on stale
        // evidence.
        val corrected = PassivePipelineCodec.decisionEntity(
            observation(asOfTime = phaseStartedAt + 2_000L),
            RevisionReason.CONTENT_CHANGED,
        )
        database.passive().insertObservationDecisions(listOf(corrected))

        val result = repository.start(opportunityId, now = phaseStartedAt + 10_000L, zoneId = zoneId)
        assertEquals(AdvisoryStartResult.NotStarted(AdvisoryIneligibleReason.SOURCE_MISSING), result)
        assertTrue(database.advisory().eventsForOpportunity(opportunityId).isEmpty())
    }

    @Test
    fun `a changed registry hash appends zero rows`() = runBlocking {
        // Simulated by hand-inserting an opportunity whose recorded
        // definition hash does not match what the live registry computes
        // for cyclic-sighing today — the only way to represent "the
        // catalogue changed since this was presented" without editing
        // the compiled-in catalogue itself. The source decision is real
        // and still current, so only the hash mismatch can trip this.
        val decision = PassivePipelineCodec.decisionEntity(observation(), RevisionReason.INITIAL)
        database.passive().insertObservationDecisions(listOf(decision))
        val tampered = tamperedOpportunity(
            protocolDefinitionSha256 = "0".repeat(64),
            sourceDecisionId = decision.id,
            sourceDecisionContentHash = decision.contentHash,
        )
        database.advisory().insertOpportunity(tampered)

        val result = repository.start(tampered.id, now = phaseStartedAt + 10_000L, zoneId = zoneId)
        assertEquals(AdvisoryStartResult.NotStarted(AdvisoryIneligibleReason.PROTOCOL_HASH_MISMATCH), result)
        assertTrue(database.advisory().eventsForOpportunity(tampered.id).isEmpty())
    }

    @Test
    fun `an active episode elsewhere appends zero rows`() = runBlocking {
        val opportunityId = createOpportunity()
        settings = settings.copy(currentEpisodeId = "some-other-running-episode")
        val result = repository.start(opportunityId, now = phaseStartedAt + 10_000L, zoneId = zoneId)
        assertEquals(AdvisoryStartResult.NotStarted(AdvisoryIneligibleReason.ACTIVE_EPISODE_EXISTS), result)
        assertTrue(database.advisory().eventsForOpportunity(opportunityId).isEmpty())
    }

    @Test
    fun `a handled opportunity appends zero rows`() = runBlocking {
        val opportunityId = createOpportunity()
        repository.dismiss(opportunityId, now = phaseStartedAt + 8_000L, zoneId = zoneId)
        val result = repository.start(opportunityId, now = phaseStartedAt + 10_000L, zoneId = zoneId)
        assertEquals(AdvisoryStartResult.NotStarted(AdvisoryIneligibleReason.OPPORTUNITY_ALREADY_HANDLED), result)
    }

    @Test
    fun `an unexpired cooldown appends zero rows`() = runBlocking {
        val first = createOpportunity()
        val firstEpisode =
            (repository.start(first, now = phaseStartedAt + 10_000L, zoneId = zoneId) as AdvisoryStartResult.Started).episodeId
        repository.stop(
            firstEpisode,
            EpisodeEventType.STOPPED_BY_USER,
            now = phaseStartedAt + 20_000L,
            deliveredForegroundMillis = 5_000L,
        )

        // A second, later, otherwise-eligible opportunity for the same
        // protocol, well before the registry's 24-hour cooldown from the
        // first STARTED has elapsed.
        val second = createOpportunity(
            asOfTime = phaseStartedAt + 30_000L,
            day = LocalDate.parse("2026-09-01"),
            reason = RevisionReason.CONTENT_CHANGED,
            now = phaseStartedAt + 40_000L,
        )
        val result = repository.start(second, now = phaseStartedAt + 50_000L, zoneId = zoneId)
        assertEquals(AdvisoryStartResult.NotStarted(AdvisoryIneligibleReason.COOLDOWN_ACTIVE), result)
        assertTrue(database.advisory().eventsForOpportunity(second).isEmpty())
    }

    @Test
    fun `dismissal uses a deterministic stream and appends exactly one event with no free text`() = runBlocking {
        val opportunityId = createOpportunity()
        val expectedEpisodeId = AdvisoryCodec.dismissalStreamId(opportunityId)

        val first = repository.dismiss(opportunityId, now = phaseStartedAt + 8_000L, zoneId = zoneId)
        assertTrue(first is AdvisoryMutationResult.Appended)
        val events = database.advisory().eventsForEpisode(expectedEpisodeId)
        assertEquals(listOf(EpisodeEventType.DISMISSED.name), events.map { it.eventType })
        assertEquals(AdvisoryCodec.EMPTY_PAYLOAD, events.single().payloadJson)

        val second = repository.dismiss(opportunityId, now = phaseStartedAt + 9_000L, zoneId = zoneId)
        assertEquals(AdvisoryMutationResult.Ignored(AdvisoryIneligibleReason.OPPORTUNITY_ALREADY_HANDLED), second)
        assertEquals(1, database.advisory().eventsForEpisode(expectedEpisodeId).size)
    }

    @Test
    fun `a second terminal call on the same episode is ignored`() = runBlocking {
        val opportunityId = createOpportunity()
        val episodeId =
            (repository.start(opportunityId, now = phaseStartedAt + 10_000L, zoneId = zoneId) as AdvisoryStartResult.Started)
                .episodeId

        val first = repository.stop(
            episodeId, EpisodeEventType.STOPPED_BY_USER, now = phaseStartedAt + 20_000L, deliveredForegroundMillis = 10_000L,
        )
        assertTrue(first is AdvisoryMutationResult.Appended)
        val second = repository.stop(
            episodeId, EpisodeEventType.STOPPED_DISCOMFORT_REPORTED, now = phaseStartedAt + 21_000L, deliveredForegroundMillis = 11_000L,
        )
        assertEquals(AdvisoryMutationResult.Ignored(AdvisoryIneligibleReason.OPPORTUNITY_ALREADY_HANDLED), second)
        assertEquals(
            1,
            database.advisory().eventsForEpisode(episodeId).count { EpisodeEventType.valueOf(it.eventType) in EpisodeTransitions.terminal },
        )
    }

    @Test
    fun `completion appends completed and outcome window opened in one transaction`() = runBlocking {
        val opportunityId = createOpportunity()
        val episodeId =
            (repository.start(opportunityId, now = phaseStartedAt + 10_000L, zoneId = zoneId) as AdvisoryStartResult.Started)
                .episodeId

        val result = repository.completeMaximumDuration(
            episodeId,
            now = phaseStartedAt + 310_000L,
            deliveredForegroundMillis = 300_000L,
            completedCycles = 33,
        )
        assertTrue(result is AdvisoryMutationResult.Appended)
        val events = database.advisory().eventsForEpisode(episodeId)
        assertEquals(
            listOf(
                EpisodeEventType.ELIGIBILITY_ATTESTED.name,
                EpisodeEventType.STARTED.name,
                EpisodeEventType.COMPLETED_MAX_DURATION.name,
                EpisodeEventType.OUTCOME_WINDOW_OPENED.name,
            ),
            events.map { it.eventType },
        )
        assertEquals(EventChainVerdict.VALID, AdvisoryCodec.verifyEpisodeChain(events))
        val opened = events.last()
        val window = AdvisoryCodec.json.decodeFromString<OutcomeWindowOpenedPayloadV1>(opened.payloadJson)
        assertEquals(phaseStartedAt + 310_000L, window.opensAt)
        assertEquals(phaseStartedAt + 310_000L + cyclicSighing.outcomeWindowSeconds * 1_000L, window.closesAt)
    }

    @Test
    fun `background before the maximum appends interrupted app background`() = runBlocking {
        val opportunityId = createOpportunity()
        val episodeId =
            (repository.start(opportunityId, now = phaseStartedAt + 10_000L, zoneId = zoneId) as AdvisoryStartResult.Started)
                .episodeId
        val result = repository.stop(
            episodeId,
            EpisodeEventType.INTERRUPTED_APP_BACKGROUND,
            now = phaseStartedAt + 100_000L,
            deliveredForegroundMillis = 90_000L,
        )
        assertTrue(result is AdvisoryMutationResult.Appended)
        assertEquals(
            EpisodeEventType.INTERRUPTED_APP_BACKGROUND.name,
            database.advisory().eventsForEpisode(episodeId).last().eventType,
        )
    }

    @Test
    fun `process recovery appends interrupted process recovery`() = runBlocking {
        val opportunityId = createOpportunity()
        val episodeId =
            (repository.start(opportunityId, now = phaseStartedAt + 10_000L, zoneId = zoneId) as AdvisoryStartResult.Started)
                .episodeId
        // A fresh process would not have this episode registered at all.
        AdvisoryProcessSessionRegistry.unregister(episodeId)
        assertFalse(AdvisoryProcessSessionRegistry.contains(episodeId))

        val result = repository.stop(
            episodeId,
            EpisodeEventType.INTERRUPTED_PROCESS_RECOVERY,
            now = phaseStartedAt + 200_000L,
            deliveredForegroundMillis = 190_000L,
        )
        assertTrue(result is AdvisoryMutationResult.Appended)
        assertEquals(
            EpisodeEventType.INTERRUPTED_PROCESS_RECOVERY.name,
            database.advisory().eventsForEpisode(episodeId).last().eventType,
        )
        assertFalse(settings.currentEpisodeId == episodeId)
    }

    @Test
    fun `disabling delivery while active appends stopped kill switch`() = runBlocking {
        val opportunityId = createOpportunity()
        val episodeId =
            (repository.start(opportunityId, now = phaseStartedAt + 10_000L, zoneId = zoneId) as AdvisoryStartResult.Started)
                .episodeId
        settings = settings.copy(deliveryAllowed = false)

        val result = repository.stop(
            episodeId,
            EpisodeEventType.STOPPED_KILL_SWITCH,
            now = phaseStartedAt + 15_000L,
            deliveredForegroundMillis = 5_000L,
        )
        assertTrue(result is AdvisoryMutationResult.Appended)
        assertEquals(
            EpisodeEventType.STOPPED_KILL_SWITCH.name,
            database.advisory().eventsForEpisode(episodeId).last().eventType,
        )
    }

    @Test
    fun `every inserted event is accompanied by a pending continuity change`() = runBlocking {
        val opportunityId = createOpportunity()
        val episodeId =
            (repository.start(opportunityId, now = phaseStartedAt + 10_000L, zoneId = zoneId) as AdvisoryStartResult.Started)
                .episodeId
        repository.stop(
            episodeId, EpisodeEventType.STOPPED_BY_USER, now = phaseStartedAt + 20_000L, deliveredForegroundMillis = 10_000L,
        )
        // Ignored second call must add nothing.
        repository.stop(
            episodeId, EpisodeEventType.STOPPED_KILL_SWITCH, now = phaseStartedAt + 21_000L, deliveredForegroundMillis = 11_000L,
        )

        val eventCount = database.advisory().eventsForEpisode(episodeId).size
        val changeCount = database.journal().allChangesNow().count { it.entityType == "INTERVENTION_EPISODE_EVENT" }
        assertEquals(3, eventCount) // attested, started, stopped
        assertEquals(eventCount, changeCount)
    }

    @Test
    fun `no event payload carries per breath timestamps raw values or free text`() = runBlocking {
        val opportunityId = createOpportunity()
        val episodeId =
            (repository.start(opportunityId, now = phaseStartedAt + 10_000L, zoneId = zoneId) as AdvisoryStartResult.Started)
                .episodeId
        repository.completeMaximumDuration(
            episodeId, now = phaseStartedAt + 310_000L, deliveredForegroundMillis = 300_000L, completedCycles = 33,
        )
        val events = database.advisory().eventsForEpisode(episodeId)
        val started = events.first { it.eventType == EpisodeEventType.STARTED.name }
        val completed = events.first { it.eventType == EpisodeEventType.COMPLETED_MAX_DURATION.name }
        assertEquals(AdvisoryCodec.EMPTY_PAYLOAD, started.payloadJson)
        val terminalPayload = AdvisoryCodec.json.decodeFromString<TerminalPayloadV1>(completed.payloadJson)
        assertEquals(300_000L, terminalPayload.deliveredForegroundMillis)
        assertEquals(33, terminalPayload.completedCycles)
        // The payload decodes to exactly this strict two-field shape —
        // kotlinx.serialization's default decoder rejects unknown keys,
        // so a successful decode into TerminalPayloadV1 is itself proof
        // no third field (a timestamp, a raw value, free text) is present.
        assertFalse(completed.payloadJson.contains("timestamp", ignoreCase = true))
        assertFalse(completed.payloadJson.contains("journal", ignoreCase = true))
        assertFalse(completed.payloadJson.contains("note", ignoreCase = true))
    }

    private fun tamperedOpportunity(
        protocolDefinitionSha256: String,
        sourceDecisionId: String,
        sourceDecisionContentHash: String,
    ) = AdvisoryOpportunityEntity(
        id = "tampered-opportunity",
        presentedAt = phaseStartedAt + 1_000L,
        localDate = "2026-09-01",
        zoneId = zoneId.id,
        sourceDecisionId = sourceDecisionId,
        sourceDecisionContentHash = sourceDecisionContentHash,
        sourceLocalDate = "2026-09-01",
        sourceAsOfTime = phaseStartedAt + 500L,
        sourceDataStatus = PassiveDataStatus.AVAILABLE_FINAL.name,
        sourceObservationState = PassiveObservationState.SUSTAINED_DEVIATION.name,
        sourceExplanation = "explanation",
        sourceBaselineSegment = "segment-1",
        sourcePassiveRuleVersion = "passive-observation-rules-v6",
        sourcePassiveModelVersion = "personal-robust-baseline-v4",
        sourceStudyPhaseId = "phase-1",
        protocolId = cyclicSighing.id,
        protocolVersion = cyclicSighing.version,
        protocolDefinitionSha256 = protocolDefinitionSha256,
        protocolCatalogSha256 = AdvisoryBuildAuthorization.PROGRAM_THREE_CATALOG_SHA256,
        protocolClinicalReviewStatus = cyclicSighing.clinicalReviewStatus.name,
        advisoryRuleVersion = AdvisoryPolicy.RULE_VERSION,
        buildMode = AdvisoryBuildMode.PERSONAL_RESEARCH.name,
        operationalEvidenceApproved = true,
        masterAdvisoryEnabled = true,
        deliveryAllowedAtPresentation = true,
        studyPhaseId = "phase-1",
        sourceDeviceId = "device-a",
        contentHash = "content-hash-tampered",
    )
}
