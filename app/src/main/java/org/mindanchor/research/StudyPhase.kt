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
 * same way the event hash does for ledger events.
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

    /** Pinned: part of the phase id, which is a primary key and a restore de-duplication key. */
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
     */
    fun next(current: StudyPhase?, vector: ProvenanceVector, now: Long): StudyPhase? {
        if (current == null) return phaseOf(ordinal = 0, startedAt = now, StudyPhaseReason.INITIAL, vector)
        if (current.vector == vector) return null
        return phaseOf(
            ordinal = current.ordinal + 1,
            startedAt = now,
            reason = firstDifference(current.vector, vector),
            vector = vector,
        )
    }

    /**
     * The phase in effect at [instant] — the last one started at or before
     * it — or null if [instant] precedes every phase. Ties on `startedAt`
     * break by ordinal, so two changes inside one millisecond still order
     * correctly.
     */
    fun phaseAt(phases: List<StudyPhase>, instant: Long): StudyPhase? = phases
        .filter { it.startedAt <= instant }
        .maxWithOrNull(compareBy({ it.startedAt }, { it.ordinal }))

    /**
     * The component that differs, in [ProvenanceVector]'s declaration
     * order. Only called when the two vectors are known to differ, so the
     * final branch is unreachable in practice and states why rather than
     * silently defaulting.
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

    /**
     * Deliberately excludes [StudyPhase.reason]: the reason is derived from
     * the two vectors, so including it would let the same phase carry two
     * different ids depending on what preceded it — and a restored phase
     * must land on the row it already occupies.
     */
    private fun idOf(ordinal: Int, startedAt: Long, vector: ProvenanceVector): String {
        val canonical = CanonicalPhase(ordinal = ordinal, startedAt = startedAt, vector = vector)
        return MessageDigest.getInstance("SHA-256")
            .digest(json.encodeToString(canonical).encodeToByteArray())
            .joinToString(separator = "") { "%02x".format(it) }
    }

    @Serializable
    private data class CanonicalPhase(val ordinal: Int, val startedAt: Long, val vector: ProvenanceVector)
}
