package org.mindanchor.research

import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Program 1 Task 6 — the coordinator opens a study phase when the
 * provenance vector changes and records the change in the ledger, with no
 * Room, no Context and no Robolectric: storage is one narrow interface,
 * faked in memory.
 */
class ResearchProvenanceCoordinatorTest {

    /**
     * [failOnAppend] simulates the torn write the transaction exists to
     * prevent. [inTransaction] genuinely rolls back on failure — a fake
     * that swallowed the exception and kept the phase would prove nothing.
     */
    private class FakeStore(private val failOnAppend: Boolean = false) : ResearchProvenanceStore {
        val phases = mutableListOf<StudyPhase>()
        val events = mutableListOf<ResearchLedgerEvent>()
        var vector: ProvenanceVector = ProvenanceVersions.vector(95, "0.71.0", "device-a")
        var transactions = 0

        override suspend fun inTransaction(block: suspend () -> StudyPhase): StudyPhase {
            transactions += 1
            val phasesBefore = phases.toList()
            val eventsBefore = events.toList()
            return runCatching { block() }.getOrElse { thrown ->
                phases.clear()
                phases += phasesBefore
                events.clear()
                events += eventsBefore
                throw thrown
            }
        }

        override suspend fun latestPhase(): StudyPhase? = phases.maxByOrNull { it.ordinal }

        override suspend fun ledgerHead(): ResearchLedgerEvent? = events.maxByOrNull { it.sequence }

        override suspend fun registeredProtocolPayloads(): List<String> = events
            .filter { it.kind == LedgerEventKind.PROTOCOL_VERSION_REGISTERED }
            .map { it.payloadJson }

        override suspend fun insertPhase(phase: StudyPhase) {
            phases += phase
        }

        override suspend fun appendEvents(events: List<ResearchLedgerEvent>) {
            if (failOnAppend) error("the disk filled up")
            this.events += events
        }
    }

    private fun coordinator(
        store: FakeStore,
        protocols: () -> List<EvidenceProtocol> = { EvidenceProtocolCatalog.registry.protocols },
    ) = ResearchProvenanceCoordinator(
        store = store,
        currentVector = { store.vector },
        protocols = protocols,
        localDateOf = { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() },
    )

    private fun kinds(store: FakeStore) = store.events.map { it.kind }

    @Test
    fun `the first call opens phase zero and registers the catalogue`() = runBlocking {
        val store = FakeStore()
        val phase = coordinator(store).ensureCurrentPhase(now = 1_000L)

        assertEquals(0, phase.ordinal)
        assertEquals(StudyPhaseReason.INITIAL, phase.reason)
        assertEquals(listOf(phase), store.phases)
        assertEquals(
            listOf(LedgerEventKind.STUDY_PHASE_STARTED) +
                List(EvidenceProtocolCatalog.registry.protocols.size) {
                    LedgerEventKind.PROTOCOL_VERSION_REGISTERED
                },
            kinds(store),
        )
    }

    @Test
    fun `the whole sequence runs in one transaction`() = runBlocking {
        val store = FakeStore()
        coordinator(store).ensureCurrentPhase(now = 1_000L)
        assertEquals(1, store.transactions)
    }

    @Test
    fun `a failed append leaves no phase behind`() = runBlocking {
        val store = FakeStore(failOnAppend = true)
        assertThrows(IllegalStateException::class.java) {
            runBlocking { coordinator(store).ensureCurrentPhase(now = 1_000L) }
        }
        // If the phase survived a failed append, the next call would find
        // the vector unchanged, return early, and the phase would exist
        // forever with no STUDY_PHASE_STARTED event to explain it.
        assertEquals(emptyList<StudyPhase>(), store.phases)
        assertEquals(emptyList<ResearchLedgerEvent>(), store.events)
    }

    @Test
    fun `the events it writes form a valid chain from genesis`() = runBlocking {
        val store = FakeStore()
        coordinator(store).ensureCurrentPhase(now = 1_000L)

        assertEquals(LedgerIntegrity.VERIFIED, LedgerChain.verify(store.events))
        assertEquals(1L, store.events.first().sequence)
        assertEquals(LedgerChain.GENESIS_PREVIOUS_HASH, store.events.first().previousEventHash)
        assertEquals(store.events.size, LedgerChain.anchorOf(store.events).eventCount)
    }

    @Test
    fun `every event it writes belongs to the phase it just opened`() = runBlocking {
        val store = FakeStore()
        val phase = coordinator(store).ensureCurrentPhase(now = 1_000L)
        assertTrue(store.events.all { it.studyPhaseId == phase.id })
        assertTrue(store.events.all { it.localDate == "1970-01-01" })
        assertTrue(store.events.all { !it.kind.isSelfReported })
        assertTrue(store.events.all { it.note.isEmpty() })
    }

    @Test
    fun `an unchanged vector writes nothing on the second call`() = runBlocking {
        val store = FakeStore()
        val first = coordinator(store).ensureCurrentPhase(now = 1_000L)
        val eventCount = store.events.size

        val second = coordinator(store).ensureCurrentPhase(now = 2_000L)

        assertEquals(first, second)
        assertEquals(1, store.phases.size)
        assertEquals(eventCount, store.events.size)
    }

    @Test
    fun `an app version change opens a phase and records what moved`() = runBlocking {
        val store = FakeStore()
        coordinator(store).ensureCurrentPhase(now = 1_000L)
        val before = store.events.size
        store.vector = store.vector.copy(appVersionCode = 96, appVersionName = "0.72.0")

        val phase = coordinator(store).ensureCurrentPhase(now = 2_000L)

        assertEquals(1, phase.ordinal)
        assertEquals(StudyPhaseReason.APP_VERSION_CHANGE, phase.reason)
        assertEquals(
            listOf(LedgerEventKind.STUDY_PHASE_STARTED, LedgerEventKind.APP_VERSION_CHANGE),
            kinds(store).drop(before),
        )
        assertEquals(
            """{"from":"0.71.0 (95)","to":"0.72.0 (96)"}""",
            store.events.last().payloadJson,
        )
        assertEquals(LedgerIntegrity.VERIFIED, LedgerChain.verify(store.events))
    }

    @Test
    fun `a device change opens a phase and records what moved`() = runBlocking {
        val store = FakeStore()
        coordinator(store).ensureCurrentPhase(now = 1_000L)
        val before = store.events.size
        store.vector = store.vector.copy(sourceDeviceId = "device-b")

        val phase = coordinator(store).ensureCurrentPhase(now = 2_000L)

        assertEquals(StudyPhaseReason.DEVICE_CHANGE, phase.reason)
        assertEquals(
            listOf(LedgerEventKind.STUDY_PHASE_STARTED, LedgerEventKind.DEVICE_CHANGE),
            kinds(store).drop(before),
        )
        assertEquals("""{"from":"device-a","to":"device-b"}""", store.events.last().payloadJson)
    }

    @Test
    fun `a backwards clock cannot open a phase before the one it succeeds`() = runBlocking {
        val store = FakeStore()
        val first = coordinator(store).ensureCurrentPhase(now = 1_700_000_000_000L)
        store.vector = store.vector.copy(appVersionCode = 96)

        val second = coordinator(store).ensureCurrentPhase(now = 1_000_000_000L)

        assertEquals(first.startedAt + 1, second.startedAt)
        // The two authorities on "which phase" must agree; before the
        // clamp, phaseAt would have answered with the older phase forever.
        assertEquals(second, StudyPhaseDecision.phaseAt(store.phases, 1_700_000_000_500L))
        // The raw clock reading is kept in the phase-start payload, so
        // the jump stays visible rather than being erased by the clamp.
        assertTrue(
            store.events.last { it.kind == LedgerEventKind.STUDY_PHASE_STARTED }
                .payloadJson.contains(""""clockMillis":1000000000"""),
        )
    }

    @Test
    fun `an already registered protocol version is not registered twice`() = runBlocking {
        val store = FakeStore()
        coordinator(store).ensureCurrentPhase(now = 1_000L)
        val registrations = store.events.count { it.kind == LedgerEventKind.PROTOCOL_VERSION_REGISTERED }
        store.vector = store.vector.copy(appVersionCode = 96)

        coordinator(store).ensureCurrentPhase(now = 2_000L)

        assertEquals(
            registrations,
            store.events.count { it.kind == LedgerEventKind.PROTOCOL_VERSION_REGISTERED },
        )
    }

    @Test
    fun `a genuinely new protocol version registers while the old one does not`() = runBlocking {
        val store = FakeStore()
        val first = EvidenceProtocolCatalog.registry.protocols.single()
        val second = first.copy(version = 2, maxDurationSeconds = first.maxDurationSeconds + 1)

        coordinator(store) { listOf(first) }.ensureCurrentPhase(now = 1_000L)
        val before = store.events.size
        store.vector = store.vector.copy(protocolCatalogSha256 = "a catalogue with two versions")

        coordinator(store) { listOf(first, second) }.ensureCurrentPhase(now = 2_000L)

        val added = store.events.drop(before)
        assertEquals(
            listOf(LedgerEventKind.STUDY_PHASE_STARTED, LedgerEventKind.PROTOCOL_VERSION_REGISTERED),
            added.map { it.kind },
        )
        assertTrue(added.last().payloadJson.contains(""""version":2"""))
        assertTrue(added.last().payloadJson.contains(EvidenceProtocolRegistry.definitionSha256(second)))
    }

    @Test
    fun `a registration payload carries the definition hash it registered`() = runBlocking {
        val store = FakeStore()
        coordinator(store).ensureCurrentPhase(now = 1_000L)
        val protocol = EvidenceProtocolCatalog.registry.protocols.single()
        assertEquals(
            """{"protocolId":"${protocol.id}","version":${protocol.version},""" +
                """"definitionSha256":"${EvidenceProtocolRegistry.definitionSha256(protocol)}"}""",
            store.events.last { it.kind == LedgerEventKind.PROTOCOL_VERSION_REGISTERED }.payloadJson,
        )
    }

    @Test
    fun `a payload that gained a field does not re-register the catalogue`() = runBlocking {
        val store = FakeStore()
        coordinator(store).ensureCurrentPhase(now = 1_000L)
        val registration = store.events.last { it.kind == LedgerEventKind.PROTOCOL_VERSION_REGISTERED }
        // Simulate a future build that added a field to the payload: the
        // semantic key is unchanged, so nothing re-registers.
        val widened = registration.payloadJson.dropLast(1) + ""","registeredBy":"a later build"}"""
        store.events[store.events.indexOf(registration)] = registration.copy(payloadJson = widened)
        val before = store.events.size
        store.vector = store.vector.copy(appVersionCode = 96)

        coordinator(store).ensureCurrentPhase(now = 2_000L)

        assertEquals(
            listOf(LedgerEventKind.STUDY_PHASE_STARTED, LedgerEventKind.APP_VERSION_CHANGE),
            kinds(store).drop(before),
        )
    }

    @Test
    fun `the chain continues from a ledger restored on another phone`() = runBlocking {
        val store = FakeStore()
        val restored = LedgerChain.link(
            UnlinkedLedgerEvent(
                sequence = 1L,
                kind = LedgerEventKind.EXERCISE,
                occurredAt = 500L,
                recordedAt = 500L,
                localDate = "1970-01-01",
                studyPhaseId = "phase-from-the-old-phone",
                sourceDeviceId = "device-old",
                note = "a run recorded before the phone was replaced",
                payloadJson = "{}",
            ),
            LedgerChain.GENESIS_PREVIOUS_HASH,
        )
        store.events += restored

        coordinator(store).ensureCurrentPhase(now = 1_000L)

        assertEquals(2L, store.events[1].sequence)
        assertEquals(restored.eventHash, store.events[1].previousEventHash)
        assertEquals(LedgerIntegrity.VERIFIED, LedgerChain.verify(store.events))
    }
}
