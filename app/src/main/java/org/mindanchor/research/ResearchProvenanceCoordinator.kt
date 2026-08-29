package org.mindanchor.research

import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Everything [ResearchProvenanceCoordinator] needs from storage.
 *
 * An interface rather than a bag of lambdas for one reason: [inTransaction]
 * has to be able to span every other call, and a set of independent
 * suspend seams cannot express that. `ResearchLedgerRepository` implements
 * it over Room; tests implement it in memory.
 */
interface ResearchProvenanceStore {

    /**
     * Runs [block] inside a single durable transaction. Every read and
     * write [ResearchProvenanceCoordinator.ensureCurrentPhase] performs
     * happens inside it — see that function for why partial completion is
     * unrepairable.
     */
    suspend fun inTransaction(block: suspend () -> StudyPhase): StudyPhase

    suspend fun latestPhase(): StudyPhase?

    suspend fun ledgerHead(): ResearchLedgerEvent?

    /** The raw payloads of every `PROTOCOL_VERSION_REGISTERED` event so far. */
    suspend fun registeredProtocolPayloads(): List<String>

    suspend fun insertPhase(phase: StudyPhase)

    suspend fun appendEvents(events: List<ResearchLedgerEvent>)

    /**
     * Called after a transaction that grew the ledger has committed, so an
     * implementation can refresh whatever it keeps outside the database.
     *
     * A no-op by default because most implementations (the in-memory ones
     * in tests) keep nothing outside it. `ResearchLedgerRepository`
     * overrides it to raise the ledger high-water mark, which is otherwise
     * only raised by the research-log card: a person who journals and
     * takes morning measures but never opens that card would accumulate
     * machine-written provenance events with no mark at all, and so no
     * truncation detection whatsoever.
     *
     * Deliberately *after* commit, not inside: raising the mark is a
     * DataStore write, and holding the SQLite write lock across an fsync
     * is what [ensureCurrentPhase] already goes out of its way to avoid.
     */
    suspend fun afterLedgerGrew() = Unit
}

/**
 * Opens study phases and writes the provenance events that explain them.
 *
 * Storage is one narrow interface, so the whole decision path is testable
 * in memory with no Room, no Context and no Robolectric.
 * `ResearchLedgerRepository.build` wires the real Room DAO.
 *
 * ## Called before a write, never at startup
 *
 * [ensureCurrentPhase] runs immediately before a research record is
 * written, not from `onCreate`. Two reasons, and the second is the
 * load-bearing one:
 *
 *  1. Ordinary offline startup should do no work it does not have to.
 *  2. A replacement phone must have an **empty** ledger when it restores.
 *     Opening phase 0 at startup would write local rows before the
 *     restore, the restore preflight would block, and the re-captured
 *     content hash would no longer match the backup. Opening it lazily
 *     makes the correct order — install, restore, *then* the first local
 *     write appends a `DEVICE_CHANGE` phase onto the restored chain —
 *     happen by construction rather than by careful sequencing.
 */
class ResearchProvenanceCoordinator(
    private val store: ResearchProvenanceStore,
    private val currentVector: suspend () -> ProvenanceVector,
    /**
     * Injected so a test can register a second protocol version against a
     * ledger that already holds the first. Defaults to the build's own
     * catalogue, which is what production always uses.
     */
    private val protocols: () -> List<EvidenceProtocol> = { EvidenceProtocolCatalog.registry.protocols },
    private val localDateOf: (Long) -> String = { millis ->
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toString()
    },
) {

    /**
     * Returns the study phase in effect, opening a new one first if the
     * provenance vector has changed since the last phase.
     *
     * When a phase opens, three kinds of event are appended in order: the
     * [LedgerEventKind.STUDY_PHASE_STARTED] record carrying the whole
     * vector, the typed change event naming what moved (absent for the
     * first phase and for a catalogue change — see [StudyPhaseReason]),
     * and one [LedgerEventKind.PROTOCOL_VERSION_REGISTERED] per catalogued
     * protocol version the ledger has not already recorded.
     *
     * The whole sequence runs inside one transaction, and that is not
     * tidiness. Read-then-write against append-only tables has no repair
     * path: if the phase row committed and the events did not, the next
     * call would see the phase, find the vector unchanged, and return
     * early — leaving a phase the ledger never announced, with every
     * record in its window attributed to it and no way to append the
     * missing event afterwards. The chain would still verify, because a
     * missing event is not a broken link.
     */
    suspend fun ensureCurrentPhase(now: Long): StudyPhase {
        // Read outside the transaction. Building the vector costs a binder
        // call for the package info and a DataStore read for the device id
        // — on a first run that read also writes and fsyncs — and holding
        // the SQLite write lock across all that is the slowest possible
        // path on the slowest possible day. It is read-only input, so
        // reading it early costs the transaction nothing.
        val vector = currentVector()
        return openPhaseIfChanged(now, vector)
    }

    private suspend fun openPhaseIfChanged(now: Long, vector: ProvenanceVector): StudyPhase {
        val before = store.ledgerHead()
        val phase = openPhaseTransaction(now, vector)
        // Only when this actually appended something, so an ordinary
        // no-change call does not spend a DataStore write per journal save.
        if (store.ledgerHead()?.id != before?.id) store.afterLedgerGrew()
        return phase
    }

    private suspend fun openPhaseTransaction(now: Long, vector: ProvenanceVector): StudyPhase = store.inTransaction {
        val current = store.latestPhase()
        val opened = StudyPhaseDecision.next(current, vector, now)
            ?: return@inTransaction requireNotNull(current) {
                "StudyPhaseDecision returned no phase with none current"
            }

        store.insertPhase(opened)

        val head = store.ledgerHead()
        check((head?.sequence ?: 0L) < Long.MAX_VALUE) { "ledger sequence exhausted" }
        var previousHash = head?.eventHash ?: LedgerChain.GENESIS_PREVIOUS_HASH
        var sequence = (head?.sequence ?: 0L) + 1L
        val localDate = localDateOf(now)

        val appended = mutableListOf<ResearchLedgerEvent>()
        fun append(kind: LedgerEventKind, payloadJson: String) {
            val linked = LedgerChain.link(
                UnlinkedLedgerEvent(
                    sequence = sequence,
                    kind = kind,
                    occurredAt = now,
                    recordedAt = now,
                    localDate = localDate,
                    studyPhaseId = opened.id,
                    sourceDeviceId = vector.sourceDeviceId,
                    note = "",
                    payloadJson = payloadJson,
                ),
                previousHash,
            )
            appended += linked
            previousHash = linked.eventHash
            sequence += 1L
        }

        append(
            LedgerEventKind.STUDY_PHASE_STARTED,
            json.encodeToString(PhaseStartedPayload(opened.ordinal, opened.reason.name, now, vector)),
        )
        opened.reason.ledgerKind?.let { kind ->
            val from = current?.let { describe(it.vector, opened.reason) }.orEmpty()
            val to = describe(vector, opened.reason)
            append(kind, json.encodeToString(VersionChangePayload(from, to)))
        }

        registerNewProtocolVersions(::append)

        store.appendEvents(appended)
        opened
    }

    /**
     * Appends one registration per catalogued protocol version the ledger
     * does not already hold.
     *
     * De-duplicates on the semantic key `id@version:definitionSha256`,
     * decoded from each stored payload, rather than on raw payload text.
     * A payload that gained a field, or an encoder that was retuned, would
     * make every stored payload miss a text comparison and re-register the
     * whole catalogue — permanently, in an append-only table, and a reader
     * would reasonably read the duplicate as evidence something changed.
     */
    private suspend fun registerNewProtocolVersions(append: (LedgerEventKind, String) -> Unit) {
        val alreadyRegistered = store.registeredProtocolPayloads()
            .mapNotNull { raw -> runCatching { json.decodeFromString<ProtocolRegisteredPayload>(raw) }.getOrNull() }
            .map { it.key() }
            .toSet()
        protocols().forEach { protocol ->
            val payload = ProtocolRegisteredPayload(
                protocolId = protocol.id,
                version = protocol.version,
                definitionSha256 = EvidenceProtocolRegistry.definitionSha256(protocol),
            )
            if (payload.key() !in alreadyRegistered) {
                append(LedgerEventKind.PROTOCOL_VERSION_REGISTERED, json.encodeToString(payload))
            }
        }
    }

    /**
     * The value of the component [reason] names, so a change event says
     * what actually moved. A `when` over the enum rather than a chain of
     * conditions: a reason added without a branch here fails to compile
     * instead of writing an empty `from`/`to` into a permanent row.
     */
    private fun describe(vector: ProvenanceVector, reason: StudyPhaseReason): String = when (reason) {
        StudyPhaseReason.APP_VERSION_CHANGE -> "${vector.appVersionName} (${vector.appVersionCode})"
        StudyPhaseReason.RULE_VERSION_CHANGE -> vector.ruleSetVersion
        StudyPhaseReason.MODEL_VERSION_CHANGE -> vector.modelSetVersion
        StudyPhaseReason.TRANSFORMATION_VERSION_CHANGE -> vector.transformationSetVersion
        StudyPhaseReason.MISSING_DATA_POLICY_CHANGE -> vector.missingDataPolicyVersion
        StudyPhaseReason.INSTRUMENT_VERSION_CHANGE -> vector.instrumentVersion
        StudyPhaseReason.DICTIONARY_VERSION_CHANGE -> vector.dictionaryVersion
        StudyPhaseReason.DEVICE_CHANGE -> vector.sourceDeviceId
        // Neither reaches here: both carry a null ledgerKind, so no change
        // event is written for them at all.
        StudyPhaseReason.INITIAL, StudyPhaseReason.PROTOCOL_CATALOG_CHANGE -> ""
    }

    private companion object {
        /** Pinned: payload JSON is inside the event hash, and ledger rows can never be rewritten. */
        private val json = Json {
            encodeDefaults = true
            prettyPrint = false
            explicitNulls = true
            ignoreUnknownKeys = true
        }
    }

    /**
     * [clockMillis] is the raw `now` the caller supplied, kept alongside
     * the phase's possibly-clamped `startedAt` so a backwards clock jump
     * stays visible in the ledger instead of being quietly erased.
     */
    @Serializable
    private data class PhaseStartedPayload(
        val ordinal: Int,
        val reason: String,
        val clockMillis: Long,
        val vector: ProvenanceVector,
    )

    @Serializable
    private data class VersionChangePayload(val from: String, val to: String)

    @Serializable
    private data class ProtocolRegisteredPayload(
        val protocolId: String,
        val version: Int,
        val definitionSha256: String,
    ) {
        /** The same `id@version:hash` shape `EvidenceProtocolRegistry.catalogSha256` uses. */
        fun key(): String = "$protocolId@$version:$definitionSha256"
    }
}
