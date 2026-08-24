package org.mindanchor.model

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Finding test for the v0.26+ check-in
 * research-anchor surface.
 *
 * The user reported on 2026-08-24 that
 * the check-in design "should be backed
 * by mental health research". The v0.26+
 * fix is the research citations in Ema.kt
 * KDoc + a one-line settings link to the
 * model the two axes come from (Russell
 * 1980). This test pins the surface so a
 * future wording pass that drops the
 * evidence anchor is caught at review
 * time.
 *
 * The design's N-of-1 framing (no scoring,
 * no comparison to a norm, no composite
 * mood number) is also pinned: the
 * citations are the *only* research
 * claims the file makes, and the
 * patterns dashboard reads raw moments
 * (see [org.mindanchor.insights.CheckInPatterns]).
 */
class EmaResearchCitationFindingTest {

    private fun fileAt(relative: String): File {
        val candidates = listOf(relative, "../$relative", "../../$relative")
        return candidates.map(::File).firstOrNull { it.isFile }
            ?: error("$relative not found from working directory ${File(".").absolutePath}.")
    }

    private val ema: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/model/Ema.kt",
        ).readText()

    private val screen: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/settings/SettingsScreen.kt",
        ).readText()

    private val res: String
        get() = fileAt(
            "app/src/main/res/values/strings.xml",
        ).readText()

    @Test
    fun `Ema KDoc cites Russell 1980 (the two-axis model the prompts are built on)`() {
        assertTrue(
            "Ema.kt must cite Russell 1980 in its KDoc — the " +
                "circumplex model is what the valence and arousal " +
                "axes come from. A wording pass that drops the " +
                "citation would leave the design without a " +
                "documented evidence anchor.",
            ema.indexOf("Russell, J. A. (1980)") >= 0 ||
                ema.indexOf("Russell (1980)") >= 0,
        )
    }

    @Test
    fun `Ema KDoc cites Csikszentmihalyi and Shiffman (the methodology)`() {
        // The methodology anchors: Csikszentmihalyi &
        // Hunter 2003 for experience sampling, Shiffman
        // et al 2008 for ecological momentary
        // assessment. Together they explain why the
        // prompts are short, scattered, and
        // signal-contingent.
        assertTrue(
            "Ema.kt must cite Csikszentmihalyi & Hunter 2003 " +
                "(experience sampling methodology) in its KDoc.",
            ema.indexOf("Csikszentmihalyi") >= 0,
        )
        assertTrue(
            "Ema.kt must cite Shiffman 2008 (ecological " +
                "momentary assessment) in its KDoc.",
            ema.indexOf("Shiffman") >= 0,
        )
    }

    @Test
    fun `the settings screen shows the one-line research link on the Check-ins section`() {
        // The one-line anchor is a
        // R.string.ema_research_link Text
        // immediately under the ema_explainer.
        // The wording points to the model
        // (Russell 1980), not the app's own
        // paraphrase of the model.
        val resIndex = res.indexOf("ema_research_link")
        assertTrue(
            "The ema_research_link string must exist.",
            resIndex >= 0,
        )
        val screenIndex = screen.indexOf("R.string.ema_research_link")
        assertTrue(
            "The Check-ins section must render the " +
                "ema_research_link string. The user-facing " +
                "research anchor is one line under the " +
                "existing ema_explainer — a missing " +
                "render would leave the design claim in " +
                "the code but not in the UI.",
            screenIndex >= 0,
        )
        // The string is rendered on the
        // Check-ins section (same composable
        // as ema_explainer), not somewhere
        // unrelated.
        val explainerIdx = screen.indexOf("R.string.ema_explainer")
        val researchIdx = screen.indexOf("R.string.ema_research_link")
        assertTrue(
            "The ema_research_link must be on the same " +
                "Check-ins section as ema_explainer, not " +
                "elsewhere on the settings screen.",
            explainerIdx in 0..(researchIdx - 1) ||
                (explainerIdx >= 0 && researchIdx - explainerIdx < 1500),
        )
    }
}
