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

    private const val MONOCHROMACY = 0

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
     * Never throws. A failure here must not take down a launcher — the
     * phone still has to open apps.
     */
    fun set(context: Context, on: Boolean): Boolean = runCatching {
        if (!isGranted(context)) return false
        if (on) {
            Settings.Secure.putInt(context.contentResolver, MODE, MONOCHROMACY)
            Settings.Secure.putInt(context.contentResolver, ENABLED, 1)
        } else {
            Settings.Secure.putInt(context.contentResolver, ENABLED, 0)
        }
        isOn(context) == on
    }.getOrDefault(false)

    /** The line to paste into a terminal, shown verbatim in settings. */
    fun grantCommand(packageName: String) =
        "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"
}
