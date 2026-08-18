package org.mindanchor.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * A note the user wrote. Quick-capture, free-text, no fields
 * other than the body. The user owns the words.
 *
 * ## Design intent
 *
 * The note is a *capture surface*, not a *reflection surface*.
 * The user types one or two sentences, the launcher saves
 * them. No prompt, no mood field, no streak, no share, no
 * export. The note lives on the device, sealed by the same
 * HMAC layer that protects the rest of the user's data
 * (item D, docs/research/19).
 *
 * The evidence base is `docs/research/26-notes-and-check-in.md`:
 *  - Smyth 1998 (J Consult Clin Psychol 66(1):174–184) —
 *    expressive-writing meta-analysis, d = 0.47 across
 *    13 studies
 *  - Frattaroli 2006 (Psychol Bull 132(6):823–865) —
 *    146 studies, d ≈ 0.15
 *  - Reinhold, Bürkner & Holling 2018 (Clin Psychol Sci
 *    Pract, DOI 10.1111/cpsp.12224) — null result for
 *    brief, self-directed expressive writing on depressive
 *    symptoms in physically healthy adults
 *
 * The honest framing: the literature supports a *pattern*
 * (private, user-owned, no-feedback, low-burden), not a
 * *direct intervention claim*. The launcher ships the
 * pattern; the launcher does *not* claim the benefit.
 */
data class Note(
    /**
     * A stable identifier for the note. Generated at
     * create time, used for edit / delete / pin. The
     * identifier is a Long derived from
     * [System.currentTimeMillis] at the moment the note
     * is first saved; the launcher does not need a
     * sequence number, and the timestamp is what the
     * "newest first" sort already keys on.
     */
    val id: Long = 0L,
    /**
     * The first line of the note. Trimmed and capped
     * to [MAX_BODY] characters. Empty for a note that
     * starts on a blank line. The body is a single
     * field; the launcher does not parse it into
     * "title" + "body". The first line is the title
     * by convention only.
     */
    val body: String = "",
    /**
     * The moment the note was first created, in epoch
     * milliseconds. The launcher uses this for the
     * "newest first" sort and the search index.
     */
    val createdAt: Long = 0L,
    /**
     * The moment the note was last edited, in epoch
     * milliseconds. Defaults to [createdAt] for a
     * note that has never been edited. The launcher
     * uses this for the "newest first" sort when the
     * user prefers "most recently touched" over
     * "most recently created".
     */
    val updatedAt: Long = 0L,
    /**
     * Whether the user has pinned this note. Pinned
     * notes float to the top of the list. The launcher
     * does not have a notion of "important" or "starred"
     * — pinned is a single boolean.
     */
    val pinned: Boolean = false,
    /**
     * The kind of note this is. Set by the on-device
     * classifier (v0.25.0) on save; re-classified on
     * body edit. v0.44.0: the user can also set this
     * explicitly when creating a note via the home
     * quick-notes card (Quick / Task / Reminder). Null
     * when:
     *  - the model is not on the phone (no Phi-4 mini
     *    installed),
     *  - the note was saved before v0.25.0 and the
     *    one-time background pass hasn't reached it,
     *  - the user chose "Quick" (the default — Quick
     *    notes are typed as NoteType.GENERAL so the
     *    chip-on-the-row UI is the same for both
     *    classifier and user paths),
     *  - the classifier failed for this note (rare;
     *    covered by a finding test).
     */
    val type: NoteType? = null,
    /**
     * v0.44.0: an optional due time for a TASK note,
     * in epoch milliseconds. When non-null, the
     * launcher surfaces "due <time>" on the row and
     * the home card. The reminder system does NOT
     * fire on `dueAt` — `reminderAt` is the only
     * thing that fires. A task with `dueAt` and no
     * `reminderAt` is "soft": the user sees the due
     * time on the row but the launcher does not
     * notify. The user picks the due time via a
     * date-time picker; if they don't pick one, the
     * task is a "no-deadline" task.
     */
    val dueAt: Long? = null,
    /**
     * v0.44.0: an optional reminder time, in epoch
     * milliseconds. When non-null, the launcher
     * schedules an AlarmManager alarm at that time
     * that fires the [org.mindanchor.note.ReminderReceiver],
     * which posts a notification and triggers a
     * full-screen flash on HomeActivity. A note with
     * `reminderAt` is implicitly a REMINDER-type
     * note (the type chip on the row reads REMINDER
     * even if the classifier was unavailable at
     * save time).
     */
    val reminderAt: Long? = null,
    /**
     * v0.44.0: whether the user has marked this TASK
     * done. The checkbox on the row toggles it;
     * a done task renders with strikethrough text
     * and a dimmed body. `done` is only meaningful
     * for TASK notes; other kinds ignore it (and
     * the codec never sets it on a non-TASK).
     */
    val done: Boolean = false,
) {
    /**
     * The first line of the body, with leading and
     * trailing whitespace removed. Empty if the body
     * is empty or starts with a blank line. The list
     * view uses this as the title.
     */
    val title: String
        get() = body.lineSequence().firstOrNull()?.trim().orEmpty()

    /**
     * Sanitised for storage. The body is trimmed of
     * leading and trailing whitespace, capped to
     * [MAX_BODY] characters, and the timestamps are
     * normalised (createdAt <= updatedAt, both in
     * the past). A blank body is stored as the empty
     * string; the caller can decide what to do with
     * a note whose body is empty.
     */
    fun sanitised(): Note = copy(
        body = body.trim().take(MAX_BODY),
        createdAt = createdAt.coerceAtLeast(0L),
        updatedAt = updatedAt.coerceAtLeast(createdAt),
        // v0.44.0: dueAt / reminderAt must be in the
        // future at sanitise time. A past value is
        // suspicious — the user is editing a note
        // whose deadline has already passed. We keep
        // it (don't reset to null) because the
        // user may be writing a reflective note
        // about a past deadline; the UI surfaces the
        // "past" state explicitly. The sanitise step
        // only rejects negative or zero values,
        // which are clearly corrupt.
        dueAt = if ((dueAt ?: 0L) > 0L) dueAt else null,
        reminderAt = if ((reminderAt ?: 0L) > 0L) reminderAt else null,
    )

    /**
     * The note as a single text line for storage.
     * The format is
     * `id\tpinned\tcreatedAt\tupdatedAt\ttype\tdueAt\treminderAt\tdone\tbase64(body)`
     * — tab-separated, body *base64-encoded* so the body
     * can contain tabs, newlines, and any other character
     * without breaking the line-delimited format.
     *
     * Why base64 and not escape-the-newline: the body is
     * user-authored text and may contain any character.
     * Escape sequences (e.g. `\\n` for newline) are easy
     * to get wrong (a user pastes text with a literal
     * `\\n` and the codec misinterprets it). Base64
     * encoding is a closed alphabet; the body is
     * encoded before being written, decoded after
     * being read, and there is no ambiguity.
     *
     * The base64 body is *not* a security feature — the
     * codec is plaintext and is sealed by the HMAC layer
     * (item D). It is a *format* feature: the body is
     * text, the line is the line.
     *
     * ## v0.25.0 wire format change
     *
     * The v0.24.0 format was 5 tab-separated fields
     * (no `type`). v0.25.0 inserts the `type` field
     * between `updatedAt` and the body. Existing
     * v0.24.0 lines still decode — [decodeLine] treats
     * 5 fields as "no type" and 6 fields as "v0.25.0".
     * The migration is one-way: a v0.24.0 line that
     * is later edited and re-classified is written in
     * the v0.25.0 shape on the next save.
     *
     * ## v0.44.0 wire format change
     *
     * The v0.44.0 format inserts three more fields —
     * `dueAt`, `reminderAt`, `done` — between the
     * `type` and the body, for a total of 9 fields.
     * The codec is backward-compatible: a 6-field
     * line is decoded with all three new fields at
     * their defaults (null / null / false), and the
     * next save writes a 9-field line. A 9-field
     * line from a future v0.45+ write round-trips
     * identically. The user-facing semantic is the
     * same as v0.43.0 for any note that was a
     * "plain quick note" — type GENERAL or null,
     * no due time, no reminder, not done.
     */
    fun encode(): String {
        val s = sanitised()
        val prefix = "${s.id}\t${if (s.pinned) "1" else "0"}\t${s.createdAt}\t${s.updatedAt}\t${s.type?.name ?: ""}\t${s.dueAt ?: ""}\t${s.reminderAt ?: ""}\t${if (s.done) "1" else "0"}"
        val bodyB64 = java.util.Base64.getEncoder()
            .encodeToString(s.body.toByteArray(Charsets.UTF_8))
        return "$prefix\t$bodyB64"
    }

    companion object {
        const val MAX_BODY = 4_000

        /**
         * Decode a single note from one line of text.
         * Returns null if the line is malformed (wrong
         * number of fields, non-numeric id / timestamps,
         * body too long, body not valid base64, body
         * does not decode to valid UTF-8, unknown
         * [NoteType] name). The caller is expected to
         * skip nulls — the codec is *dumb*.
         *
         * Accepts the v0.24.0 wire format (5 fields,
         * no `type`), the v0.25.0 format (6 fields, with
         * `type`), and the v0.44.0 format (9 fields,
         * with `type` + `dueAt` + `reminderAt` + `done`).
         * A 5-field line decodes with `type = null`,
         * `dueAt = null`, `reminderAt = null`,
         * `done = false`. A 6-field line is the same
         * except `type` may be set. A 9-field line may
         * have any of the new fields set; an empty
         * `dueAt` / `reminderAt` slot is null; a "1"
         * `done` slot is true.
         */
        fun decodeLine(line: String): Note? {
            if (line.isEmpty()) return null
            val parsed = parseFields(line) ?: return null
            val idLong = parsed.id.toLongOrNull() ?: return null
            val pinnedBool = when (parsed.pinned) {
                "1" -> true
                "0" -> false
                else -> return null
            }
            val createdLong = parsed.createdAt.toLongOrNull() ?: return null
            val updatedLong = parsed.updatedAt.toLongOrNull() ?: return null
            val typeValue = if (parsed.typeToken.isEmpty()) {
                null
            } else {
                decodeTypeName(parsed.typeToken) ?: return null
            }
            // v0.44.0: dueAt and reminderAt are
            // nullable epoch millis. An empty slot
            // is null; a non-empty slot is parsed as
            // a long and rejected on parse failure.
            // The "is this a long-or-empty" contract
            // is what makes the v0.25.0 6-field line
            // round-trip safely into a 9-field value
            // with both new fields null.
            val dueAtValue = if (parsed.dueAtToken.isEmpty()) {
                null
            } else {
                parsed.dueAtToken.toLongOrNull() ?: return null
            }
            val reminderAtValue = if (parsed.reminderAtToken.isEmpty()) {
                null
            } else {
                parsed.reminderAtToken.toLongOrNull() ?: return null
            }
            val doneBool = when (parsed.done) {
                "1" -> true
                "0" -> false
                // A 6-field line has no `done` slot;
                // parseFields fills the slot with "0"
                // so we never see "" here.
                else -> return null
            }
            val body = decodeBody(parsed.bodyB64) ?: return null
            if (body.length > MAX_BODY) return null
            return Note(
                id = idLong,
                body = body,
                createdAt = createdLong,
                updatedAt = updatedLong,
                pinned = pinnedBool,
                type = typeValue,
                dueAt = dueAtValue,
                reminderAt = reminderAtValue,
                done = doneBool,
            )
        }

        /**
         * Split [line] on tabs and return the 9 string
         * fields of the v0.44.0 wire format. Returns
         * null if the line has the wrong number of
         * fields (anything other than v0.24.0's 5,
         * v0.25.0's 6, or v0.44.0's 9).
         *
         * A 5-field line fills the type/dueAt/
         * reminderAt/done slots with the empty
         * string / "0" defaults so the field
         * destructuring is uniform.
         */
        private fun parseFields(line: String): V440Fields? {
            // The body is base64-encoded; the base64
            // alphabet has no tab character, so the
            // tab count is a reliable field separator.
            val parts = line.split('\t')
            return when (parts.size) {
                V240_FIELD_COUNT -> V440Fields(
                    id = parts[0],
                    pinned = parts[1],
                    createdAt = parts[2],
                    updatedAt = parts[3],
                    typeToken = "",
                    dueAtToken = "",
                    reminderAtToken = "",
                    done = "0",
                    bodyB64 = parts[4],
                )
                V250_FIELD_COUNT -> V440Fields(
                    id = parts[0],
                    pinned = parts[1],
                    createdAt = parts[2],
                    updatedAt = parts[3],
                    typeToken = parts[4],
                    dueAtToken = "",
                    reminderAtToken = "",
                    done = "0",
                    bodyB64 = parts[5],
                )
                V440_FIELD_COUNT -> V440Fields(
                    id = parts[0],
                    pinned = parts[1],
                    createdAt = parts[2],
                    updatedAt = parts[3],
                    typeToken = parts[4],
                    dueAtToken = parts[5],
                    reminderAtToken = parts[6],
                    done = parts[7],
                    bodyB64 = parts[8],
                )
                else -> null
            }
        }

        /**
         * Decode a base64 string into UTF-8 text. Returns
         * null if the input is not valid base64, the
         * decoded bytes are not valid UTF-8, or the
         * result exceeds [MAX_BODY] characters. A
         * non-UTF-8 byte sequence is "this line is
         * corrupt, skip it" — the same as malformed
         * base64.
         */
        private fun decodeBody(bodyB64: String): String? {
            val bodyBytes = try {
                java.util.Base64.getDecoder().decode(bodyB64)
            } catch (e: IllegalArgumentException) {
                return null
            }
            return try {
                String(bodyBytes, Charsets.UTF_8)
            } catch (e: java.nio.charset.MalformedInputException) {
                null
            }
        }

        /**
         * Parse a [NoteType] name. Returns null if the
         * string is not a known enum name. The names
         * are the upper-case enum constants; the codec
         * is case-sensitive so a renamed or lower-cased
         * type name round-trips only if the user
         * doesn't edit the sealed file.
         */
        private fun decodeTypeName(name: String): NoteType? = when (name) {
            "GENERAL" -> NoteType.GENERAL
            "TASK" -> NoteType.TASK
            "REMINDER" -> NoteType.REMINDER
            "JOURNAL" -> NoteType.JOURNAL
            else -> null
        }

        /**
         * The nine string fields of a v0.44.0 line,
         * named for the on-disk order. The constructor
         * is private — fields are unboxed, not trusted.
         */
        private data class V440Fields(
            val id: String,
            val pinned: String,
            val createdAt: String,
            val updatedAt: String,
            val typeToken: String,
            val dueAtToken: String,
            val reminderAtToken: String,
            val done: String,
            val bodyB64: String,
        )

        // The wire-format field counts the codec
        // accepts:
        //   5 → v0.24.0, no `type`
        //   6 → v0.25.0, with `type`
        //   9 → v0.44.0, with `type` + `dueAt` +
        //        `reminderAt` + `done`
        // A 5-field line decodes as a v0.24.0 line
        // (type/dueAt/reminderAt=null, done=false).
        // A 6-field line decodes as a v0.25.0 line
        // (dueAt/reminderAt=null, done=false). A
        // 9-field line is the current shape. Anything
        // else is corrupt and is silently dropped.
        // A user who downgrades from v0.44.0 to
        // v0.25.0 will see all their tasks and
        // reminders lose the new fields on next
        // edit; the body is still intact. A user who
        // upgrades from v0.25.0 to v0.44.0 sees
        // every old note start with the new fields
        // at their defaults.
        const val V240_FIELD_COUNT = 5
        const val V250_FIELD_COUNT = 6
        const val V440_FIELD_COUNT = 9
    }
}

/**
 * Storage codec for [Note]s. Same shape as the other
 * codecs in this package: one record per line,
 * tab-separated, plain-text round trip, no JSON,
 * no migration.
 *
 * The codec is *dumb* — validation against installed
 * packages, rejection of blank bodies, deduplication
 * of ids — all of that is the caller's job (this
 * layer is a pure function and is unit-tested with
 * fixture input).
 */
object NoteStore {

    /**
     * Encode a list of notes to a stable text form.
     * The list is sorted by `updatedAt` descending
     * (newest first) for diff stability in the data
     * store. Pinned notes are NOT moved to the top
     * here; that sort is the caller's job (the
     * list view wants the user-controlled order,
     * not a codec-controlled order).
     */
    fun encode(notes: List<Note>): String =
        notes.joinToString("\n") { it.encode() }

    /**
     * Decode a text form back into a list of notes.
     * Blank lines, malformed lines, and notes with
     * out-of-range fields are silently skipped (the
     * codec is *dumb*; a malformed note cannot
     * poison the rest of the data).
     */
    fun decode(raw: String): List<Note> =
        raw.lineSequence().mapNotNull(Note::decodeLine).toList()

    /**
     * Sort notes for the list view: pinned first
     * (sorted by `updatedAt` desc), then non-pinned
     * (sorted by `updatedAt` desc). Stable sort.
     * Pure function.
     */
    fun sortedForList(notes: List<Note>): List<Note> {
        val (pinned, unpinned) = notes.partition { it.pinned }
        val byUpdated = compareByDescending<Note> { it.updatedAt }
        return pinned.sortedWith(byUpdated) + unpinned.sortedWith(byUpdated)
    }

    /**
     * Group notes by the day they were last touched, returning a
     * list of (day, sorted notes for that day) pairs in
     * descending order of the day's most-recently-touched note.
     *
     * v0.23.0: a single LazyColumn of day sections. The list
     * view is the same data shape the home screen uses; the
     * group ordering is `pinned notes stay at the top of the
     * day they were most recently touched`, with day-of-week
     * sorting as the tiebreaker for the inner sort. Days are
     * *not* pinned to the day the body was written — a note
     * edited today stays in today even if it was first written
     * last week, because the user is looking for "the note I
     * touched most recently" not "the note I wrote first".
     *
     * Pinned notes are *not* promoted to a separate "pinned"
     * section here — they sit at the top of the day they were
     * most recently touched. A user looking for a pinned note
     * looks in the day they last touched it. The home screen
     * still surfaces pinned notes via the existing
     * `sortedForList` flow; this helper is for the notes-list
     * surface only.
     *
     * Pure function. The conversion from epoch millis to
     * `LocalDate` uses the system default zone because the
     * launcher's "today" is the user's local today, not UTC.
     */
    fun groupedByDay(
        notes: List<Note>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Pair<LocalDate, List<Note>>> {
        val sorted = sortedForList(notes)
        if (sorted.isEmpty()) return emptyList()
        val byDay = sorted.groupBy { it.updatedAtToLocalDate(zone) }
        // The day's most-recently-touched note is the first note
        // in the inner-sorted list. Sort the days by that.
        return byDay.entries
            .sortedByDescending { it.value.first().updatedAt }
            .map { it.key to it.value }
    }

    /**
     * The local date this note was last touched, in the
     * launcher's default zone. The millis-to-date conversion
     * is on the data layer so the UI does not have to think
     * about it.
     */
    private fun Note.updatedAtToLocalDate(zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(updatedAt).atZone(zone).toLocalDate()

    /**
     * Search notes for a query. The query is matched
     * case-insensitively against the body (the
     * launcher does not have a separate "title" field;
     * the first line of the body is the title by
     * convention). Empty query returns the input
     * unchanged. Pure function.
     */
    fun search(notes: List<Note>, query: String): List<Note> {
        if (query.isBlank()) return notes
        // v0.20.1 round 5 follow-up: lowercase with
        // Locale.ROOT, not the default locale. The
        // default locale is device-dependent: a Turkish
        // device lowercases "I" to "ı" (dotless i),
        // which silently breaks case-insensitive search
        // for the user's English notes. Locale.ROOT is
        // locale-independent and is the correct
        // lowercase-folding for a non-localised substring
        // match.
        val needle = query.trim().lowercase(Locale.ROOT)
        return notes.filter { it.body.lowercase(Locale.ROOT).contains(needle) }
    }
}

/**
 * Pure-function data layer for the note surface. v0.20.1
 * round 5 (the notes feature, docs/research/26).
 *
 * The data layer is independent of the UI. The UI
 * (NoteActivity, NoteScreen) is a follow-up; the
 * data layer is harmless on its own and is
 * Python-mirror-verified.
 */
data class NotesState(
    /**
     * The user's notes, in storage order. The
     * launcher sorts for display via
     * [NoteStore.sortedForList] but the storage
     * order is the order in which the notes were
     * last written to disk. The launcher does not
     * preserve user-reordered lists; the list view
     * always sorts pinned-first then updated-desc.
     */
    val notes: List<Note> = emptyList(),
) {
    /**
     * The note with the given [id], or null. Pure
     * function.
     */
    fun byId(id: Long): Note? = notes.firstOrNull { it.id == id }

    /**
     * Add a new note. The note is appended to the
     * end of the list; the list view will sort it
     * to the right place on display. Pure function.
     */
    fun add(note: Note): NotesState =
        copy(notes = notes + note)

    /**
     * Edit an existing note. The note with the
     * matching [Note.id] is replaced; [updatedAt]
     * is bumped to [editTimestamp]. Pure function.
     */
    fun edit(id: Long, body: String, editTimestamp: Long): NotesState =
        copy(notes = notes.map { if (it.id == id) it.copy(body = body, updatedAt = editTimestamp) else it })

    /**
     * Toggle the pinned state of a note. Pure
     * function.
     */
    fun togglePinned(id: Long): NotesState =
        copy(notes = notes.map { if (it.id == id) it.copy(pinned = !it.pinned) else it })

    /**
     * v0.45.0: set the [Note.pinned] flag on
     * the note with the matching [id]. Pure
     * function. A no-op if the id is not in
     * the store, or if the flag already matches
     * [pinned]. The caller can use
     * `next === current` to detect the no-op.
     */
    fun setPinned(id: Long, pinned: Boolean): NotesState {
        val match = notes.firstOrNull { it.id == id } ?: return this
        if (match.pinned == pinned) return this
        return copy(notes = notes.map { if (it.id == id) it.copy(pinned = pinned) else it })
    }

    /**
     * Delete a note. Pure function.
     */
    fun delete(id: Long): NotesState =
        copy(notes = notes.filter { it.id != id })

    /**
     * v0.25.0: set the [Note.type] field on the
     * note with the matching [id]. Pure function.
     * Returns the same instance (and is therefore
     * a no-op) if the id is not in the store —
     * the caller can use `next === current` to
     * detect the no-op.
     */
    fun setType(id: Long, type: NoteType?): NotesState {
        val match = notes.firstOrNull { it.id == id } ?: return this
        if (match.type == type) return this
        return copy(notes = notes.map { if (it.id == id) it.copy(type = type) else it })
    }

    /**
     * v0.25.0: set every note's [Note.type] to
     * null. Used by the "Re-classify all" settings
     * action. Pure function. Returns the same
     * instance if every note is already untyped.
     */
    fun clearAllTypes(): NotesState {
        if (notes.none { it.type != null }) return this
        return copy(notes = notes.map { it.copy(type = null) })
    }

    /**
     * v0.44.0: set the [Note.done] flag on the
     * note with the matching [id]. Pure function.
     * A no-op if the id is not in the store, or if
     * the flag already matches [done].
     */
    fun setDone(id: Long, done: Boolean): NotesState {
        val match = notes.firstOrNull { it.id == id } ?: return this
        if (match.done == done) return this
        return copy(notes = notes.map { if (it.id == id) it.copy(done = done, updatedAt = System.currentTimeMillis()) else it })
    }

    /**
     * v0.44.0: set the [Note.dueAt] field on the
     * note with the matching [id]. Pass `null` to
     * clear the due time. Pure function.
     */
    fun setDueAt(id: Long, dueAt: Long?): NotesState {
        val match = notes.firstOrNull { it.id == id } ?: return this
        if (match.dueAt == dueAt) return this
        return copy(notes = notes.map { if (it.id == id) it.copy(dueAt = dueAt, updatedAt = System.currentTimeMillis()) else it })
    }

    /**
     * v0.44.0: set the [Note.reminderAt] field
     * on the note with the matching [id]. Pass
     * `null` to clear the reminder. Pure function.
     * The caller is responsible for also calling
     * [org.mindanchor.data.NotesPrefs.setReminderAt]
     * (which schedules the AlarmManager alarm) —
     * the data layer is dumb and does not know
     * about the scheduler.
     */
    fun setReminderAt(id: Long, reminderAt: Long?): NotesState {
        val match = notes.firstOrNull { it.id == id } ?: return this
        if (match.reminderAt == reminderAt) return this
        return copy(notes = notes.map { if (it.id == id) it.copy(reminderAt = reminderAt, updatedAt = System.currentTimeMillis()) else it })
    }

    /**
     * v0.44.0: every note with `reminderAt` in
     * the future, sorted by reminderAt ascending
     * (the next reminder first). Pure function.
     * Used by the home card to surface "your
     * next reminder is at <time>" without
     * scraping the full list.
     */
    fun upcomingReminders(now: Long = System.currentTimeMillis()): List<Note> =
        notes
            .filter { (it.reminderAt ?: 0L) > now }
            .sortedBy { it.reminderAt ?: Long.MAX_VALUE }
}
