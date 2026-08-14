@file:Suppress(
    "SwallowedException",
    "MaxLineLength",
    "LoopWithTooManyJumpStatements",
    "UnusedPrivateMember",
)

package org.mindanchor.crash

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v0.25.19: the [org.mindanchor.crash.CrashReporter] contract
 * is pinned by these file-shape tests.
 *
 * The contract is the [CrashReporter] interface, the
 * [NoOpCrashReporter] default impl, and the
 * [org.mindanchor.MindAnchorApp] Application class that
 * installs the default uncaught-exception handler and routes
 * uncaught exceptions through the reporter. The runtime
 * verification is "does the app survive an uncaught exception
 * in a background thread and shut down cleanly with the
 * reporter logging the trace?" — that needs an emulator and
 * a real crash. The static FindingTest pin is the gate that
 * makes a regression loud in CI before it ever reaches a
 * device.
 *
 * The four pre-existing tests on the SOTA-v2 wave pinned
 * the B-shape: "every channel-creation site must guard the
 * call." The v0.25.19 refactor moved the call to
 * [org.mindanchor.notifications.Channels], so the old
 * guards are gone; the new FindingTest is on the
 * wiring, not the guards.
 */
class CrashReporterWiringFindingTest {

    private fun readSource(path: String): String? = try {
        // Tests run from app/ — the source files are at
        // ../app/src/main/...; or from the worktree root,
        // where the path is app/src/main/.... The
        // candidate pattern is the same as the other
        // FindingTests in the repo (see
        // A11ySurfaceFindingTest.fileAt).
        val candidates = listOf(path, "../$path", "../../$path")
        candidates.map(::File).firstNotNullOfOrNull { f ->
            if (f.isFile) f.readText(Charsets.UTF_8) else null
        }
    } catch (t: Throwable) {
        null
    }

    @Test
    fun `CrashReporter interface exists at the documented package`() {
        val src = readSource("app/src/main/java/org/mindanchor/crash/CrashReporter.kt")
        assertTrue(
            "CrashReporter.kt must exist at the v0.25.19 package " +
                "org.mindanchor.crash.",
            src != null,
        )
        assertTrue(
            "CrashReporter.kt must declare a CrashReporter interface. " +
                "The interface is the contract a future HTTP-`POST` " +
                "implementation will satisfy.",
            src!!.contains("interface CrashReporter"),
        )
        assertTrue(
            "CrashReporter interface must declare recordUncaught(thread, throwable, tags) " +
                "— the entry point the default uncaught-exception handler calls.",
            src.contains("fun recordUncaught("),
        )
        assertTrue(
            "CrashReporter interface must declare recordNonFatal(throwable, tags) " +
                "— the entry point a caught-throwable code path calls.",
            src.contains("fun recordNonFatal("),
        )
        assertTrue(
            "CrashReporter interface must declare install(context) — the one-time " +
                "install hook called from Application.onCreate.",
            src.contains("fun install("),
        )
    }

    @Test
    fun `NoOpCrashReporter default implementation exists`() {
        val src = readSource("app/src/main/java/org/mindanchor/crash/CrashReporter.kt")
        assertTrue("CrashReporter.kt must be readable", src != null)
        assertTrue(
            "CrashReporter.kt must declare a NoOpCrashReporter class. " +
                "The no-op is the v0.25.19 default — a real implementation " +
                "is a future work item gated on privacy review and a user " +
                "opt-in preference.",
            src!!.contains("class NoOpCrashReporter"),
        )
        assertTrue(
            "NoOpCrashReporter must implement the CrashReporter interface. " +
                "Without `CrashReporter` in the class declaration, the " +
                "default does not satisfy the contract.",
            src.contains("class NoOpCrashReporter : CrashReporter"),
        )
    }

    @Test
    fun `MindAnchorApp_Application class wires the uncaught-exception handler to the reporter`() {
        val src = readSource("app/src/main/java/org/mindanchor/MindAnchorApp.kt")
        assertTrue(
            "MindAnchorApp.kt must exist (v0.25.19). Without it, no " +
                "Application subclass is registered and onCreate never " +
                "runs, and no uncaught-exception handler is wired.",
            src != null,
        )
        val body = src!!
        assertTrue(
            "MindAnchorApp must extend Application (the manifest " +
                "registers it as android:name=\".MindAnchorApp\").",
            body.contains("class MindAnchorApp : Application("),
        )
        assertTrue(
            "MindAnchorApp.onCreate must install the CrashReporter " +
                "(via the install() entry point or by setting the " +
                "default uncaught-exception handler).",
            body.contains("installCrashReporter()") ||
                body.contains("Thread.setDefaultUncaughtExceptionHandler"),
        )
        assertTrue(
            "MindAnchorApp must call CrashReporter.instance (the " +
                "singleton the interface exposes). The future " +
                "opt-in install will swap the value of `instance`, " +
                "and onCreate's call site must already be reading " +
                "from the singleton, not hard-coding NoOp.",
            body.contains("CrashReporter.instance"),
        )
        assertTrue(
            "MindAnchorApp.onCreate must call Channels.ensureAll(this) " +
                "so every notification channel is created at process " +
                "start. (This is the secondary responsibility of the " +
                "Application class; the crash wiring is the primary " +
                "one, but the channel wiring is the same call site.)",
            body.contains("Channels.ensureAll(this)"),
        )
    }

    @Test
    fun `the previous uncaught-exception handler is chained after the reporter`() {
        val src = readSource("app/src/main/java/org/mindanchor/MindAnchorApp.kt")
        assertTrue("MindAnchorApp.kt must be readable", src != null)
        assertTrue(
            "MindAnchorApp must capture the previous default " +
                "uncaught-exception handler and chain to it after " +
                "the reporter records the crash. Without the chain, " +
                "the OS does not terminate the process and the user " +
                "sees a frozen screen instead of the standard " +
                "'MindAnchor has stopped' dialog.",
            src!!.contains("Thread.getDefaultUncaughtExceptionHandler()") &&
                src.contains("previous?.uncaughtException("),
        )
    }
}
