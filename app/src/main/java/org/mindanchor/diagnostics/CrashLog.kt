package org.mindanchor.diagnostics

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v0.72.x: minimal on-device crash log.
 *
 * Not Sentry, not Crashlytics, not Bugsnag. A single file
 * at `<filesDir>/crashes/last-crash.txt` and a rolling
 * log at `crashes/log.txt` (capped at 256 KB). The user
 * is the on-call; the log is read on demand via
 * [readLast] / [readLog].
 *
 * The default [Thread.UncaughtExceptionHandler] is
 * chained, not replaced. Any previously-installed handler
 * (the system's default, Firebase, R8 stripped stub,
 * whatever) still runs after we write our line. We are
 * a forensic layer, not a takeover.
 *
 * Why this and not Sentry: the user (single developer,
 * personal-tool product) needs the trail more than they
 * need the dashboard. A 256 KB file is enough to ship
 * the last three crashes with stack frames. When the
 * user wants a real backend, the file is the seed for
 * it.
 */
object CrashLog {

    private const val LOG_DIR = "crashes"
    private const val LAST_CRASH = "last-crash.txt"
    private const val LOG = "log.txt"
    private const val LOG_MAX_BYTES = 256L * 1024

    private val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    @Volatile
    private var installed = false

    @Volatile
    private var breadcrumbs: StringBuilder? = null

    fun install(context: Context) {
        if (installed) return
        installed = true
        breadcrumbs = StringBuilder()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrash(context.applicationContext, thread, throwable)
            } catch (_: Throwable) {
                // We are the last line of defence. A
                // crashing crash handler must not crash
                // the crash handler.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /**
     * v0.72.x: append a one-line breadcrumb to the in-memory
     * trail. The trail is included in the next crash dump
     * so a developer can see the last few user actions
     * leading up to the crash.
     *
     * The trail is bounded: the most recent 32 lines are
     * kept. The trail is never written to disk unless
     * there's a crash — it lives in memory only, so a
     * non-crash shutdown leaves no trace.
     *
     * Cheap enough to call on any user-visible action:
     *   CrashLog.breadcrumb("settings.llm.test_connection")
     *   CrashLog.breadcrumb("launcher.open.mindanchor")
     */
    fun breadcrumb(event: String) {
        val sb = breadcrumbs ?: return
        synchronized(sb) {
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append(timestamp.format(Date())).append(' ').append(event)
            // Keep only the most recent 32 lines. Drop the
            // oldest from the front by truncating the whole
            // builder to its tail.
            if (sb.lines().size > 32) {
                val tail = sb.lines().takeLast(32).joinToString("\n")
                sb.clear()
                sb.append(tail)
            }
        }
    }

    private fun writeCrash(context: Context, thread: Thread, throwable: Throwable) {
        val dir = File(context.filesDir, LOG_DIR).apply { mkdirs() }
        val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val now = timestamp.format(Date())
        val trail = breadcrumbs?.toString().orEmpty()
        val entry = buildString {
            append("=== crash ===\n")
            append("when: ").append(now).append('\n')
            append("thread: ").append(thread.name).append('\n')
            append("os: ").append(System.getProperty("os.version")).append('\n')
            append("model: ").append(android.os.Build.MODEL).append('\n')
            append("app: ").append(context.packageName).append('\n')
            append("version: ").append(
                try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                } catch (_: Throwable) { "?" }
            ).append('\n')
            append("message: ").append(throwable.message ?: "<no message>").append('\n')
            if (trail.isNotBlank()) {
                append("trail:\n").append(trail).append('\n')
            }
            append('\n')
            append(stack)
        }
        File(dir, LAST_CRASH).writeText(entry)
        appendLog(dir, entry)
    }

    private fun appendLog(dir: File, entry: String) {
        val file = File(dir, LOG)
        file.appendText(entry + "\n\n")
        if (file.length() > LOG_MAX_BYTES) {
            // Roll: keep the last 192 KB so a single
            // recent crash is preserved in full even
            // after rotation. Two-pass trim is cheaper
            // than parsing.
            val keep = LOG_MAX_BYTES * 3 / 4
            val tail = file.readText().takeLast(keep.toInt())
            file.writeText(tail)
        }
    }

    /** Last crash, or null. */
    fun readLast(context: Context): String? {
        val f = File(File(context.filesDir, LOG_DIR), LAST_CRASH)
        return f.takeIf { it.exists() }?.readText()
    }

    /** Rolling log, or null. */
    fun readLog(context: Context): String? {
        val f = File(File(context.filesDir, LOG_DIR), LOG)
        return f.takeIf { it.exists() }?.readText()
    }

    /** Wipe both files. The user wanted the trail, not the wall. */
    fun clear(context: Context) {
        val dir = File(context.filesDir, LOG_DIR)
        File(dir, LAST_CRASH).delete()
        File(dir, LOG).delete()
    }
}
