package org.mindanchor.vitals

import java.time.Instant

/**
 * One PPG session — when it started, when it finished, the mean heart
 * rate the camera derived, and the duration the user actually spent
 * with their finger on the lens.
 *
 * v0.25.5 WP-D: local-only telemetry for the act of measuring. The
 * reading itself is what the report reads; the session log is what
 * makes the *act* of measuring visible — "the last three times you
 * sat down with this, you got 45-50 second readings at 70-74 bpm"
 * is the answer to a question the user is allowed to ask.
 *
 * Nothing leaves the device. The log is the same shape as the rest
 * of the app's preferences — newline-separated, plain text, one
 * session per line.
 */
data class PpgSession(
    /** When the camera started recording luma samples. */
    val start: Instant,
    /** When the session finished (success, user-stop, or quality-gate reject). */
    val end: Instant,
    /** The mean heart rate the camera derived, in bpm. Null when the gate rejected. */
    val meanHr: Double?,
) {
    /** Seconds the session ran. Always non-negative. */
    val durationSeconds: Long
        get() = (end.toEpochMilli() - start.toEpochMilli()) / 1000L
}

/**
 * The newline + tab-separated codec for [PpgSession]s. Same discipline
 * as [MeasuredLedger] and the rest of this app's local-only stores:
 * one corrupt line costs one entry and never the file.
 *
 * Wire format: one session per line, four tab-separated fields:
 *   `startIso<TAB>endIso<TAB>meanHrOrEmpty<TAB>durationSeconds`
 *
 * The `durationSeconds` is stored alongside the start/end so a session
 * can be read without a clock — a regression that called
 * `(end - start)` would be the right answer, but a stored value makes
 * the read-side pure and side-steps the Instant comparison entirely.
 */
object PpgSessionLog {

    fun encode(sessions: List<PpgSession>): String =
        sessions.joinToString("\n") { session ->
            "${session.start}\t${session.end}\t${session.meanHr ?: ""}\t${session.durationSeconds}"
        }

    /** Bad lines are dropped, never thrown on. */
    fun decode(raw: String): List<PpgSession> =
        raw.lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 4) return@mapNotNull null
            val start = runCatching { Instant.parse(parts[0]) }.getOrNull() ?: return@mapNotNull null
            val end = runCatching { Instant.parse(parts[1]) }.getOrNull() ?: return@mapNotNull null
            // A non-blank meanHr that does not parse is corrupt — drop
            // the line. A blank meanHr is the legitimate "no bpm" case.
            val meanHr = when {
                parts[2].isBlank() -> null
                else -> parts[2].toDoubleOrNull() ?: return@mapNotNull null
            }
            val storedDuration = parts[3].toLongOrNull() ?: return@mapNotNull null
            // Trust the stored duration; the read side never has to do
            // an Instant diff. A line that *says* a session is negative
            // is corrupt — drop it.
            if (storedDuration < 0L) return@mapNotNull null
            PpgSession(start = start, end = end, meanHr = meanHr)
        }.toList()
}
