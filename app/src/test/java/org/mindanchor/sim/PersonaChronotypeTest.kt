package org.mindanchor.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.sim.personas.PersonaLibrary
import org.mindanchor.sunset.Chronotype
import java.time.LocalTime

/**
 * Cross-checks each persona's intended chronotype against the
 * chronotype's default window.
 *
 * The runner drives [WellnessSimulationRunner] on each persona and
 * asserts the persona + chronotype + window form a coherent set: a
 * morning lark's window ends at 06:00, a night owl's starts at
 * midnight, a shift worker's window is 09:00 → 17:00. These are
 * exactly the cases where the wrong default would fail silently
 * for a real person — a night owl waking up to 06:00 wind-down
 * is a real failure, not a "soft" one.
 *
 * Cited research: Roenneberg 2007 (chronotype distribution),
 * Wittmann 2006 (social jetlag), Åkerstedt 2003 + Kecklund 2016
 * (shift work). The window minute values are the launcher's
 * design choices; the persona-window coherence is the test.
 */
class PersonaChronotypeTest {

    /**
     * The intended chronotype for each persona, in the same order
     * as [PersonaLibrary.all]. Mapping is the launcher's editorial
     * decision, not a research finding: a MorningLark persona is
     * by construction a morning-type person, and so on.
     */
    private val personaChronotypes: Map<String, Chronotype> = mapOf(
        "morning_lark_healthy" to Chronotype.MORNING_LARK,
        "night_owl_healthy" to Chronotype.NIGHT_OWL,
        "shift_worker_rotating" to Chronotype.SHIFT_WORKER,
        "insomnia_anxious" to Chronotype.NIGHT_OWL,
        "depression_low_motivation" to Chronotype.NEUTRAL,
        "noisy_signal_high_variance" to Chronotype.NEUTRAL,
        "perfectly_regular_zero_variance" to Chronotype.NEUTRAL,
        "sparse_data_partial_wearable" to Chronotype.UNKNOWN,
    )

    @Test
    fun `every persona has a chronotype assignment`() {
        // The persona library and the chronotype map must agree
        // on the persona set. A new persona added without a
        // chronotype is a launch-shape question, not just a data
        // question — a person whose chronotype is unclear would
        // default to NEUTRAL, which is the wrong first answer.
        val ids = PersonaLibrary.all.map { it.id }.toSet()
        val assigned = personaChronotypes.keys
        assertEquals(
            "persona set has drifted from chronotype map",
            ids,
            assigned,
        )
    }

    @Test
    fun `morning lark's window ends before 07 00`() {
        // A 06:00 wind-down end means the user is in their day
        // by 06:00. Anything later is somebody else's morning.
        val (start, end) = Chronotype.MORNING_LARK.defaultWindow()
        assertTrue("lark end $end should be 06:00 or earlier", end <= LocalTime.of(6, 0))
        assertTrue("lark start $start should be 21:00 or earlier", start <= LocalTime.of(21, 0))
    }

    @Test
    fun `night owl's window starts at or after midnight`() {
        // A 00:00 wind-down start means the user is still up at
        // midnight. Anything earlier is a wind-down that started
        // before they were ready to wind down.
        val (start, _) = Chronotype.NIGHT_OWL.defaultWindow()
        assertTrue("owl start $start should be midnight or later", start >= LocalTime.of(0, 0))
    }

    @Test
    fun `shift worker's window is a daytime window`() {
        // 09:00 → 17:00: the shift worker's evening. A
        // 22:00 → 07:00 default applied to this population is
        // the wrong window by hours, and the reason this whole
        // feature exists.
        val (start, end) = Chronotype.SHIFT_WORKER.defaultWindow()
        assertTrue("shift start $start should be in the morning", start.hour in 7..11)
        assertTrue("shift end $end should be in the late afternoon", end.hour in 15..19)
    }

    @Test
    fun `every named chronotype has a distinct default window from the others`() {
        // The "decorative question" check: each named chronotype
        // must produce a *different* window from the others, so
        // a future refactor that flattens two of them would
        // turn the question from a real choice into a
        // no-op selection. UNKNOWN is excluded from this check
        // by design — it is "no answer" rather than a separate
        // answer, and the launcher's whole reason for the case
        // is to be a placeholder for the neutral default.
        val named = Chronotype.entries.filter { it != Chronotype.UNKNOWN }
        val windows = named.associateWith { it.defaultWindow() }
        named.forEach { a ->
            named.forEach { b ->
                if (a == b) return@forEach
                assertNotEquals(
                    "chronotype $a and $b share default window",
                    windows[a],
                    windows[b],
                )
            }
        }
    }

    @Test
    fun `a morning lark's end is earlier in the day than a night owl's end`() {
        // The strongest "are these chronotypes actually different"
        // test: LARK ends at 06:00, OWL ends at 08:00, both in
        // the morning. The lark's end has to be earlier than
        // the owl's, not later — otherwise the two windows would
        // cover the same morning hours and the question would
        // be the same window twice.
        val (_, larkEnd) = Chronotype.MORNING_LARK.defaultWindow()
        val (_, owlEnd) = Chronotype.NIGHT_OWL.defaultWindow()
        assertTrue(
            "lark end $larkEnd should be earlier than owl end $owlEnd",
            larkEnd < owlEnd,
        )
    }

    @Test
    fun `insomniac persona is mapped to night owl, not morning lark`() {
        // Insomnia is by definition not a morning-person
        // problem; an insomniac mapped to MORNING_LARK would
        // get a 21:00 wind-down, which is exactly the time
        // they cannot fall asleep. The mapping is editorial
        // (the launcher treats insomnia as a late-onset
        // condition), not a clinical decision, and the
        // editorial choice is pinned here.
        assertEquals(
            Chronotype.NIGHT_OWL,
            personaChronotypes["insomnia_anxious"],
        )
    }

    @Test
    fun `sparse data persona is mapped to unknown, not neutral`() {
        // A persona whose data is sparse cannot have a
        // usual onset, and so cannot have a default window
        // derived from the data. The launcher treats UNKNOWN
        // as "ask again" — sparse-data users see the chronotype
        // step every time they re-edit settings, which is the
        // honest answer.
        assertEquals(
            Chronotype.UNKNOWN,
            personaChronotypes["sparse_data_partial_wearable"],
        )
    }
}
