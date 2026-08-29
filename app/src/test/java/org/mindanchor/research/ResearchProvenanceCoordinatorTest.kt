package org.mindanchor.research

import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Program 1 Task 6 — the coordinator opens a study phase when the
 * provenance vector changes and records the change in the ledger, with no
 * Room, no Context and no Robolectric: every collaborator is a narrow
 * suspend lambda, the same seam `RestoreCoordinator` uses.
 */
class ResearchProvenanceCoordinatorTest {

    private class FakeStore {
        val phases = mutableListOf<StudyPhase>()
        val events = mutableListOf<ResearchLedgerEvent>()
        var vector: ProvenanceVector = ProvenanceVersions.vector(95, "0.71.0", "device-a")
    }

    private fun coordinator(store: FakeStore) = ResearchProvenanceCoordinator(
        latestPhase = { store.phases.maxByOrNull { it.ordinal } },
        insertPhase = { store.phases += it },
        ledgerHead = { store.events.maxByOrNull { it.sequence } },
        registeredProtocolKeys = {
            store.events
                .filter { it.kind == LedgerEventKind.PROTOCOL_VERSION_REGISTERED }
                .map { it.payloadJson }
                .toSet()
        },
        appendEvents = { store.events += it },
        currentVector = { store.vector },
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
    fun `the events it writes form a valid chain from genesis`() = runBlocking {
        val store = FakeStore()
        coordinator(store).ensureCurrentPhase(now = 1_000L)

        assertEquals(LedgerIntegrity.VERIFIED, LedgerChain.verify(store.events, LedgerChain.anchorOf(store.events)))
        assertEquals(1L, store.events.first().sequence)
        assertEquals(LedgerChain.GENESIS_PREVIOUS_HASH, store.events.first().previousEventHash)
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
    fun `an app version change opens a phase and records the change`() = runBlocking {
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
        assertEquals(LedgerIntegrity.VERIFIED, LedgerChain.verify(store.events))
    }

    @Test
    fun `a device change opens a phase and records the change`() = runBlocking {
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
        assertTrue(store.events.last().payloadJson.contains("device-b"))
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
    fun `a catalogue change registers only the newly seen versions`() = runBlocking {
        val store = FakeStore()
        coordinator(store).ensureCurrentPhase(now = 1_000L)
        val before = store.events.size
        store.vector = store.vector.copy(protocolCatalogSha256 = "a-different-catalogue")

        coordinator(store).ensureCurrentPhase(now = 2_000L)

        // The catalogue object itself is unchanged in this test, so only
        // the phase start is new: nothing re-registers what is already there.
        assertEquals(listOf(LedgerEventKind.STUDY_PHASE_STARTED), kinds(store).drop(before))
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

    @Test
    fun `a protocol registration records the definition hash it registered`() = runBlocking {
        val store = FakeStore()
        coordinator(store).ensureCurrentPhase(now = 1_000L)
        val registration = store.events.last { it.kind == LedgerEventKind.PROTOCOL_VERSION_REGISTERED }
        val protocol = EvidenceProtocolCatalog.registry.protocols.last()
        assertTrue(registration.payloadJson.contains(protocol.id))
        assertTrue(
            registration.payloadJson.contains(EvidenceProtocolRegistry.definitionSha256(protocol)),
        )
    }
}
