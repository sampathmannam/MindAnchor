package org.mindanchor.diagnostics

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * The settings entry point for sharing the on-device
 * log with a developer. The user taps "Share logs" in
 * settings; the intent is the Android Sharesheet
 * (ACTION_SEND, text/plain, EXTRA_STREAM = the log
 * file's content URI).
 *
 * The receiving app gets per-URI read access via
 * [FileProvider]; the log file is not exposed as a
 * `file://` URI (the FileProvider documentation is
 * explicit: "We recommend that you avoid using
 * Uri.fromFile()... Instead, use URI permissions.").
 *
 * The "what's in this file" note that precedes the
 * share button in the settings UI is the wording-heavy
 * surface. The note is clinical-review-required: the
 * log file may contain the user's small-things, if-
 * then plans, and compassion moments.
 *
 * @wording-reviewed — see the share button KDoc and
 * the "what's in this file" note.
 */
object ShareLogsEntryPoint {

    /**
     * Build the share intent. The intent is the
     * Android Sharesheet (via [Intent.createChooser])
     * so the user can pick a recipient (email,
     * messaging, drive, etc.) without the app
     * having to enumerate.
     *
     * v0.28+ (Phase 3 G-35): the share intent surfaces
     * a *scrubbed* sibling of the current log, not
     * the log itself. [LogScrubber] redacts phone
     * numbers, email addresses, and held-notification
     * bodies before the URI is shared. The original
     * log is never modified.
     */
    fun buildShareIntent(context: Context, logFile: File): Intent {
        val scrubbed = File(logFile.parentFile, "log-scrubbed.txt")
        LogScrubber.scrubTo(logFile, scrubbed)
        val authority = context.packageName + ".fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, scrubbed)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_SUBJECT,
                "MindAnchor diagnostic logs",
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Share diagnostic logs")
    }
}
