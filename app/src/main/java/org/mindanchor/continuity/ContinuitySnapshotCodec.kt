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
        if (parsed.formatVersion != ContinuitySnapshot.CURRENT_FORMAT_VERSION) {
            return DecodeResult.UnsupportedVersion(parsed.formatVersion)
        }
        return DecodeResult.Success(parsed)
    }
}
