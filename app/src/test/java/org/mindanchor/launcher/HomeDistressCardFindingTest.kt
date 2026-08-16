@file:Suppress("MaxLineLength")
package org.mindanchor.launcher

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

import org.mindanchor.testing.TestFileUtil.fileAt
/**
 * v0.28.0: the home-surface Distress Thermometer card.
 *
 * Pins:
 *  1. HomeScreen calls HomeDistressCard as the FIRST card on home
 *     (before OpenLoopCard and the right-now section).
 *  2. HomeDistressCard renders the title, caption, and "Ground me
 *     here" button.
 *  3. HomeScreen wires `onOpenDistressThermometer` to launch
 *     DistressThermometerActivity.
 *  4. OneThingCard is no longer rendered on home (data model kept).
 *  5. Strings exist for the home card title, caption, and button.
 *  6. BPD-safe: no directive language; no "good day / bad day" framing.
 */
class HomeDistressCardFindingTest {

    private val homeScreen: String
        get() = fileAt("app/src/main/java/org/mindanchor/launcher/HomeScreen.kt").readText()

    private val strings: String
        get() = fileAt("app/src/main/res/values/strings.xml").readText()

    @Test
    fun `HomeDistressCard Composable is defined and is the first card on home`() {
        assertNotNull(homeScreen)
        assertTrue(
            "HomeScreen must define a HomeDistressCard Composable",
            homeScreen.contains("private fun HomeDistressCard("),
        )
        // v0.32.0: OpenLoopCard was the second card on home from
        // v0.25.5 to v0.31.x. v0.32.0 cuts it for the same reason
        // v0.28.0 cut OneThingCard and v0.26.6 cut BedtimeListCard:
        // the v0.26.6 audit counted three task-capture cards as
        // "one too many for a person with BPD" (DBT: low cognitive
        // load is the floor). The data model is kept; only the
        // home-surface render is removed. The home scroll is now
        // Distress → QuickNotes; the test below pins the new
        // order. The OpenLoop call-site index is asserted < 0 so
        // a regression that re-introduces the card is caught.
        val distressIdx = homeScreen.indexOf("\n            HomeDistressCard(")
        val openLoopIdx = homeScreen.indexOf("\n            OpenLoopCard(")
        assertTrue("HomeDistressCard call site must exist", distressIdx > 0)
        assertTrue(
            "OpenLoopCard must NOT be rendered on the home surface " +
                "(v0.32.0 cut: third task-capture card removed for BPD-safe " +
                "home. Data model is kept in LauncherViewModel.openLoop).",
            openLoopIdx < 0,
        )
        assertTrue(
            "HomeDistressCard must be the first card on home (only QuickNotes follows).",
            distressIdx > 0,
        )
    }

    @Test
    fun `HomeDistressCard has the title, caption, and Ground-me-here button`() {
        assertNotNull(homeScreen)
        // Title, caption, button — all from the home card strings.
        listOf(
            "R.string.home_distress_card_title",
            "R.string.home_distress_card_caption",
            "R.string.home_ground_me_button",
        ).forEach { token ->
            assertTrue(
                "HomeDistressCard must render $token",
                homeScreen.contains(token),
            )
        }
    }

    @Test
    fun `HomeScreen wires onOpenDistressThermometer to launch DistressThermometerActivity`() {
        assertNotNull(homeScreen)
        assertTrue(
            "HomeScreen must have an onOpenDistressThermometer parameter",
            homeScreen.contains("onOpenDistressThermometer: () -> Unit"),
        )
        assertTrue(
            "HomeScreen must wire onOpenDistressThermometer to launch DistressThermometerActivity",
            homeScreen.contains("DistressThermometerActivity::class.java"),
        )
    }

    @Test
    fun `OneThingCard is removed entirely from the launcher (v0-28-0 BPD-strict cut)`() {
        assertNotNull(homeScreen)
        // v0.28.0: OneThingCard Composable is removed. The
        // data model (oneThing StateFlow + setOneThing method in
        // LauncherViewModel) is kept for the export payload and
        // any future re-introduction — but the home-surface
        // Composable is gone. The function definition AND the
        // call site must be absent (the word "OneThingCard" is
        // still in the v0.28.0 comment, which is fine).
        val defIdx = homeScreen.indexOf("private fun OneThingCard(")
        val callIdx = homeScreen.indexOf("\n            OneThingCard(")
        assertTrue(
            "HomeScreen must NOT define OneThingCard Composable " +
                "(v0.28.0 BPD-strict cut: the data model is kept in " +
                "LauncherViewModel; the home Composable is removed). " +
                "defIdx=$defIdx callIdx=$callIdx",
            defIdx < 0 && callIdx < 0,
        )
    }

    @Test
    fun `strings xml defines home card title, caption, and button`() {
        listOf(
            "home_distress_card_title",
            "home_distress_card_caption",
            "home_ground_me_button",
        ).forEach { key ->
            assertTrue(
                "values/strings.xml must define <string name=\"$key\">",
                strings.contains("name=\"$key\""),
            )
        }
    }

    @Test
    fun `HomeDistressCard has no directive language (BPD-safe per audit)`() {
        assertNotNull(homeScreen)
        assertTrue(
            "HomeDistressCard must not contain directive phrases (BPD-safe)",
            !homeScreen.contains("you should", ignoreCase = true) &&
                !homeScreen.contains("you must", ignoreCase = true) &&
                !homeScreen.contains("you need to", ignoreCase = true),
        )
        // No "good day" / "bad day" framing — BPD-safe per audit §2.3.
        assertTrue(
            "HomeDistressCard must not use 'good day' / 'bad day' framing (BPD-safe)",
            !homeScreen.contains("good day", ignoreCase = true) &&
                !homeScreen.contains("bad day", ignoreCase = true),
        )
    }
}
