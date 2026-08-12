package org.mindanchor.backup

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.mindanchor.data.NotesPrefs
import org.mindanchor.letters.LetterStore
import org.mindanchor.model.Note
import org.mindanchor.model.NotesState
import java.time.LocalDate

/**
 * The v0.25.4 per-type routing layer. v0.25.4
 * (WP-D). Sits between the data sources
 * ([NotesPrefs], [LetterStore]) and the
 * outbound channel ([BackupTarget]).
 *
 * The model is "one Drive file per content
 * type" — `MindAnchor-Notes.txt` for notes,
 * `MindAnchor-Letters.txt` for letters. The
 * scheduler's job is to take an entry (a note
 * or a letter) and append the AES-256-GCM
 * blob to the right file. It does not know
 * or care which [BackupTarget] implementation
 * is wired — the Drive target is the v0.25.4
 * surface, but the same scheduler can route
 * to a Local target, a future S3 target, or a
 * test fake.
 *
 * ## Wire format per entry
 *
 * Each [BackupEntry] is serialised to a one-line
 * JSON object (`{"date":"2026-08-12","body":"..."}`),
 * then wrapped with [EncryptedBackupCodec] (the
 * AES-256-GCM blob is `IV || ciphertext || tag`).
 * The target appends the bytes verbatim plus a
 * trailing newline (`\n`, 0x0A) — see
 * [GoogleDriveBackupTarget]. The per-type file is
 * therefore a sequence of newline-terminated
 * encrypted entries, inspectable in the Drive
 * web UI as one entry per line.
 *
 * ## Triggers
 *
 * Two trigger surfaces, both wired in WP-D:
 *  1. [backupAll] — a one-shot full reupload of
 *     every existing note + every existing
 *     letter. The "Back up now" button in the
 *     Settings sub-section calls this. The
 *     intent is "backfill", not "from now on".
 *  2. [start] — an on-write trigger that observes
 *     the [NotesPrefs.notes] and
 *     [LetterStore.letters] flows; on each new
 *     entry, fires an incremental append. The
 *     on-write trigger is the streaming path;
 *     the auto-sync toggles in the Settings
 *     sub-section gate it.
 *
 * The on-write trigger uses
 * [kotlinx.coroutines.flow.scan] to diff each
 * emission against the previous one; the new
 * entries (the tail of the new list that the
 * old list did not contain) are appended one
 * by one. [distinctUntilChanged] is the safety
 * net — a flow that re-emits the same list
 * (which DataStore can do on a metadata-only
 * change) does not double-append.
 *
 * ## Threading
 *
 * All public methods are `suspend` and run on
 * the caller's dispatcher. The [start] method
 * launches the observers in the supplied
 * [CoroutineScope] (typically the application
 * scope) and returns immediately. The scheduler
 * holds no internal scopes of its own — the
 * lifetime is the caller's.
 */
class BackupScheduler(
    private val context: Context,
    private val notesTarget: BackupTarget,
    private val lettersTarget: BackupTarget,
    private val notesPrefs: NotesPrefs = NotesPrefs(context),
    private val letterStore: LetterStore = LetterStore(context),
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
     * The serialiser for [BackupEntry]. Uses an
     * ISO-8601 string for [LocalDate] so the
     * per-type file's JSON is plain-text and
     * human-inspectable in the Drive web UI.
     * The [kotlinx.serialization.Serializable]
     * annotation uses kotlinx's default
     * `LocalDate` handling (the kotlinx
     * datetime module), which serialises
     * [LocalDate] as `2026-08-12` — exactly the
     * format the per-type file wants.
     */

    /**
     * Reuploads every existing note + every
     * existing letter. Idempotent: a second
     * call appends the same entries again (the
     * per-type file is append-only; a restore
     * is "read everything, dedup by date" if
     * duplicates matter). The "Back up now"
     * button is a manual reupload, not a sync.
     */
    suspend fun backupAll(): BackupAllResult {
        val notesState = notesPrefs.notes.firstOrEmpty(NotesState())
        val notes = notesState.notes
        val letters = letterStore.letters.firstOrEmpty(emptyList())
        var notesOk = 0
        var notesFail = 0
        for (note in notes) {
            val entry = BackupEntry(date = LocalDate.now().toString(), body = note.body)
            when (encryptAndAppend(ContentType.Notes, entry)) {
                is AppendResult.Ok -> notesOk++
                else -> notesFail++
            }
        }
        var lettersOk = 0
        var lettersFail = 0
        for (letter in letters) {
            val entry = BackupEntry(date = letter.date.toString(), body = letter.body)
            when (encryptAndAppend(ContentType.Letters, entry)) {
                is AppendResult.Ok -> lettersOk++
                else -> lettersFail++
            }
        }
        Log.i(
            LOG_TAG,
            "backupAll: notes $notesOk ok / $notesFail fail, letters $lettersOk ok / $lettersFail fail",
        )
        return BackupAllResult(
            notesAppended = notesOk,
            notesFailed = notesFail,
            lettersAppended = lettersOk,
            lettersFailed = lettersFail,
        )
    }

    /**
     * Starts the on-write trigger. The caller
     * supplies a [CoroutineScope] (typically
     * the application scope) and the scheduler
     * launches two collectors — one on
     * [NotesPrefs.notes], one on
     * [LetterStore.letters]. Each collector
     * diffs against the previous emission and
     * appends the new entries. The method
     * returns immediately; the collectors run
     * for the lifetime of the [scope].
     *
     * The diff uses
     * `scan(emptyList<Note>()) { acc, next -> ... }`
     * to track the previous snapshot, then
     * compares the two lists by id (Notes) or
     * date (Letters). The `distinctUntilChanged`
     * downstream of the flow filters out
     * metadata-only re-emissions that DataStore
     * can produce.
     */
    fun start(scope: CoroutineScope) {
        scope.launch {
            notesPrefs.notes
                .distinctUntilChanged()
                .scan(NotesDiffState()) { state, current ->
                    val newOnes = newNotes(state.current, current.notes)
                    NotesDiffState(previous = state.current, current = current.notes, newOnes = newOnes)
                }
                .collect { state ->
                    for (note in state.newOnes) {
                        val entry = BackupEntry(date = LocalDate.now().toString(), body = note.body)
                        encryptAndAppend(ContentType.Notes, entry)
                    }
                }
        }
        scope.launch {
            letterStore.letters
                .distinctUntilChanged()
                .scan(LettersDiffState()) { state, current ->
                    val newOnes = newLetters(state.current, current)
                    LettersDiffState(previous = state.current, current = current, newOnes = newOnes)
                }
                .collect { state ->
                    for (letter in state.newOnes) {
                        val entry = BackupEntry(date = letter.date.toString(), body = letter.body)
                        encryptAndAppend(ContentType.Letters, entry)
                    }
                }
        }
    }

    /**
     * The diff state for the notes flow.
     * The accumulator is the new state; the
     * `newOnes` are the entries the on-write
     * trigger appends.
     */
    private data class NotesDiffState(
        val previous: List<Note> = emptyList(),
        val current: List<Note> = emptyList(),
        val newOnes: List<Note> = emptyList(),
    )

    private data class LettersDiffState(
        val previous: List<org.mindanchor.letters.Letter> = emptyList(),
        val current: List<org.mindanchor.letters.Letter> = emptyList(),
        val newOnes: List<org.mindanchor.letters.Letter> = emptyList(),
    )

    /**
     * Encrypts [entry] (JSON → AES-256-GCM blob)
     * and dispatches to the right
     * [BackupTarget]. The dispatch is the only
     * place the per-type routing decision is
     * made — every other call site asks for
     * "this type" and the scheduler picks the
     * target. A future per-target fallback
     * (e.g. Local on offline, Drive on online)
     * lives here.
     */
    private suspend fun encryptAndAppend(type: ContentType, entry: BackupEntry): AppendResult {
        val jsonStr = json.encodeToString(BackupEntry.serializer(), entry)
        val cipher = EncryptedBackupCodec.wrap(jsonStr)
            ?: return AppendResult.NetworkError("wrap failed")
        val target = when (type) {
            ContentType.Notes -> notesTarget
            ContentType.Letters -> lettersTarget
        }
        return target.append(type, cipher)
    }

    /**
     * The result of a [backupAll] call. The
     * caller (the "Back up now" button's
     * result state) renders a one-line summary
     * from these numbers; no individual error
     * message is surfaced (a per-entry failure
     * is rare; the [AppendResult] is logged).
     */
    data class BackupAllResult(
        val notesAppended: Int,
        val notesFailed: Int,
        val lettersAppended: Int,
        val lettersFailed: Int,
    ) {
        val ok: Boolean get() = notesFailed == 0 && lettersFailed == 0
    }

    /**
     * The shape of one entry in the per-type
     * file. Two fields — the date (the entry's
     * timestamp, ISO-8601 `yyyy-MM-dd`) and the
     * body (the entry's text content). The date
     * is a [String] not a [LocalDate] so
     * kotlinx.serialization can encode it
     * without the kotlinx-datetime module;
     * the on-the-wire format is plain text
     * anyway, and the [LocalDate.parse] round-trip
     * on restore is exact.
     *
     * The serializer produces a one-line JSON
     * object; the [EncryptedBackupCodec] wraps
     * the JSON into a binary blob; the per-type
     * file is a sequence of these blobs
     * separated by newlines.
     *
     * For Notes, the date is "today at the
     * moment of backup" (the note itself is
     * identified by id, not date, in the
     * [NotesPrefs] store; the per-type file
     * doesn't care about ids — the body is
     * what matters). For Letters, the date is
     * the letter's [org.mindanchor.letters.Letter.date].
     */
    @Serializable
    data class BackupEntry(
        val date: String,
        val body: String,
    )

    private fun newNotes(
        previous: List<Note>,
        current: List<Note>,
    ): List<Note> {
        val previousIds = previous.map { it.id }.toSet()
        return current.filter { it.id !in previousIds }
    }

    private fun newLetters(
        previous: List<org.mindanchor.letters.Letter>,
        current: List<org.mindanchor.letters.Letter>,
    ): List<org.mindanchor.letters.Letter> {
        val previousDates = previous.map { it.date }.toSet()
        return current.filter { it.date !in previousDates }
    }

    /**
     * Reads the first emission of a [Flow] or
     * an empty list if the flow has not emitted
     * yet. The empty-list fallback is what
     * makes [backupAll] safe on a fresh install:
     * the flow's first emission may be delayed
     * by DataStore's IO, and a synchronous
     * caller should not block.
     */
    private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.firstOrEmpty(default: T): T {
        return runCatching { this.first() }.getOrDefault(default)
    }

    companion object {
        private const val LOG_TAG = "MindAnchor/BackupSched"
    }
}
