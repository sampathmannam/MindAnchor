@file:Suppress("UnusedPrivateMember")

package org.mindanchor.crash

import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter

/**
 * v0.25.19 crash-reporting contract.
 *
 * The MindAnchor privacy promise is that nothing user-authored ever
 * leaves the phone. The first version of this interface is a no-op
 * for the same reason: a Crashlytics / Sentry / Bugsnag dependency
 * would either bake a network call into the app (which the privacy
 * promise forbids) or demand a Play-Services dependency (which the
 * app does not want — see the [F] file in
 * `.git/sdd/bug_hunt_v2_integration.md` for the build-bloat
 * reasoning).
 *
 * The interface exists so that:
 *
 *  1. The [org.mindanchor.MindAnchorApp] Application class can
 *     install a single [Thread.UncaughtExceptionHandler] and route
 *     uncaught exceptions to a `CrashReporter` implementation, with
 *     no Play-Services / Crashlytics dependency today.
 *
 *  2. A future backend (a self-hosted Sentry-compatible ingest
 *     server, run on a private IP that the app can reach when the
 *     user explicitly opts in) can be dropped in by swapping the
 *     [NoOpCrashReporter] for an HTTP-`POST`-based implementation.
 *     The contract is fixed; the implementation is the variable.
 *
 *  3. The static [CrashReporterWiringFindingTest] can pin the
 *     contract — a future regression that drops the wiring flips
 *     the build red.
 *
 * The default impl is [NoOpCrashReporter]. It is installed in
 * `Application.onCreate` unconditionally; it is replaced at
 * install time by an opt-in implementation when the user
 * enables a future "share crash reports" preference (not in
 * v0.25.19).
 */
interface CrashReporter {
    /**
     * Report an uncaught exception. Called from the
     * [Thread.UncaughtExceptionHandler] installed in
     * `Application.onCreate`. The implementation must NOT throw —
     * a crash reporter that crashes the app on crash is worse
     * than no crash reporter.
     *
     * @param thread the thread that threw; supplied so the
     *   reporter can annotate "main" / "background"
     * @param throwable the uncaught exception
     * @param tags optional key-value tags. Use for stable
     *   classification (e.g. `versionName`, `model`,
     *   `locale`, `surface`) — never for user content
     *   (the privacy promise forbids sending note bodies,
     *   letter bodies, or held-notification text to any
     *   backend, no matter what the user opted into).
     */
    fun recordUncaught(
        thread: Thread,
        throwable: Throwable,
        tags: Map<String, String> = emptyMap(),
    )

    /**
     * Report a non-fatal exception — a caught `Throwable` that
     * the user-facing flow recovered from. Same privacy rules
     * as [recordUncaught].
     */
    fun recordNonFatal(
        throwable: Throwable,
        tags: Map<String, String> = emptyMap(),
    )

    /**
     * One-time install. Called from `Application.onCreate`
     * before any other work. The default no-op installs
     * a no-op handler; a real implementation registers
     * the global [Thread.setDefaultUncaughtExceptionHandler]
     * and chains to the previous handler so the OS still
     * terminates the crashed process.
     */
    fun install(context: Context)

    companion object {
        /**
         * The global default. Replaced at install time by
         * `Application.onCreate`. The [NoOpCrashReporter] is
         * the value before `install` runs; the field is
         * `var` (not `val`) so the future opt-in install
         * can swap in an HTTP implementation.
         */
        @Volatile
        var instance: CrashReporter = NoOpCrashReporter()
            private set
    }
}

/**
 * Default no-op implementation. Installed in
 * `Application.onCreate` today. The no-op is intentional:
 * shipping a Crashlytics / Sentry dependency to satisfy
 * "the contract exists" would either add a Play-Services
 * dependency (the app does not want) or hard-code a
 * future backend (the privacy promise does not allow).
 *
 * The no-op is correct: a real implementation is a future
 * work item, gated on (a) a privacy review of the backend,
 * (b) a user opt-in toggle, and (c) the no-op being
 * replaceable through this interface.
 */
class NoOpCrashReporter : CrashReporter {
    override fun recordUncaught(
        thread: Thread,
        throwable: Throwable,
        tags: Map<String, String>,
    ) {
        // No-op. A real implementation would queue the
        // event for upload; this one is silent.
    }

    override fun recordNonFatal(
        throwable: Throwable,
        tags: Map<String, String>,
    ) {
        // No-op.
    }

    override fun install(context: Context) {
        // No-op. A real implementation would:
        //   val previous = Thread.getDefaultUncaughtExceptionHandler()
        //   Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        //       recordUncaught(thread, throwable, tags = ...)
        //       previous?.uncaughtException(thread, throwable)
        //   }
        // Chaining to the previous handler is the only way
        // to make sure the OS still terminates the process
        // after the report is queued; otherwise the app
        // sits in a half-dead state and the user sees a
        // frozen screen instead of "MindAnchor has stopped."
    }
}

/**
 * v0.25.19 internal helper: format a throwable for a tag
 * value. A real implementation would never put a stack
 * trace in a tag (tags are short key-value pairs, not
 * payloads); this is here so a future non-no-op impl
 * does not have to roll its own stack-trace-to-string
 * conversion. The privacy contract is enforced at the
 * call site, not in the helper.
 */
internal fun Throwable.stackTraceString(): String {
    val sw = StringWriter()
    PrintWriter(sw).use { printStackTrace(it) }
    return sw.toString()
}
