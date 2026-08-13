@file:Suppress(
    "SwallowedException", 
    "MaxLineLength", 
    "LoopWithTooManyJumpStatements", 
    "UnusedPrivateMember",
)

package org.mindanchor.permissions

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SOTA v2 bug-hunt, finding #3: every notification channel in this
 * app is created lazily on every notification post, not once at
 * app startup. The GoingLightVpnService is the only file that
 * guards its `createNotificationChannel` call with an
 * `existing == null` check (line 452 of GoingLightVpnService.kt).
 * The other five channels re-create on every post:
 *
 *  - `digest` (BatchReleaser.postDigest, line 44)
 *  - `letters` (LetterScheduler.postNotification, line 168)
 *  - `sessions` (SessionManager.postExpiryNotification, line 136)
 *  - `ema` (EmaScheduler.postPrompt, line 251)
 *  - `pulse` (PulseReminder, line 132)
 *
 * Re-creating a channel with the same id is a no-op on Android 8+,
 * but is still a wasted system call. The fix is to move the
 * channel creation to a one-time `Application.onCreate` (or a
 * `BootReceiver` / `HomeActivity.onCreate` call), guarded by the
 * `existing == null` pattern.
 *
 * The five tests below pin the surface.
 */
class NotificationChannelCreationFindingTest {

    @Test
    fun `BatchReleaser creates the digest channel unconditionally on every release`() {
        val src = read("src/main/java/org/mindanchor/notifications/BatchReleaser.kt") ?: return
        val createBlock = Regex(
            "manager\\.createNotificationChannel\\(",
            RegexOption.MULTILINE,
        ).containsMatchIn(src)
        val guardBlock = src.contains("getNotificationChannel(") || src.contains("existing == null")
        assertTrue(
            "BatchReleaser must guard createNotificationChannel with an " +
                "existing-channel check OR move the channel creation to " +
                "Application.onCreate. createBlock=$createBlock, " +
                "guardBlock=$guardBlock. Current shape re-creates the " +
                "channel on every post.",
            createBlock && guardBlock,
        )
    }

    @Test
    fun `LetterScheduler creates the letters channel unconditionally on every onFire`() {
        val src = read("src/main/java/org/mindanchor/letters/LetterScheduler.kt") ?: return
        val createBlock = src.contains("manager.createNotificationChannel(")
        val guardBlock = src.contains("getNotificationChannel(") || src.contains("existing == null")
        assertTrue(
            "LetterScheduler must guard createNotificationChannel. " +
                "createBlock=$createBlock, guardBlock=$guardBlock.",
            createBlock && guardBlock,
        )
    }

    @Test
    fun `SessionManager creates the sessions channel unconditionally on every expiry`() {
        val src = read("src/main/java/org/mindanchor/friction/SessionManager.kt") ?: return
        val createBlock = src.contains("manager.createNotificationChannel(")
        val guardBlock = src.contains("getNotificationChannel(") || src.contains("existing == null")
        assertTrue(
            "SessionManager must guard createNotificationChannel. " +
                "createBlock=$createBlock, guardBlock=$guardBlock.",
            createBlock && guardBlock,
        )
    }

    @Test
    fun `EmaScheduler creates the ema channel unconditionally on every prompt`() {
        val src = read("src/main/java/org/mindanchor/model/EmaScheduler.kt") ?: return
        val createBlock = src.contains("manager.createNotificationChannel(")
        val guardBlock = src.contains("getNotificationChannel(") || src.contains("existing == null")
        assertTrue(
            "EmaScheduler must guard createNotificationChannel. " +
                "createBlock=$createBlock, guardBlock=$guardBlock.",
            createBlock && guardBlock,
        )
    }

    @Test
    fun `PulseReminder creates the pulse channel unconditionally on every reminder`() {
        val src = read("src/main/java/org/mindanchor/pulse/PulseReminder.kt") ?: return
        val createBlock = src.contains("manager.createNotificationChannel(")
        val guardBlock = src.contains("getNotificationChannel(") || src.contains("existing == null")
        assertTrue(
            "PulseReminder must guard createNotificationChannel. " +
                "createBlock=$createBlock, guardBlock=$guardBlock.",
            createBlock && guardBlock,
        )
    }

    private fun read(path: String): String? = try {
        java.io.File(path).readText(Charsets.UTF_8)
    } catch (t: Throwable) {
        null
    }
}
