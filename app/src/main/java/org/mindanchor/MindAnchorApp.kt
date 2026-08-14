@file:Suppress("TooGenericExceptionCaught")

package org.mindanchor

import android.app.Application
import org.mindanchor.crash.CrashReporter
import org.mindanchor.notifications.Channels

/**
 * v0.25.19: the MindAnchor [Application] subclass.
 *
 * Two responsibilities:
 *
 *  1. Install the [CrashReporter] (no-op today, see
 *     [org.mindanchor.crash.NoOpCrashReporter] for the
 *     privacy reasoning) and wire it into the
 *     default uncaught-exception handler.
 *
 *  2. Create every notification channel exactly once
 *     via [Channels.ensureAll]. Channels are
 *     application-scope state, not per-post state —
 *     before v0.25.19, six call sites re-created the
 *     channel on every post (a no-op on Android 8+,
 *     but a wasted system call, and a re-introducible
 *     bug the v0.25.11 SOTA sweep pinned but did not
 *     actually fix). After v0.25.19 the call sites
 *     are pure `manager.notify(...)` with no channel
 *     guard.
 *
 * Listed in the manifest as `android:name=".MindAnchorApp"`.
 */
class MindAnchorApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Order matters: install the crash reporter
        // BEFORE creating the channels. A channel
        // creation that throws (string resource
        // missing, an OEM-specific channel-tying
        // bug) would otherwise crash the app
        // before the reporter is wired in, and the
        // crash would not be reported.
        installCrashReporter()
        Channels.ensureAll(this)
    }

    private fun installCrashReporter() {
        // The no-op implementation is a singleton; the
        // singleton is set in CrashReporter.Companion's
        // field initialiser. A future opt-in install
        // (HTTP-`POST` to a self-hosted ingest) would
        // replace this with `HttpCrashReporter(this)`.
        val reporter = CrashReporter.instance
        reporter.install(this)
        // Wire the default uncaught-exception handler.
        // The chain-to-previous pattern keeps the OS
        // termination behaviour intact: the reporter
        // records the crash, then the previous handler
        // runs (the default handler, on Android,
        // terminates the process).
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                reporter.recordUncaught(
                    thread = thread,
                    throwable = throwable,
                    tags = baseTags(),
                )
            } catch (_: Throwable) {
                // A crash reporter that crashes the app
                // on crash is worse than no crash
                // reporter. The reporter's own contract
                // says it must not throw; this is a
                // belt-and-braces guard.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun baseTags(): Map<String, String> = mapOf(
        "versionName" to appVersionName(),
        "versionCode" to appVersionCode(),
    )

    private fun appVersionName(): String = try {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
    } catch (_: Throwable) {
        "unknown"
    }

    private fun appVersionCode(): String = try {
        @Suppress("DEPRECATION")
        val code = packageManager.getPackageInfo(packageName, 0).versionCode
        code.toString()
    } catch (_: Throwable) {
        "unknown"
    }
}
