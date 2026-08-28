package org.mindanchor.continuity

import android.content.Context
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.mindanchor.backup.BackupRepository
import org.mindanchor.data.FrictionPrefs
import org.mindanchor.data.NotesPrefs
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.journal.DeviceIdentityStore
import org.mindanchor.letters.LetterStore

/**
 * Captures the one canonical [ContinuitySnapshot] for this device: every
 * Room table and DataStore Task 7 protects, gathered, sorted into
 * canonical order, and hashed. Local-only — no network call is made
 * anywhere in this class; the actual upload is a later task.
 */
class ContinuitySnapshotRepository(
    private val context: Context,
    private val database: AnchorDatabase,
    private val notesPrefs: NotesPrefs,
    private val letterStore: LetterStore,
    private val frictionPrefs: FrictionPrefs,
    private val deviceIdentity: DeviceIdentityStore,
    private val backupRepository: BackupRepository,
) {

    suspend fun capture(now: Long): ContinuitySnapshot = withContext(Dispatchers.IO) {
        val dao = database.journal()

        val rawPayload = ContinuityPayload(
            journalEntries = dao.entriesNow().map { it.toDto() },
            contextRows = dao.allContext().map { it.toDto() },
            morningMeasures = dao.morningMeasuresNow().map { it.toDto() },
            notes = notesPrefs.notes.first().notes.map { it.toDto() },
            letters = letterStore.letters.first().map { it.toDto() },
            readLetterDates = letterStore.readDates.first().map { it.toString() },
            frictionedApps = frictionPrefs.flaggedApps.first().toList(),
            alwaysOpenApps = frictionPrefs.alwaysOpen.first().toList(),
            continuityChanges = dao.allChangesNow().map { it.toDto() },
            legacyBackupJson = backupRepository.export(now),
        )
        val payload = ContinuityContentHasher.sorted(rawPayload)
        val contentSha256 = ContinuityContentHasher.hash(payload)

        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)

        ContinuitySnapshot(
            formatVersion = ContinuitySnapshot.CURRENT_FORMAT_VERSION,
            snapshotId = UUID.randomUUID().toString(),
            createdAt = now,
            appVersionCode = packageInfo.longVersionCode.toInt(),
            appVersionName = packageInfo.versionName.orEmpty(),
            sourceDeviceId = deviceIdentity.id(),
            payload = payload,
            contentSha256 = contentSha256,
        )
    }
}
