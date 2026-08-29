package org.mindanchor.research

import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Program 1 Task 4 — the research ledger's immutability is a property a
 * reader can check, not a claim they have to accept. Every test here
 * either proves the chain links deterministically or proves a specific
 * kind of tampering is detected.
 */
class LedgerChainTest {

    private fun unlinked(
        sequence: Long = 1L,
        kind: LedgerEventKind = LedgerEventKind.EXERCISE,
        occurredAt: Long = 1_000L,
        recordedAt: Long = 1_050L,
        localDate: String = "2026-08-29",
        studyPhaseId: String = "phase-0",
        sourceDeviceId: String = "device-a",
        note: String = "morning run",
        payloadJson: String = "{}",
    ) = UnlinkedLedgerEvent(
        sequence = sequence,
        kind = kind,
        occurredAt = occurredAt,
        recordedAt = recordedAt,
        localDate = localDate,
        studyPhaseId = studyPhaseId,
        sourceDeviceId = sourceDeviceId,
        note = note,
        payloadJson = payloadJson,
    )

    private fun chainOf(count: Int): List<ResearchLedgerEvent> {
        val events = mutableListOf<ResearchLedgerEvent>()
        repeat(count) { index ->
            events += LedgerChain.link(
                unlinked(sequence = index + 1L, occurredAt = 1_000L + index, note = "note $index"),
                LedgerChain.headHash(events),
            )
        }
        return events
    }

    @Test
    fun `linking is deterministic`() {
        val event = unlinked()
        assertEquals(
            LedgerChain.link(event, "abc").eventHash,
            LedgerChain.link(event, "abc").eventHash,
        )
    }

    @Test
    fun `the event id is the event hash`() {
        val linked = LedgerChain.link(unlinked(), LedgerChain.GENESIS_PREVIOUS_HASH)
        assertEquals(linked.eventHash, linked.id)
    }

    @Test
    fun `every field of the event changes the hash`() {
        val base = unlinked()
        val mutations: List<Pair<String, UnlinkedLedgerEvent>> = listOf(
            "sequence" to base.copy(sequence = 2L),
            "kind" to base.copy(kind = LedgerEventKind.ILLNESS),
            "occurredAt" to base.copy(occurredAt = 1_001L),
            "recordedAt" to base.copy(recordedAt = 1_051L),
            "localDate" to base.copy(localDate = "2026-08-30"),
            "studyPhaseId" to base.copy(studyPhaseId = "phase-1"),
            "sourceDeviceId" to base.copy(sourceDeviceId = "device-b"),
            "note" to base.copy(note = "morning run "),
            "payloadJson" to base.copy(payloadJson = """{"a":1}"""),
        )
        assertEquals(
            "every declared field must be covered by a mutation",
            UnlinkedLedgerEvent::class.java.declaredFields.count {
                !it.isSynthetic && !Modifier.isStatic(it.modifiers)
            },
            mutations.size,
        )
        mutations.forEach { (field, mutated) ->
            assertNotEquals(
                "changing $field must change the event hash",
                LedgerChain.link(base, "abc").eventHash,
                LedgerChain.link(mutated, "abc").eventHash,
            )
        }
    }

    @Test
    fun `the previous hash changes the event hash`() {
        val base = unlinked()
        assertNotEquals(
            LedgerChain.link(base, LedgerChain.GENESIS_PREVIOUS_HASH).eventHash,
            LedgerChain.link(base, "abc").eventHash,
        )
    }

    @Test
    fun `an empty chain is vacuously intact`() {
        assertEquals(LedgerIntegrity.VERIFIED, LedgerChain.verify(emptyList()))
        assertEquals("", LedgerChain.headHash(emptyList()))
        assertEquals(1L, LedgerChain.nextSequence(emptyList()))
    }

    @Test
    fun `a well formed chain verifies`() {
        val chain = chainOf(3)
        assertEquals(LedgerIntegrity.VERIFIED, LedgerChain.verify(chain))
        assertEquals(chain.last().eventHash, LedgerChain.headHash(chain))
        assertEquals(4L, LedgerChain.nextSequence(chain))
    }

    @Test
    fun `input order does not matter`() {
        val chain = chainOf(3)
        assertEquals(LedgerIntegrity.VERIFIED, LedgerChain.verify(chain.reversed()))
        assertEquals(chain.last().eventHash, LedgerChain.headHash(chain.reversed()))
    }

    @Test
    fun `editing an event without relinking is detected`() {
        val chain = chainOf(3).toMutableList()
        chain[1] = chain[1].copy(note = "edited after the fact")
        assertEquals(LedgerIntegrity.BROKEN, LedgerChain.verify(chain))
    }

    @Test
    fun `swapping an event id is detected`() {
        val chain = chainOf(2).toMutableList()
        chain[1] = chain[1].copy(id = "not-the-hash")
        assertEquals(LedgerIntegrity.BROKEN, LedgerChain.verify(chain))
    }

    @Test
    fun `deleting an event is detected`() {
        val chain = chainOf(3)
        assertEquals(LedgerIntegrity.BROKEN, LedgerChain.verify(listOf(chain[0], chain[2])))
    }

    @Test
    fun `a chain that does not start at one is detected`() {
        val chain = chainOf(3)
        assertEquals(LedgerIntegrity.BROKEN, LedgerChain.verify(chain.drop(1)))
    }

    @Test
    fun `a duplicated sequence number is detected`() {
        val chain = chainOf(2)
        assertEquals(LedgerIntegrity.BROKEN, LedgerChain.verify(listOf(chain[0], chain[0])))
    }

    @Test
    fun `a non empty genesis previous hash is detected`() {
        val forged = LedgerChain.link(unlinked(), "not-genesis")
        assertEquals(LedgerIntegrity.BROKEN, LedgerChain.verify(listOf(forged)))
    }

    @Test
    fun `a broken link between two intact events is detected`() {
        val chain = chainOf(2).toMutableList()
        val relinked = LedgerChain.link(
            unlinked(sequence = 2L, occurredAt = 1_001L, note = "note 1"),
            "a-different-previous-hash",
        )
        chain[1] = relinked
        assertEquals(LedgerIntegrity.BROKEN, LedgerChain.verify(chain))
    }

    @Test
    fun `the ledger records every kind the design names`() {
        assertEquals(
            listOf(
                "SHIFT_SCHEDULE", "EXERCISE", "ILLNESS", "CAFFEINE", "MEDICATION_CHANGE",
                "LIFE_EVENT", "ADVERSE_OR_UNINTENDED_EFFECT",
                "STUDY_PHASE_STARTED", "PROTOCOL_VERSION_REGISTERED", "APP_VERSION_CHANGE",
                "RULE_VERSION_CHANGE", "MODEL_VERSION_CHANGE", "TRANSFORMATION_VERSION_CHANGE",
                "MISSING_DATA_POLICY_CHANGE", "INSTRUMENT_VERSION_CHANGE", "DICTIONARY_VERSION_CHANGE",
                "DEVICE_CHANGE", "SENSOR_GAP",
            ),
            LedgerEventKind.entries.map { it.name },
        )
    }

    @Test
    fun `exactly the confounder kinds are self reported`() {
        assertEquals(
            setOf(
                LedgerEventKind.SHIFT_SCHEDULE, LedgerEventKind.EXERCISE, LedgerEventKind.ILLNESS,
                LedgerEventKind.CAFFEINE, LedgerEventKind.MEDICATION_CHANGE, LedgerEventKind.LIFE_EVENT,
                LedgerEventKind.ADVERSE_OR_UNINTENDED_EFFECT,
            ),
            LedgerEventKind.entries.filter { it.isSelfReported }.toSet(),
        )
    }

    @Test
    fun `the note cap is stated once`() {
        assertEquals(500, MAX_LEDGER_NOTE_LENGTH)
    }
}
