@file:Suppress("MaxLineLength")
package org.mindanchor.launcher

import org.junit.Assert.assertTrue
import org.junit.Test

import org.mindanchor.testing.TestFileUtil.fileAt
/**
 * v0.35.0: the BPD reflective-action entry points.
 *
 * The §3.4 spec chain capture (ChainCaptureActivity), IFS
 * picker (IfsPickerActivity), and data export (ExportActivity)
 * are still built and reachable. v0.32.0 surfaced them as
 * the v0.26.4 "Right now" home section. v0.35.0 moves them
 * to the "Get through this" needs-card door → GetThroughSubMenu
 * stacked surface (a sibling of Home, Settings, Drawer).
 *
 * The FindingTest pins:
 *  1. The GetThroughSubMenu Composable renders the three
 *     reflective-action labels.
 *  2. The home surface dispatches Intents to the three
 *     activities (now from the sub-menu callback path, not
 *     from the home Column directly).
 *  3. The strings for the sub-menu are defined.
 *
 * A future refactor that drops the entry points, re-orders
 * the sub-menu, or changes the Intent pattern flips one of
 * the assertions red.
 */
class BpdEntryPointsFindingTest {

    private val homeScreen: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/launcher/HomeScreen.kt",
        ).readText()

    private val strings: String
        get() = fileAt(
            "app/src/main/res/values/strings.xml",
        ).readText()

    @Test
    fun `GetThroughSubMenu renders the three reflective-action labels`() {
        // v0.35.0: the three buttons on the sub-menu are
        // "What just happened?", "Which part is loud?", and
        // "Export for my therapist". The sub-menu is its own
        // Composable (GetThroughSubMenu.kt); the strings are
        // re-used from the v0.33.0 strings.xml block.
        val subMenu = fileAt("app/src/main/java/org/mindanchor/launcher/GetThroughSubMenu.kt")
            .readText()
        assertTrue(
            "GetThroughSubMenu must call stringResource(R.string.home_get_through_title) " +
                "for the sub-menu header",
            subMenu.contains("stringResource(R.string.home_get_through_title)"),
        )
        listOf(
            "R.string.home_get_through_what_happened",
            "R.string.home_get_through_which_part",
            "R.string.home_get_through_export",
        ).forEach { token ->
            assertTrue(
                "GetThroughSubMenu must render $token",
                subMenu.contains(token),
            )
        }
    }

    @Test
    fun `HomeScreen dispatches Intents to the three BPD activities from the GetThrough sub-menu`() {
        // v0.35.0: each reflective-action callback starts the
        // corresponding activity. The Intent pattern is the
        // same runCatching + startActivity shape the rest of
        // the launcher uses (defensive against a misconfigured
        // manifest). The Intents live inside the
        // LauncherSurface.GetThrough when-branch.
        val chainPattern = Regex(
            """runCatching\s*\{[\s\S]*?Intent\([\s\S]*?context[\s\S]*?org\.mindanchor\.chain\.ChainCaptureActivity::class\.java[\s\S]*?\)""",
        )
        assertTrue(
            "HomeScreen must dispatch an Intent to org.mindanchor.chain.ChainCaptureActivity " +
                "from the 'What just happened?' button (GetThrough sub-menu)",
            chainPattern.containsMatchIn(homeScreen),
        )
        val ifsPattern = Regex(
            """runCatching\s*\{[\s\S]*?Intent\([\s\S]*?context[\s\S]*?org\.mindanchor\.ifs\.IfsPickerActivity::class\.java[\s\S]*?\)""",
        )
        assertTrue(
            "HomeScreen must dispatch an Intent to org.mindanchor.ifs.IfsPickerActivity " +
                "from the 'Which part is loud?' button (GetThrough sub-menu)",
            ifsPattern.containsMatchIn(homeScreen),
        )
        val exportPattern = Regex(
            """runCatching\s*\{[\s\S]*?Intent\([\s\S]*?context[\s\S]*?org\.mindanchor\.export\.ExportActivity::class\.java[\s\S]*?\)""",
        )
        assertTrue(
            "HomeScreen must dispatch an Intent to org.mindanchor.export.ExportActivity " +
                "from the 'Export for my therapist' button (GetThrough sub-menu)",
            exportPattern.containsMatchIn(homeScreen),
        )
    }

    @Test
    fun `strings xml defines the GetThrough sub-menu keys`() {
        // The four keys are: home_get_through_title,
        // home_get_through_what_happened, home_get_through_which_part,
        // home_get_through_export. (Captions are also defined
        // but the title + 3 affordance labels are the user-
        // visible surface.)
        listOf(
            "home_get_through_title",
            "home_get_through_what_happened",
            "home_get_through_which_part",
            "home_get_through_export",
        ).forEach { key ->
            assertTrue(
                "values/strings.xml must define <string name=\"$key\">",
                strings.contains("name=\"$key\""),
            )
        }
    }

    @Test
    fun `v0-32-0 Right now section is removed from the home surface (v0-35-0)`() {
        // v0.32.0 rendered the BPD entry points as a "Right now"
        // home section (right_now_section, right_now_chain,
        // right_now_ifs, right_now_export). v0.35.0 moves them
        // to the GetThrough sub-menu. The old section is no
        // longer on the home surface — the right_now_section /
        // right_now_caption strings are still defined (for any
        // historic reference) but not called from HomeScreen.kt.
        assertTrue(
            "HomeScreen must NOT call stringResource(R.string.right_now_section) " +
                "— the v0.32.0 Right now section was replaced by the GetThrough sub-menu",
            !homeScreen.contains("stringResource(R.string.right_now_section)"),
        )
        assertTrue(
            "HomeScreen must NOT call stringResource(R.string.right_now_chain) " +
                "— the v0.32.0 chain-capture home button is now in the sub-menu",
            !homeScreen.contains("stringResource(R.string.right_now_chain)"),
        )
        assertTrue(
            "HomeScreen must NOT call stringResource(R.string.right_now_ifs) " +
                "— the v0.32.0 IFS-picker home button is now in the sub-menu",
            !homeScreen.contains("stringResource(R.string.right_now_ifs)"),
        )
        assertTrue(
            "HomeScreen must NOT call stringResource(R.string.right_now_export) " +
                "— the v0.32.0 export home button is now in the sub-menu",
            !homeScreen.contains("stringResource(R.string.right_now_export)"),
        )
    }
}
