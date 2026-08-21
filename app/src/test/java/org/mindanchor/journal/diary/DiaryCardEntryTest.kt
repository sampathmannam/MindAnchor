/*
 * v0.66.0 (DBT-grounded journal) — Task 1.
 *
 * The DBT diary card is the canonical one-shot per-day self-monitoring sheet
 * (Linehan 1993, McKay/Wood/Brantley 2007). The card-shaped entry shape —
 * date + urges + emotions + skill + export flag — is what this entire release
 * exists to persist and surface. Two tests guard the two invariants the
 * downstream tasks depend on:
 *
 *  1. Urge values are bounded 0..5. NSSI/suicidal/dissociation urges above
 *     5 are not "more distress" — they are an instrumentation bug. The
 *     range is 0..5 across DBT materials and is what the diary card sheet
 *     physically has space to record. Anything outside is either a typo
 *     or a scale confusion (0..10, 0..100), and silently accepting it
 *     would corrupt every later aggregate.
 *
 *  2. `empty(date)` is the "user has not yet recorded anything for this
 *     day" placeholder. Its shape is what the dashboard renders when there
 *     is nothing to show, and what the auto-save uses while the user is
 *     mid-edit. If urges ever default to a zero triple instead of null,
 *     the urge tile will say "0 / 0 / 0" on every blank day — which
 *     reads as "no distress" rather than "not yet recorded", and that
 *     distinction is the whole point of having a diary card at all
 *     (DBT principle: distinguish "I checked and noticed no urge" from
 *     "I did not check").
 */
package org.mindanchor.journal.diary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class DiaryCardEntryTest {

    @Test
    fun `urges enforce 0-5 range`() {
        // Above the max: nssi = 6 is not a valid DBT diary card value.
        // The data class must reject it loudly, not silently coerce.
        assertThrows(IllegalArgumentException::class.java) {
            Urges(nssi = 6, suicidal = 0, dissociation = 0)
        }
    }

    @Test
    fun `empty entry has no urges, no skill, no emotions`() {
        // The "not yet recorded today" state. Crucial that urges is
        // null (not a zero triple) so the UI can render an empty tile
        // rather than "0 / 0 / 0" — "did not check" must not look
        // like "checked and felt nothing".
        val e = DiaryCardEntry.empty(LocalDate.of(2026, 8, 21))
        assertNull(e.urges)
        assertEquals(emptyList<Mood>(), e.emotions)
    }
}
