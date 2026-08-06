package org.mindanchor.ui

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.launcher.LauncherRoot
import org.mindanchor.onboarding.OnboardingScreen
import org.mindanchor.pulse.PulseScreen
import org.mindanchor.settings.SettingsScreen
import org.mindanchor.support.SupportScreen
import java.io.File

/**
 * Renders every surface and writes it to the device as a PNG, for the CI
 * job to pull back and print.
 *
 * This exists because nobody has ever actually looked at this app. It was
 * written, reviewed and tested without a screen — the build environment
 * has no Android SDK and cannot reach the artifact host — so behaviour
 * could be proven while appearance could only be reasoned about. For a
 * project whose entire claim is that it feels calm, that is not good
 * enough. These turn "it should look right" into something checkable.
 *
 * They assert nothing. They are a camera, not a gate, and a camera that
 * fails the build would be worse than no camera at all.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {

    private val TAG = "MINDANCHOR_SHOT"

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun shoot(name: String, content: @Composable () -> Unit) {
        rule.setContent { MindAnchorTheme { content() } }
        rule.waitForIdle()

        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // External storage is not guaranteed to be mounted on a bare AVD,
        // and getExternalFilesDir returns null when it is not. The first
        // version of this assumed otherwise and silently wrote nowhere:
        // the tests passed, the workflow found no files, and the whole
        // point of the exercise produced nothing. Internal storage always
        // exists, so that is the fallback.
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(base, "shots").apply { mkdirs() }
        val file = File(dir, "$name.png")
        // Lossless on purpose: JPEG banding around a gradient would be
        // indistinguishable from a real fault in the sky, which is the
        // main thing worth looking at.
        val bytes = java.io.ByteArrayOutputStream().use { buffer ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, buffer)
            buffer.toByteArray()
        }
        file.writeBytes(bytes)
        // Send the image itself out through logcat, in chunks.
        //
        // Writing a file and pulling it back does not work: Android 11+
        // blocks the shell user from /sdcard/Android/data/<pkg>, so adb
        // pull fails, and run-as answered "unknown package" on these AOSP
        // images. The first attempt at this quietly redirected that error
        // text into the .png files, producing five 40-byte "screenshots"
        // that were really an error message.
        //
        // logcat has no such problem — it is how the paths were getting
        // out in the first place. Chunks stay well under logcat's
        // per-message limit so nothing is truncated.
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val chunk = 2000
        val parts = (encoded.length + chunk - 1) / chunk
        Log.i(TAG, "BEGIN $name bytes=${bytes.size} parts=$parts")
        for (i in 0 until parts) {
            val slice = encoded.substring(i * chunk, minOf((i + 1) * chunk, encoded.length))
            Log.i(TAG, "DATA $name $i $slice")
        }
        Log.i(TAG, "END $name")
    }

    @Test
    fun home() = shoot("home") { LauncherRoot() }

    @Test
    fun onboarding() = shoot("onboarding") { OnboardingScreen(onDone = {}) }

    @Test
    fun support() = shoot("support") { SupportScreen(onClose = {}) }

    @Test
    fun pulse() = shoot("pulse") { PulseScreen(onClose = {}) }

    @Test
    fun settings() = shoot("settings") {
        SettingsScreen(
            allApps = emptyList(),
            hiddenApps = emptyList(),
            onUnhide = {},
            onBack = {},
        )
    }
}
