package org.mindanchor.research

import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Opens study phases and writes the provenance events that explain them.
 *
 * Every collaborator is a narrow suspend function — the same seam
 * [org.mindanchor.continuity.RestoreCoordinator] uses — so the whole
 * decision path is testable as plain in-memory lambdas with no Room, no
 * Context and no Robolectric. `ResearchLedgerRepository.build` wires the
 * real Room DAO.
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
 *
 * ## Appending is a read-modify-write
 *
 * [ensureCurrentPhase] reads the ledger head, links, and appends. The
 * caller must serialise that against every other append; the production
 * wiring runs it inside one Room transaction. See [LedgerChain]'s KDoc.
 */
class ResearchProvenanceCoordinator(
    private val latestPhase: suspend () -> StudyPhase?,
    private val insertPhase: suspend (StudyPhase) -> Unit,
    private val ledgerHead: suspend () -> ResearchLedgerEvent?,
    private val registeredProtocolKeys: suspend () -> Set<String>,
    private val appendEvents: suspend (List<ResearchLedgerEvent>) -> Unit,
    private val currentVector: suspend () -> ProvenanceVector,
    private val localDateOf: (Long) -> String = { millis ->
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toString()
    },
) {

    /**
     * The catalogue is read directly rather than injected: it is a
     * compile-time constant of the build, not a collaborator a caller
     * could sensibly vary, and every study phase must register the
     * catalogue this build actually ships.
     */
    private val catalog: EvidenceProtocolRegistry = EvidenceProtocolCatalog.registry

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
     */
    suspend fun ensureCurrentPhase(now: Long): StudyPhase {
        val current = latestPhase()
        val vector = currentVector()
        val opened = StudyPhaseDecision.next(current, vector, now) ?: return requireNotNull(current) {
            "StudyPhaseDecision returned no phase with none current"
        }

        insertPhase(opened)

        val head = ledgerHead()
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
            encode(PhaseStartedPayload(opened.ordinal, opened.reason.name, vector)),
        )
        opened.reason.ledgerKind?.let { kind ->
            val from = describe(current?.vector, opened.reason)
            val to = describe(vector, opened.reason)
            append(kind, encode(VersionChangePayload(from, to)))
        }

        val alreadyRegistered = registeredProtocolKeys()
        catalog.protocols.forEach { protocol ->
            val payload = encode(
                ProtocolRegisteredPayload(
                    protocolId = protocol.id,
                    version = protocol.version,
                    definitionSha256 = EvidenceProtocolRegistry.definitionSha256(protocol),
                ),
            )
            if (payload !in alreadyRegistered) append(LedgerEventKind.PROTOCOL_VERSION_REGISTERED, payload)
        }

        appendEvents(appended)
        return opened
    }

    /** The value of the component [reason] names, so a change event says what actually moved. */
    private fun describe(vector: ProvenanceVector?, reason: StudyPhaseReason): String = when {
        vector == null -> ""
        reason == StudyPhaseReason.APP_VERSION_CHANGE -> "${vector.appVersionName} (${vector.appVersionCode})"
        reason == StudyPhaseReason.RULE_VERSION_CHANGE -> vector.ruleSetVersion
        reason == StudyPhaseReason.MODEL_VERSION_CHANGE -> vector.modelSetVersion
        reason == StudyPhaseReason.TRANSFORMATION_VERSION_CHANGE -> vector.transformationSetVersion
        reason == StudyPhaseReason.MISSING_DATA_POLICY_CHANGE -> vector.missingDataPolicyVersion
        reason == StudyPhaseReason.INSTRUMENT_VERSION_CHANGE -> vector.instrumentVersion
        reason == StudyPhaseReason.DICTIONARY_VERSION_CHANGE -> vector.dictionaryVersion
        reason == StudyPhaseReason.DEVICE_CHANGE -> vector.sourceDeviceId
        else -> ""
    }

    private inline fun <reified T> encode(payload: T): String = json.encodeToString(payload)

    private companion object {
        /** Pinned: payload JSON is inside the event hash, and ledger rows can never be rewritten. */
        private val json = Json {
            encodeDefaults = true
            prettyPrint = false
            explicitNulls = true
        }
    }

    @Serializable
    private data class PhaseStartedPayload(val ordinal: Int, val reason: String, val vector: ProvenanceVector)

    @Serializable
    private data class VersionChangePayload(val from: String, val to: String)

    @Serializable
    private data class ProtocolRegisteredPayload(
        val protocolId: String,
        val version: Int,
        val definitionSha256: String,
    )
}
