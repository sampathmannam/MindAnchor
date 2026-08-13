package org.mindanchor.launcher

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.25.8+ WP-3: the QuickNotesCard was below the
 * fold on 1080x2400 devices (the most common
 * emulator size and a real mid-range phone).
 * v0.25.5 WP-F added the OneThingCard to the
 * home scroll column without re-checking the
 * "always visible" comment on QuickNotesCard;
 * v0.25.5 WP-G added the haptic confirmation
 * pulse; neither commit verified the
 * "above-the-fold" claim against the 1080x2400
 * baseline.
 *
 * The fix promotes QuickNotesCard to the top of
 * the action stack: after OpenLoop, before
 * OneThing and BedtimeList. The file-shape pin
 * here is the call-site order in the home scroll
 * Column — QuickNotesCard must be rendered
 * between OpenLoopCard and OneThingCard.
 */
class QuickNotesCardAboveFoldFindingTest {

    @Test
    fun `QuickNotesCard call site is between OpenLoopCard and OneThingCard`() {
        val source = readSource("HomeScreen.kt")
        assertNotNull(source)
        // The home scroll Column is built inside
        // LauncherRoot; the function declarations
        // for the card Composables are elsewhere in
        // the file. We pin the call-site order, not
        // the function-declaration order. The call
        // site is the indented `OpenLoopCard(`
        // inside the Column body, distinct from the
        // `private fun OpenLoopCard(` declaration.
        val openLoopCall = source!!.indexOf("\n            OpenLoopCard(")
        val quickNotesCall = source.indexOf("\n            QuickNotesCard(")
        val oneThingCall = source.indexOf("\n            OneThingCard(")
        val bedtimeCall = source.indexOf("\n            BedtimeListCard(")
        assertTrue(
            "OpenLoopCard call site must come before QuickNotesCard " +
                "call site. openLoopCall=$openLoopCall " +
                "quickNotesCall=$quickNotesCall. A regression that " +
                "moved QuickNotesCard below OneThingCard would push " +
                "the home's primary capture surface below the fold " +
                "on 1080x2400 devices.",
            openLoopCall > 0 && quickNotesCall > openLoopCall,
        )
        assertTrue(
            "QuickNotesCard call site must come before OneThingCard " +
                "call site. quickNotesCall=$quickNotesCall " +
                "oneThingCall=$oneThingCall.",
            quickNotesCall > 0 && oneThingCall > quickNotesCall,
        )
        assertTrue(
            "QuickNotesCard call site must come before BedtimeListCard " +
                "call site. quickNotesCall=$quickNotesCall " +
                "bedtimeCall=$bedtimeCall.",
            quickNotesCall > 0 && bedtimeCall > quickNotesCall,
        )
    }

    private fun readSource(filename: String): String? = runCatching {
        val candidates = listOf(
            "app/src/main/java/org/mindanchor/launcher/$filename",
            "app/src/main/java/org/mindanchor/$filename",
            "../app/src/main/java/org/mindanchor/launcher/$filename",
            "../app/src/main/java/org/mindanchor/$filename",
        )
        candidates.firstNotNullOfOrNull { path ->
            val file = java.io.File(path)
            if (file.exists()) file.readText() else null
        }
    }.getOrNull()
}
