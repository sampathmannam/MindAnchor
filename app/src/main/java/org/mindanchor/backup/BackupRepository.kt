package org.mindanchor.backup

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.mindanchor.corpus.CorpusImport
import org.mindanchor.corpus.CorpusStore
import org.mindanchor.data.FrictionPrefs
import org.mindanchor.data.LauncherPrefs
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.data.replaceFlagged
import org.mindanchor.model.MomentStore
import org.mindanchor.support.RoomSafetyPlanStore
import org.mindanchor.support.SafetyPlanSaveResult
import org.mindanchor.support.SafetyPlanStore
import org.mindanchor.support.SaveSafetyPlan
import org.mindanchor.usage.InferredStore
import org.mindanchor.vitals.MeasuredStore

/**
 * Gathers everything worth keeping into a file, and puts it back.
 *
 * Restore is additive for contacts, pulses, check-ins, readings and
 * corpus additions, and replacing for the plan and the launcher choices. The reasoning: a plan is one document and the
 * newer one wins, while contacts and mood history are a record — quietly
 * deleting either because a person restored an old file would be its own
 * kind of data loss.
 */
class BackupRepository internal constructor(
    private val context: Context,
    private val db: AnchorDatabase,
    private val safetyPlanStore: SafetyPlanStore,
) {
    constructor(context: Context) : this(
        context = context.applicationContext,
        db = AnchorDatabase.get(context.applicationContext),
        safetyPlanStore = RoomSafetyPlanStore(
            AnchorDatabase.get(context.applicationContext).safety(),
        ),
    )

    private val prefs = LauncherPrefs(context)
    private val friction = FrictionPrefs(context)

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
                frictioned = friction.flaggedApps.first().toList().sorted(),
                renames = prefs.renames.first(),
                checkIns = MomentStore(context).moments.first().map(BackupCodec::checkInOf),
                readings = MeasuredStore(context).all().map(BackupCodec::readingOf),
                inferred = InferredStore(context).all().map(BackupCodec::readingOf),
                // The imported difference file as it stands, verbatim —
                // already the person's own curation in a readable format.
                corpusAdditions = runCatching {
                    CorpusStore.importedFile(context).takeIf { it.exists() }?.readText()
                }.getOrNull().orEmpty(),
            ),
        )
    }

    /** Returns true if the text was a backup this build could read. */
    suspend fun import(text: String, now: Long): Boolean = withContext(Dispatchers.IO) {
        val backup = BackupCodec.decode(text) ?: return@withContext false

        val planResult = safetyPlanStore.save(
            SaveSafetyPlan(
                operationId = BACKUP_IMPORT_OPERATION_ID,
                draft = BackupCodec.toSafetyPlan(backup.plan, now),
            ),
        )
        if (planResult is SafetyPlanSaveResult.Failed) throw planResult.cause

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

        restoreFrictioned(backup)

        // Check-ins: additive, de-duplicated by day and minute. The label
        // history is the slowest data in the app to accumulate, so
        // restoring an old file must only ever add to it.
        val momentStore = MomentStore(context)
        val haveMoments = momentStore.moments.first()
            .map { it.day to it.atMinuteOfDay }.toHashSet()
        BackupCodec.toMoments(backup.checkIns)
            .filterNot { (it.day to it.atMinuteOfDay) in haveMoments }
            .forEach { momentStore.append(it) }

        // Readings: the phone's own copy wins on conflict. A reading
        // already on this device is at least as fresh as the file's.
        val measuredStore = MeasuredStore(context)
        val haveReadings = measuredStore.all().map { it.day to it.key }.toHashSet()
        BackupCodec.toMeasurements(backup.readings)
            .filterNot { (it.day to it.key) in haveReadings }
            .forEach { reading ->
                // A hand-edited day that does not parse costs that one
                // reading, not the restore.
                runCatching {
                    measuredStore.record(java.time.LocalDate.parse(reading.day), reading.key, reading.value)
                }
            }

        // The inferred ledger restores under exactly the readings' rules:
        // local wins, and a day that does not parse costs itself alone.
        val inferredStore = InferredStore(context)
        val haveInferred = inferredStore.all().map { it.day to it.key }.toHashSet()
        BackupCodec.toMeasurements(backup.inferred)
            .filterNot { (it.day to it.key) in haveInferred }
            .forEach { reading ->
                runCatching {
                    inferredStore.record(java.time.LocalDate.parse(reading.day), reading.key, reading.value)
                }
            }

        // Corpus additions: only passages whose ids are new anywhere.
        // Restoring an old file must never overwrite a correction made
        // since it was saved.
        if (backup.corpusAdditions.isNotBlank()) {
            val current = CorpusStore.load(context)
            val known = current.map { it.id }.toHashSet()
            val incoming = CorpusStore.parse(backup.corpusAdditions).filterNot { it.id in known }
            if (incoming.isNotEmpty()) {
                CorpusStore.saveImported(
                    context,
                    CorpusImport.merge(current, CorpusImport.render(incoming)).corpus,
                )
            }
        }
        true
    }

    /**
     * The pauses restore like the other launcher preferences: the file wins
     * outright rather than merging.
     *
     * The empty case is the exception. Every copy saved before the export
     * carried this field says `"frictioned": []`, so replacing with it would
     * delete the pauses belonging to the phone doing the restoring.
     */
    private suspend fun restoreFrictioned(backup: BackupCodec.Backup) {
        if (backup.frictioned.isNotEmpty()) {
            friction.replaceFlagged(backup.frictioned.toSet())
        }
    }

    companion object {
        private const val BACKUP_IMPORT_OPERATION_ID = 0L

        /** A name someone can recognise in a file picker a year from now. */
        fun fileName(stamp: String) = "mindanchor-backup-$stamp.json"

        fun read(context: Context, uri: Uri): String? = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        }.getOrNull()

        fun write(context: Context, uri: Uri, text: String): Boolean = runCatching {
            writeTo(context.contentResolver.openOutputStream(uri), text)
        }.getOrDefault(false)

        /** A provider is allowed to return null; that means no bytes were written. */
        internal fun writeTo(output: java.io.OutputStream?, text: String): Boolean {
            val destination = output ?: return false
            destination.use { it.write(text.encodeToByteArray()) }
            return true
        }
    }
}
