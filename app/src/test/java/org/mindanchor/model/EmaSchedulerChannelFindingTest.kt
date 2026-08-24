package org.mindanchor.model

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Finding test for the v0.26+ EMA check-in
 * notification banner upgrade.
 *
 * The pre-v0.26 shape was a notification channel
 * named `ema` with `IMPORTANCE_LOW`, which Android
 * renders as a silent entry in the shade — the
 * user never saw the prompt. The user reported this
 * on 2026-08-24 as "I want a banner notification".
 *
 * v0.26+ fix:
 *  1. The channel id is renamed to `ema_prompts_v2`
 *     because Android 8+ does not allow raising the
 *     importance of an existing channel.
 *  2. The new channel is `IMPORTANCE_DEFAULT` (a
 *     banner/heads-up).
 *  3. The notification has no sound and no vibration
 *     (a check-in is not a summons).
 *  4. The old `ema` channel is deleted on first post
 *     so the user's settings do not carry a dead
 *     silent entry alongside the new one.
 *
 * This test pins the surface so a future change to
 * the channel config cannot silently re-silence the
 * prompt.
 */
class EmaSchedulerChannelFindingTest {

    private fun fileAt(relative: String): File {
        val candidates = listOf(relative, "../$relative", "../../$relative")
        return candidates.map(::File).firstOrNull { it.isFile }
            ?: error("$relative not found from working directory ${File(".").absolutePath}.")
    }

    private val scheduler: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/model/EmaScheduler.kt",
        ).readText()

    private val res: String
        get() = fileAt(
            "app/src/main/res/values/strings.xml",
        ).readText()

    @Test
    fun `the check-in channel id is ema_prompts_v2 (not the silent v1 id)`() {
        assertTrue(
            "The check-in notification channel id must be " +
                "ema_prompts_v2 (Android 8+ does not allow raising " +
                "the importance of an existing channel — the rename " +
                "is the upgrade). Reverting to 'ema' would re-silence " +
                "the prompt on every existing install.",
            scheduler.indexOf("CHANNEL_ID = \"ema_prompts_v2\"") >= 0,
        )
    }

    @Test
    fun `the legacy ema channel is deleted on first post so settings do not carry a dead silent entry`() {
        assertTrue(
            "The old 'ema' channel must be deleted the first time " +
                "the new channel is created, so the user's Android " +
                "notification settings do not show a dead silent " +
                "entry. The fix is deleteNotificationChannel(" +
                "LEGACY_CHANNEL_ID) inside postPrompt.",
            scheduler.indexOf("deleteNotificationChannel(LEGACY_CHANNEL_ID)") >= 0,
        )
    }

    @Test
    fun `the new channel is IMPORTANCE_DEFAULT (banner, not silent)`() {
        // IMPORTANCE_DEFAULT is the lowest level that renders
        // a heads-up banner on Android 8+. Lower than that
        // (LOW, MIN) is silent in the shade.
        assertTrue(
            "The check-in channel must be IMPORTANCE_DEFAULT " +
                "(banner). IMPORTANCE_LOW is silent in the shade " +
                "and is the pre-v0.26 shape the user reported.",
            scheduler.indexOf("IMPORTANCE_DEFAULT") >= 0,
        )
        assertTrue(
            "The channel must NOT be IMPORTANCE_LOW — that is " +
                "the pre-v0.26 silent-in-the-shade shape.",
            scheduler.indexOf("IMPORTANCE_LOW") < 0,
        )
    }

    @Test
    fun `the notification has no sound and no vibration (a check-in is not a summons)`() {
        assertTrue(
            "The notification must have sound disabled. A " +
                "check-in is a brief opt-in prompt, not a " +
                "summons; an audible notification is the wrong " +
                "shape and a worse shape inside the user's own " +
                "quiet hours.",
            scheduler.indexOf("setSound(null") >= 0,
        )
        assertTrue(
            "The notification must have vibration disabled.",
            scheduler.indexOf("enableVibration(false)") >= 0,
        )
    }

    @Test
    fun `the channel has a description so it does not look unconfigured in Android settings`() {
        assertTrue(
            "The channel must have a description string " +
                "(ema_channel_description) so the user's Android " +
                "notification settings do not show an empty " +
                "description.",
            res.indexOf("ema_channel_description") >= 0,
        )
    }
}
