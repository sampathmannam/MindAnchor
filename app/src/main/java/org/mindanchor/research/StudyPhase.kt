package org.mindanchor.research

import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Why a study phase opened. Each case names the [ProvenanceVector]
 * component that differed, in that class's declaration order, and carries
 * the ledger kind that records the change — except [INITIAL], which has
 * nothing to record a change *from*, and [PROTOCOL_CATALOG_CHANGE], whose
 * record is the per-protocol
 * [LedgerEventKind.PROTOCOL_VERSION_REGISTERED] events rather than one
 * catalogue-wide entry.
 */
enum class StudyPhaseReason(val ledgerKind: LedgerEventKind?) {
    INITIAL(null),
    APP_VERSION_CHANGE(LedgerEventKind.APP_VERSION_CHANGE),
    PROTOCOL_CATALOG_CHANGE(null),
    RULE_VERSION_CHANGE(LedgerEventKind.RULE_VERSION_CHANGE),
    MODEL_VERSION_CHANGE(LedgerEventKind.MODEL_VERSION_CHANGE),
    TRANSFORMATION_VERSION_CHANGE(LedgerEventKind.TRANSFORMATION_VERSION_CHANGE),
    MISSING_DATA_POLICY_CHANGE(LedgerEventKind.MISSING_DATA_POLICY_CHANGE),
    INSTRUMENT_VERSION_CHANGE(LedgerEventKind.INSTRUMENT_VERSION_CHANGE),
    DICTIONARY_VERSION_CHANGE(LedgerEventKind.DICTIONARY_VERSION_CHANGE),
    DEVICE_CHANGE(LedgerEventKind.DEVICE_CHANGE),
}

/**
 * One study phase: the window during which a particular [ProvenanceVector]
 * was in effect.
 *
 * **There is no end timestamp, deliberately.** A phase runs until the next
 * one starts. Writing an end onto a historical row would be a mutation of
 * history, which the design forbids and the database triggers prevent, so
 * the shape of this record has to make the mutation unnecessary rather
 * than merely forbidden.
 *
 * [id] is content-addressed over `(ordinal, startedAt, vector)`, which is
 * what makes a replacement-phone restore duplicate-free for phases the
 * same way the event hash does for ledger events. [reason] is excluded
 * because it is derived from the *previous* vector rather than from this
 * phase's own contents; including it would make the same phase hash
 * differently depending on what preceded it. A restored phase carries its
 * stored [reason] verbatim and is never recomputed, so nothing is lost.
 */
data class StudyPhase(
    val id: String,
    val ordinal: Int,
    val startedAt: Long,
    val reason: StudyPhaseReason,
    val vector: ProvenanceVector,
)

/**
 * The pure decisions about phases: when one opens, and which one an
 * instant falls in. No storage, no clock, no Android — all three are the
 * caller's.
 */
object StudyPhaseDecision {

    /**
     * Pinned: part of the phase id, which is a primary key and a restore
     * de-duplication key. `StudyPhaseTest` freezes both a golden id and
     * [StudyPhaseCanonical]'s serialised element names, so retuning this
     * or reordering [ProvenanceVector] goes red instead of silently
     * changing every id ever computed.
     */
    private val json = Json {
        encodeDefaults = true
        prettyPrint = false
        explicitNulls = true
    }

    /**
     * The phase that should open now, or null if [vector] is unchanged
     * from [current] — no phase churn for a launch that changed nothing.
     *
     * When several components differ at once, [StudyPhase.reason] names the
     * **first** in [ProvenanceVector]'s declaration order. Deterministic on
     * purpose: an arbitrary choice would make the same upgrade report a
     * different reason on different devices.
     *
     * `startedAt` is forced past the current phase's start even when [now]
     * is earlier. A phone whose clock jumps backwards — a lost RTC across
     * a reboot, say — would otherwise open a phase that begins *before*
     * the one it succeeds, and [phaseAt] would then answer with the older
     * phase forever, disagreeing permanently with the phase this function
     * returns. The two authorities on "which phase" have to agree, and
     * append-only rows cannot be corrected afterwards.
     */
    fun next(current: StudyPhase?, vector: ProvenanceVector, now: Long): StudyPhase? {
        if (current == null) return phaseOf(ordinal = 0, startedAt = now, StudyPhaseReason.INITIAL, vector)
        if (current.vector == vector) return null
        check(current.ordinal < Int.MAX_VALUE) { "study phase ordinals exhausted" }
        check(current.startedAt < Long.MAX_VALUE) { "study phase clock exhausted" }
        return phaseOf(
            ordinal = current.ordinal + 1,
            startedAt = maxOf(now, current.startedAt + 1),
            reason = firstDifference(current.vector, vector),
            vector = vector,
        )
    }

    /**
     * The phase in effect at [instant] — the last one started at or before
     * it — or null if [instant] precedes every phase.
     *
     * Relies on [next]'s invariant that ordinal order and `startedAt` order
     * agree. The comparator's ordinal tie-break is what keeps two phases
     * opened inside one millisecond ordered correctly.
     */
    fun phaseAt(phases: List<StudyPhase>, instant: Long): StudyPhase? = phases
        .filter { it.startedAt <= instant }
        .maxWithOrNull(compareBy({ it.startedAt }, { it.ordinal }))

    /**
     * The component that differs, in [ProvenanceVector]'s declaration
     * order. Only called when the two vectors are known to differ, so the
     * final branch is unreachable in practice and states why rather than
     * silently defaulting. `StudyPhaseTest` pins the vector's field list so
     * a component added without a branch here fails at build time rather
     * than throwing inside a research write.
     */
    private fun firstDifference(from: ProvenanceVector, to: ProvenanceVector): StudyPhaseReason = when {
        from.appVersionCode != to.appVersionCode -> StudyPhaseReason.APP_VERSION_CHANGE
        from.appVersionName != to.appVersionName -> StudyPhaseReason.APP_VERSION_CHANGE
        from.protocolCatalogSha256 != to.protocolCatalogSha256 -> StudyPhaseReason.PROTOCOL_CATALOG_CHANGE
        from.ruleSetVersion != to.ruleSetVersion -> StudyPhaseReason.RULE_VERSION_CHANGE
        from.modelSetVersion != to.modelSetVersion -> StudyPhaseReason.MODEL_VERSION_CHANGE
        from.transformationSetVersion != to.transformationSetVersion ->
            StudyPhaseReason.TRANSFORMATION_VERSION_CHANGE
        from.missingDataPolicyVersion != to.missingDataPolicyVersion ->
            StudyPhaseReason.MISSING_DATA_POLICY_CHANGE
        from.instrumentVersion != to.instrumentVersion -> StudyPhaseReason.INSTRUMENT_VERSION_CHANGE
        from.dictionaryVersion != to.dictionaryVersion -> StudyPhaseReason.DICTIONARY_VERSION_CHANGE
        from.sourceDeviceId != to.sourceDeviceId -> StudyPhaseReason.DEVICE_CHANGE
        else -> error("firstDifference called on two identical provenance vectors")
    }

    private fun phaseOf(
        ordinal: Int,
        startedAt: Long,
        reason: StudyPhaseReason,
        vector: ProvenanceVector,
    ): StudyPhase = StudyPhase(
        id = idOf(ordinal, startedAt, vector),
        ordinal = ordinal,
        startedAt = startedAt,
        reason = reason,
        vector = vector,
    )

    private fun idOf(ordinal: Int, startedAt: Long, vector: ProvenanceVector): String {
        val canonical = StudyPhaseCanonical(ordinal = ordinal, startedAt = startedAt, vector = vector)
        return MessageDigest.getInstance("SHA-256")
            .digest(json.encodeToString(canonical).encodeToByteArray())
            .joinToString(separator = "") { "%02x".format(it) }
    }
}

/**
 * The exact bytes a study phase id hashes over.
 *
 * Field order is wire format — kotlinx.serialization writes keys in
 * declaration order — and so is [ProvenanceVector]'s field order, since it
 * is nested here. `internal` rather than private so `StudyPhaseTest` can
 * read the shape directly instead of inferring it from a digest the same
 * code produced.
 */
@Serializable
internal data class StudyPhaseCanonical(
    val ordinal: Int,
    val startedAt: Long,
    val vector: ProvenanceVector,
)
