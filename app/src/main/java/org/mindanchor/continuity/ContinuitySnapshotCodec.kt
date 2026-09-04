package org.mindanchor.continuity

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

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
        encodeDefaults = true
    }

    private val snapshotFields = setOf(
        "formatVersion",
        "snapshotId",
        "createdAt",
        "appVersionCode",
        "appVersionName",
        "sourceDeviceId",
        "payload",
        "contentSha256",
    )
    private val programZeroFields = setOf(
        "journalEntries",
        "contextRows",
        "morningMeasures",
        "notes",
        "letters",
        "readLetterDates",
        "frictionedApps",
        "alwaysOpenApps",
        "continuityChanges",
        "legacyBackupJson",
    )
    private val programOneFields = setOf("researchLedgerEvents", "studyPhases")
    private val programTwoFields = setOf(
        "passiveRawProvenance",
        "passiveSourceReads",
        "passiveSourceLags",
        "passiveBaselineSegments",
        "passivePipelineRuns",
        "passiveWindowRevisions",
        "passiveDailyRevisions",
        "passiveObservationDecisions",
    )
    private val programThreeFields = setOf("advisoryOpportunities", "interventionEpisodeEvents")

    sealed class DecodeResult {
        data class Success(val snapshot: ContinuitySnapshot) : DecodeResult()
        data class UnsupportedVersion(val formatVersion: Int) : DecodeResult()
        data object Corrupt : DecodeResult()
    }

    fun encode(snapshot: ContinuitySnapshot): String = json.encodeToString(snapshot)

    @Suppress("ReturnCount")
    fun decode(text: String): DecodeResult {
        val raw = runCatching { Json.parseToJsonElement(text).jsonObject }
            .getOrElse { return DecodeResult.Corrupt }
        val formatVersion = (raw["formatVersion"] as? JsonPrimitive)?.intOrNull
            ?: return DecodeResult.Corrupt
        if (formatVersion !in ContinuityContract.SUPPORTED_SNAPSHOT_FORMAT_VERSIONS) {
            return DecodeResult.UnsupportedVersion(formatVersion)
        }
        if (!rawFieldsAreCompatible(raw, formatVersion)) return DecodeResult.Corrupt

        val parsed = runCatching { json.decodeFromString<ContinuitySnapshot>(text) }
            .getOrElse { return DecodeResult.Corrupt }
        if (smugglesNewerContent(parsed)) return DecodeResult.Corrupt
        return DecodeResult.Success(parsed)
    }

    private fun allowedFieldsFor(formatVersion: Int): Set<String> = when (formatVersion) {
        ContinuityContract.PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION -> programZeroFields
        ContinuityContract.PROGRAM_ONE_SNAPSHOT_FORMAT_VERSION -> programZeroFields + programOneFields
        ContinuityContract.PROGRAM_TWO_SNAPSHOT_FORMAT_VERSION ->
            programZeroFields + programOneFields + programTwoFields
        else -> programZeroFields + programOneFields + programTwoFields + programThreeFields
    }

    private fun knownLaterFieldsFor(formatVersion: Int): Set<String> = when (formatVersion) {
        ContinuityContract.PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION ->
            programOneFields + programTwoFields + programThreeFields
        ContinuityContract.PROGRAM_ONE_SNAPSHOT_FORMAT_VERSION -> programTwoFields + programThreeFields
        ContinuityContract.PROGRAM_TWO_SNAPSHOT_FORMAT_VERSION -> programThreeFields
        else -> emptySet()
    }

    private fun rawFieldsAreCompatible(root: JsonObject, formatVersion: Int): Boolean {
        if (root.keys.any { it !in snapshotFields }) return false
        val payload = root["payload"] as? JsonObject ?: return false
        if ("passiveRawSamples" in payload) return false

        val allowedFields = allowedFieldsFor(formatVersion)
        val knownLaterFields = knownLaterFieldsFor(formatVersion)
        return payload.all { (field, value) ->
            field in allowedFields ||
                (field in knownLaterFields && value is JsonArray && value.isEmpty())
        }
    }

    private fun smugglesNewerContent(snapshot: ContinuitySnapshot): Boolean = when (snapshot.formatVersion) {
        ContinuityContract.PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION ->
            programOneSnapshotContentByField(snapshot.payload).values.any { it } ||
                programTwoSnapshotContentByField(snapshot.payload).values.any { it } ||
                programThreeSnapshotContentByField(snapshot.payload).values.any { it }
        ContinuityContract.PROGRAM_ONE_SNAPSHOT_FORMAT_VERSION ->
            programTwoSnapshotContentByField(snapshot.payload).values.any { it } ||
                programThreeSnapshotContentByField(snapshot.payload).values.any { it }
        ContinuityContract.PROGRAM_TWO_SNAPSHOT_FORMAT_VERSION ->
            programThreeSnapshotContentByField(snapshot.payload).values.any { it }
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

internal fun programThreeSnapshotContentByField(payload: ContinuityPayload): Map<String, Boolean> = mapOf(
    "advisoryOpportunities" to payload.advisoryOpportunities.isNotEmpty(),
    "interventionEpisodeEvents" to payload.interventionEpisodeEvents.isNotEmpty(),
)
