package org.mindanchor.insights

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Finding test for the v0.26+ check-in
 * patterns dashboard.
 *
 * The Composable in [CheckInInsightsSection]
 * renders up to four [Insight]s, each mapped
 * to a wording in strings.xml. This test pins
 * the surface so:
 *  1. The "What your check-ins show" section
 *     is rendered in the Check-ins area of
 *     the settings screen.
 *  2. Each of the four insights has a wording
 *     string present in the resource table.
 *  3. The Composable calls the engine (a future
 *     pass that drops the call renders an empty
 *     section).
 *  4. The section is gated on the "Ask me how I
 *     am" toggle (a future pass that always
 *     shows the section would mislead a user
 *     who has not opted in).
 *
 * The wording is the clinical-review surface; a
 * future wording pass that adds judgement
 * language ("low", "concerning", "depressed")
 * is caught here, because adding such words
 * without bumping the test means breaking the
 * N-of-1 framing the rest of the launcher is
 * built on.
 */
class CheckInPatternsInsightStringsTest {

    private fun fileAt(relative: String): File {
        val candidates = listOf(relative, "../$relative", "../../$relative")
        return candidates.map(::File).firstOrNull { it.isFile }
            ?: error("$relative not found from working directory ${File(".").absolutePath}.")
    }

    private val res: String
        get() = fileAt(
            "app/src/main/res/values/strings.xml",
        ).readText()

    private val screen: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/settings/SettingsScreen.kt",
        ).readText()

    private val section: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/insights/CheckInInsightsSection.kt",
        ).readText()

    @Test
    fun `all four pattern wording strings are present in the resource table`() {
        // The wording is the surface; if any of
        // these is missing the section would
        // crash at runtime with an unresolved
        // resource. The test pins the four
        // direction strings plus the best-hours
        // and coverage placeholders.
        listOf(
            "check_in_insights_trend_brighter",
            "check_in_insights_trend_rougher",
            "check_in_insights_trend_same",
            "check_in_insights_best_hours",
            "check_in_insights_coverage",
            "check_in_insights_baseline_brighter",
            "check_in_insights_baseline_rougher",
            "check_in_insights_baseline_same",
            "check_in_insights_empty",
        ).forEach { name ->
            assertTrue(
                "Missing string: $name. The patterns " +
                    "dashboard renders one of these " +
                    "strings; a missing entry would " +
                    "crash the Composable at runtime.",
                res.indexOf("\"$name\"") >= 0,
            )
        }
    }

    @Test
    fun `no pattern wording string introduces judgement language (N-of-1 rule)`() {
        // The N-of-1 framing the rest of the
        // launcher uses means the patterns
        // dashboard never says "low",
        // "concerning", or "depressed" — those
        // are the diagnostic words the rest of
        // the app explicitly avoids
        // (see docs/CLINICAL_REVIEW.md). A wording
        // pass that drifts toward judgement is
        // caught here.
        val judgementWords = listOf(
            "low", "concerning", "depressed",
            "anxious", "diagnos", "problem",
            "unhealthy", "abnormal",
        )
        val sectionStrings = listOf(
            "check_in_insights_trend_brighter",
            "check_in_insights_trend_rougher",
            "check_in_insights_trend_same",
            "check_in_insights_best_hours",
            "check_in_insights_coverage",
            "check_in_insights_baseline_brighter",
            "check_in_insights_baseline_rougher",
            "check_in_insights_baseline_same",
            "check_in_insights_empty",
        )
        for (name in sectionStrings) {
            val pattern = "\"$name\">(.*?)</string>"
            val match = Regex(pattern, RegexOption.DOT_MATCHES_ALL)
                .find(res) ?: error("string $name not found in strings.xml")
            val value = match.groupValues[1].lowercase()
            for (word in judgementWords) {
                assertTrue(
                    "Pattern wording '$name' contains " +
                        "judgement word '$word': $value. " +
                        "The N-of-1 framing means the " +
                        "dashboard never uses diagnostic " +
                        "language.",
                    !value.contains(word),
                )
            }
        }
    }

    @Test
    fun `CheckInInsightsSection calls the engine (not a stub)`() {
        // A future pass that drops the call to
        // CheckInPatterns.compute would render
        // an empty list and the empty-state line
        // always. The test pins the call so a
        // regression that drops the engine
        // surfaces in review.
        assertTrue(
            "CheckInInsightsSection must call " +
                "CheckInPatterns.compute — a stub that " +
                "renders an empty list is a regression.",
            section.indexOf("CheckInPatterns.compute") >= 0,
        )
    }

    @Test
    fun `the settings screen renders the section after the Check-ins toggle (gated on emaEnabled)`() {
        // The section is rendered inline below
        // the ema_learning/ema_settled Text.
        // The Composable takes the toggle state
        // so the section is hidden when the
        // user has not opted in.
        val emaEnabledIdx = screen.indexOf("emaEnabled by viewModel.emaEnabled.collectAsState()")
        val insightsIdx = screen.indexOf("CheckInInsightsSection(")
        assertTrue(
            "The settings screen must read emaEnabled " +
                "before rendering the insights section " +
                "— the section is gated on it.",
            emaEnabledIdx >= 0,
        )
        assertTrue(
            "The settings screen must render the " +
                "CheckInInsightsSection after the " +
                "Check-ins section body, not above or " +
                "below the toggle group.",
            insightsIdx > emaEnabledIdx,
        )
        assertTrue(
            "The Composable must be called with " +
                "isEnabled = emaEnabled (the toggle " +
                "value). A future pass that always " +
                "renders the section regardless of " +
                "the toggle would mislead a user who " +
                "has not opted in.",
            screen.indexOf("isEnabled = emaEnabled") >= 0,
        )
    }
}
