package org.mindanchor.advisory

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.continuity.ContinuityPrefs
import org.mindanchor.data.db.AnchorDatabase
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
 * Program 3 Task 3 — [RoomAdvisoryRepository.refreshOpportunity] against
 * real Room, real Program 1 study-phase provenance, and real Program 2
 * decision decoding.
 *
 * The property under test throughout is idempotent, latest-only
 * materialization: the same eligible source produces exactly one
 * opportunity and one continuity change no matter how many times the
 * pipeline reruns, a corrected source produces a genuinely different
 * opportunity, and every ineligible source produces nothing at all.
 */
@RunWith(AndroidJUnit4::class)
class AdvisoryOpportunityRepositoryTest {

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
        // Open the phase this decision must be attributed to before the
        // decision itself exists, exactly as a real device's Program 1
        // does — never inside the same transaction as the refresh.
        researchLedger.provenance.ensureCurrentPhase(phaseStartedAt)
        repository = RoomAdvisoryRepository(
            context = context,
            database = database,
            researchLedger = researchLedger,
            settingsProvider = { settings },
            buildAuthorization = {
                AdvisoryBuildAuthorization.forFlags(personalResearchBuild = true, operationalEvidenceApproved = true)
            },
        )
    }

    @After
    fun close() {
        database.close()
    }

    private fun observation(
        dataStatus: PassiveDataStatus = PassiveDataStatus.AVAILABLE_FINAL,
        state: PassiveObservationState = PassiveObservationState.SUSTAINED_DEVIATION,
        asOfTime: Long = phaseStartedAt + 1_000L,
        day: LocalDate = LocalDate.parse("2026-09-01"),
        explanation: String = "Sustained deviation across two features.",
    ) = PassiveObservation(
        day = day,
        asOfTime = asOfTime,
        dataStatus = dataStatus,
        state = state,
        threshold = 1.5,
        crossed = true,
        baselineDays = 30,
        frozenBaselineAsOfTime = null,
        frozenBaselineThroughDay = null,
        baselineSegment = "segment-1",
        domains = emptyList(),
        calibration = null,
        baselineShift = null,
        explanation = explanation,
    )

    private suspend fun insertDecision(observation: PassiveObservation, reason: RevisionReason = RevisionReason.INITIAL) {
        val entity = PassivePipelineCodec.decisionEntity(observation, reason)
        database.passive().insertObservationDecisions(listOf(entity))
    }

    @Test
    fun `a final sustained decision appends one opportunity and one continuity change`() = runBlocking {
        insertDecision(observation())
        val result = repository.refreshOpportunity(now = phaseStartedAt + 5_000L, zoneId = zoneId)

        assertTrue(result is AdvisoryRefreshResult.Created)
        assertEquals(1, database.advisory().opportunitiesNow().size)
        val changes = database.journal().allChangesNow()
        assertEquals(1, changes.count { it.entityType == "ADVISORY_OPPORTUNITY" })
        assertEquals(
            (result as AdvisoryRefreshResult.Created).opportunityId,
            changes.first { it.entityType == "ADVISORY_OPPORTUNITY" }.entityId,
        )
    }

    @Test
    fun `an exact refresh appends neither a duplicate opportunity nor a duplicate continuity change`() = runBlocking {
        insertDecision(observation())
        repository.refreshOpportunity(now = phaseStartedAt + 5_000L, zoneId = zoneId)
        val second = repository.refreshOpportunity(now = phaseStartedAt + 6_000L, zoneId = zoneId)

        assertTrue(second is AdvisoryRefreshResult.Ineligible)
        assertEquals(
            AdvisoryIneligibleReason.OPPORTUNITY_ALREADY_RECORDED,
            (second as AdvisoryRefreshResult.Ineligible).reason,
        )
        assertEquals(1, database.advisory().opportunitiesNow().size)
        assertEquals(1, database.journal().allChangesNow().count { it.entityType == "ADVISORY_OPPORTUNITY" })
    }

    @Test
    fun `a corrected decision hash appends a distinct opportunity`() = runBlocking {
        insertDecision(observation(explanation = "First reading."))
        val first = repository.refreshOpportunity(now = phaseStartedAt + 5_000L, zoneId = zoneId)
            as AdvisoryRefreshResult.Created

        // Same local date, same asOfTime and state, but a corrected
        // explanation: a genuinely different Program 2 decision content,
        // so this must not be treated as the same opportunity.
        insertDecision(observation(explanation = "Corrected reading."), reason = RevisionReason.CONTENT_CHANGED)
        val second = repository.refreshOpportunity(now = phaseStartedAt + 7_000L, zoneId = zoneId)

        assertTrue(second is AdvisoryRefreshResult.Created)
        assertNotEquals(first.opportunityId, (second as AdvisoryRefreshResult.Created).opportunityId)
        assertEquals(2, database.advisory().opportunitiesNow().size)
    }

    @Test
    fun `the latest overall decision is used even when it is ineligible and an older one was not`() = runBlocking {
        insertDecision(observation(day = LocalDate.parse("2026-09-01"), asOfTime = phaseStartedAt + 1_000L))
        insertDecision(
            observation(
                dataStatus = PassiveDataStatus.AVAILABLE_PROVISIONAL,
                day = LocalDate.parse("2026-09-02"),
                asOfTime = phaseStartedAt + 2_000L,
            ),
        )
        val result = repository.refreshOpportunity(now = phaseStartedAt + 5_000L, zoneId = zoneId)

        // The provisional, later decision is the latest overall row, and
        // the policy must stop there rather than falling back to the
        // earlier eligible one.
        assertEquals(
            AdvisoryRefreshResult.Ineligible(AdvisoryIneligibleReason.SOURCE_NOT_FINAL),
            result,
        )
        assertTrue(database.advisory().opportunitiesNow().isEmpty())
    }

    @Test
    fun `provisional data appends nothing`() = runBlocking {
        insertDecision(observation(dataStatus = PassiveDataStatus.AVAILABLE_PROVISIONAL))
        val result = repository.refreshOpportunity(now = phaseStartedAt + 5_000L, zoneId = zoneId)
        assertEquals(AdvisoryRefreshResult.Ineligible(AdvisoryIneligibleReason.SOURCE_NOT_FINAL), result)
        assertTrue(database.advisory().opportunitiesNow().isEmpty())
    }

    @Test
    fun `no deviation appends nothing`() = runBlocking {
        insertDecision(observation(state = PassiveObservationState.WITHIN_PERSON_RANGE))
        val result = repository.refreshOpportunity(now = phaseStartedAt + 5_000L, zoneId = zoneId)
        assertEquals(AdvisoryRefreshResult.Ineligible(AdvisoryIneligibleReason.SOURCE_NOT_SUSTAINED_DEVIATION), result)
        assertTrue(database.advisory().opportunitiesNow().isEmpty())
    }

    @Test
    fun `malformed decision json appends nothing`() = runBlocking {
        val valid = PassivePipelineCodec.decisionEntity(observation(), RevisionReason.INITIAL)
        database.passive().insertObservationDecisions(listOf(valid.copy(decisionJson = "not json")))
        val result = repository.refreshOpportunity(now = phaseStartedAt + 5_000L, zoneId = zoneId)
        assertEquals(AdvisoryRefreshResult.Ineligible(AdvisoryIneligibleReason.SOURCE_DECODE_FAILED), result)
        assertTrue(database.advisory().opportunitiesNow().isEmpty())
    }

    @Test
    fun `incomplete phase provenance appends nothing`() = runBlocking {
        // A fresh database with no phase opened yet at all: the very
        // first refresh must not silently attribute the decision to a
        // phase that did not exist when it was produced.
        val freshDb = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java)
            .withResearchImmutability()
            .build()
        try {
            val freshLedger = testLedgerRepository(context, freshDb, sourceDeviceId = "device-b")
            val freshRepository = RoomAdvisoryRepository(
                context = context,
                database = freshDb,
                researchLedger = freshLedger,
                settingsProvider = { settings },
                buildAuthorization = {
                    AdvisoryBuildAuthorization.forFlags(personalResearchBuild = true, operationalEvidenceApproved = true)
                },
            )
            freshDb.passive().insertObservationDecisions(
                listOf(PassivePipelineCodec.decisionEntity(observation(), RevisionReason.INITIAL)),
            )
            val result = freshRepository.refreshOpportunity(now = phaseStartedAt + 5_000L, zoneId = zoneId)
            assertEquals(
                AdvisoryRefreshResult.Ineligible(AdvisoryIneligibleReason.SOURCE_PROVENANCE_INCOMPLETE),
                result,
            )
            assertTrue(freshDb.advisory().opportunitiesNow().isEmpty())
        } finally {
            freshDb.close()
        }
    }

    @Test
    fun `the persisted opportunity fields match their owners exactly`() = runBlocking {
        insertDecision(observation())
        val created = repository.refreshOpportunity(now = phaseStartedAt + 5_000L, zoneId = zoneId)
            as AdvisoryRefreshResult.Created
        val row = requireNotNull(database.advisory().opportunity(created.opportunityId))
        val phase = researchLedger.provenance.ensureCurrentPhase(phaseStartedAt + 5_000L)

        assertEquals("2026-09-01", row.sourceLocalDate)
        assertEquals(phaseStartedAt + 1_000L, row.sourceAsOfTime)
        assertEquals(PassiveDataStatus.AVAILABLE_FINAL.name, row.sourceDataStatus)
        assertEquals(PassiveObservationState.SUSTAINED_DEVIATION.name, row.sourceObservationState)
        assertEquals("Sustained deviation across two features.", row.sourceExplanation)
        assertEquals("segment-1", row.sourceBaselineSegment)
        assertEquals(phase.vector.sourceDeviceId, row.sourceDeviceId)
        assertEquals(phase.id, row.sourceStudyPhaseId)
        assertEquals(phase.id, row.studyPhaseId)

        assertEquals("cyclic-sighing", row.protocolId)
        assertEquals(1, row.protocolVersion)
        assertEquals(cyclicDefinitionHash, row.protocolDefinitionSha256)
        assertEquals(EvidenceProtocolCatalog.registry.catalogSha256, row.protocolCatalogSha256)
        assertEquals(cyclicSighing.clinicalReviewStatus.name, row.protocolClinicalReviewStatus)
        assertEquals(AdvisoryPolicy.RULE_VERSION, row.advisoryRuleVersion)
        assertEquals(AdvisoryBuildMode.PERSONAL_RESEARCH.name, row.buildMode)
        assertTrue(row.operationalEvidenceApproved)
        assertTrue(row.masterAdvisoryEnabled)
        assertTrue(row.deliveryAllowedAtPresentation)
    }
}
