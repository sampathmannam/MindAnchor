package org.mindanchor.research

import java.lang.reflect.Modifier
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Program 1 Task 4 — the research ledger's immutability is a property a
 * reader can check, not a claim they have to accept. Every test here
 * either proves the chain links deterministically or proves a specific
 * kind of tampering is detected — including the one kind that is *not*
 * detectable without an anchor, which is asserted explicitly so the limit
 * stays documented rather than discovered.
 */
@OptIn(ExperimentalSerializationApi::class)
class LedgerChainTest {

    private val base = UnlinkedLedgerEvent(
        sequence = 1L,
        kind = LedgerEventKind.EXERCISE,
        occurredAt = 1_000L,
        recordedAt = 1_050L,
        localDate = "2026-08-29",
        studyPhaseId = "phase-0",
        sourceDeviceId = "device-a",
        note = "morning run",
        payloadJson = "{}",
    )

    /** Program 1's canonical ledger field order. This list is wire format; it does not change. */
    private val canonicalFields = listOf(
        "sequence",
        "kind",
        "occurredAt",
        "recordedAt",
        "localDate",
        "studyPhaseId",
        "sourceDeviceId",
        "note",
        "payloadJson",
        "previousEventHash",
    )

    private fun chainOf(count: Int): List<ResearchLedgerEvent> {
        val events = mutableListOf<ResearchLedgerEvent>()
        repeat(count) { index ->
            events += LedgerChain.link(
                base.copy(sequence = index + 1L, occurredAt = 1_000L + index, note = "note $index"),
                LedgerChain.headHash(events),
            )
        }
        return events
    }

    @Test
    fun `the ledger event hash is frozen`() {
        assertEquals(
            "8776708ede27c3b98cda032f4b7f4426e378bc62b3a93b3b98dea9bad8e1dc49",
            LedgerChain.link(base, LedgerChain.GENESIS_PREVIOUS_HASH).eventHash,
        )
    }

    @Test
    fun `the canonical form serialises exactly the frozen field order`() {
        assertEquals(canonicalFields, serializer<LedgerCanonicalEvent>().descriptor.elementNames.toList())
    }

    @Test
    fun `linking is deterministic`() {
        assertEquals(LedgerChain.link(base, "abc").eventHash, LedgerChain.link(base, "abc").eventHash)
    }

    @Test
    fun `the event id is the event hash`() {
        val linked = LedgerChain.link(base, LedgerChain.GENESIS_PREVIOUS_HASH)
        assertEquals(linked.eventHash, linked.id)
    }

    @Test
    fun `every field of the event changes the hash`() {
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
    fun `the note and the payload cannot be confused for each other`() {
        assertNotEquals(
            LedgerChain.link(base.copy(note = "a", payloadJson = "bc"), "abc").eventHash,
            LedgerChain.link(base.copy(note = "ab", payloadJson = "c"), "abc").eventHash,
        )
    }

    @Test
    fun `the previous hash changes the event hash`() {
        assertNotEquals(
            LedgerChain.link(base, LedgerChain.GENESIS_PREVIOUS_HASH).eventHash,
            LedgerChain.link(base, "abc").eventHash,
        )
    }

    @Test
    fun `an out of range sequence is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            LedgerChain.link(base.copy(sequence = 0L), LedgerChain.GENESIS_PREVIOUS_HASH)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LedgerChain.link(base.copy(sequence = -1L), LedgerChain.GENESIS_PREVIOUS_HASH)
        }
    }

    @Test
    fun `an over long note is refused`() {
        assertEquals(
            MAX_LEDGER_NOTE_LENGTH,
            LedgerChain.link(
                base.copy(note = "x".repeat(MAX_LEDGER_NOTE_LENGTH)),
                LedgerChain.GENESIS_PREVIOUS_HASH,
            ).note.length,
        )
        assertThrows(IllegalArgumentException::class.java) {
            LedgerChain.link(
                base.copy(note = "x".repeat(MAX_LEDGER_NOTE_LENGTH + 1)),
                LedgerChain.GENESIS_PREVIOUS_HASH,
            )
        }
    }

    @Test
    fun `an empty chain is vacuously intact`() {
        assertEquals(LedgerIntegrity.VERIFIED, LedgerChain.verify(emptyList()))
        assertEquals("", LedgerChain.headHash(emptyList()))
        assertEquals(1L, LedgerChain.nextSequence(emptyList()))
        assertEquals(LedgerAnchor("", 0), LedgerChain.anchorOf(emptyList()))
    }

    @Test
    fun `a well formed chain verifies`() {
        val chain = chainOf(3)
        assertEquals(LedgerIntegrity.VERIFIED, LedgerChain.verify(chain))
        assertEquals(LedgerIntegrity.VERIFIED, LedgerChain.verify(chain, LedgerChain.anchorOf(chain)))
        assertEquals(chain.last().eventHash, LedgerChain.headHash(chain))
        assertEquals(4L, LedgerChain.nextSequence(chain))
        assertEquals(3, LedgerChain.anchorOf(chain).eventCount)
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
    fun `deleting an interior event is detected`() {
        val chain = chainOf(3)
        assertEquals(LedgerIntegrity.BROKEN, LedgerChain.verify(listOf(chain[0], chain[2])))
    }

    @Test
    fun `truncating the newest events needs an anchor to detect`() {
        val chain = chainOf(3)
        val truncated = chain.dropLast(1)
        // The documented limit: what is left is a shorter but perfectly
        // self-consistent chain, so the chain alone cannot see the loss.
        assertEquals(LedgerIntegrity.VERIFIED, LedgerChain.verify(truncated))
        // With the anchor recorded before the truncation, it is obvious.
        assertEquals(
            LedgerIntegrity.BROKEN,
            LedgerChain.verify(truncated, LedgerChain.anchorOf(chain)),
        )
        assertEquals(
            LedgerIntegrity.BROKEN,
            LedgerChain.verify(emptyList(), LedgerChain.anchorOf(chain)),
        )
    }

    @Test
    fun `an anchor with the right count but the wrong head is detected`() {
        val chain = chainOf(2)
        assertEquals(
            LedgerIntegrity.BROKEN,
            LedgerChain.verify(chain, LedgerAnchor(headHash = "someone-elses-head", eventCount = 2)),
        )
    }

    @Test
    fun `a chain that does not start at one is detected`() {
        val chain = chainOf(3)
        assertEquals(LedgerIntegrity.BROKEN, LedgerChain.verify(chain.drop(1)))
    }

    @Test
    fun `two different events at the same sequence are detected`() {
        val chain = chainOf(2)
        val fork = LedgerChain.link(
            base.copy(sequence = 2L, occurredAt = 9_999L, note = "a forked event"),
            chain[0].eventHash,
        )
        assertNotEquals(chain[1].id, fork.id)
        assertEquals(LedgerIntegrity.BROKEN, LedgerChain.verify(listOf(chain[0], chain[1], fork)))
    }

    @Test
    fun `a non empty genesis previous hash is detected`() {
        val forged = LedgerChain.link(base, "not-genesis")
        assertEquals(LedgerIntegrity.BROKEN, LedgerChain.verify(listOf(forged)))
    }

    @Test
    fun `a broken link between two intact events is detected`() {
        val chain = chainOf(2).toMutableList()
        val relinked = LedgerChain.link(
            base.copy(sequence = 2L, occurredAt = 1_001L, note = "note 1"),
            "a-different-previous-hash",
        )
        // The premise: same event contents, different predecessor. If
        // chainOf ever changes shape this assertion fails rather than the
        // test silently degrading into a weaker one.
        assertEquals(chain[1].unlinked(), relinked.unlinked())
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
}
