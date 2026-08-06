package org.mindanchor.backup

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.mindanchor.data.LauncherPrefs
import org.mindanchor.data.db.AnchorDatabase

/**
 * Gathers everything worth keeping into a file, and puts it back.
 *
 * Restore is additive for contacts and pulses and replacing for the plan
 * and the launcher choices. The reasoning: a plan is one document and the
 * newer one wins, while contacts and mood history are a record — quietly
 * deleting either because a person restored an old file would be its own
 * kind of data loss.
 */
class BackupRepository(private val context: Context) {

    private val db = AnchorDatabase.get(context)
    private val prefs = LauncherPrefs(context)

    suspend fun export(now: Long): String = withContext(Dispatchers.IO) {
        val plan = db.safety().plan().first() ?: org.mindanchor.data.db.SafetyPlan()
        BackupCodec.encode(
            BackupCodec.Backup(
                savedAt = now,
                plan = BackupCodec.planOf(plan),
                contacts = db.safety().contacts().first().map(BackupCodec::contactOf),
                pulses = db.pulses().history().first().map(BackupCodec::pulseOf),
                favorites = prefs.favorites.first(),
                hidden = prefs.hidden.first().toList().sorted(),
                frictioned = emptyList(),
                renames = prefs.renames.first(),
            ),
        )
    }

    /** Returns true if the text was a backup this build could read. */
    suspend fun import(text: String, now: Long): Boolean = withContext(Dispatchers.IO) {
        val backup = BackupCodec.decode(text) ?: return@withContext false

        db.safety().savePlan(BackupCodec.toSafetyPlan(backup.plan, now))

        // Additive, and de-duplicated by number so restoring the same file
        // twice does not fill the crisis card with copies of one person.
        val existing = db.safety().contacts().first().map { it.phone }.toSet()
        BackupCodec.toCrisisContacts(backup.contacts)
            .filterNot { it.phone in existing }
            .forEach { db.safety().addContact(it) }

        val seen = db.pulses().history().first().map { it.takenAt }.toSet()
        BackupCodec.toPulseResults(backup.pulses)
            .filterNot { it.takenAt in seen }
            .forEach { db.pulses().insert(it) }

        prefs.replaceFavorites(backup.favorites)
        prefs.replaceHidden(backup.hidden.toSet())
        prefs.replaceRenames(backup.renames)
        true
    }

    companion object {
        /** A name someone can recognise in a file picker a year from now. */
        fun fileName(stamp: String) = "mindanchor-backup-$stamp.json"

        fun read(context: Context, uri: Uri): String? = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        }.getOrNull()

        fun write(context: Context, uri: Uri, text: String): Boolean = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.encodeToByteArray()) }
            true
        }.getOrDefault(false)
    }
}
