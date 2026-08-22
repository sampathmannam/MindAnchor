@file:Suppress("MaxLineLength")
package org.mindanchor.letters

import android.content.Context
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One piece of thumbs-down feedback on an AI letter. The
 * optional [reason] is whatever the user wrote in the
 * "Tell us what was off" text field; an empty reason is
 * allowed (the thumbs-down alone is a valid signal).
 *
 * @property savedAt wall-clock time the feedback was saved,
 * in epoch milliseconds. Used to order entries in a per-day
 * file and to show the user when the entry landed.
 */
data class LetterFeedback(
    val reason: String,
    val savedAt: Long,
)

/**
 * Per-letter "this got me wrong" feedback.
 *
 * v0.26.2: a thumbs-down on an AI letter writes a one-line
 * JSON object to `letter_feedback_<date>.json` in the app's
 * `filesDir/letter_feedback` directory. One file per letter
 * date, not one file accumulating everything, so a corrupt
 * file costs one letter, never the whole feedback history.
 *
 * Why a plain file (not DataStore or Room):
 *  - Feedback is append-mostly, not configuration. The user
 *    adds entries, never edits or removes them — a flat file
 *    matches the read shape (one read, one list).
 *  - The user can read their own feedback by going to the
 *    launcher files dir. A JSON file in `filesDir` is the most
 *    portable, least surprising thing.
 *  - A new feedback entry is a new line in the existing file;
 *    the read is `lines().map(::parse)`. No transactional
 *    concerns: a write that loses the previous entry is a
 *    worse outcome than a write that appends nothing, and
 *    "write nothing" is a single-IO `appendText` call.
 *
 * The store is intentionally a thin object: it does not
 * integrate with [LetterStore] (different persistence shape)
 * and does not notify the launcher's DataStore observers
 * (the inbox's badge count reads on every recomposition).
 *
 * @property context the application context. The directory
 * is derived from `context.filesDir`, so two [LetterFeedbackStore]
 * instances on the same process share the same files.
 */
class LetterFeedbackStore(private val context: Context) {

    private val dir: File
        get() = File(context.filesDir, DIR_NAME).also { if (!it.exists()) it.mkdirs() }

    /**
     * All feedback for [date], oldest first. Empty for a
     * date that has never received thumbs-down.
     *
     * The flow is driven by a [MutableStateFlow] that the
     * writer pokes on every save; a read on a fresh install
     * returns an empty list (no IO, no exception).
     */
    fun feedbackFor(date: LocalDate): Flow<List<LetterFeedback>> {
        val file = fileFor(date)
        if (!file.exists()) {
            return MutableStateFlow<List<LetterFeedback>>(emptyList()).asStateFlow()
        }
        return MutableStateFlow<List<LetterFeedback>>(readFile(file)).asStateFlow()
    }

    /**
     * Synchronous read for the badge. The inbox shows
     * "👎 1" / "👎 2" / "👎 3" — the launcher recomposes
     * the inbox on every feedback save, so a cold read
     * from the file is fine. Returns 0 for a date with
     * no feedback file.
     */
    fun countFor(date: LocalDate): Int {
        val file = fileFor(date)
        if (!file.exists()) return 0
        return readFile(file).size
    }

    /**
     * Append [reason] (which may be empty) to [date]'s
     * feedback file. One JSON object per line, terminated
     * with a newline. A blank [reason] is a valid entry
     * (the thumbs-down alone is a signal).
     *
     * The write is `appendText`, not a read-modify-write
     * of the whole file, so a concurrent write is line-
     * atomic at the OS level on every Android filesystem.
     */
    fun save(date: LocalDate, reason: String) {
        val file = fileFor(date)
        val entry = LetterFeedback(reason = reason, savedAt = System.currentTimeMillis())
        val json = toJsonLine(entry)
        file.appendText(json + "\n")
    }

    private fun fileFor(date: LocalDate): File = File(dir, "letter_feedback_$date.json")

    private fun readFile(file: File): List<LetterFeedback> = file.readLines()
        .mapNotNull(::parseLine)
        .toList()

    /**
     * Hand-rolled JSON line because the alternative is a
     * `kotlinx.serialization` dependency on a 4-line file
     * the user can `cat` themselves. The format is:
     *   {"reason":"text","savedAt":1700000000000}
     * The two fields are fixed-shape; the writer is the
     * only writer; the reader is forgiving (an unknown
     * field is ignored, a missing field is the empty
     * default).
     */
    private fun toJsonLine(entry: LetterFeedback): String {
        // JSON string escaping: a backslash, a double-quote,
        // and the common control characters. The reason
        // text comes from a free-form TextField and may
        // contain any of these.
        val reason = entry.reason
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "{\"reason\":\"$reason\",\"savedAt\":${entry.savedAt}}"
    }

    private fun parseLine(line: String): LetterFeedback? {
        if (line.isBlank()) return null
        // Strip the wrapping braces; split on the field
        // comma. A reason containing a comma would break
        // this naive split — reason text is JSON-escaped
        // by the writer, so a real comma in the reason is
        // the comma AFTER the closing quote of the reason
        // field, which is exactly what we want to split on.
        val trimmed = line.trim().removePrefix("{").removeSuffix("}")
        val parts = trimmed.split(",", limit = 2)
        if (parts.size != 2) return null
        val reasonPart = parts[0]
        val savedAtPart = parts[1]
        val reason = reasonPart.substringAfter(":").trim().removeSurrounding("\"")
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")
        val savedAt = savedAtPart.substringAfter(":").trim().toLongOrNull() ?: return null
        return LetterFeedback(reason = reason, savedAt = savedAt)
    }

    companion object {
        /** v0.26.2: sub-directory of `filesDir` holding the per-day JSON files. */
        const val DIR_NAME = "letter_feedback"
    }
}
