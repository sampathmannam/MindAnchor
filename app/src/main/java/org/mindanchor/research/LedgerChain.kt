package org.mindanchor.research

import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Whether a ledger's chain is intact.
 *
 * [NOT_APPLICABLE] is for a document that carries **no ledger field at
 * all** — a research export written by a build that had none (Program 0's
 * `mindanchor-research-v1`). It is not the same as a ledger field holding
 * zero events, which is [VERIFIED]: an empty chain that matches its anchor
 * has genuinely been checked.
 */
enum class LedgerIntegrity { VERIFIED, BROKEN, NOT_APPLICABLE }

/**
 * Where a ledger is expected to end: the head event's hash and how many
 * events precede it.
 *
 * This is the part of the chain that cannot live *in* the chain. Without
 * it, removing the newest events leaves a shorter but perfectly
 * self-consistent chain — see [LedgerChain.verify].
 */
data class LedgerAnchor(val headHash: String, val eventCount: Int)

/**
 * Links research ledger events into a hash chain and verifies one.
 *
 * The ledger is the object a future research report rests on, so
 * "append-only" should be something a reader can check rather than
 * something they have to believe. Event *n*'s hash covers event *n−1*'s
 * hash, so editing, deleting, reordering, or inserting an event anywhere
 * except at the very end breaks every event after it.
 *
 * ## What this does and does not prove
 *
 * Honestly scoped, because a research provenance claim that overstates
 * itself is worse than none:
 *
 *  - **Detected without an anchor:** any edit, deletion, reordering, or
 *    insertion in the interior of the chain, and any accidental
 *    corruption.
 *  - **Detected only against a count recorded elsewhere:** truncation of
 *    the newest events. Drop the last *k* rows and what remains is a valid
 *    chain of length *n−k*; only a count kept somewhere other than the
 *    ledger catches that. `ContinuityPrefs` keeps one as a high-water
 *    mark, and the research export carries it as `ledgerHighWaterCount`
 *    so a recipient can make the same comparison. [LedgerAnchor] and the
 *    two-argument [verify] express the same check against a full expected
 *    head; nothing in the app calls them yet.
 *  - **Not detected at all:** somebody who holds the whole file and
 *    re-links every event from scratch. A hash chain with no externally
 *    published head cannot defend against its own custodian. If a
 *    recipient wants that guarantee, they record the head hash at
 *    handover; the chain then tells them whether the file they hold is
 *    the file they were given.
 *
 * ## Appending is a read-modify-write
 *
 * [headHash] then [link] then insert is not atomic on its own. Two
 * concurrent appends that both read the same head will either produce two
 * rows at the same sequence (permanently `BROKEN`, and the tables are
 * append-only so it cannot be repaired) or, for byte-identical content,
 * one row silently dropped by `INSERT OR IGNORE`. Callers must serialise
 * the whole sequence — `ResearchLedgerRepository` does it inside a single
 * Room transaction.
 */
object LedgerChain {

    /** What [ResearchLedgerEvent.previousEventHash] holds at sequence 1. */
    const val GENESIS_PREVIOUS_HASH = ""

    private const val FIRST_SEQUENCE = 1L

    /**
     * Pinned: this configuration is part of every event hash ever written,
     * and the tables are append-only, so a digest that stops reproducing
     * cannot be repaired. `LedgerChainTest` freezes both the digest of a
     * fixture and [LedgerCanonicalEvent]'s serialised element names.
     */
    private val json = Json {
        encodeDefaults = true
        prettyPrint = false
        explicitNulls = true
    }

    /**
     * Computes [event]'s hash over its own contents plus [previousEventHash].
     *
     * @throws IllegalArgumentException if the sequence is below
     *   [FIRST_SEQUENCE] or the note exceeds [MAX_LEDGER_NOTE_LENGTH].
     *   Both are checked here rather than at the call site because a row
     *   violating either can never be deleted once written.
     */
    fun link(event: UnlinkedLedgerEvent, previousEventHash: String): ResearchLedgerEvent {
        require(event.sequence >= FIRST_SEQUENCE) {
            "ledger sequence must start at $FIRST_SEQUENCE, was ${event.sequence}"
        }
        require(event.note.length <= MAX_LEDGER_NOTE_LENGTH) {
            "ledger note must be at most $MAX_LEDGER_NOTE_LENGTH characters, was ${event.note.length}"
        }
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
     * Checks [events] as a whole, against [expected] when one is known.
     * Sorts by sequence first, so a caller may pass rows in any order.
     *
     * A chain is [LedgerIntegrity.VERIFIED] only when all of these hold:
     * sequences run 1..n with no gap and no duplicate; the first event's
     * previous hash is [GENESIS_PREVIOUS_HASH]; each later event's previous
     * hash is its predecessor's hash; every event's stored hash and id are
     * what re-linking its own contents produces; and, if [expected] is
     * given, the head hash and event count match it.
     *
     * Pass [expected] wherever an anchor exists. Without one, tail
     * truncation is undetectable — see this object's KDoc.
     */
    fun verify(events: List<ResearchLedgerEvent>, expected: LedgerAnchor? = null): LedgerIntegrity {
        val ordered = events.sortedBy { it.sequence }
        // A duplicate sequence is caught by the contiguity check below: a
        // sorted list containing a repeat cannot equal a strictly
        // increasing range. That is also why sort stability is irrelevant.
        val intact = sequencesAreContiguous(ordered) && hashesLinkUp(ordered)
        val anchored = expected == null ||
            (expected.headHash == headHash(ordered) && expected.eventCount == ordered.size)
        return if (intact && anchored) LedgerIntegrity.VERIFIED else LedgerIntegrity.BROKEN
    }

    /** The highest-sequence event's hash, or [GENESIS_PREVIOUS_HASH] for an empty ledger. */
    fun headHash(events: List<ResearchLedgerEvent>): String =
        events.maxByOrNull { it.sequence }?.eventHash ?: GENESIS_PREVIOUS_HASH

    /** The anchor describing [events] as they stand. */
    fun anchorOf(events: List<ResearchLedgerEvent>): LedgerAnchor =
        LedgerAnchor(headHash = headHash(events), eventCount = events.size)

    /**
     * The sequence number the next appended event takes.
     *
     * @throws IllegalStateException rather than wrapping to
     *   [Long.MIN_VALUE] if the ledger somehow reached [Long.MAX_VALUE] —
     *   an unrepairable table is no place for silent overflow.
     */
    fun nextSequence(events: List<ResearchLedgerEvent>): Long {
        val highest = events.maxOfOrNull { it.sequence } ?: 0L
        check(highest < Long.MAX_VALUE) { "ledger sequence exhausted" }
        return highest + 1L
    }

    private fun sequencesAreContiguous(ordered: List<ResearchLedgerEvent>): Boolean =
        ordered.map { it.sequence } == (FIRST_SEQUENCE..ordered.size.toLong()).toList()

    private fun hashesLinkUp(ordered: List<ResearchLedgerEvent>): Boolean {
        var expectedPrevious = GENESIS_PREVIOUS_HASH
        ordered.forEach { event ->
            val recomputed = hashOf(event.unlinked(), event.previousEventHash)
            val linked = event.previousEventHash == expectedPrevious &&
                event.eventHash == recomputed &&
                event.id == recomputed
            if (!linked) return false
            expectedPrevious = event.eventHash
        }
        return true
    }

    private fun hashOf(event: UnlinkedLedgerEvent, previousEventHash: String): String {
        val canonical = LedgerCanonicalEvent(
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
}

/**
 * The exact bytes a ledger event hashes over.
 *
 * Three things here are wire format and may not change without
 * invalidating every ledger already written, permanently, because the
 * tables are append-only and cannot be repaired:
 *
 *  1. **Field order.** kotlinx.serialization writes keys in declaration
 *     order.
 *  2. **[LedgerEventKind] constant names.** [kind] hashes the name, not
 *     the ordinal — renaming a case rewrites history's digests.
 *  3. **The JSON encoder configuration**, pinned in `LedgerChain`.
 *
 * `LedgerChainTest` freezes all three: a golden digest, an assertion on
 * this class's serialised element names, and an assertion on the exact
 * ordered list of kind names.
 *
 * `internal` rather than private so that test can read the shape directly
 * instead of inferring it from a digest it also generated.
 *
 * One known limit on injectivity: `String.encodeToByteArray()` maps an
 * unpaired surrogate to the replacement character, so two notes differing
 * only in an unpaired surrogate hash alike. Unreachable from keyboard
 * input; noted because the id is also a primary key.
 */
@Serializable
internal data class LedgerCanonicalEvent(
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
