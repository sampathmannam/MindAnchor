package org.mindanchor.ui

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.Modifier
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.launcher.LauncherRoot
import org.mindanchor.onboarding.OnboardingScreen
import org.mindanchor.report.ReportScreen
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
        capture(name)
    }

    /**
     * The same camera with the palette and font scale forced.
     *
     * Dark mode and a doubled font scale are exactly the states no
     * screenshot had ever covered, and every affordance fault this app
     * has shipped was found in a screenshot — so the unpictured states
     * are where the next one is. Font scale 2.0 is the top of Android's
     * ordinary range and the setting chosen by exactly the people the
     * 48dp touch floors exist for.
     */
    private fun shootForced(
        name: String,
        dark: Boolean,
        fontScale: Float,
        content: @Composable () -> Unit,
    ) {
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                MindAnchorThemeForced(dark = dark) {
                    // Inside a Surface, as every one of these screens is
                    // in the app itself — HomeScreen wraps each surface it
                    // opens. The first run of this camera skipped that and
                    // photographed dark-palette text on the activity's
                    // light window: a state the app cannot produce, which
                    // is the one thing a camera must never show.
                    Surface(modifier = Modifier.fillMaxSize()) { content() }
                }
            }
        }
        capture(name)
    }

    private fun capture(name: String) {
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
    fun onboarding() = shoot("onboarding") { OnboardingScreen(onDone = { _, _ -> }) }

    @Test
    fun support() = shoot("support") { SupportScreen(onClose = {}) }

    @Test
    fun settings() = shoot("settings") {
        SettingsScreen(
            allApps = emptyList(),
            hiddenApps = emptyList(),
            onUnhide = {},
            onBack = {},
        )
    }

    /**
     * The report, which had no shot until now — and is the one screen the
     * whole system exists to produce.
     *
     * On a bare emulator nothing has ever been generated, so this catches
     * the empty state rather than a full report. That is worth having on
     * its own: "nothing yet" is what somebody sees for their first weeks,
     * it is the state this app is least likely to have thought about, and
     * a screen that looks broken when it is merely early would cost the
     * person exactly the patience the design is asking them for.
     */
    @Test
    fun report() = shoot("report") { ReportScreen(onBack = {}) }

    // The full report — shaped like a bad night on purpose, see
    // ReportFixtures — with yesterday's facts under it, so the one block
    // added after the first camera run is in the frame too.

    @Test
    fun reportWithContent() = shoot("report-full") {
        ReportScreen(stored = ReportFixtures.stored(), facts = ReportFixtures.facts(), onBack = {})
    }

    /**
     * The observations and their research with nothing above them — the
     * container treatment is this screen's one real design decision, and
     * in the full shot it sits below the fold where no picture reaches.
     */
    @Test
    fun reportSectionsOnly() = shoot("report-sections") {
        ReportScreen(
            stored = ReportFixtures.stored().copy(narration = null, patterns = emptyList()),
            onBack = {},
        )
    }

    // The matrix: the two states every affordance fault so far had in
    // common was that nothing had ever photographed them.

    @Test
    fun reportFullDark() = shootForced("report-full-dark", dark = true, fontScale = 1f) {
        ReportScreen(stored = ReportFixtures.stored(), facts = ReportFixtures.facts(), onBack = {})
    }

    @Test
    fun settingsDark() = shootForced("settings-dark", dark = true, fontScale = 1f) {
        SettingsScreen(allApps = emptyList(), hiddenApps = emptyList(), onUnhide = {}, onBack = {})
    }

    @Test
    fun settingsBigType() = shootForced("settings-x2", dark = false, fontScale = 2f) {
        SettingsScreen(allApps = emptyList(), hiddenApps = emptyList(), onUnhide = {}, onBack = {})
    }

    @Test
    fun reportFullBigType() = shootForced("report-full-x2", dark = false, fontScale = 2f) {
        ReportScreen(stored = ReportFixtures.stored(), facts = ReportFixtures.facts(), onBack = {})
    }
}
