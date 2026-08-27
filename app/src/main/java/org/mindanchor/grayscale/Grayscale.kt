package org.mindanchor.grayscale

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.edit

/**
 * Turns the screen grey.
 *
 * This is the largest single effect in the digital-wellbeing literature
 * that a phone can actually apply to itself. Dekker & Baumgartner (2024)
 * and a 2026 cross-over trial in medical students both report significant
 * screen-time reductions from monochrome displays, and the mechanism is
 * not willpower: colour is what makes a feed feel alive, and removing it
 * removes the pull without removing the phone.
 *
 * ## Two paths, one decision
 *
 * v0.72.x: this object is the single source of truth for
 * "should MindAnchor currently be drawn in greyscale",
 * but it propagates that decision through two
 * different mechanisms depending on the platform:
 *
 * 1. The daltonizer secure settings
 *    (`accessibility_display_daltonizer`,
 *    `accessibility_display_daltonizer_enabled`). On
 *    Android 13 and earlier, the system reads these
 *    and applies full-screen monochromacy. On
 *    Android 14+, these constants are no longer in
 *    the public SDK and the system no longer applies
 *    them to third-party apps that are not registered
 *    as accessibility services — the write is a
 *    no-op, see the note at the top of the file.
 *
 * 2. [GrayscaleState], a singleton [StateFlow] that
 *    the launcher home composable observes. When
 *    this flow is true, a saturation-0
 *    [androidx.compose.ui.graphics.ColorMatrix] is
 *    applied to the launcher's root. This is the
 *    fallback that always works and that the user
 *    can see: MindAnchor itself goes grey whenever
 *    the toggle is on, regardless of platform.
 *
 * The settings screen and the sunset alarm both call
 * [set]; [set] writes to whichever paths are
 * available (always the StateFlow, the secure
 * settings when the permission is held) and lets
 * each renderer pick up the change independently.
 *
 * ## Why this needs a computer once (for the system-wide path)
 *
 * The system-wide path goes through the
 * `WRITE_SECURE_SETTINGS` permission, which Android
 * will not grant to an app from within the app, at any
 * price, for good reason. It can be granted over adb,
 * once, and it survives reboots:
 *
 * ```
 * adb shell pm grant org.mindanchor android.permission.WRITE_SECURE_SETTINGS
 * ```
 *
 * Until then the system-wide write is a no-op and
 * the only effect is the in-app one. The settings
 * screen still says "needs a computer once" because
 * the *intent* of the section is full-system greyscale;
 * the in-app layer is a graceful degradation on
 * platforms where the system path is gone.
 *
 * ## Why it uses the colour-correction slot
 *
 * On the platforms where the system path still
 * works, the daltonizer is the only public slot. It
 * is the same mechanism the well-known adb grayscale
 * trick uses. The borrowing has one real consequence,
 * and the settings screen says so: someone who
 * genuinely uses colour correction for colour vision
 * deficiency cannot use both.
 */
object Grayscale {

    /** Secure setting: whether colour correction is applied at all. */
    private const val ENABLED = "accessibility_display_daltonizer_enabled"

    /** Secure setting: which correction. 0 is monochromacy. */
    private const val MODE = "accessibility_display_daltonizer"

    private const val MONOCHROMACY = GrayscalePolicy.MONOCHROMACY

    /**
     * Where the person's own colour-correction settings are kept while
     * grayscale has borrowed them.
     *
     * Plain SharedPreferences rather than DataStore, which this project
     * uses everywhere else, for one reason: every function here is
     * synchronous and is called from the sunset alarm as well as the
     * settings screen. Making them suspend to reach a DataStore would push
     * coroutine plumbing into an alarm receiver to store two integers.
     */
    private fun store(context: Context) =
        context.getSharedPreferences("grayscale_restore", Context.MODE_PRIVATE)

    private const val SAVED_ENABLED = "prior_enabled"
    private const val SAVED_MODE = "prior_mode"

    private fun remembered(context: Context): ColourState? = runCatching {
        val prefs = store(context)
        if (!prefs.contains(SAVED_MODE)) return null
        ColourState(
            enabled = prefs.getBoolean(SAVED_ENABLED, false),
            mode = prefs.getInt(SAVED_MODE, MONOCHROMACY),
        )
    }.getOrNull()

    private fun current(context: Context): ColourState = ColourState(
        enabled = Settings.Secure.getInt(context.contentResolver, ENABLED, 0) == 1,
        mode = Settings.Secure.getInt(context.contentResolver, MODE, MONOCHROMACY),
    )

    fun isGranted(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    fun isOn(context: Context): Boolean = runCatching {
        Settings.Secure.getInt(context.contentResolver, ENABLED, 0) == 1 &&
            Settings.Secure.getInt(context.contentResolver, MODE, MONOCHROMACY) == MONOCHROMACY
    }.getOrDefault(false)

    /**
     * Returns true if the screen ended up in the requested state.
     *
     * Turning grayscale on saves whatever colour correction was configured
     * first; turning it off puts that back rather than merely clearing the
     * flag. See [GrayscalePolicy] for why that matters and to whom.
     *
     * Never throws. A failure here must not take down a launcher — the
     * phone still has to open apps.
     *
     * v0.72.x: also publishes the requested state to
     * [GrayscaleState] so the launcher composable can
     * apply an in-app ColorMatrix fallback. The
     * secure-settings write may be a no-op on the
     * current platform (Android 14+ removed the
     * daltonizer constants from the public SDK and
     * stopped applying them to non-accessibility-service
     * apps); the StateFlow write is not.
     */
    fun set(context: Context, on: Boolean): Boolean = runCatching {
        // v0.72.x: always publish the requested state
        // to GrayscaleState so the launcher can apply
        // its in-app ColorMatrix fallback, even when
        // the secure-settings write below is a no-op
        // (Android 14+ removed the daltonizer
        // constants from the public SDK and stopped
        // applying them to non-accessibility-service
        // apps).
        GrayscaleState.set(on)
        if (!isGranted(context)) return on
        if (on) {
            GrayscalePolicy.stateToRemember(
                current = current(context),
                alreadyRemembered = remembered(context) != null,
            )?.let { prior ->
                store(context).edit {
                    putBoolean(SAVED_ENABLED, prior.enabled)
                    putInt(SAVED_MODE, prior.mode)
                }
            }
            Settings.Secure.putInt(context.contentResolver, MODE, MONOCHROMACY)
            Settings.Secure.putInt(context.contentResolver, ENABLED, 1)
        } else {
            val restore = GrayscalePolicy.stateToRestore(remembered(context))
            Settings.Secure.putInt(context.contentResolver, ENABLED, if (restore.enabled) 1 else 0)
            Settings.Secure.putInt(context.contentResolver, MODE, restore.mode)
            store(context).edit { clear() }
        }
        isOn(context) == on
    }.getOrDefault(false)

    /** The line to paste into a terminal, shown verbatim in settings. */
    fun grantCommand(packageName: String) =
        "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"

    /**
     * v0.72.x: rehydrates [GrayscaleState] from whatever the
     * secure-settings track says right now. Call this once on
     * app start (e.g. `HomeActivity.onCreate`) so the
     * [GreyscaleRoot] ColorMatrix layer reflects the
     * persisted state instead of starting at `false` after
     * every process death. The daltonizer secure-settings
     * persist across launches; the in-memory state does not.
     * Without this, the UI toggle and the visual effect
     * drift apart after every relaunch — a real bug a user
     * would notice immediately: the toggle says "on" but the
     * launcher stays coloured.
     *
     * The `isOn` check tolerates a no-permission state by
     * defaulting to `false` (the toggle will not appear in
     * the UI when the permission is missing anyway, so a
     * false negative is harmless).
     */
    fun rehydrateFromSettings(context: Context) {
        GrayscaleState.set(isOn(context))
    }
}
