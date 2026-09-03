package org.mindanchor.advisory

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.data.db.InterventionEpisodeEventEntity
import org.mindanchor.data.db.withResearchImmutability
import org.mindanchor.research.ProvenanceVersions
import org.mindanchor.research.ResearchLedgerRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Program 3 Task 4 — closing a due outcome window is a statement of
 * absence, never an inference. The only content of a closure is that no
 * registered instrument exists to measure it; a stopped, interrupted, or
 * dismissed episode never had a window to close in the first place.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdvisoryOutcomeReconcilerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zoneId: ZoneId = ZoneId.of("UTC")

    private fun openDatabase(): AnchorDatabase = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java)
        .withResearchImmutability()
        .build()

    private fun reconciler(database: AnchorDatabase) = RoomAdvisoryOutcomeReconciler(
        context = context,
        database = database,
        researchLedger = ResearchLedgerRepository(
            context = context,
            database = database,
            currentVector = { ProvenanceVersions.vector(1, "test", "device-a") },
        ),
    )

    private fun event(
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
            zoneId = zoneId.id,
            studyPhaseId = "phase-1",
            sourceDeviceId = "device-a",
            protocolId = "cyclic-sighing",
            protocolVersion = 1,
            protocolDefinitionSha256 = "definition-hash",
            protocolCatalogSha256 = "catalog-hash",
            advisoryRuleVersion = AdvisoryPolicy.RULE_VERSION,
            buildMode = AdvisoryBuildMode.PERSONAL_RESEARCH.name,
            operationalEvidenceApproved = true,
            masterAdvisoryEnabled = true,
            deliveryAllowed = true,
            payloadSchemaVersion = AdvisoryCodec.EVENT_PAYLOAD_SCHEMA_VERSION,
            payloadJson = payload,
            previousEventHash = previous,
            eventHash = "",
        ),
    )

    /** A completed episode with an open outcome window, chained correctly. */
    private fun completedChain(
        episodeId: String,
        startedAt: Long,
        outcomeWindowSeconds: Long,
    ): List<InterventionEpisodeEventEntity> {
        val attested = event(episodeId, 1L, EpisodeEventType.ELIGIBILITY_ATTESTED, startedAt, "")
        val started = event(episodeId, 2L, EpisodeEventType.STARTED, startedAt, attested.eventHash)
        val terminalPayload = AdvisoryCodec.json.encodeToString(TerminalPayloadV1(300_000L, 33))
        val completed = event(
            episodeId, 3L, EpisodeEventType.COMPLETED_MAX_DURATION,
            startedAt + 300_000L, started.eventHash, terminalPayload,
        )
        val windowPayload = AdvisoryCodec.json.encodeToString(
            OutcomeWindowOpenedPayloadV1(
                opensAt = startedAt + 300_000L,
                closesAt = startedAt + 300_000L + outcomeWindowSeconds * 1_000L,
            ),
        )
        val opened = event(
            episodeId, 4L, EpisodeEventType.OUTCOME_WINDOW_OPENED,
            startedAt + 300_000L, completed.eventHash, windowPayload,
        )
        return listOf(attested, started, completed, opened)
    }

    @Test
    fun `a due outcome window closes exactly once with the exact missing reason`() = runBlocking {
        val database = openDatabase()
        try {
            val chain = completedChain("episode-1", startedAt = 1_000L, outcomeWindowSeconds = 86_400L)
            database.advisory().insertEvents(chain)
            val dueAt = 1_000L + 300_000L + 86_400_000L

            val firstRun = reconciler(database).reconcile(now = dueAt, zoneId = zoneId, requestCheckpoint = false)
            assertEquals(1, firstRun)

            val events = database.advisory().eventsForEpisode("episode-1")
            val closed = events.single { it.eventType == EpisodeEventType.OUTCOME_WINDOW_CLOSED_MISSING.name }
            val payload = AdvisoryCodec.json.decodeFromString<MissingOutcomePayloadV1>(closed.payloadJson)
            assertEquals(MissingOutcomeReason.NO_REGISTERED_COMPATIBLE_INSTRUMENT, payload.reason)
            assertEquals(EventChainVerdict.VALID, AdvisoryCodec.verifyEpisodeChain(events))

            val secondRun = reconciler(database)
                .reconcile(now = dueAt + 1_000L, zoneId = zoneId, requestCheckpoint = false)
            assertEquals(0, secondRun)
            assertEquals(1, database.advisory().eventsForEpisode("episode-1").count {
                it.eventType == EpisodeEventType.OUTCOME_WINDOW_CLOSED_MISSING.name
            })
        } finally {
            database.close()
        }
    }

    @Test
    fun `a window not yet due is left open`() = runBlocking {
        val database = openDatabase()
        try {
            val chain = completedChain("episode-1", startedAt = 1_000L, outcomeWindowSeconds = 86_400L)
            database.advisory().insertEvents(chain)
            val stillOpenAt = 1_000L + 300_000L + 1_000L

            val closedCount = reconciler(database)
                .reconcile(now = stillOpenAt, zoneId = zoneId, requestCheckpoint = false)
            assertEquals(0, closedCount)
            assertTrue(
                database.advisory().eventsForEpisode("episode-1").none {
                    it.eventType == EpisodeEventType.OUTCOME_WINDOW_CLOSED_MISSING.name
                },
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun `a stopped episode never opens or closes an outcome window`() = runBlocking {
        val database = openDatabase()
        try {
            val attested = event("episode-stopped", 1L, EpisodeEventType.ELIGIBILITY_ATTESTED, 1_000L, "")
            val started = event("episode-stopped", 2L, EpisodeEventType.STARTED, 1_000L, attested.eventHash)
            val stopped = event(
                "episode-stopped", 3L, EpisodeEventType.STOPPED_BY_USER, 2_000L, started.eventHash,
                AdvisoryCodec.json.encodeToString(TerminalPayloadV1(1_000L, 0)),
            )
            database.advisory().insertEvents(listOf(attested, started, stopped))

            val closedCount = reconciler(database)
                .reconcile(now = Long.MAX_VALUE / 2, zoneId = zoneId, requestCheckpoint = false)
            assertEquals(0, closedCount)
            val types = database.advisory().eventsForEpisode("episode-stopped").map { it.eventType }
            assertTrue(EpisodeEventType.OUTCOME_WINDOW_OPENED.name !in types)
            assertTrue(EpisodeEventType.OUTCOME_WINDOW_CLOSED_MISSING.name !in types)
        } finally {
            database.close()
        }
    }

    @Test
    fun `an interrupted episode never opens or closes an outcome window`() = runBlocking {
        val database = openDatabase()
        try {
            val attested = event("episode-interrupted", 1L, EpisodeEventType.ELIGIBILITY_ATTESTED, 1_000L, "")
            val started = event("episode-interrupted", 2L, EpisodeEventType.STARTED, 1_000L, attested.eventHash)
            val interrupted = event(
                "episode-interrupted", 3L, EpisodeEventType.INTERRUPTED_APP_BACKGROUND, 2_000L, started.eventHash,
                AdvisoryCodec.json.encodeToString(TerminalPayloadV1(1_000L, 0)),
            )
            database.advisory().insertEvents(listOf(attested, started, interrupted))

            val closedCount = reconciler(database)
                .reconcile(now = Long.MAX_VALUE / 2, zoneId = zoneId, requestCheckpoint = false)
            assertEquals(0, closedCount)
        } finally {
            database.close()
        }
    }

    @Test
    fun `a dismissed opportunity never opens or closes an outcome window`() = runBlocking {
        val database = openDatabase()
        try {
            val dismissed = event("dismissal-1", 1L, EpisodeEventType.DISMISSED, 1_000L, "")
            database.advisory().insertEvents(listOf(dismissed))

            val closedCount = reconciler(database)
                .reconcile(now = Long.MAX_VALUE / 2, zoneId = zoneId, requestCheckpoint = false)
            assertEquals(0, closedCount)
        } finally {
            database.close()
        }
    }

    @Test
    fun `a corrupted chain is skipped rather than acted on`() = runBlocking {
        val database = openDatabase()
        try {
            val chain = completedChain("episode-corrupt", startedAt = 1_000L, outcomeWindowSeconds = 86_400L)
            // Tamper with the completion event's payload after sealing, so
            // the chain no longer verifies — reconciliation must refuse to
            // act on evidence it cannot trust rather than closing it anyway.
            val tampered = chain.mapIndexed { index, row ->
                if (index == 2) row.copy(payloadJson = """{"tampered":true}""") else row
            }
            database.advisory().insertEvents(tampered)
            assertEquals(EventChainVerdict.BROKEN, AdvisoryCodec.verifyEpisodeChain(tampered))

            val closedCount = reconciler(database).reconcile(
                now = 1_000L + 300_000L + 86_400_000L,
                zoneId = zoneId,
                requestCheckpoint = false,
            )
            assertEquals(0, closedCount)
        } finally {
            database.close()
        }
    }
}
