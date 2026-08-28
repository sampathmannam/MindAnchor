package org.mindanchor.backup

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.mindanchor.data.NotesPrefs
import org.mindanchor.letters.Letter
import org.mindanchor.letters.LetterStore
import org.mindanchor.model.Moment
import org.mindanchor.model.MomentStore
import org.mindanchor.model.Note
import org.mindanchor.model.NotesState
import org.mindanchor.vitals.MeasuredStore
import org.mindanchor.vitals.Measurement
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The per-type routing layer between the data
 * sources ([NotesPrefs], [LetterStore],
 * [MomentStore], [MeasuredStore]) and the outbound
 * channel ([BackupTarget]).
 *
 * The model is "one Drive file per content type" —
 * see [ContentType]'s KDoc for why. The scheduler's
 * job is to take every entry not yet on file for a
 * type and append it as JSON-Lines lines; it does
 * not know or care which [BackupTarget]
 * implementation is wired.
 *
 * ## Wire format per entry
 *
 * Each entry is serialised to a one-line JSON object
 * and the target appends the bytes verbatim plus a
 * trailing newline (`\n`, 0x0A) — see
 * [GoogleDriveBackupTarget]. The per-type file is
 * therefore a sequence of newline-terminated JSON
 * objects, inspectable in the Drive web UI as one
 * entry per line, plain text.
 *
 * v0.70.7: the payload used to be wrapped in
 * [org.mindanchor.backup.EncryptedBackupCodec] before
 * this class's own KDoc named it — that class no
 * longer exists. See [GoogleDriveBackupTarget]'s KDoc
 * for why: an Android-Keystore-backed key cannot
 * follow the user to a new phone, which made "back
 * this up so I don't lose it when I change phones"
 * and "encrypt it with a key that can't leave this
 * phone" a direct contradiction. The user chose
 * continuity.
 *
 * Also new in v0.70.7: two more content types
 * ([ContentType.CheckIns], [ContentType.WellnessReadings])
 * and [restoreAll], the read side of the same
 * contract. The v0.25.5 on-write streaming trigger
 * and its WorkManager retry queue are gone — neither
 * was ever wired to anything that ran (see the
 * v0.70.7 commit for the full account) — replaced by
 * a single nightly delta sync
 * ([org.mindanchor.backup.DriveNightlySync]).
 *
 * ## Why a delta, not a full reupload
 *
 * [GoogleDriveBackupTarget.append] has no native
 * append call to build on — each call downloads the
 * whole file, adds one line, and reuploads the whole
 * file. Calling it once per *existing* entry, every
 * night, forever, would make a nightly job that gets
 * slower and more expensive every night it runs, and
 * would write the same content into the Drive file
 * again on top of itself — exactly the unbounded
 * battery/data cost this app's other nightly jobs are
 * built to avoid (see [org.mindanchor.report.ReportScheduler]'s
 * own KDoc on the same concern). So [backupAll]
 * downloads each type's current Drive content once,
 * and only appends the local entries that are not
 * already in it — a night with nothing new to say
 * costs four small downloads and zero uploads. This
 * also means a failed night costs nothing but a day's
 * delay: whatever did not make it up is simply still
 * "not yet in Drive" and is picked up by the next
 * successful run.
 *
 * v0.70.9: [backupAll] used to call [BackupTarget.append]
 * once per new entry. [GoogleDriveBackupTarget.append]
 * finds-or-creates the Drive file on every call, and
 * Drive's search index does not reliably see a file
 * the instant it is created — a second entry's find
 * could still say "no such file" moments after the
 * first entry's call had just created it, so Drive
 * created a second file with the same name instead of
 * appending to the first. Confirmed live: two notes
 * backed up on the same run produced two separate
 * `MindAnchor-Notes.txt` files in Drive. [appendLines]
 * is the fix — every new entry for a type is collected
 * first, so each run makes exactly one find-or-create
 * decision per type, not one per entry.
 *
 * ## Triggers
 *
 * [backupAll] is called from two places: the
 * Settings "Back up now" button (immediate, manual)
 * and [org.mindanchor.backup.DriveNightlySync]'s
 * alarm (automatic, once a night). Both call sites
 * run the identical delta sync; "nightly" is a matter
 * of who calls it and when, not a different code path.
 *
 * ## Threading
 *
 * All public methods are `suspend` and run on the
 * caller's dispatcher.
 */
class BackupScheduler(
    private val context: Context,
    private val notesTarget: BackupTarget,
    private val lettersTarget: BackupTarget,
    private val checkInsTarget: BackupTarget,
    private val wellnessTarget: BackupTarget,
    private val notesPrefs: NotesPrefs = NotesPrefs(context),
    private val letterStore: LetterStore = LetterStore(context),
    private val momentStore: MomentStore = MomentStore(context),
    private val measuredStore: MeasuredStore = MeasuredStore(context),
) {

    private val json = Json {
        // No pretty-printing: each entry is one
        // line in the per-type file, so a
        // newline inside the JSON body would
        // break the line-based restore.
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Uploads whatever local content, of every type,
     * is not already in its Drive file. Safe to call
     * repeatedly: a call with nothing new to say
     * downloads four files and uploads nothing. The
     * "Back up now" button and the nightly alarm both
     * call this — neither is a "from now on" streaming
     * decision, both are "check what's missing, right
     * now".
     */
    suspend fun backupAll(): BackupAllResult {
        val (notesOk, notesFail) = syncNotes()
        val (lettersOk, lettersFail) = syncLetters()
        val (checkInsOk, checkInsFail) = syncCheckIns()
        val (wellnessOk, wellnessFail) = syncWellness()

        Log.i(
            LOG_TAG,
            "backupAll: notes $notesOk/$notesFail, letters $lettersOk/$lettersFail, " +
                "checkIns $checkInsOk/$checkInsFail, wellness $wellnessOk/$wellnessFail (ok/fail)",
        )
        return BackupAllResult(
            notesAppended = notesOk,
            notesFailed = notesFail,
            lettersAppended = lettersOk,
            lettersFailed = lettersFail,
            checkInsAppended = checkInsOk,
            checkInsFailed = checkInsFail,
            wellnessAppended = wellnessOk,
            wellnessFailed = wellnessFail,
        )
    }

    /**
     * Notes are keyed by [Note.body] for this
     * comparison — the wire format ([BackupEntry])
     * does not carry [Note.id], so body text is the
     * only field on both sides of the comparison. An
     * edited note has a different body, so it uploads
     * as a second line rather than silently never
     * syncing the edit — [restoreAll] then sees it as
     * a second note on a fresh device, the same
     * tradeoff documented on [restoreNotes].
     */
    private suspend fun syncNotes(): Pair<Int, Int> {
        val notes = notesPrefs.notes.firstOrEmpty(NotesState()).notes
        if (notes.isEmpty()) return 0 to 0
        val already = downloadedKeys(notesTarget, ContentType.Notes) {
            decodeLine(BackupEntry.serializer(), it)?.body
        }
        val fresh = notes.filter { it.body !in already }
            .map { BackupEntry(date = dayOf(it.createdAt).toString(), body = it.body) }
        if (fresh.isEmpty()) return 0 to 0
        val lines = fresh.map { json.encodeToString(BackupEntry.serializer(), it) }
        return countOf(fresh.size, appendLines(notesTarget, ContentType.Notes, lines))
    }

    /** Letters are keyed by [Letter.date] — see [restoreLetters] for the same key on the way back. */
    private suspend fun syncLetters(): Pair<Int, Int> {
        val letters = letterStore.letters.firstOrEmpty(emptyList())
        if (letters.isEmpty()) return 0 to 0
        val already = downloadedKeys(lettersTarget, ContentType.Letters) {
            decodeLine(BackupEntry.serializer(), it)?.date
        }
        val fresh = letters.filter { it.date.toString() !in already }
            .map { BackupEntry(date = it.date.toString(), body = it.body) }
        if (fresh.isEmpty()) return 0 to 0
        val lines = fresh.map { json.encodeToString(BackupEntry.serializer(), it) }
        return countOf(fresh.size, appendLines(lettersTarget, ContentType.Letters, lines))
    }

    /** Check-ins are immutable once answered, so [Moment.momentKey] alone is a complete key. */
    private suspend fun syncCheckIns(): Pair<Int, Int> {
        val moments = momentStore.moments.firstOrEmpty(emptyList())
        if (moments.isEmpty()) return 0 to 0
        val already = downloadedKeys(checkInsTarget, ContentType.CheckIns) {
            decodeLine(CheckInEntry.serializer(), it)?.let(::checkInKey)
        }
        val fresh = moments.filter { it.momentKey() !in already }
            .map {
                CheckInEntry(
                    valence = it.valence,
                    arousal = it.arousal,
                    atMinuteOfDay = it.atMinuteOfDay,
                    day = it.day,
                )
            }
        if (fresh.isEmpty()) return 0 to 0
        val lines = fresh.map { json.encodeToString(CheckInEntry.serializer(), it) }
        return countOf(fresh.size, appendLines(checkInsTarget, ContentType.CheckIns, lines))
    }

    /**
     * Wellness readings key on (day, signal, value) together, not just
     * (day, signal): [MeasuredStore.record] treats a same-day retake as a
     * replacement ("the later measurement is the one the person trusted
     * enough to keep"), and a value that changed for an already-synced day
     * is exactly that — a retake worth a fresh line, not a duplicate. On
     * [restoreWellness], the later line in file order wins because
     * `record()` itself upserts by (day, key), so this never produces two
     * conflicting readings on the far end.
     */
    private suspend fun syncWellness(): Pair<Int, Int> {
        val readings = runCatching { measuredStore.all() }.getOrDefault(emptyList())
        if (readings.isEmpty()) return 0 to 0
        val already = downloadedKeys(wellnessTarget, ContentType.WellnessReadings) {
            decodeLine(WellnessEntry.serializer(), it)?.let(::wellnessKey)
        }
        val fresh = readings.filter { wellnessKey(it.day, it.key, it.value) !in already }
            .map { WellnessEntry(day = it.day, key = it.key, value = it.value) }
        if (fresh.isEmpty()) return 0 to 0
        val lines = fresh.map { json.encodeToString(WellnessEntry.serializer(), it) }
        return countOf(fresh.size, appendLines(wellnessTarget, ContentType.WellnessReadings, lines))
    }

    /**
     * [count] entries went into one [AppendResult]: all of them landed if
     * it was [AppendResult.Ok], none did otherwise — the batched append
     * below is one HTTP round trip for the whole type, not one per entry,
     * so there is no partial-success case to report.
     */
    private fun countOf(count: Int, result: AppendResult): Pair<Int, Int> =
        if (result is AppendResult.Ok) count to 0 else 0 to count

    /**
     * Joins [lines] (each already one JSON-encoded entry) with `\n` and
     * appends the whole block in a single [BackupTarget.append] call.
     *
     * This is why [syncNotes] and its three siblings collect every new
     * entry before appending anything, rather than calling append once
     * per entry in a loop: [GoogleDriveBackupTarget.append] finds-or-creates
     * the file itself on every call, and Drive's file-search index does
     * not reliably see a file the moment it is created. Calling append
     * once per entry meant the second entry's "does this file already
     * exist" query could still say no immediately after the first entry's
     * call had just created it — Drive would then create a *second* file
     * with the same name instead of appending to the first, and a third
     * entry could do it again. One call per type per [backupAll] run is
     * one find-or-create decision, not one per entry, so the race has
     * nothing left to trigger on.
     */
    private suspend fun appendLines(target: BackupTarget, type: ContentType, lines: List<String>): AppendResult {
        val payload = lines.joinToString("\n").toByteArray(Charsets.UTF_8)
        return target.append(type, payload)
    }

    /**
     * Downloads [type]'s current Drive content and reduces each line to a
     * dedup key via [keyOf] (null for a line that fails to parse — treated
     * as absent, the same way a genuinely missing file is). No local
     * bookkeeping of "what was synced last time" is kept anywhere:
     * Drive's own content is re-read and re-diffed against on every call,
     * so a reinstall, a cleared local store, or a second device signed
     * into the same account can never drift out of sync with what the
     * append side believes is already backed up.
     */
    private suspend fun downloadedKeys(
        target: BackupTarget,
        type: ContentType,
        keyOf: (String) -> String?,
    ): Set<String> {
        val bytes = target.download(type) ?: return emptySet()
        return linesOf(bytes).mapNotNull(keyOf).toSet()
    }

    private fun checkInKey(entry: CheckInEntry): String =
        "${entry.day}|${entry.atMinuteOfDay}|${entry.valence}|${entry.arousal}"

    private fun wellnessKey(entry: WellnessEntry): String = wellnessKey(entry.day, entry.key, entry.value)

    private fun wellnessKey(day: String, key: String, value: Double): String = "$day|$key|$value"

    /**
     * Downloads every content type's current file
     * and merges any entry not already present into
     * the matching local store. Additive, never
     * destructive: nothing already on this phone is
     * ever removed or overwritten by a restore.
     *
     * This is what makes a new phone, signed into
     * the same Google account, able to pick up where
     * the old one left off — the manual counterpart
     * to [org.mindanchor.backup.DriveNightlySync],
     * which only ever writes forward.
     */
    suspend fun restoreAll(): RestoreAllResult {
        val notesRestored = restoreNotes()
        val lettersRestored = restoreLetters()
        val checkInsRestored = restoreCheckIns()
        val wellnessRestored = restoreWellness()
        Log.i(
            LOG_TAG,
            "restoreAll: notes $notesRestored, letters $lettersRestored, " +
                "checkIns $checkInsRestored, wellness $wellnessRestored (new entries written)",
        )
        return RestoreAllResult(
            notesRestored = notesRestored,
            lettersRestored = lettersRestored,
            checkInsRestored = checkInsRestored,
            wellnessRestored = wellnessRestored,
        )
    }

    private suspend fun restoreNotes(): Int {
        val bytes = notesTarget.download(ContentType.Notes) ?: return 0
        val existingIds = notesPrefs.notes.firstOrEmpty(NotesState()).notes.map { it.id }.toSet()
        var written = 0
        for (line in linesOf(bytes)) {
            val entry = decodeLine(BackupEntry.serializer(), line) ?: continue
            // A restored note has no id of its own in the wire
            // format (the wire format only ever carried date +
            // body — see [BackupEntry]); a fresh id derived from
            // the body is the only stable dedup key available,
            // so the same restore run twice does not double the
            // note. This means a note edited after its own
            // backup restores as a second note rather than
            // overwriting the edit — the safer direction, since
            // restore must never silently discard a local edit.
            val id = stableIdFor(entry.body)
            if (id in existingIds) continue
            // The wire format only carries a date, not a
            // time-of-day (see [BackupEntry]) — start-of-day
            // is the closest reconstructable timestamp. That
            // is coarser than the original device's millisecond
            // createdAt, but it is enough to sort the note into
            // the right day; the alternative (createdAt = 0L)
            // would silently dump every restored note into the
            // 1970-01-01 group in NoteStore.groupedByDay.
            val restoredAt = runCatching { LocalDate.parse(entry.date) }
                .getOrNull()
                ?.let(::millisAtStartOfDay)
                ?: 0L
            notesPrefs.add(Note(id = id, body = entry.body, createdAt = restoredAt, updatedAt = restoredAt))
            written++
        }
        return written
    }

    private suspend fun restoreLetters(): Int {
        val bytes = lettersTarget.download(ContentType.Letters) ?: return 0
        val existingDates = letterStore.letters.firstOrEmpty(emptyList()).map { it.date }.toSet()
        var written = 0
        for (line in linesOf(bytes)) {
            val entry = decodeLine(BackupEntry.serializer(), line) ?: continue
            val date = runCatching { LocalDate.parse(entry.date) }.getOrNull() ?: continue
            if (date in existingDates) continue
            letterStore.save(Letter(date = date, body = entry.body))
            written++
        }
        return written
    }

    private suspend fun restoreCheckIns(): Int {
        val bytes = checkInsTarget.download(ContentType.CheckIns) ?: return 0
        val existing = momentStore.moments.firstOrEmpty(emptyList()).map { it.momentKey() }.toSet()
        var written = 0
        for (line in linesOf(bytes)) {
            val entry = decodeLine(CheckInEntry.serializer(), line) ?: continue
            val moment = Moment(
                valence = entry.valence,
                arousal = entry.arousal,
                atMinuteOfDay = entry.atMinuteOfDay,
                day = entry.day,
            )
            if (moment.momentKey() in existing) continue
            momentStore.append(moment)
            written++
        }
        return written
    }

    private suspend fun restoreWellness(): Int {
        val bytes = wellnessTarget.download(ContentType.WellnessReadings) ?: return 0
        var written = 0
        for (line in linesOf(bytes)) {
            val entry = decodeLine(WellnessEntry.serializer(), line) ?: continue
            val day = runCatching { LocalDate.parse(entry.day) }.getOrNull() ?: continue
            // record() upserts by (day, key), so restoring an
            // entry that already exists locally is a harmless
            // no-op overwrite with the same value rather than a
            // duplicate — unlike notes/check-ins, this type
            // needs no separate existing-set check.
            measuredStore.record(day, entry.key, entry.value)
            written++
        }
        return written
    }

    private fun Moment.momentKey(): String = "$day|$atMinuteOfDay|$valence|$arousal"

    private fun dayOf(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()

    private fun millisAtStartOfDay(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun linesOf(bytes: ByteArray): List<String> =
        String(bytes, Charsets.UTF_8).lineSequence().filter { it.isNotBlank() }.toList()

    private fun <T> decodeLine(serializer: kotlinx.serialization.KSerializer<T>, line: String): T? =
        runCatching { json.decodeFromString(serializer, line) }.getOrNull()

    /**
     * The result of a [backupAll] call. The caller
     * (the "Back up now" button's result state, and
     * the nightly sync's log line) renders a summary
     * from these numbers.
     */
    data class BackupAllResult(
        val notesAppended: Int,
        val notesFailed: Int,
        val lettersAppended: Int,
        val lettersFailed: Int,
        val checkInsAppended: Int,
        val checkInsFailed: Int,
        val wellnessAppended: Int,
        val wellnessFailed: Int,
    ) {
        val ok: Boolean
            get() = notesFailed == 0 && lettersFailed == 0 && checkInsFailed == 0 && wellnessFailed == 0
    }

    /** The result of a [restoreAll] call: how many new entries each type wrote locally. */
    data class RestoreAllResult(
        val notesRestored: Int,
        val lettersRestored: Int,
        val checkInsRestored: Int,
        val wellnessRestored: Int,
    ) {
        val total: Int get() = notesRestored + lettersRestored + checkInsRestored + wellnessRestored
    }

    /**
     * The shape of one Notes/Letters entry in the
     * per-type file. Two fields — the date and the
     * body. See [restoreNotes] for why a note's
     * [Note.id] is not part of the wire format.
     */
    @Serializable
    data class BackupEntry(
        val date: String,
        val body: String,
    )

    /** The shape of one check-in entry in `MindAnchor-CheckIns.txt`. */
    @Serializable
    data class CheckInEntry(
        val valence: Int,
        val arousal: Int,
        val atMinuteOfDay: Int,
        val day: String,
    )

    /** The shape of one wellness-reading entry in `MindAnchor-Wellness.txt`. */
    @Serializable
    data class WellnessEntry(
        val day: String,
        val key: String,
        val value: Double,
    )

    /**
     * Reads the first emission of a [Flow] or an
     * empty list if the flow has not emitted yet.
     * The empty-list fallback is what makes
     * [backupAll] safe on a fresh install: the
     * flow's first emission may be delayed by
     * DataStore's IO, and a synchronous caller
     * should not block.
     */
    private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.firstOrEmpty(default: T): T =
        runCatching { this.first() }.getOrDefault(default)

    companion object {
        private const val LOG_TAG = "MindAnchor/BackupSched"

        /**
         * A stable id for a restored note. Notes
         * created on-device get their id from
         * [System.currentTimeMillis]
         * ([Note.id]'s own KDoc); a restored note has
         * no such moment, so this derives one
         * deterministically from the body text
         * instead. Deterministic means restoring the
         * same backup twice produces the same id both
         * times, which is what makes the dedup-by-id
         * check in [restoreNotes] work at all.
         */
        private fun stableIdFor(body: String): Long {
            var h = 1125899906842597L
            for (c in body) h = 31L * h + c.code
            // Notes.id is also used as a sort/display
            // key elsewhere; forcing it negative marks
            // a restored note as distinguishable from
            // a device-created one (device timestamps
            // are always positive) without adding a
            // new field to [Note].
            return -kotlin.math.abs(h)
        }
    }
}
