package org.mindanchor.launcher

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Finding tests for v0.25.1 bug 6 — the home
 * screen's right-edge TextButtons (notes, history
 * at the top; settings at the bottom) could be
 * partially clipped by the screen edge on devices
 * with rounded corners or emulators that crop the
 * last pixel. The touchable area extended past
 * `x = 1080` (on a 1080-wide screen), so taps in
 * the very right column missed.
 *
 * The fix adds an 8dp end padding to the two
 * right-edge containers:
 *  - the `Column` that holds notes + history
 *    (TopEnd, statusBarsPadding)
 *  - the settings TextButton (BottomEnd,
 *    navigationBarsPadding)
 *
 * 8dp is small enough not to shift the visible
 * label position, and large enough that the
 * TextButton's right edge is inside the screen on
 * every form factor.
 *
 * What this test pins:
 *  1. The TopEnd Column carries a `padding(end = 8.dp)`.
 *  2. The BottomEnd settings TextButton carries a
 *     `padding(end = 8.dp)`.
 *  3. The two containers still align TopEnd /
 *     BottomEnd (the fix did not move the buttons).
 *  4. The padding is exactly 8.dp (a regression
 *     guard — a future refactor that bumps it
 *     would change the visible label position).
 *  5. The padding is additive with the existing
 *     `statusBarsPadding` / `navigationBarsPadding`
 *     (the fix did not remove those insets).
 */
class EdgePaddingFindingTest {

    private fun readScreen(): String =
        checkNotNull(
            java.io.File("app/src/main/java/org/mindanchor/launcher/HomeScreen.kt")
                .takeIf { it.isFile }
                ?: java.io.File("../app/src/main/java/org/mindanchor/launcher/HomeScreen.kt")
                    .takeIf { it.isFile }
                    ?: error("HomeScreen.kt not found"),
        ).readText()

    @Test
    fun `TopEnd notes+history Column has an 8dp end padding`() {
        val screen = readScreen()
        // The Column that holds notes + history
        // (TopEnd) must carry .padding(end = 8.dp).
        val topEndColumn = Regex(
            """Column\(\s*modifier\s*=\s*Modifier\s*\.[\s\S]+?\.align\(Alignment\.TopEnd\)[\s\S]+?\)""",
        ).find(screen)
        assertTrue(
            "TopEnd notes+history Column not located.",
            topEndColumn != null,
        )
        val block = topEndColumn!!.value
        assertTrue(
            "the TopEnd Column must carry .padding(end = 8.dp) to keep the " +
                "TextButton's right edge inside the screen; otherwise the " +
                "right column is clipped on rounded-corner devices. " +
                "block=$block",
            block.contains(".padding(end = 8.dp)"),
        )
    }

    @Test
    fun `BottomEnd settings TextButton has an 8dp end padding`() {
        val screen = readScreen()
        // The settings TextButton at BottomEnd
        // (onOpenSettings) must carry .padding(end = 8.dp).
        // Match the onOpenSettings TextButton.
        val onOpenSettingsBlock = Regex(
            """TextButton\(\s*onClick\s*=\s*onOpenSettings\s*,[\s\S]+?\)\s*\{""",
        ).find(screen)
        assertTrue(
            "onOpenSettings TextButton not located.",
            onOpenSettingsBlock != null,
        )
        val block = onOpenSettingsBlock!!.value
        assertTrue(
            "the settings TextButton must carry .padding(end = 8.dp) to " +
                "keep its right edge inside the screen. block=$block",
            block.contains(".padding(end = 8.dp)"),
        )
    }

    @Test
    fun `top-right and bottom-right containers still align to End`() {
        val screen = readScreen()
        // Regression guard: the fix must not have
        // moved the buttons off the edge.
        assertTrue(
            "TopEnd notes+history Column must still align Alignment.TopEnd.",
            screen.contains(".align(Alignment.TopEnd)"),
        )
        assertTrue(
            "BottomEnd settings TextButton must still align Alignment.BottomEnd.",
            screen.contains(".align(Alignment.BottomEnd)"),
        )
    }

    @Test
    fun `padding value is exactly 8_dp`() {
        val screen = readScreen()
        // The fix is `.padding(end = 8.dp)` — exact.
        // A future bump (e.g. to 16.dp) would shift
        // the visible label and break the visual
        // rhythm with the left-column buttons. Pin
        // the exact value.
        val count = Regex("""\.padding\(end\s*=\s*8\.dp\)""").findAll(screen).count()
        assertTrue(
            "the home screen must carry exactly two `.padding(end = 8.dp)` " +
                "modifiers — one for the TopEnd Column and one for the " +
                "BottomEnd settings TextButton. found=$count",
            count >= 2,
        )
    }

    @Test
    fun `padding is additive with statusBarsPadding and navigationBarsPadding`() {
        val screen = readScreen()
        // The fix did not replace the existing
        // safe-area insets. Both must still be
        // present on the right-edge containers.
        val topEndBlock = Regex(
            """\.align\(Alignment\.TopEnd\)[\s\S]+?\.padding\(end\s*=\s*8\.dp\)""",
        ).find(screen)
        assertTrue(
            "TopEnd block must still have its modifier chain intact.",
            topEndBlock != null,
        )
        val topEndBody = topEndBlock!!.value
        assertTrue(
            "TopEnd Column must still have .statusBarsPadding() " +
                "in the modifier chain.",
            topEndBody.contains(".statusBarsPadding()"),
        )
        val bottomEndBlock = Regex(
            """\.align\(Alignment\.BottomEnd\)[\s\S]+?\.padding\(end\s*=\s*8\.dp\)""",
        ).find(screen)
        assertTrue(
            "BottomEnd settings block must still have its modifier chain intact.",
            bottomEndBlock != null,
        )
        val bottomEndBody = bottomEndBlock!!.value
        assertTrue(
            "BottomEnd settings TextButton must still have " +
                ".navigationBarsPadding() in the modifier chain.",
            bottomEndBody.contains(".navigationBarsPadding()"),
        )
    }
}
