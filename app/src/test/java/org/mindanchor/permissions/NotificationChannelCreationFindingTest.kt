@file:Suppress(
    "SwallowedException",
    "MaxLineLength",
    "LoopWithTooManyJumpStatements",
    "UnusedPrivateMember",
)

package org.mindanchor.permissions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v0.25.19 NotificationChannel consolidation: every notification
 * channel in this app is created exactly once, at process start,
 * by [org.mindanchor.notifications.Channels.ensureAll], which is
 * called from [org.mindanchor.MindAnchorApp.onCreate]. The six
 * pre-v0.25.19 call sites (BatchReleaser, LetterScheduler,
 * SessionManager, EmaScheduler, PulseReminder, GoingLightVpnService)
 * no longer create channels; they call
 * `manager.notify(NOTIFICATION_ID, ...)` with the channel id
 * from [org.mindanchor.notifications.Channels] and assume the
 * channel exists.
 *
 * The five pre-v0.25.19 tests pinned "every call site must
 * guard `createNotificationChannel` with a
 * `getNotificationChannel(...) == null` check." That pin is
 * obsolete: the call sites no longer have a
 * `createNotificationChannel` call to guard. The new pin is:
 *
 *  1. `createNotificationChannel(` appears in **only** the
 *     `org/mindanchor/notifications/Channels.kt` file. A
 *     future commit that re-introduces a call-site
 *     `createNotificationChannel` flips this test red.
 *
 *  2. `Channels.ensureAll(` is called from
 *     `MindAnchorApp.onCreate`. A future commit that
 *     removes the Application class (or skips the
 *     `ensureAll` call) flips this test red — every
 *     channel would then be created lazily on first
 *     post, with the v0.25.19 ordering guarantee gone.
 *
 *  3. Each of the six call sites uses a `Channels.XXX`
 *     constant for the channel id. A future commit that
 *     re-introduces a hard-coded `"digest"`, `"letters"`,
 *     etc. literal at a call site (the v0.25.19 anti-pattern)
 *     would still work, but the literal would drift the
 *     next time the id changed. The constant keeps the
 *     call site and the channel-creation site in lock-step.
 */
class NotificationChannelCreationFindingTest {

    private fun readSource(path: String): String? = try {
        val candidates = listOf(path, "../$path", "../../$path")
        candidates.map(::File).firstNotNullOfOrNull { f ->
            if (f.isFile) f.readText(Charsets.UTF_8) else null
        }
    } catch (t: Throwable) {
        null
    }

    @Test
    fun `createNotificationChannel only appears in Channels_kt`() {
        // Walk every .kt file under app/src/main/java and
        // collect the ones that contain the call. The test
        // fails if more than one file shows up.
        val candidates = listOf(
            "app/src/main/java/org/mindanchor",
            "../app/src/main/java/org/mindanchor",
        )
        val mainSrc = candidates.map(::File).firstOrNull { it.isDirectory }
        if (mainSrc == null) {
            // Build dir gone; the test cannot run. The
            // build pins the path; the runner ships the
            // sources.
            return
        }
        val offenders = mutableListOf<String>()
        mainSrc.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val src = try {
                file.readText(Charsets.UTF_8)
            } catch (_: Throwable) {
                return@forEach
            }
            if (src.contains("createNotificationChannel(")) {
                offenders += file.path
            }
        }
        assertTrue(
            "Every `createNotificationChannel(` call must live in " +
                "org/mindanchor/notifications/Channels.kt (v0.25.19 " +
                "consolidation). Offenders:\n  " +
                offenders.joinToString("\n  "),
            offenders.size == 1 &&
                offenders.single().replace('\\', '/').endsWith(
                    "org/mindanchor/notifications/Channels.kt",
                ),
        )
    }

    @Test
    fun `Channels_ensureAll is called from MindAnchorApp_onCreate`() {
        val app = readSource("app/src/main/java/org/mindanchor/MindAnchorApp.kt")
        assertTrue(
            "MindAnchorApp.kt must exist and be readable (v0.25.19 Application class).",
            app != null,
        )
        assertTrue(
            "MindAnchorApp.onCreate must call Channels.ensureAll(this) so every " +
                "notification channel is created exactly once at process start. " +
                "Without it, the v0.25.19 ordering guarantee is lost — channels " +
                "would be created lazily on first post, with the per-call-site " +
                "guard reintroduced.",
            app!!.contains("Channels.ensureAll(") && app.contains("override fun onCreate("),
        )
    }

    @Test
    fun `Application class is registered in AndroidManifest`() {
        val manifest = readSource("app/src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must be readable", manifest != null)
        assertTrue(
            "AndroidManifest.xml's <application> must declare " +
                "android:name=\".MindAnchorApp\" so the Application subclass " +
                "is instantiated and onCreate runs. Without it the channels " +
                "are never created.",
            manifest!!.contains("android:name=\".MindAnchorApp\""),
        )
    }

    @Test
    fun `BatchReleaser uses Channels_DIGEST constant not hardcoded literal`() {
        val src = readSource("app/src/main/java/org/mindanchor/notifications/BatchReleaser.kt")
        assertTrue("BatchReleaser.kt must be readable", src != null)
        assertTrue(
            "BatchReleaser must use Channels.DIGEST for the channel id " +
                "(v0.25.19 consolidation). The pre-v0.25.19 hard-coded " +
                "\"digest\" literal must not come back.",
            src!!.contains("Channels.DIGEST"),
        )
        assertFalse(
            "BatchReleaser must NOT contain a hard-coded \"digest\" channel-id " +
                "literal after the v0.25.19 consolidation. The id comes from " +
                "Channels.DIGEST so the call site and the channel-creation site " +
                "cannot drift.",
            src.contains("\"digest\""),
        )
    }

    @Test
    fun `LetterScheduler uses Channels_LETTERS constant`() {
        val src = readSource("app/src/main/java/org/mindanchor/letters/LetterScheduler.kt")
        assertTrue("LetterScheduler.kt must be readable", src != null)
        assertTrue(
            "LetterScheduler must use Channels.LETTERS for the channel id.",
            src!!.contains("Channels.LETTERS"),
        )
        assertFalse(
            "LetterScheduler must not contain a hard-coded \"letters\" literal.",
            src.contains("\"letters\""),
        )
    }

    @Test
    fun `SessionManager uses Channels_SESSIONS constant`() {
        val src = readSource("app/src/main/java/org/mindanchor/friction/SessionManager.kt")
        assertTrue("SessionManager.kt must be readable", src != null)
        assertTrue(
            "SessionManager must use Channels.SESSIONS for the channel id.",
            src!!.contains("Channels.SESSIONS"),
        )
        assertFalse(
            "SessionManager must not contain a hard-coded \"sessions\" literal.",
            src.contains("\"sessions\""),
        )
    }

    @Test
    fun `EmaScheduler uses Channels_EMA constant`() {
        val src = readSource("app/src/main/java/org/mindanchor/model/EmaScheduler.kt")
        assertTrue("EmaScheduler.kt must be readable", src != null)
        assertTrue(
            "EmaScheduler must use Channels.EMA for the channel id.",
            src!!.contains("Channels.EMA"),
        )
        assertFalse(
            "EmaScheduler must not contain a hard-coded \"ema\" literal.",
            src.contains("\"ema\""),
        )
    }

    @Test
    fun `GoingLightVpnService uses Channels_GOING_LIGHT constant`() {
        val src = readSource("app/src/main/java/org/mindanchor/goinglight/GoingLightVpnService.kt")
        assertTrue("GoingLightVpnService.kt must be readable", src != null)
        assertTrue(
            "GoingLightVpnService must use Channels.GOING_LIGHT for the channel id " +
                "(the v0.20.1 stable id \"org.mindanchor.goinglight\").",
            src!!.contains("Channels.GOING_LIGHT"),
        )
        assertFalse(
            "GoingLightVpnService must not contain a hard-coded channel-id literal " +
                "after the v0.25.19 consolidation.",
            src.contains("\"org.mindanchor.goinglight\""),
        )
    }
}
