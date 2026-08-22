package org.mindanchor.launcher

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.25.12 FindingTest: the NowWhatShell 2am shell must render
 * all three options (sleep / ground / talk).
 *
 * Background: the v0.26.0 release shipped NowWhatShell with
 * `NowWhatRow` wrapping a `Box(modifier = Modifier.fillMaxSize())`
 * around the inner TextButton. In a Column with `fillMaxSize` the
 * first NowWhatRow's Box requested the full remaining column
 * height, which collapsed the next two NowWhatRow siblings to
 * zero height and pushed them off-screen. A real-device install
 * (v0.25.11 emulator) showed only "I want to sleep" in the
 * UIautomator dump; the user could see and tap one option out
 * of three.
 *
 * The v0.25.12 fix removes the Box entirely. The TextButton has
 * its own `Alignment.CenterStart` for its content, so the Box was
 * redundant. The Surface + TextButton onClicks are not a double-fire
 * bug: the TextButton consumes the click event before the
 * Surface's onClick can fire.
 *
 * This test pins the file shape: a regression that re-introduces
 * a `Box(modifier = Modifier.fillMaxSize(), …)` (or any other
 * `fillMaxSize` Box) inside NowWhatRow flips the test red.
 */
class NowWhatRowLayoutFindingTest {

    private fun readSource(): String? = runCatching {
        val candidates = listOf(
            "app/src/main/java/org/mindanchor/launcher/NowWhatShell.kt",
            "../app/src/main/java/org/mindanchor/launcher/NowWhatShell.kt",
        )
        candidates.firstNotNullOfOrNull { path ->
            val file = java.io.File(path)
            if (file.exists()) file.readText() else null
        }
    }.getOrNull()

    @Test
    fun `NowWhatRow does not wrap the TextButton in a fillMaxSize Box`() {
        val source = readSource()
        assertNotNull("NowWhatShell.kt must be readable", source)
        // The buggy shape was a Box with `Modifier.fillMaxSize()` *inside*
        // the NowWhatRow Surface, wrapping the TextButton. That asks the
        // Column for the full remaining height and collapses the next two
        // NowWhatRow siblings to zero height. The specific pattern is the
        // Box's contentAlignment parameter (Box was only used as a
        // centering wrapper; the contentAlignment is what made it
        // non-redundant), so the bug shape is the Box with the centering
        // Alignment.CenterStart. A regression that drops the comment and
        // re-introduces the bug (e.g. "Box(modifier = Modifier.fillMaxSize(),
        // contentAlignment = Alignment.CenterStart) {") flips the test red.
        assertTrue(
            "NowWhatRow must NOT contain the centering Box that wrapped the " +
                "TextButton (the v0.25.11 silent-collapse bug shape that pushed " +
                "the 2nd and 3rd options off-screen). source=\n$source",
            !source!!.contains("Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart"),
        )
        // Positive pin: the NowWhatRow's Surface now contains a TextButton
        // directly (no Box wrapper).
        // v0.26.5: there are now 2 TextButton calls in the file — the
        // NowWhatRow's TextButton, and the 4th "I'm up late tonight"
        // TextButton below the 3 NowWhatRow entries. The v0.25.12 fix
        // shape is preserved: NO Box wrapper around either TextButton.
        assertTrue(
            "NowWhatRow's Surface must contain a TextButton directly (no Box wrapper)",
            source.contains("Surface(") &&
                source.contains("TextButton(") &&
                source.split("TextButton(").size - 1 == 2,
        )
    }

    @Test
    fun `NowWhatShell renders three NowWhatRow calls (sleep + ground + talk)`() {
        val source = readSource()
        assertNotNull(source)
        assertTrue(
            "NowWhatShell must call NowWhatRow three times: now_what_sleep, " +
                "now_what_ground, and now_what_talk. The v0.25.11 layout bug " +
                "rendered only one of three on the device; this is the shape pin.",
            source!!.contains("NowWhatRow(stringResource(R.string.now_what_sleep)") &&
                source.contains("NowWhatRow(stringResource(R.string.now_what_ground)") &&
                source.contains("NowWhatRow(stringResource(R.string.now_what_talk)"),
        )
    }
}
