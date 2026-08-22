/*
 * v0.66.0 (DBT-grounded journal) — Task 5.
 *
 * The DataStore round-trip for [SkillsPrefs]. The prefs file is the
 * on-device store for the "what skill did you use on date X" tracking
 * that powers the "you used TIPP on Mon" reflection (Task 9, the
 * skill-nudge surface) and the PDF export (Task 8). One entry per
 * `LocalDate`, keyed by epoch day under the `"skills"` preferences
 * file.
 *
 * Three tests cover the three contracts every later task depends on:
 *
 *  1. `markUsed` then `usedOn` round-trips the entry unchanged. The
 *     value is the `SkillId.name` (a plain string), so the round-trip
 *     is trivial — but a typo in `keyForDate` (e.g. forgetting the
 *     `"used:"` prefix and clashing with another `Long`-keyed entry)
 *     would silently read null on the next call. Asserting equality
 *     on the `SkillId` itself catches the typo at write time.
 *
 *  2. A date that was never written returns null. The skill-nudge
 *     surface uses the null to render the "no skill recorded for this
 *     day" placeholder, NOT a default `TIPP` — picking a default would
 *     silently lie to the user about which skill they used. If
 *     `usedOn` ever silently returned `TIPP` for an unwritten date,
 *     every blank day in the diary list would show "you used TIPP".
 *
 *  3. `entriesInRange` returns only the inclusive window, sorted by
 *     date. The PDF export (Task 8) depends on this — a wrong filter
 *     silently drops or duplicates entries in the exported PDF, and
 *     a missing sort leaves the PDF in a chaotic order.
 *
 * Follows the v0.66.0 Task 2 / Task 4 pattern: JUnit 4, Robolectric
 * 4.13 with `@Config(sdk = [34])`, and a `@Before` that isolates
 * tests in the same class (DataStore is a process-wide singleton
 * keyed on the preferences name, so two tests share state without an
 * explicit reset). The reset path reaches into
 * `Context.skillsDataStore` directly because the production class
 * intentionally has no `reset()` method — `internal` on a production
 * method is module-wide callable and would let any same-module code
 * wipe the user's skill history.
 *
 * The plan's brief used `kotlinx.coroutines.test.runTest` from
 * `kotlinx-coroutines-test`. That artifact is NOT in the project
 * (no entry in `gradle/libs.versions.toml`, and the plan's "no new
 * external dependencies" rule is binding). The project's convention
 * is `kotlinx.coroutines.runBlocking` from the already-transitive
 * `kotlinx-coroutines-core`, as in the v0.25.4 round-trip test.
 */
package org.mindanchor.journal.skills

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SkillsPrefsTest {

    @Before
    fun resetStore() = runBlocking<Unit> {
        // DataStore is a process-wide singleton keyed on the
        // preferences name ("skills"), so two tests in this class
        // share state without an explicit reset. Wipe before each
        // test so a write in `mark used stores and retrieves by
        // date` does not leak into `missing date returns null`
        // (or vice versa).
        //
        // We reach into [Context.skillsDataStore] directly rather
        // than going through a `SkillsPrefs.reset()` method. The
        // top-level val is `internal` (not `private`) so this is in
        // scope; adding a `reset()` to the production class would
        // make it module-wide callable, which lets any same-module
        // code wipe the user's skill history.
        //
        // `<Unit>` is load-bearing: `edit { it.clear() }` returns
        // the cleared `Preferences`, which would otherwise be the
        // inferred return type of this `@Before` method. JUnit 4
        // requires `@Before` to be `void`-returning, and `Preferences`
        // would compile to a Java `Object` return. Forcing `<Unit>`
        // makes the contract explicit.
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.skillsDataStore.edit { it.clear() }
    }

    @Test
    fun `mark used stores and retrieves by date`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SkillsPrefs(ctx)
        val date = LocalDate.of(2026, 8, 21)
        prefs.markUsed(SkillId.TIPP, date)
        // `first()` subscribes to the flow and awaits the first
        // emission, which includes the new write. If the write did
        // not land (or the key prefix was wrong) the assertion below
        // fails loudly.
        assertEquals(SkillId.TIPP, prefs.usedOn(date).first())
    }

    @Test
    fun `missing date returns null`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SkillsPrefs(ctx)
        // A date that was never written. The store was reset in
        // @Before, so this read sees a freshly-empty store. The
        // skill-nudge surface relies on null here to render the
        // "no skill recorded" placeholder; a non-null return would
        // silently turn every blank day into a default-skill entry.
        assertNull(prefs.usedOn(LocalDate.of(1999, 1, 1)).first())
    }

    @Test
    fun `entriesInRange returns only the inclusive window, sorted by date`() = runBlocking {
        // The range query is the only public method not covered
        // by the round-trip tests, and the PDF export (Task 8)
        // depends on its filter + sort contract. Three skills
        // across three dates — outside, inside, outside — let
        // one test cover both the inclusive bounds and the
        // sortedBy(date) ordering in a single assertion.
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SkillsPrefs(ctx)
        val before = LocalDate.of(2026, 8, 19)
        val inside = LocalDate.of(2026, 8, 21)
        val after = LocalDate.of(2026, 8, 23)
        // Write in non-date order so a naive "insertion order"
        // implementation would fail the sortBy assertion.
        prefs.markUsed(SkillId.STOP, after)
        prefs.markUsed(SkillId.TIPP, before)
        prefs.markUsed(SkillId.WISE_MIND, inside)

        val inRange = prefs.entriesInRange(
            from = LocalDate.of(2026, 8, 20),
            to = LocalDate.of(2026, 8, 22),
        )
        // Only the 21st falls inside the inclusive window; the
        // list must contain it and only it, in date order.
        assertEquals(listOf(inside to SkillId.WISE_MIND), inRange)
    }
}
