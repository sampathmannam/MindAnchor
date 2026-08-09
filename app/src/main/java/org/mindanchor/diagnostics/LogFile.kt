package org.mindanchor.diagnostics

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * The on-device log file. The project has no
 * cloud, no crash reporter, no analytics SDK; the
 * local log is the only diagnostic surface.
 *
 * Format: one line per record, four tab-separated
 * fields:
 *   timestamp<TAB>level<TAB>tag<TAB>message
 *
 * The file is rotated at 1 MB. The five most
 * recent files are kept in `cacheDir/logs/`; older
 * files are deleted. The rotation policy is
 * bounded — 1 MB × 5 = 5 MB on disk — so the log
 * cannot grow without bound on a no-cloud app.
 *
 * The [append] method is thread-safe via the JVM
 * `synchronized` block; the log file is a
 * single-writer, multi-reader resource. The
 * [ShareLogsEntryPoint] is the reader; the
 * friction gate, the pulse flow, and the bedtime
 * list are the writers.
 *
 * @wording-reviewed — the share-intent text and
 * the "what's in this file" note in settings are
 * clinical-review surfaces.
 */
class LogFile(private val logDir: File) {

    /**
     * The active log file. The path is
     * `logDir/log-current.txt`; rotation renames
     * the current file to `log-1.txt` and the
     * older files shift down (log-2, log-3, ...).
     */
    val current: File
        get() = File(logDir, "log-current.txt")

    init {
        logDir.mkdirs()
    }

    /**
     * Append a single record. The format is
     * `timestamp<TAB>level<TAB>tag<TAB>message`.
     * The message is sanitized: control characters
     * (tab, newline, carriage return) are replaced
     * with spaces so a multi-line message cannot
     * corrupt the line-oriented format.
     */
    @Synchronized
    fun append(
        level: Level,
        tag: String,
        message: String,
        now: Long = System.currentTimeMillis(),
    ) {
        if (current.length() > MAX_FILE_BYTES) rotate()
        val safeMessage = message
            .replace('\t', ' ')
            .replace('\n', ' ')
            .replace('\r', ' ')
        val line = "$now\t$level\t$tag\t$safeMessage\n"
        FileOutputStream(current, /* append = */ true).use { fos ->
            OutputStreamWriter(fos, StandardCharsets.UTF_8).use { w ->
                w.write(line)
            }
        }
    }

    /**
     * Rotate: rename the current file to log-1,
     * shift the older files down (log-1 → log-2,
     * log-2 → log-3, ...), and delete anything
     * past log-5. The next append creates a new
     * current file.
     */
    @Synchronized
    fun rotate() {
        if (!current.exists()) return
        // Shift down: log-N → log-(N+1), with delete
        // at the top. Iterate in reverse so the
        // renaming doesn't clobber the next file.
        for (n in MAX_FILES downTo 1) {
            val src = if (n == 1) current else File(logDir, "log-${n - 1}.txt")
            val dst = File(logDir, "log-$n.txt")
            if (src.exists()) {
                if (n == MAX_FILES && dst.exists()) dst.delete()
                if (dst.exists()) dst.delete()  // belt and suspenders
                src.renameTo(dst)
            }
        }
    }

    enum class Level { DEBUG, INFO, WARN, ERROR }

    companion object {
        const val MAX_FILE_BYTES: Long = 1L * 1024 * 1024  // 1 MB
        const val MAX_FILES: Int = 5
    }
}
