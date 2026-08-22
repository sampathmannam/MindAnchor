/*
 * v0.66.0 (DBT-grounded journal) — Task 1.
 *
 * The diary card is the canonical DBT self-monitoring sheet (Linehan 1993,
 * McKay/Wood/Brantley 2007 "The Dialectical Behavior Therapy Skills Workbook").
 * The user fills in one card per day with: urges (NSSI / suicidal /
 * dissociation, each 0..5), the emotions they noticed (drawn from the
 * existing 5-state mood enum so the mood screen and the diary card speak
 * the same vocabulary), the DBT skill they used (TIPP / ACCEPTS / etc. —
 * declared in `org.mindanchor.journal.skills`, delivered by Task 6), and
 * whether the card was shared with their therapist.
 *
 * Shape is deliberately narrow:
 *   - `urges: Urges?` (NOT `Urges` with a zero default) — null means
 *     "not yet recorded today", which the UI must render as an empty
 *     tile, never as "0 / 0 / 0". The DBT distinction between "checked
 *     and noticed no urge" and "did not check" depends on this null vs
 *     zero distinction being preserved all the way to the UI.
 *   - `emotions: List<Mood>` — multi-select (the workbook allows
 *     several per day). Reuses the existing 5-state Mood enum so the
 *     mood screen chips and the diary card chips share the same
 *     vocabulary and the same 5 values.
 *   - `skillUsed: SkillId?` — null means "no skill used today" (a
 *     legitimate non-pathological answer; the card is about noticing,
 *     not performing).
 *   - `exportedToTherapist: Boolean` — explicit rather than inferred
 *     from the existence of a PDF, because the share is its own
 *     consent moment and the user may revoke it.
 *
 * The BPD-safe defaults carried in v0.65.0 apply unchanged: no streak
 * counter, no "you've recorded N cards" copy, no ranking of cards
 * against each other. This struct is the substrate the rest of v0.66.0
 * (diary list, skill nudge, export) reads from; it carries no UI.
 */
package org.mindanchor.journal.diary

import org.mindanchor.journal.Mood
import org.mindanchor.journal.skills.SkillId
import java.time.LocalDate

// v0.66.0: marked `internal` to match the visibility of `Mood`
// (promoted from `private` to `internal` in JournalMood.kt for this
// same task). A public data class that exposes an internal type in
// its signature is a Kotlin compile error — making the wrapper
// internal keeps Mood's narrowest-necessary surface intact while
// letting the diary package see both types. Can be promoted to
// `public` in a later task once Mood itself is promoted.
internal data class Urges(
    val nssi: Int,
    val suicidal: Int,
    val dissociation: Int,
) {
    init {
        // DBT diary cards use 0..5 for urge intensity. Above 5 is
        // either a scale confusion (0..10, 0..100) or a typo; silently
        // coercing would corrupt every later aggregate (weekly average,
        // streak, trend) without flagging the input.
        require(nssi in 0..5) { "NSSI urge must be 0-5, got $nssi" }
        require(suicidal in 0..5) { "Suicidal urge must be 0-5, got $suicidal" }
        require(dissociation in 0..5) { "Dissociation urge must be 0-5, got $dissociation" }
    }
}

// See `Urges` above for why this is `internal` rather than the
// Kotlin default of `public`. The companion object inherits the
// enclosing class's visibility, so `empty()` is also module-internal.
internal data class DiaryCardEntry(
    val date: LocalDate,
    val urges: Urges?,
    val emotions: List<Mood>,
    val skillUsed: SkillId?,
    val exportedToTherapist: Boolean,
) {
    companion object {
        /**
         * The "not yet recorded today" placeholder. The diary list view
         * renders one of these for every day in the visible range so
         * the user sees a card to tap on, rather than a blank list.
         *
         * `urges = null` is load-bearing: it is what the UI checks to
         * decide between "empty tile" and "0 / 0 / 0 tile". A zero
         * triple here would silently mean "checked and noticed nothing"
         * for every day the user has not yet opened — wrong, and not
         * what DBT means by an empty card.
         */
        fun empty(date: LocalDate) = DiaryCardEntry(
            date = date,
            urges = null,
            emotions = emptyList(),
            skillUsed = null,
            exportedToTherapist = false,
        )
    }
}
