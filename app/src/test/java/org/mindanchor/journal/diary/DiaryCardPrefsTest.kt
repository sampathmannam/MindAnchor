/*
 * v0.66.0 (DBT-grounded journal) — Task 2.
 *
 * The DataStore round-trip for [DiaryCardPrefs]. The prefs file is the
 * on-device store for the per-day diary card (Task 2's deliverable); the
 * diary list surface (Task 3) reads from it, the skill nudge (Task 5)
 * reads recent cards to pick a suggestion, and the PDF export (Task 8)
 * iterates a date range.
 *
 * Two tests cover the two contracts every later task depends on:
 *
 *  1. `setEntry` then `entryFor` round-trips the entry unchanged. The
 *     pipe-delimited encoding is hand-rolled, so a typo in `encode` or
 *     `decode` would silently corrupt every saved card. Asserting
 *     equality on the whole struct (date, urges, emotions, skill,
 *     export flag) catches the typo at write time, not at the export
 *     step where the user notices.
 *
 *  2. A date that was never written returns null. The list view uses
 *     the null to render the `empty(date)` placeholder, NOT a zero
 *     `Urges` triple. If `entryFor` ever silently returned an empty
 *     struct for an unwritten date, the diary list would show
 *     "0 / 0 / 0" on every blank day — the DBT "did not check" vs
 *     "checked and noticed nothing" distinction is gone.
 *
 * Follows the v0.25.4 `BackupPrefsRoundTripFindingTest` pattern: JUnit
 * 4, Robolectric 4.13 with `@Config(sdk = [34])`, and a `@Before` that
 * calls a `reset()` method on the prefs class to isolate tests in the
 * same class (DataStore is a process-wide singleton keyed on the
 * preferences name, so two tests share state without an explicit reset).
 *
 * The plan's brief used `kotlinx.coroutines.test.runTest` from
 * `kotlinx-coroutines-test`. That artifact is NOT in the project
 * (no entry in `gradle/libs.versions.toml`, and the plan's "no new
 * external dependencies" rule is binding). The project's convention
 * is `kotlinx.coroutines.runBlocking` from the already-transitive
 * `kotlinx-coroutines-core`, as in the v0.25.4 round-trip test.
 */
package org.mindanchor.journal.diary

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.journal.Mood
import org.mindanchor.journal.skills.SkillId
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiaryCardPrefsTest {

    @Before fun resetStore() = runBlocking {
        // DataStore is a process-wide singleton keyed on the
        // preferences name ("diary_card"), so two tests in this
        // class share state without an explicit reset. Wipe before
        // each test so a write in `entry round-trips` does not
        // leak into `missing entry returns null` (or vice versa).
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        DiaryCardPrefs(ctx).reset()
    }

    @Test
    fun `entry round-trips through DataStore`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = DiaryCardPrefs(ctx)
        val date = LocalDate.of(2026, 8, 21)
        val entry = DiaryCardEntry(
            date = date,
            urges = Urges(nssi = 2, suicidal = 0, dissociation = 1),
            emotions = listOf(Mood.HEAVY),
            skillUsed = SkillId.TIPP,
            exportedToTherapist = false,
        )
        prefs.setEntry(entry)
        // `first()` subscribes to the flow and awaits the first
        // emission, which includes the new write. If the write did
        // not land (or the encode/decode round-tripped incorrectly)
        // the assertion below fails loudly.
        assertEquals(entry, prefs.entryFor(date).first())
    }

    @Test
    fun `missing entry returns null`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = DiaryCardPrefs(ctx)
        // A date that was never written. The store was reset in
        // @Before, so this read sees a freshly-empty store. The
        // diary list view relies on null here to render the
        // `empty(date)` placeholder; a non-null return would
        // silently turn every blank day into a zero-urge card.
        assertNull(prefs.entryFor(LocalDate.of(1999, 1, 1)).first())
    }
}
