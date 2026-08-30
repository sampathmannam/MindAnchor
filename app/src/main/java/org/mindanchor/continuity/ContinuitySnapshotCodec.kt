package org.mindanchor.continuity

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Reading and writing a [ContinuitySnapshot] as JSON.
 *
 * [decode] never throws: a snapshot file is meant to outlive the build that
 * wrote it, and a caller (a restore screen, a later sync task) needs to
 * distinguish "this file is from a future format we don't understand yet"
 * from "this file is not a snapshot at all" without ever having a
 * [kotlinx.serialization.SerializationException] land in its lap.
 */
object ContinuitySnapshotCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    sealed class DecodeResult {
        data class Success(val snapshot: ContinuitySnapshot) : DecodeResult()
        data class UnsupportedVersion(val formatVersion: Int) : DecodeResult()
        data object Corrupt : DecodeResult()
    }

    fun encode(snapshot: ContinuitySnapshot): String = json.encodeToString(snapshot)

    fun decode(text: String): DecodeResult {
        val parsed = runCatching { json.decodeFromString<ContinuitySnapshot>(text) }
            .getOrElse { return DecodeResult.Corrupt }
        if (parsed.formatVersion !in ContinuityContract.SUPPORTED_SNAPSHOT_FORMAT_VERSIONS) {
            return DecodeResult.UnsupportedVersion(parsed.formatVersion)
        }
        if (smugglesNewerContent(parsed)) return DecodeResult.Corrupt
        return DecodeResult.Success(parsed)
    }

    private fun smugglesNewerContent(snapshot: ContinuitySnapshot): Boolean = when (snapshot.formatVersion) {
        ContinuityContract.PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION ->
            programOneSnapshotContentByField(snapshot.payload).values.any { it } ||
                programTwoSnapshotContentByField(snapshot.payload).values.any { it }
        ContinuityContract.PROGRAM_ONE_SNAPSHOT_FORMAT_VERSION ->
            programTwoSnapshotContentByField(snapshot.payload).values.any { it }
        else -> false
    }
}

internal fun programOneSnapshotContentByField(payload: ContinuityPayload): Map<String, Boolean> = mapOf(
    "researchLedgerEvents" to payload.researchLedgerEvents.isNotEmpty(),
    "studyPhases" to payload.studyPhases.isNotEmpty(),
)

internal fun programTwoSnapshotContentByField(payload: ContinuityPayload): Map<String, Boolean> = mapOf(
    "passiveRawProvenance" to payload.passiveRawProvenance.isNotEmpty(),
    "passiveSourceReads" to payload.passiveSourceReads.isNotEmpty(),
    "passiveSourceLags" to payload.passiveSourceLags.isNotEmpty(),
    "passiveBaselineSegments" to payload.passiveBaselineSegments.isNotEmpty(),
    "passivePipelineRuns" to payload.passivePipelineRuns.isNotEmpty(),
    "passiveWindowRevisions" to payload.passiveWindowRevisions.isNotEmpty(),
    "passiveDailyRevisions" to payload.passiveDailyRevisions.isNotEmpty(),
    "passiveObservationDecisions" to payload.passiveObservationDecisions.isNotEmpty(),
)
