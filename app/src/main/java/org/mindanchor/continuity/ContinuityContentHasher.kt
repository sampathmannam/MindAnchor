package org.mindanchor.continuity

import java.security.MessageDigest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.mindanchor.backup.BackupCodec

/**
 * Computes the deterministic content hash for a [ContinuityPayload].
 *
 * The hash is over *content* only — [ContinuitySnapshot.snapshotId],
 * [ContinuitySnapshot.createdAt], and [ContinuitySnapshot.sourceDeviceId]
 * are never passed in here, which is why [hash] takes the payload rather
 * than the whole snapshot. The same logical data restored on a new phone
 * (new snapshot id, new creation time, new device id) must produce the
 * same hash, so list order and the legacy backup's own embedded save
 * timestamp are also normalised away before hashing.
 */
object ContinuityContentHasher {

    private val json = Json { encodeDefaults = true }

    /**
     * Sorts every list in [payload] into its stable canonical order.
     * Journal/context/measure/change rows sort lexicographically by their
     * string id; notes sort numerically by id; letters and read dates sort
     * lexicographically by their ISO date string (which sorts correctly
     * for `yyyy-MM-dd`); package lists sort lexicographically. Exposed so
     * [ContinuitySnapshotRepository] can build an already-canonical payload
     * before computing [hash], rather than duplicating the sort keys.
     */
    fun sorted(payload: ContinuityPayload): ContinuityPayload = payload.copy(
        journalEntries = payload.journalEntries.sortedBy { it.id },
        contextRows = payload.contextRows.sortedBy { it.id },
        morningMeasures = payload.morningMeasures.sortedBy { it.id },
        notes = payload.notes.sortedBy { it.id },
        letters = payload.letters.sortedBy { it.date },
        readLetterDates = payload.readLetterDates.sorted(),
        frictionedApps = payload.frictionedApps.sorted(),
        alwaysOpenApps = payload.alwaysOpenApps.sorted(),
        continuityChanges = payload.continuityChanges.sortedBy { it.id },
    )

    /** Lowercase SHA-256 hex of the canonicalized [payload]. */
    fun hash(payload: ContinuityPayload): String {
        val canonical = sorted(payload).let { sortedPayload ->
            sortedPayload.copy(
                // Excluded from hashed content: which snapshot last
                // acknowledged a change is bookkeeping about the sync
                // process, not about the change itself.
                continuityChanges = sortedPayload.continuityChanges.map {
                    it.copy(acknowledgedSnapshotId = null)
                },
                legacyBackupJson = normalizeLegacyBackup(sortedPayload.legacyBackupJson),
            )
        }
        val bytes = json.encodeToString(canonical).encodeToByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    /**
     * Zeroes the legacy backup's own `savedAt` field so its embedded
     * export timestamp never perturbs the content hash. Falls back to the
     * raw text unchanged if it does not parse as a legacy backup — the
     * hash should still be deterministic for the same raw string, just
     * without the timestamp normalisation.
     */
    private fun normalizeLegacyBackup(raw: String): String {
        if (raw.isBlank()) return raw
        val backup = BackupCodec.decode(raw) ?: return raw
        return BackupCodec.encode(backup.copy(savedAt = 0L))
    }
}
