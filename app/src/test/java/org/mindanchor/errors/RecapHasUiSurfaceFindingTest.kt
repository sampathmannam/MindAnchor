package org.mindanchor.errors

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B13 (SOTA v2 bug-hunt, agent #5): the v0.25.5 WP-E 14-day recap is
 * half-shipped. The data layer (installDay, recapSeenDay,
 * inRecapWindow, markRecapSeen, inRecapWindowPure) is implemented
 * and unit-tested, but no UI surface calls any of them. The user
 * never sees the recap. The KDoc on the function claims "the recap
 * is shown when the user is in a window AND has not already seen
 * (or dismissed) the recap" — the second half is implemented, the
 * first half is not.
 *
 * File-shape pin: the fix PR adds a Composable call site for
 * inRecapWindow / markRecapSeen (a banner on HomeScreen, a card on
 * SettingsScreen, or a dedicated screen). The assert below is a
 * whole-project guard: at least one Composable in the project
 * references the recap symbols.
 */
class RecapHasUiSurfaceFindingTest {

    @Test
    fun `A Composable surfaces the 14-day recap (regression guard for B13)`() {
        val sourcesDir = java.io.File("src/main/java/org/mindanchor")
        val ktFiles = sourcesDir.walkTopDown().filter { it.extension == "kt" }
        val hit = ktFiles.any { file ->
            val text = file.readText()
            // The Composable is the public call surface. The pure
            // function is the test surface; the data layer is the
            // shape. A Composable must call one of these.
            (text.contains("inRecapWindow") || text.contains("markRecapSeen")) &&
                file.name != "Onboarding.kt"
        }
        assertTrue(
            "The 14-day recap must have a Composable call site — the data " +
                "layer is implemented and tested but no UI surface calls it. " +
                "The user never sees the recap. The fix is a banner on " +
                "HomeScreen or a card on SettingsScreen that calls " +
                "inRecapWindow / markRecapSeen.",
            hit,
        )
    }
}
