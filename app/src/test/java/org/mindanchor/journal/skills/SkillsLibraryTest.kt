/*
 * v0.66.0 (DBT-grounded journal) — Task 6.
 *
 * SkillsLibrary is the static metadata layer for the five DBT / ACT /
 * grounding skills surfaced in the journal: TIPP, DEAR MAN, S.T.O.P.,
 * 3-Minute Breathing Space, Wise Mind. The library is a plain
 * `List<Skill>` of well-known entries, not a fetched or generated
 * catalogue — on-device, no cloud, no telemetry.
 *
 * The three tests below guard the three invariants downstream tasks
 * (skill nudge, diary card, picker UI) depend on:
 *
 *  1. The library is exactly five entries. Adding a sixth without
 *     updating the picker UI is a silent UI bug; removing one is a
 *     silent content regression. The DBT diary card has five skill
 *     slots in the workbook (McKay/Wood/Brantley 2007); the spec
 *     calls for those five exactly. Anything else is a fork from the
 *     spec, not a freedom.
 *  2. TIPP's `howToDoIt` includes the temperature cue ("cold water")
 *     because the T in TIPP is the protocol's signature
 *     fast-acting-distress-reduction lever (Linehan 1993). A copy
 *     that omits it is no longer TIPP, and the diary card's
 *     "what did you do?" UX depends on the cue being present.
 *  3. Wise Mind's `howToDoIt` is descriptive ("middle path"), not
 *     directive ("you should"). The BPD-safe defaults carried since
 *     v0.64.0 forbid directive voice on the journal surface
 *     (no "you should / you must / you have to"). The skills are
 *     journal content, so the same rule applies.
 */
package org.mindanchor.journal.skills

import org.junit.Assert.assertEquals
import org.junit.Test

class SkillsLibraryTest {
    @Test
    fun `library has 5 skills`() = assertEquals(5, SkillsLibrary.all.size)

    @Test
    fun `TIPP includes temperature step`() {
        val tipp = SkillsLibrary.byId(SkillId.TIPP)
        assertEquals("TIPP", tipp.title)
        assert(tipp.howToDoIt.contains("cold water", ignoreCase = true))
    }

    @Test
    fun `Wise Mind is not directive`() {
        val wise = SkillsLibrary.byId(SkillId.WISE_MIND)
        assert(wise.howToDoIt.contains("middle", ignoreCase = true))
        // BPD-safety: no "you should"
        assert(!wise.howToDoIt.contains("you should", ignoreCase = true))
    }
}
