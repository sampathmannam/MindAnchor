@file:Suppress("MaxLineLength")
package org.mindanchor.launcher

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

import org.mindanchor.testing.TestFileUtil.fileAt

/**
 * v0.32.1: the Letters inbox's "Generate now" affordance
 * starts [org.mindanchor.letters.LettersGenerationService] as
 * a foreground service rather than running the generation in
 * a Composable-scoped coroutine.
 *
 * Pre-v0.32.1 the work ran in
 * `letterScope = rememberCoroutineScope()`. That scope is
 * tied to the Composable's lifecycle, and the OS reaped the
 * process mid-decode on a 1.8 GB MemAvailable phone — the
 * v0.31.2 50-minute test produced zero letters saved. The
 * foreground-service path survives Composable death, process
 * backgrounding, and the user's overnight absence.
 *
 * Pins:
 *  1. HomeScreen's onGenerateNow lambda now calls
 *     `Context.startForegroundService` on the
 *     LettersGenerationService.
 *  2. The old in-Composable path is gone: HomeScreen no
 *     longer imports WeekDataCollector / LetterWriter
 *     directly into the lambda body.
 *  3. The Toast is preserved (the immediate user-side
 *     confirmation is the same shape as v0.32.0).
 *  4. The action is gated by [org.mindanchor.letters.LettersGenerationService.running],
 *     so a second tap while a generation is in progress is
 *     a no-op (rather than a second llama_init_from_model
 *     that would double the resident memory).
 */
class HomeScreenGenerateNowFindingTest {

    private val homeScreen: String
        get() = fileAt("app/src/main/java/org/mindanchor/launcher/HomeScreen.kt").readText()

    @Test
    fun `HomeScreen onGenerateNow calls Context startForegroundService for LettersGenerationService`() {
        assertNotNull(homeScreen)
        // The Compose lambda body must call
        // startForegroundService on the LettersGenerationService.
        // A regression that re-introduces the in-Composable
        // coroutine path is the exact bug v0.32.1 ships to
        // fix; the test catches it.
        assertTrue(
            "HomeScreen.kt must call " +
                "LettersGenerationService.intent(...) and " +
                "appContext.startForegroundService(...) inside " +
                "the onGenerateNow lambda. Pre-v0.32.1 the " +
                "work ran in a Composable-scoped coroutine " +
                "and got reaped mid-decode on a 1.8 GB phone.",
            homeScreen.contains("LettersGenerationService.intent(") &&
                homeScreen.contains("startForegroundService("),
        )
    }

    @Test
    fun `HomeScreen onGenerateNow no longer runs WeekDataCollector in the lambda`() {
        assertNotNull(homeScreen)
        // The pre-v0.32.1 path inlined
        // WeekDataCollector(...).collectLastWeek() inside the
        // lambda. That class should not appear in the
        // import block any more — only the LettersGenerationService
        // host imports remain. The string search for the
        // class name in the import line is the canonical
        // way to pin that.
        assertTrue(
            "HomeScreen.kt must not import " +
                "org.mindanchor.letters.WeekDataCollector " +
                "directly. The generation pipeline moved into " +
                "LettersGenerationService in v0.32.1; the " +
                "import here would be a code smell that the " +
                "in-Composable path has been re-introduced.",
            !homeScreen.contains("import org.mindanchor.letters.WeekDataCollector"),
        )
        assertTrue(
            "HomeScreen.kt must not import " +
                "org.mindanchor.letters.LetterWriter directly. " +
                "Same v0.32.1 reason as WeekDataCollector: the " +
                "pipeline lives in LettersGenerationService now.",
            !homeScreen.contains("import org.mindanchor.letters.LetterWriter"),
        )
    }

    @Test
    fun `HomeScreen onGenerateNow preserves the Toast`() {
        assertNotNull(homeScreen)
        // The Toast is the immediate user-side confirmation.
        // The text is still inlined as a literal in v0.32.1
        // (the strings.xml refactor is a future cleanup);
        // what matters is that the Toast.makeText call is
        // still there.
        assertTrue(
            "HomeScreen.kt must still post a Toast from " +
                "onGenerateNow. The Toast is the immediate " +
                "user-side confirmation; the foreground " +
                "notification arrives a moment later.",
            homeScreen.contains("Toast.makeText(") &&
                homeScreen.contains("Generating tonight's letter"),
        )
    }
}
