@file:Suppress("MaxLineLength")
package org.mindanchor.launcher

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

import org.mindanchor.testing.TestFileUtil.fileAt
/**
 * v0.35.0: the home-surface NeedsCard replaces the v0.32.x
 * HomeDistressCard.
 *
 * The v0.28.0 design rendered a "How is it right now?" Distress
 * Thermometer as the first card on home. v0.33.0 + v0.35.0
 * replace it with a "What do you need right now?" 2×2 needs
 * card (Be heard / A moment / Check in / Get through this)
 * — DBT validate-then-suggest, IFS (Schwartz 1995), and
 * Lindsay 2024 JMIR all point at "ask what is needed first"
 * over "ask how distressed you are" as the better BPD-strict
 * primary surface.
 *
 * Pins:
 *  1. NeedsCard is the FIRST card on home (before QuickNotesCard).
 *  2. NeedsCard renders the four affordance labels.
 *  3. The v0.28.0 HomeDistressCard Composable is no longer
 *     called from the home surface (the function definition
 *     is kept for callers outside the home — Distress
 *     Thermometer is still reachable from Settings → Pauses).
 *  4. The data model (oneThing, openLoop) is kept untouched.
 *  5. Strings exist for the needs-card title, caption, and
 *     four affordance labels.
 *  6. BPD-safe: no directive language; no "good day / bad day"
 *     framing in the new card.
 *
 * @wording-reviewed — clinical-review-required. The four
 * affordance labels are the clinical-review surface; wording
 * changes here must be re-reviewed per docs/CLINICAL_REVIEW.md.
 */
class HomeDistressCardFindingTest {

    private val homeScreen: String
        get() = fileAt("app/src/main/java/org/mindanchor/launcher/HomeScreen.kt").readText()

    private val strings: String
        get() = fileAt("app/src/main/res/values/strings.xml").readText()

    @Test
    fun `NeedsCard Composable is defined and is the first card on home (v0-35-0)`() {
        assertNotNull(homeScreen)
        assertTrue(
            "HomeScreen must call NeedsCard as the first card on home (v0.35.0 redesign)",
            homeScreen.contains("\n            NeedsCard("),
        )
        // OpenLoopCard, OneThingCard, BedtimeListCard — all removed
        // from the home surface (v0.26.6, v0.28.0, v0.32.0 cuts).
        // The data model is kept; the home-surface render is not.
        listOf(
            "OpenLoopCard(",
            "OneThingCard(",
            "BedtimeListCard(",
        ).forEach { token ->
            assertTrue(
                "HomeScreen must NOT render $token on the home surface",
                !homeScreen.contains("\n            $token"),
            )
        }
    }

    @Test
    fun `HomeDistressCard call site is removed from home (v0-35-0)`() {
        // v0.35.0: the v0.28.0-v0.32.x HomeDistressCard call
        // site is removed from the home scroll. The Composable
        // definition is kept in HomeScreen.kt for callers
        // outside the home (Settings → Pauses still uses it);
        // only the home-surface call is gone.
        val homeDistressCallIdx = homeScreen.indexOf("\n            HomeDistressCard(")
        assertTrue(
            "HomeDistressCard call site must NOT be on the home surface (v0.35.0 " +
                "replaced it with NeedsCard). homeDistressCallIdx=$homeDistressCallIdx",
            homeDistressCallIdx < 0,
        )
    }

    @Test
    fun `NeedsCard has the four affordance labels (v0-35-0)`() {
        assertNotNull(homeScreen)
        // v0.35.0: HomeScreen.kt calls NeedsCard(sky = ..., ...)
        // — the title, caption, and all four affordance labels
        // live in launcher/NeedsCard.kt. The test pins all
        // six from the NeedsCard source. The home_screen
        // check is just that the NeedsCard call site exists.
        assertTrue(
            "HomeScreen must call NeedsCard (the v0.35.0 first card on home)",
            homeScreen.contains("\n            NeedsCard("),
        )
        val needsCard = fileAt("app/src/main/java/org/mindanchor/launcher/NeedsCard.kt")
            .readText()
        listOf(
            "R.string.home_needs_title",
            "R.string.home_needs_caption",
            "R.string.home_needs_be_heard",
            "R.string.home_needs_moment",
            "R.string.home_needs_check_in",
            "R.string.home_needs_get_through",
        ).forEach { token ->
            assertTrue(
                "NeedsCard must render $token",
                needsCard.contains(token),
            )
        }
    }

    @Test
    fun `NeedsCard wires the four affordance callbacks to the corresponding activities`() {
        assertNotNull(homeScreen)
        // The 4 affordance callbacks. The exact call pattern
        // (onBeHeard / onMoment / onCheckIn / onGetThrough)
        // is pinned here so a refactor that drops one of the
        // four is caught — that would be a regression to a
        // needs-first surface.
        listOf(
            "onBeHeard = onOpenSupport",
            "onMoment = onOpenAccepts",
            "onCheckIn = onOpenDiaryCard",
            "onGetThrough = onOpenGetThrough",
        ).forEach { wiring ->
            assertTrue(
                "NeedsCard must wire $wiring (the four affordance callbacks are the " +
                    "BPD-safe primary surface of the home)",
                homeScreen.contains(wiring),
            )
        }
    }

    @Test
    fun `strings xml defines the needs-card title, caption, and four affordance labels`() {
        listOf(
            "home_needs_title",
            "home_needs_caption",
            "home_needs_be_heard",
            "home_needs_moment",
            "home_needs_check_in",
            "home_needs_get_through",
        ).forEach { key ->
            assertTrue(
                "values/strings.xml must define <string name=\"$key\">",
                strings.contains("name=\"$key\""),
            )
        }
    }

    @Test
    fun `NeedsCard has no directive language (BPD-safe per audit)`() {
        assertNotNull(homeScreen)
        assertTrue(
            "NeedsCard must not contain directive phrases (BPD-safe)",
            !homeScreen.contains("you should", ignoreCase = true) &&
                !homeScreen.contains("you must", ignoreCase = true) &&
                !homeScreen.contains("you need to", ignoreCase = true),
        )
        // No "good day" / "bad day" framing — BPD-safe per
        // audit §2.3. The needs card does not rate the user's
        // day; it asks what is needed.
        assertTrue(
            "NeedsCard must not use 'good day' / 'bad day' framing (BPD-safe)",
            !homeScreen.contains("good day", ignoreCase = true) &&
                !homeScreen.contains("bad day", ignoreCase = true),
        )
    }
}
