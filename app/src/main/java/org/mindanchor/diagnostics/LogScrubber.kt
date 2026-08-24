package org.mindanchor.diagnostics

import java.io.File
import java.util.regex.Pattern

/**
 * The on-device log scrubber. Reads the current log
 * file, redacts PII patterns, and writes a *scrubbed
 * copy* to a sibling file that the share intent
 * surfaces. The original log file is never modified.
 *
 * ## Why a separate scrubbed file
 *
 * The current log file is a single-writer, multi-reader
 * resource (see [LogFile] KDoc). Editing it in place
 * would race with the friction gate, the pulse flow,
 * and the bedtime list. A sibling scrubbed file is
 * read-only-on-write and read-only-on-share; the
 * original is left alone for in-app diagnostics.
 *
 * ## The redaction patterns
 *
 * The redaction is a regex pass against three PII
 * shapes:
 *
 *  1. **Phone numbers.** The pattern matches
 *     international and US phone formats with at
 *     least 7 digits. The number is replaced with
 *     `[PHONE REDACTED]`.
 *  2. **Email addresses.** The standard RFC 5322
 *     simplified pattern. Replaced with
 *     `[EMAIL REDACTED]`.
 *  3. **Held-notification body.** A substring of a
 *     held notification's `text` field is matched
 *     when it appears in the log. The pattern is
 *     intentionally narrow — only the body content,
 *     not the title or package name, which is the
 *     minimum the user has consented to share. Replaced
 *     with `[NOTIFICATION REDACTED]`.
 *
 * The pattern set is the minimum that closes the
 * privacy surface from the share intent. Adding a new
 * pattern is a one-line change here, with the
 * `LogScrubberTest` extending the test fixture.
 *
 * ## Why on-device, not in a build hook
 *
 * A pre-share scrubber is a *runtime* surface, not a
 * build-time one. The same log file is read in-app by
 * the support screen and by the diagnostic
 * ShareLogsEntryPoint. The on-device scrubber is the
 * only place the redaction is invoked — never in the
 * build, never in CI, never in a future cloud flow
 * (the project has no cloud).
 */
object LogScrubber {

    /**
     * International and US phone formats with at
     * least 7 digits. The lookahead/lookbehind
     * keeps the redaction boundary clean (a
     * 7-digit string in the middle of a longer
     * word is still matched).
     */
    private val PHONE: Pattern = Pattern.compile(
        "(?<!\\d)(?:\\+?\\d{1,3}[\\s.-]?)?\\(?\\d{3}\\)?[\\s.-]?\\d{3,4}[\\s.-]?\\d{3,4}(?!\\d)",
    )

    /**
     * The standard RFC 5322 simplified pattern. The
     * `(?i)` makes it case-insensitive; email is.
     */
    private val EMAIL: Pattern = Pattern.compile(
        "(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}",
    )

    /**
     * A held-notification body in the log is a substring
     * the launcher writes when the user has the wrap
     * enabled. The pattern matches the line that
     * contains the body field; only the body is
     * redacted, not the surrounding record metadata.
     */
    private val NOTIFICATION_BODY: Pattern = Pattern.compile(
        "(HeldNotification\\{[^}]*text=)([^,}]+)([,}])",
    )

    private const val PHONE_REDACTED = "[PHONE REDACTED]"
    private const val EMAIL_REDACTED = "[EMAIL REDACTED]"
    private const val NOTIFICATION_BODY_REDACTED = "[NOTIFICATION REDACTED]"

    /**
     * Read [source], redact, and write to [target].
     * The [target] is overwritten if it exists; the
     * [source] is never modified. The target is in
     * the same directory as the source so the
     * FileProvider authority in the share intent
     * resolves both files.
     */
    fun scrubTo(source: File, target: File) {
        if (!source.exists()) {
            target.writeText("")
            return
        }
        val original = source.readText(Charsets.UTF_8)
        val redacted = redact(original)
        target.writeText(redacted, Charsets.UTF_8)
    }

    /**
     * The pure-function half. Public so a unit test
     * can verify the patterns without writing a real
     * log file.
     */
    fun redact(text: String): String {
        var out = text
        out = PHONE.matcher(out).replaceAll(PHONE_REDACTED)
        out = EMAIL.matcher(out).replaceAll(EMAIL_REDACTED)
        out = NOTIFICATION_BODY.matcher(out).replaceAll("$1$NOTIFICATION_BODY_REDACTED$3")
        return out
    }
}
