package org.mindanchor.research

import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Whether a ledger's chain is intact.
 *
 * [NOT_APPLICABLE] exists for a research export written by a build that
 * had no ledger at all (Program 0's `mindanchor-research-v1`). Reporting
 * `BROKEN` there would be wrong, and reporting `VERIFIED` would claim a
 * check that never ran — an absent ledger is neither.
 */
enum class LedgerIntegrity { VERIFIED, BROKEN, NOT_APPLICABLE }

/**
 * Links research ledger events into a hash chain and verifies one.
 *
 * The ledger is the object a future research report rests on, so
 * "append-only" needs to be something a reader can check rather than
 * something they have to believe. Event *n*'s hash covers event *n−1*'s
 * hash, so any edit, deletion, reordering, or insertion anywhere in the
 * history invalidates every event after it — including in a file handed
 * to somebody outside this device, where the database triggers and the
 * insert-only DAO offer no protection at all.
 *
 * This is tamper *evidence*, not tamper prevention, and deliberately so: a
 * keyed MAC would need key management and would still not stop the holder
 * of the key. Detection is the property that matters for research
 * provenance.
 */
object LedgerChain {

    /** What [ResearchLedgerEvent.previousEventHash] holds at sequence 1. */
    const val GENESIS_PREVIOUS_HASH = ""

    private const val FIRST_SEQUENCE = 1L

    private val json = Json { encodeDefaults = true }

    /** Computes [event]'s hash over its own contents plus [previousEventHash]. */
    fun link(event: UnlinkedLedgerEvent, previousEventHash: String): ResearchLedgerEvent {
        val hash = hashOf(event, previousEventHash)
        return ResearchLedgerEvent(
            id = hash,
            sequence = event.sequence,
            kind = event.kind,
            occurredAt = event.occurredAt,
            recordedAt = event.recordedAt,
            localDate = event.localDate,
            studyPhaseId = event.studyPhaseId,
            sourceDeviceId = event.sourceDeviceId,
            note = event.note,
            payloadJson = event.payloadJson,
            previousEventHash = previousEventHash,
            eventHash = hash,
        )
    }

    /**
     * Checks [events] as a whole. Sorts by sequence first, so a caller may
     * pass rows in any order.
     *
     * A chain is [LedgerIntegrity.VERIFIED] only when every one of these
     * holds: sequences run 1..n with no gap and no duplicate; the first
     * event's previous hash is [GENESIS_PREVIOUS_HASH]; each later event's
     * previous hash is its predecessor's hash; and every event's stored
     * hash and id are what re-linking its own contents produces.
     */
    fun verify(events: List<ResearchLedgerEvent>): LedgerIntegrity {
        if (events.isEmpty()) return LedgerIntegrity.VERIFIED
        val ordered = events.sortedBy { it.sequence }
        if (ordered.map { it.sequence } != (FIRST_SEQUENCE..ordered.size.toLong()).toList()) {
            return LedgerIntegrity.BROKEN
        }
        var expectedPrevious = GENESIS_PREVIOUS_HASH
        ordered.forEach { event ->
            if (event.previousEventHash != expectedPrevious) return LedgerIntegrity.BROKEN
            val recomputed = hashOf(event.unlinked(), event.previousEventHash)
            if (event.eventHash != recomputed || event.id != recomputed) return LedgerIntegrity.BROKEN
            expectedPrevious = event.eventHash
        }
        return LedgerIntegrity.VERIFIED
    }

    /** The highest-sequence event's hash, or [GENESIS_PREVIOUS_HASH] for an empty ledger. */
    fun headHash(events: List<ResearchLedgerEvent>): String =
        events.maxByOrNull { it.sequence }?.eventHash ?: GENESIS_PREVIOUS_HASH

    /** The sequence number the next appended event takes. */
    fun nextSequence(events: List<ResearchLedgerEvent>): Long =
        (events.maxOfOrNull { it.sequence } ?: 0L) + 1L

    private fun hashOf(event: UnlinkedLedgerEvent, previousEventHash: String): String {
        val canonical = CanonicalEvent(
            sequence = event.sequence,
            kind = event.kind.name,
            occurredAt = event.occurredAt,
            recordedAt = event.recordedAt,
            localDate = event.localDate,
            studyPhaseId = event.studyPhaseId,
            sourceDeviceId = event.sourceDeviceId,
            note = event.note,
            payloadJson = event.payloadJson,
            previousEventHash = previousEventHash,
        )
        return MessageDigest.getInstance("SHA-256")
            .digest(json.encodeToString(canonical).encodeToByteArray())
            .joinToString(separator = "") { "%02x".format(it) }
    }

    /**
     * The exact bytes a ledger event hashes over. Field order is part of
     * the wire format — kotlinx.serialization writes keys in declaration
     * order — so nothing here may be added, removed, or reordered without
     * invalidating every ledger already written.
     */
    @Serializable
    private data class CanonicalEvent(
        val sequence: Long,
        val kind: String,
        val occurredAt: Long,
        val recordedAt: Long,
        val localDate: String,
        val studyPhaseId: String,
        val sourceDeviceId: String,
        val note: String,
        val payloadJson: String,
        val previousEventHash: String,
    )
}
