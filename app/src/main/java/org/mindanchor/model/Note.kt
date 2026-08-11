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
    )

    /**
     * The note as a single text line for storage.
     * The format is `id\tpinned\tcreatedAt\tupdatedAt\tbase64(body)`
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
     */
    fun encode(): String {
        val s = sanitised()
        val prefix = "${s.id}\t${if (s.pinned) "1" else "0"}\t${s.createdAt}\t${s.updatedAt}"
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
         * does not decode to valid UTF-8). The caller
         * is expected to skip nulls — the codec is
         * *dumb*.
         */
        fun decodeLine(line: String): Note? {
            if (line.isEmpty()) return null
            // The body is everything after the 4th tab.
            // Use indexOf 4 times rather than split('\t')
            // so the body can contain tabs (the base64
            // alphabet includes / and + but never \t).
            var idx = -1
            val tabPositions = IntArray(4)
            for (i in 0 until 4) {
                idx = line.indexOf('\t', idx + 1)
                if (idx < 0) return null
                tabPositions[i] = idx
            }
            val idStr = line.substring(0, tabPositions[0])
            val pinnedStr = line.substring(tabPositions[0] + 1, tabPositions[1])
            val createdStr = line.substring(tabPositions[1] + 1, tabPositions[2])
            val updatedStr = line.substring(tabPositions[2] + 1, tabPositions[3])
            val bodyB64 = line.substring(tabPositions[3] + 1)
            val id = idStr.toLongOrNull() ?: return null
            val pinned = when (pinnedStr) {
                "1" -> true
                "0" -> false
                else -> return null
            }
            val createdAt = createdStr.toLongOrNull() ?: return null
            val updatedAt = updatedStr.toLongOrNull() ?: return null
            // Decode the body. A malformed base64 line
            // returns null; a valid base64 line that
            // decodes to a non-UTF-8 byte sequence
            // throws — both are "this line is corrupt,
            // skip it" cases.
            val bodyBytes = try {
                java.util.Base64.getDecoder().decode(bodyB64)
            } catch (e: IllegalArgumentException) {
                return null
            }
            val body = try {
                String(bodyBytes, Charsets.UTF_8)
            } catch (e: java.nio.charset.MalformedInputException) {
                return null
            }
            if (body.length > MAX_BODY) return null
            return Note(
                id = id,
                body = body,
                createdAt = createdAt,
                updatedAt = updatedAt,
                pinned = pinned,
            )
        }
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
     * Delete a note. Pure function.
     */
    fun delete(id: Long): NotesState =
        copy(notes = notes.filter { it.id != id })
}
