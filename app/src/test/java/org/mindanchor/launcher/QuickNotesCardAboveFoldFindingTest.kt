package org.mindanchor.launcher

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.25.8+ WP-3: the QuickNotesCard was below the
 * fold on 1080x2400 devices (the most common
 * emulator size and a real mid-range phone).
 *
 * v0.26.6: BedtimeListCard removed from the home
 * surface (third task-capture card overlapping
 * with OpenLoop + OneThing).
 *
 * v0.28.0: OneThingCard removed from the home
 * surface (BPD-strict cut: the first question the
 * home asks is "how is it right now?", via the
 * HomeDistressCard). The home scroll order is now
 * HomeDistress -> OpenLoop -> QuickNotes.
 *
 * The file-shape pin here is the call-site order
 * in the home scroll Column — QuickNotesCard must
 * be rendered after OpenLoopCard and after the
 * HomeDistressCard.
 */
class QuickNotesCardAboveFoldFindingTest {

    @Test
    fun `QuickNotesCard call site is after HomeDistressCard and the OpenLoopCard is removed in v0-32-0`() {
        val source = readSource("HomeScreen.kt")
        assertNotNull(source)
        // v0.32.0: OpenLoopCard removed from home for the same
        // reason v0.28.0 cut OneThingCard and v0.26.6 cut
        // BedtimeListCard — the v0.26.6 audit counted three
        // task-capture cards as one too many for a person
        // with BPD. The home scroll is now Distress → QuickNotes
        // — the minimum surface that still gives the URL-bar-
        // equivalent primary capture. The data model
        // (LauncherViewModel.openLoop + the friction Prefs
        // DataStore) is kept untouched; this is a render-only
        // cut. The test pins the new structure.
        val distressCall = source!!.indexOf("\n            HomeDistressCard(")
        val quickNotesCall = source.indexOf("\n            QuickNotesCard(")
        val openLoopCall = source.indexOf("\n            OpenLoopCard(")
        assertTrue(
            "HomeDistressCard call site must come before QuickNotesCard " +
                "call site. distressCall=$distressCall " +
                "quickNotesCall=$quickNotesCall. A regression that moved " +
                "QuickNotesCard above the Distress Thermometer would push " +
                "the BPD-strict primary surface below the fold on 1080x2400.",
            distressCall > 0 && quickNotesCall > distressCall,
        )
        assertTrue(
            "OpenLoopCard must NOT be rendered on the home surface " +
                "(v0.32.0 cut: third task-capture card removed for the " +
                "BPD-safe home. Data model is kept in LauncherViewModel). " +
                "openLoopCall=$openLoopCall.",
            openLoopCall < 0,
        )
        // v0.28.0: OneThingCard is no longer rendered
        // on home (BPD-strict cut: the first question
        // is "how is it right now?", not "what's the
        // one thing today?"). The data model is kept
        // in LauncherViewModel for the export payload
        // and any future re-introduction.
        val oneThingCall = source.indexOf("\n            OneThingCard(")
        assertTrue(
            "OneThingCard must NOT be rendered on the home surface " +
                "(v0.28.0 cut: BPD-strict home — the first question is the " +
                "Distress Thermometer, not 'one thing today'). " +
                "oneThingCall=$oneThingCall.",
            oneThingCall < 0,
        )
        // v0.26.6: BedtimeListCard is no longer rendered
        // on home. A regression that re-introduces it
        // would re-add the third task-capture card
        // that v0.26.6 explicitly cut.
        val bedtimeCall = source.indexOf("\n            BedtimeListCard(")
        assertTrue(
            "BedtimeListCard must NOT be rendered on the home surface " +
                "(v0.26.6 cut: three task-capture cards was one too many). " +
                "bedtimeCall=$bedtimeCall. A regression that re-introduces " +
                "the card on home would re-add the cognitive load v0.26.6 " +
                "explicitly removed for the BPD-safe home.",
            bedtimeCall < 0,
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
