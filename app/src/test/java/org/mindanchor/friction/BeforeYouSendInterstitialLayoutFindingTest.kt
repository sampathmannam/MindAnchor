package org.mindanchor.friction

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.25.13 FindingTest: the "Send" Surface in the BeforeYouSendInterstitial
 * must not be wrapped in a Box with `Modifier.fillMaxSize()`.
 *
 * Background: the v0.26.0 release shipped the "Send" Surface (line 53-62 of
 * BeforeYouSendInterstitial.kt) with a Box inside that had
 * `Modifier.fillMaxSize()`. The Surface sits inside a Row inside a Column
 * with `fillMaxSize`. A Box with `fillMaxSize` requests the full column
 * height, so the "Send" Surface grew to 1126 px (half the screen) while
 * the "Send anyway" TextButton on the left was 48 dp (126 px) — a 9x
 * height mismatch. The "Send" button dominated the screen, with the
 * FAST / DEAR MAN / GIVE template card squashed into the top quarter.
 *
 * The v0.25.13 fix changes the Box to `Modifier.fillMaxWidth().heightIn(min = 48.dp)`.
 * The Box now fills the Surface horizontally but sizes vertically to the
 * minimum 48 dp, matching the "Send anyway" button.
 *
 * The test pins the file shape: a regression that re-introduces
 * `Modifier.fillMaxSize()` on the Box inside the Send Surface flips
 * the test red.
 */
class BeforeYouSendInterstitialLayoutFindingTest {

    private fun readSource(): String? = runCatching {
        val candidates = listOf(
            "app/src/main/java/org/mindanchor/friction/BeforeYouSendInterstitial.kt",
            "../app/src/main/java/org/mindanchor/friction/BeforeYouSendInterstitial.kt",
        )
        candidates.firstNotNullOfOrNull { path ->
            val file = java.io.File(path)
            if (file.exists()) file.readText() else null
        }
    }.getOrNull()

    @Test
    fun `Send Surface does not wrap the inner Box in fillMaxSize`() {
        val source = readSource()
        assertNotNull("BeforeYouSendInterstitial.kt must be readable", source)
        // The buggy shape was a Box inside the "Send" Surface with
        // `Modifier.fillMaxSize()`. The fix uses `Modifier.fillMaxWidth()` and
        // an explicit `heightIn(min = 48.dp)`.
        assertTrue(
            "BeforeYouSendInterstitial must NOT use Box with Modifier.fillMaxSize() " +
                "inside the Send Surface (the v0.26.0 silent-collapse bug shape that " +
                "grew the Send button to 1126 px / half the screen). " +
                "source=\n$source",
            !source!!.contains("Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()"),
        )
    }

    @Test
    fun `Send Surface uses fillMaxWidth and heightIn for the inner Box`() {
        val source = readSource()
        assertNotNull(source)
        assertTrue(
            "The Send Surface inner Box must use fillMaxWidth (not fillMaxSize) " +
                "so the button height matches 'Send anyway' at 48 dp",
            source!!.contains("fillMaxWidth") && source.contains("heightIn(min = 48.dp)"),
        )
    }
}
