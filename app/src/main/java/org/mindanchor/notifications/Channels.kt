@file:Suppress("TooManyFunctions")

package org.mindanchor.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import org.mindanchor.R

/**
 * v0.25.19: every notification channel in the app, in one
 * place, created exactly once at process start by
 * [org.mindanchor.MindAnchorApp.onCreate].
 *
 * Pre-v0.25.19, six call sites (BatchReleaser, LetterScheduler,
 * SessionManager, EmaScheduler, GoingLightVpnService,
 * v0.26.6-removed-PulseReminder) each created the channel
 * on every post, guarded by a `getNotificationChannel(...) == null`
 * check. The guard prevented redundant work, but the channel
 * creation was scattered: a future channel had to be added in
 * the right call site, with the right id, with the right
 * description, with the right importance. The v0.25.11 SOTA
 * sweep pinned the guard but did not move the creation.
 * v0.25.19 moves it here. v0.26.6 dropped the pulse package
 * entirely (third ad-hoc check-in alongside EMA + CheckIn;
 * see [v0.26.6 release notes](docs/releases/v0.26.6.md) for
 * the reasoning).
 *
 * The contract enforced by the
 * [org.mindanchor.permissions.NotificationChannelCreationFindingTest]
 * is now: the string `createNotificationChannel(` must only
 * appear in this file. The call sites use [id] constants
 * declared here to identify the channel and call
 * `manager.notify(...)` with no guard.
 */
object Channels {

    /** Held-notification digest, released as one calm notification per batch. */
    const val DIGEST = "digest"

    /** Letter from the user's last week, delivered by the letter scheduler. */
    const val LETTERS = "letters"

    /** Per-app session expiry. */
    const val SESSIONS = "sessions"

    /** EMA (ecological momentary assessment) check-in prompt. */
    const val EMA = "ema"

    /** Going Light VPN foreground service. The id must match
     *  GoingLightVpnService.CHANNEL_ID — the value is stable
     *  across restarts and must not be changed in isolation. */
    const val GOING_LIGHT = "org.mindanchor.goinglight"

    /** SMS tone-check (v0.26.1 AppWatchService). High importance — it's a prompt, not a feed. */
    const val TONE_CHECK = "org.mindanchor.tonecheck"

    /**
     * v0.32.1: the foreground-service notification for the
     * "Generate now" / overnight letter run. The channel is
     * IMPORTANCE_LOW so the user gets a status-bar icon (and
     * can pull down to read the progress text) without a
     * heads-up pop, a sound, or a launcher badge. Same shape
     * as [GOING_LIGHT] — an ongoing foreground service whose
     * presence in the bar is itself the signal, not the alert.
     */
    const val LETTERS_GENERATION = "org.mindanchor.letters.generation"

    /**
     * Create every channel. Idempotent on Android 8+ (the
     * `createNotificationChannel` call is a no-op if a
     * channel with the same id already exists, so the
     * `ensureAll` re-call is harmless). Called once at
     * process start by [org.mindanchor.MindAnchorApp.onCreate].
     */
    fun ensureAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?: return
        listOf(
            digest(manager, context),
            letters(manager, context),
            sessions(manager, context),
            ema(manager, context),
            goingLight(manager, context),
            toneCheck(manager, context),
            lettersGeneration(manager, context),
        )
    }

    private fun digest(manager: NotificationManager, context: Context) {
        val channel = NotificationChannel(
            DIGEST,
            context.getString(R.string.digest_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.digest_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    private fun letters(manager: NotificationManager, context: Context) {
        val channel = NotificationChannel(
            LETTERS,
            context.getString(R.string.letters_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.letters_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    private fun sessions(manager: NotificationManager, context: Context) {
        val channel = NotificationChannel(
            SESSIONS,
            context.getString(R.string.session_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.session_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    private fun ema(manager: NotificationManager, context: Context) {
        val channel = NotificationChannel(
            EMA,
            context.getString(R.string.ema_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun goingLight(manager: NotificationManager, context: Context) {
        val channel = NotificationChannel(
            GOING_LIGHT,
            context.getString(R.string.going_light_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.going_light_channel_description)
            // Going Light is an ongoing foreground
            // service. A badge on the launcher icon
            // would be wallpaper — the user already
            // knows it's on, the notification is in
            // the status bar.
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun toneCheck(manager: NotificationManager, context: Context) {
        // v0.26.1 §3.3: the SMS tone-check prompt. AppWatchService
        // posts a notification when an SMS arrives, deep-linking
        // to BeforeYouSend. The notification is a prompt (the
        // user is being asked to pause), so IMPORTANCE_HIGH
        // (heads-up) is the right shape — different from the
        // letter channel's IMPORTANCE_DEFAULT.
        val channel = NotificationChannel(
            TONE_CHECK,
            context.getString(R.string.tone_check_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.tone_check_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    private fun lettersGeneration(manager: NotificationManager, context: Context) {
        // v0.32.1: the ongoing "Generating tonight's letter"
        // notification. Same shape as goingLight: ongoing, low
        // importance, no badge, no sound. The user sees a small
        // status-bar icon and can pull down for the progress
        // text. The letter has already been announced by the
        // Toast on the home screen; this notification is the
        // "still running, you can ignore me" signal.
        val channel = NotificationChannel(
            LETTERS_GENERATION,
            context.getString(R.string.letters_generation_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.letters_generation_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}
