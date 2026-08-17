package org.mindanchor.launcher

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.25.8+ WP-3: the QuickNotesCard was below the fold on
 * 1080x2400 devices (the most common emulator size and a
 * real mid-range phone).
 *
 * v0.26.6: BedtimeListCard removed from the home surface
 * (third task-capture card overlapping with OpenLoop +
 * OneThing).
 *
 * v0.28.0: OneThingCard removed from the home surface
 * (BPD-strict cut: the first question the home asks is
 * "how is it right now?", via the HomeDistressCard). The
 * home scroll order was HomeDistress -> QuickNotes.
 *
 * v0.32.0: OpenLoopCard removed from the home surface
 * (third task-capture card cut for the same v0.26.6
 * reason). The home scroll order was HomeDistress ->
 * QuickNotes — the minimum surface that still gives the
 * URL-bar-equivalent primary capture.
 *
 * v0.35.0: HomeDistressCard replaced by NeedsCard (DBT
 * validate-then-suggest, Schwartz 1995 IFS, Lindsay 2024
 * JMIR — "what do you need?" outperforms "how distressed
 * are you?" as the BPD-strict primary surface). The home
 * scroll order is now NeedsCard -> QuickNotesCard. The
 * data model (oneThing + openLoop) is kept untouched.
 *
 * The file-shape pin here is the call-site order in the
 * home scroll Column — QuickNotesCard must be rendered
 * after NeedsCard.
 */
class QuickNotesCardAboveFoldFindingTest {

    @Test
    fun `QuickNotesCard call site is after NeedsCard and no other task-capture card is rendered (v0-35-0)`() {
        val source = readSource("HomeScreen.kt")
        assertNotNull(source)
        // v0.35.0: NeedsCard is the first card on home. The
        // QuickNotesCard is the second (and last primary
        // capture). v0.26.6 + v0.28.0 + v0.32.0 + v0.35.0
        // cumulatively removed every other task-capture
        // card from the home surface.
        val needsCall = source!!.indexOf("\n            NeedsCard(")
        val quickNotesCall = source.indexOf("\n            QuickNotesCard(")
        val openLoopCall = source.indexOf("\n            OpenLoopCard(")
        val oneThingCall = source.indexOf("\n            OneThingCard(")
        val bedtimeCall = source.indexOf("\n            BedtimeListCard(")
        val homeDistressCall = source.indexOf("\n            HomeDistressCard(")
        assertTrue(
            "NeedsCard call site must come before QuickNotesCard " +
                "call site. needsCall=$needsCall " +
                "quickNotesCall=$quickNotesCall. A regression that moved " +
                "QuickNotesCard above the needs card would push the " +
                "BPD-strict primary surface below the fold on 1080x2400.",
            needsCall > 0 && quickNotesCall > needsCall,
        )
        // v0.32.0: OpenLoopCard removed.
        assertTrue(
            "OpenLoopCard must NOT be rendered on the home surface " +
                "(v0.32.0 cut: third task-capture card removed for the " +
                "BPD-safe home. Data model is kept in LauncherViewModel). " +
                "openLoopCall=$openLoopCall.",
            openLoopCall < 0,
        )
        // v0.28.0: OneThingCard removed.
        assertTrue(
            "OneThingCard must NOT be rendered on the home surface " +
                "(v0.28.0 cut: BPD-strict home — the first question is " +
                "the needs card, not 'one thing today'). " +
                "oneThingCall=$oneThingCall.",
            oneThingCall < 0,
        )
        // v0.26.6: BedtimeListCard removed.
        assertTrue(
            "BedtimeListCard must NOT be rendered on the home surface " +
                "(v0.26.6 cut: three task-capture cards was one too many). " +
                "bedtimeCall=$bedtimeCall.",
            bedtimeCall < 0,
        )
        // v0.35.0: HomeDistressCard removed from the home surface
        // (replaced by NeedsCard).
        assertTrue(
            "HomeDistressCard must NOT be rendered on the home surface " +
                "(v0.35.0 cut: replaced by NeedsCard as the BPD-strict " +
                "primary surface). homeDistressCall=$homeDistressCall.",
            homeDistressCall < 0,
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
