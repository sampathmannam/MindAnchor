package org.mindanchor.ui

import android.graphics.Bitmap
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

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun shoot(name: String, content: @Composable () -> Unit) {
        rule.setContent { MindAnchorTheme { content() } }
        rule.waitForIdle()

        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.getExternalFilesDir(null), "shots").apply { mkdirs() }
        // Lossless on purpose: JPEG banding around a gradient would be
        // indistinguishable from a real fault in the sky, which is the
        // main thing worth looking at.
        File(dir, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
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
