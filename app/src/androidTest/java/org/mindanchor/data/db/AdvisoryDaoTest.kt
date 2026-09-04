package org.mindanchor.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.advisory.AdvisoryCodec
import org.mindanchor.advisory.EpisodeEventType
import org.mindanchor.advisory.EventChainVerdict

/**
 * Program 3 Task 2 — the advisory evidence tables against real SQLite.
 *
 * The properties under test are the ones a rescan depends on: inserting
 * the same content twice leaves one row, ordering is stable across
 * inserts made out of order, and a sequence number is claimed once per
 * episode. Together they are why the pipeline can be re-run at any time
 * without producing a second version of a past advisory.
 *
 * Built through `withResearchImmutability`, because Room's generated
 * `createAllTables` carries no triggers: if these pass on a fresh
 * in-memory database, the callback a brand-new install depends on is
 * installing the advisory triggers too.
 */
@RunWith(AndroidJUnit4::class)
class AdvisoryDaoTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: AnchorDatabase
    private lateinit var dao: AdvisoryDao

    @Before
    fun open() {
        database = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java)
            .withResearchImmutability()
            .build()
        dao = database.advisory()
    }

    @After
    fun close() = database.close()

    private fun opportunity(id: String, presentedAt: Long) = AdvisoryOpportunityEntity(
        id = id,
        presentedAt = presentedAt,
        localDate = "2026-09-03",
        zoneId = "Asia/Kolkata",
        sourceDecisionId = "decision-1",
        sourceDecisionContentHash = "decision-hash",
        sourceLocalDate = "2026-09-02",
        sourceAsOfTime = 900L,
        sourceDataStatus = "AVAILABLE_FINAL",
        sourceObservationState = "SUSTAINED_DEVIATION",
        sourceExplanation = "explanation",
        sourceBaselineSegment = "segment-1",
        sourcePassiveRuleVersion = "passive-observation-rules-v6",
        sourcePassiveModelVersion = "personal-robust-baseline-v4",
        sourceStudyPhaseId = "phase-1",
        protocolId = "cyclic-sighing",
        protocolVersion = 1,
        protocolDefinitionSha256 = "definition-hash",
        protocolCatalogSha256 = "catalog-hash",
        protocolClinicalReviewStatus = "NOT_REVIEWED",
        advisoryRuleVersion = "advisory-opportunity-v1",
        buildMode = "PERSONAL_RESEARCH",
        operationalEvidenceApproved = true,
        masterAdvisoryEnabled = true,
        deliveryAllowedAtPresentation = true,
        studyPhaseId = "phase-1",
        sourceDeviceId = "device-a",
        contentHash = "content-hash-$id",
    )

    private fun event(
        episodeId: String,
        opportunityId: String,
        sequence: Long,
        type: EpisodeEventType,
        previous: String,
        payload: String = AdvisoryCodec.EMPTY_PAYLOAD,
    ) = AdvisoryCodec.seal(
        InterventionEpisodeEventEntity(
            id = "",
            episodeId = episodeId,
            opportunityId = opportunityId,
            sequence = sequence,
            eventType = type.name,
            occurredAt = 1_000L + sequence,
            localDate = "2026-09-03",
            zoneId = "Asia/Kolkata",
            studyPhaseId = "phase-1",
            sourceDeviceId = "device-a",
            protocolId = "cyclic-sighing",
            protocolVersion = 1,
            protocolDefinitionSha256 = "definition-hash",
            protocolCatalogSha256 = "catalog-hash",
            advisoryRuleVersion = "advisory-opportunity-v1",
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

    private fun episode(episodeId: String, opportunityId: String): List<InterventionEpisodeEventEntity> {
        val attested = event(episodeId, opportunityId, 1L, EpisodeEventType.ELIGIBILITY_ATTESTED, "")
        val started = event(episodeId, opportunityId, 2L, EpisodeEventType.STARTED, attested.eventHash)
        return listOf(attested, started)
    }

    @Test
    fun anExactRescanInsertsNothingNew() = runBlocking {
        val row = opportunity("opportunity-1", presentedAt = 1_000L)
        assertTrue(dao.insertOpportunity(row) > 0L)
        assertEquals(-1L, dao.insertOpportunity(row))
        assertEquals(1, dao.opportunitiesNow().size)

        val rows = episode("episode-1", "opportunity-1")
        assertTrue(dao.insertEvents(rows).all { it > 0L })
        assertEquals(listOf(-1L, -1L), dao.insertEvents(rows))
        assertEquals(2, dao.eventsNow().size)
    }

    @Test
    fun opportunitiesComeBackInPresentationOrder() = runBlocking {
        dao.insertOpportunity(opportunity("opportunity-late", presentedAt = 3_000L))
        dao.insertOpportunity(opportunity("opportunity-early", presentedAt = 1_000L))
        dao.insertOpportunity(opportunity("opportunity-middle", presentedAt = 2_000L))
        assertEquals(
            listOf("opportunity-early", "opportunity-middle", "opportunity-late"),
            dao.opportunitiesNow().map { it.id },
        )
        assertEquals("opportunity-middle", dao.opportunity("opportunity-middle")?.id)
        assertNull(dao.opportunity("never-written"))
    }

    @Test
    fun oneSequencePerEpisodeIsClaimedOnce() = runBlocking {
        val rows = episode("episode-1", "opportunity-1")
        dao.insertEvents(rows)
        // Same episode and sequence, different content and therefore a
        // different id: the unique index refuses it, so a chain cannot
        // fork at a sequence number.
        val fork = event("episode-1", "opportunity-1", 2L, EpisodeEventType.DISMISSED, rows.first().eventHash)
        assertEquals(listOf(-1L), dao.insertEvents(listOf(fork)))
        assertEquals(
            listOf(EpisodeEventType.ELIGIBILITY_ATTESTED.name, EpisodeEventType.STARTED.name),
            dao.eventsForEpisode("episode-1").map { it.eventType },
        )
    }

    @Test
    fun eventsAreScopedByEpisodeAndOpportunity() = runBlocking {
        dao.insertEvents(episode("episode-1", "opportunity-1"))
        dao.insertEvents(episode("episode-2", "opportunity-2"))
        assertEquals(2, dao.eventsForEpisode("episode-1").size)
        assertEquals(2, dao.eventsForOpportunity("opportunity-2").size)
        assertTrue(dao.eventsForEpisode("episode-3").isEmpty())
        assertEquals(4, dao.eventsNow().size)
    }

    @Test
    fun aStoredEpisodeStillVerifiesAfterARoundTrip() = runBlocking {
        dao.insertEvents(episode("episode-1", "opportunity-1"))
        assertEquals(
            EventChainVerdict.VALID,
            AdvisoryCodec.verifyEpisodeChain(dao.eventsForEpisode("episode-1")),
        )
    }
}
