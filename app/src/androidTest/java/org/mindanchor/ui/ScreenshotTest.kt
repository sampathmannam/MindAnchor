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
import org.mindanchor.corpus.Passage
import org.mindanchor.report.Direction
import org.mindanchor.report.Label
import org.mindanchor.report.Observation
import org.mindanchor.report.Pattern
import org.mindanchor.report.Report
import org.mindanchor.report.ReportScreen
import org.mindanchor.report.ReportSection
import org.mindanchor.report.Signal
import org.mindanchor.report.StoredReport
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

    /**
     * The report with something in it, which is the state that matters
     * and the one nothing could photograph until [ReportScreen] grew an
     * overload taking its content directly.
     *
     * The data is invented, and deliberately shaped like a bad night
     * rather than a good one: two observations, a pattern, and a
     * generated paragraph all at once. That is close to the most this
     * screen will ever show — three sections is the hard cap, two
     * patterns is the other — so if the layout holds here it holds
     * everywhere. A screenshot of the gentlest possible case would prove
     * nothing about the case that actually needs to read calmly.
     */
    @Test
    fun reportWithContent() = shoot("report-full") {
        ReportScreen(
            stored = StoredReport(
                report = Report(
                    day = "2026-08-06",
                    sections = listOf(
                        ReportSection(
                            observation = Observation(
                                signal = Signal.SLEEP_ONSET,
                                direction = Direction.ABOVE,
                                today = 330.0,
                                usual = 240.0,
                            ),
                            passages = listOf(
                                Passage(
                                    "sleep-regularity",
                                    "Windred et al. 2024, Sleep 47:zsad285",
                                    "In a large accelerometry cohort, sleep regularity predicted " +
                                        "all-cause mortality more strongly than sleep duration did. " +
                                        "Regularity here means the consistency of sleep and wake " +
                                        "timing, not the number of hours.",
                                ),
                            ),
                        ),
                        ReportSection(
                            observation = Observation(
                                signal = Signal.HRV,
                                direction = Direction.BELOW,
                                today = 28.0,
                                usual = 46.0,
                            ),
                            passages = listOf(
                                Passage(
                                    "hrv-rmssd",
                                    "Shaffer & Ginsberg 2017, Frontiers in Public Health",
                                    "RMSSD is the primary time-domain measure of short-term heart " +
                                        "rate variability. It reflects vagally mediated, " +
                                        "beat-to-beat changes in heart rhythm.",
                                ),
                            ),
                        ),
                    ),
                    notYetKnown = listOf(Signal.STEPS),
                ),
                narration = "Heart-rate variability describes how much the time between your " +
                    "heartbeats changes from one beat to the next. Sleep onset is simply when " +
                    "you first fell asleep. Both were further from your usual last night than " +
                    "they normally are.",
                patterns = listOf(
                    Pattern(
                        signal = Signal.SLEEP_ONSET,
                        label = Label.VALENCE,
                        similarDays = 22,
                        medianWhenLikeToday = 2.0,
                        medianOverall = 3.0,
                    ),
                ),
            ),
            onBack = {},
        )
    }
}
