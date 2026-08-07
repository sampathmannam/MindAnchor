package org.mindanchor.grayscale

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Turns the whole screen grey.
 *
 * This is the largest single effect in the digital-wellbeing literature
 * that a phone can actually apply to itself. Dekker & Baumgartner (2024)
 * and a 2026 cross-over trial in medical students both report significant
 * screen-time reductions from monochrome displays, and the mechanism is
 * not willpower: colour is what makes a feed feel alive, and removing it
 * removes the pull without removing the phone.
 *
 * ## Why this needs a computer once
 *
 * Grayscale is a system-wide accessibility setting, so writing it needs
 * `WRITE_SECURE_SETTINGS` — a permission Android will not grant to an app
 * from within the app, at any price, for good reason. It can be granted
 * over adb, once, and it survives reboots:
 *
 * ```
 * adb shell pm grant org.mindanchor android.permission.WRITE_SECURE_SETTINGS
 * ```
 *
 * Until then everything here reports "not granted" and does nothing. The
 * app never nags for it and works fully without it.
 *
 * ## Why it uses the colour-correction slot
 *
 * Android has no public "make everything grey" API. What it has is the
 * daltonizer — colour correction for colour blindness — whose
 * monochromacy mode is exactly a full desaturation of the display. That
 * is the same mechanism the well-known adb grayscale trick uses. The
 * borrowing has one real consequence, and the settings screen says so:
 * someone who genuinely uses colour correction for colour vision
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
     */
    fun set(context: Context, on: Boolean): Boolean = runCatching {
        if (!isGranted(context)) return false
        if (on) {
            GrayscalePolicy.stateToRemember(
                current = current(context),
                alreadyRemembered = remembered(context) != null,
            )?.let { prior ->
                store(context).edit()
                    .putBoolean(SAVED_ENABLED, prior.enabled)
                    .putInt(SAVED_MODE, prior.mode)
                    .apply()
            }
            Settings.Secure.putInt(context.contentResolver, MODE, MONOCHROMACY)
            Settings.Secure.putInt(context.contentResolver, ENABLED, 1)
        } else {
            val restore = GrayscalePolicy.stateToRestore(remembered(context))
            Settings.Secure.putInt(context.contentResolver, ENABLED, if (restore.enabled) 1 else 0)
            Settings.Secure.putInt(context.contentResolver, MODE, restore.mode)
            store(context).edit().clear().apply()
        }
        isOn(context) == on
    }.getOrDefault(false)

    /** The line to paste into a terminal, shown verbatim in settings. */
    fun grantCommand(packageName: String) =
        "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"
}
