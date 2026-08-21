/*
 * v0.66.0 (DBT-grounded journal) — Task 4.
 *
 * The DataStore-backed persistence for the single-instance Stanley-Brown
 * safety plan defined in [SafetyPlanEntry]. The plan is one per user
 * (not per date) — a user writes it once and edits it over time, the
 * way a person keeps the card in their wallet updated. The plan
 * surface (JournalCrisis.kt, long-press on the crisis dials) reads
 * from it; the editor surface (Task 5) writes to it.
 *
 * ## Why six separate `stringPreferencesKey` values rather than one blob
 *
 * The natural alternative is a single key holding a serialised
 * `SafetyPlanEntry` (or a JSON blob, the way v0.28.0
 * `support.DiaryCardPrefs` did for a similar shape). That is rejected
 * for two reasons:
 *
 *  1. **No write contention across steps**: every edit to one step
 *     only touches that step's preference. A user who edits only
 *     "Means restriction" does not rewrite the other five. The
 *     DataStore write is async and does not block the main thread
 *     either way, but rewriting the whole plan on every keystroke
 *     makes the write footprint proportional to "what changed" only
 *     by accident — one stray field name in the encoder and every
 *     edit rewrites the world.
 *  2. **Partial corruption blast radius**: a torn write to a single
 *     key can leave the whole plan unrecoverable. With per-field
 *     keys, a torn write to "Warning signs" leaves the other five
 *     steps intact, which is the difference between "user loses one
 *     step they can re-type" and "user loses the whole plan they
 *     have been building for months".
 *
 * The cost is six reads instead of one on every read. The plan
 * surface reads once per screen open, not per keystroke, so the
 * extra reads are noise.
 *
 * ## Why a separate `"safety_plan"` file rather than reusing `JournalPrefs`
 *
 * `JournalPrefs` is the v0.65.0 in-progress draft store — a single
 * `today_entry` key for the editor the user is actively typing in.
 * The safety plan is a different lifecycle: it is *not* a draft, it
 * is the user's actual plan. Mixing them in one file would commit
 * the plan to the journal draft's write path, which is the wrong
 * shape. A separate DataStore name keeps the two concerns apart on
 * disk and in `adb shell run-as` dumps.
 *
 * ## Why `internal` on the top-level extension, `private` on the companion
 *
 * The top-level `Context.safetyPlanDataStore` is `internal` (not
 * `private`) so the unit test in
 * `app/src/test/java/org/mindanchor/journal/crisis/SafetyPlanPrefsTest.kt`
 * can call `context.safetyPlanDataStore.edit { it.clear() }` to
 * isolate tests in the same class. The test source set is the same
 * Gradle module as `main`, so `internal` is in scope. The production
 * class has no `reset()` method on purpose — see the `@Before` in
 * the test for the rationale. The `KEYS` list stays `private`; the
 * test does not need it.
 *
 * ## The six keys, in order
 *
 * The order MUST match [SafetyPlanEntry]'s field order and
 * [SafetyPlanEntry.LABELS]'s display order. A reordering here that
 * disagrees with `SafetyPlanEntry` would silently swap which field
 * the user sees in which step. The unit test pins the round-trip
 * with non-empty values in every field, so a key-order mismatch
 * fails at the first run.
 */
package org.mindanchor.journal.crisis

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// The DataStore name MUST NOT collide with any other
// `preferencesDataStore(name = "...")` in the project. Verified at
// Task 4 implementation time: the only existing names matching a
// `safety_*` pattern are `sms_tone_check` (SmsToneCheckPrefs), no
// `safety_plan`. The delegate is process-wide per file name — two
// `private val` extensions in different packages with the same
// name would throw `IllegalStateException: There are multiple
// DataStores active for the same file` the moment the live app
// touched both stores.
//
// `internal` (not `private`) so the unit test can call
// `context.safetyPlanDataStore.edit { it.clear() }` to isolate
// tests in the same class. See the file header for the full
// rationale.
internal val Context.safetyPlanDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "safety_plan",
)

/**
 * Single-instance Stanley-Brown safety plan persistence. One
 * [SafetyPlanEntry] for the whole user, held in six string
 * preferences.
 *
 * `public` to match [SafetyPlanEntry]'s visibility. Both are
 * deliberately public, NOT `internal`: the v0.66.0 plan surface
 * (Task 12, `org.mindanchor.journal.JournalCrisis`) is a composable
 * in the `org.mindanchor.journal` package, a sibling of
 * `org.mindanchor.journal.crisis`, so the data class and the prefs
 * class it constructs / mutates must be reachable across the
 * package boundary. The previous v0.66.0 Task 3 dispatch override
 * pinned this: "do NOT add `internal` modifier — the safety plan
 * has no visibility coupling with other internal types".
 *
 * Only the top-level [Context.safetyPlanDataStore] extension is
 * `internal` (see the file header for the test-only-extensions
 * rationale). The `public` class is the only sanctioned read /
 * write surface for code outside the `crisis` package.
 */
class SafetyPlanPrefs(private val context: Context) {

    private val store get() = context.safetyPlanDataStore

    /**
     * The current plan, as a [Flow] of [SafetyPlanEntry]. The
     * `orEmpty()` on each field means a fresh install — the user
     * has never written a plan — reads as [SafetyPlanEntry.empty],
     * not as null. The plan surface renders six blank text fields
     * in that case (matching the "no plan yet" placeholder on the
     * first launch). If this ever returned null, the surface would
     * have to special-case it; the `orEmpty()` keeps the call site
     * straight.
     */
    val plan: Flow<SafetyPlanEntry> = store.data.map { prefs ->
        SafetyPlanEntry(
            warningSigns = prefs[KEYS[0]].orEmpty(),
            internalCoping = prefs[KEYS[1]].orEmpty(),
            socialDistractions = prefs[KEYS[2]].orEmpty(),
            people = prefs[KEYS[3]].orEmpty(),
            professionals = prefs[KEYS[4]].orEmpty(),
            meansRestriction = prefs[KEYS[5]].orEmpty(),
        )
    }

    /**
     * Persist the whole plan. Every field is written; passing a
     * partially-filled [SafetyPlanEntry] (e.g. only `warningSigns`
     * set) overwrites the other five with the values the caller
     * passed. The editor surface (Task 5) is responsible for
     * reading the current plan first, mutating the field the user
     * edited, and passing the result — so a half-built plan on
     * disk does not get clobbered.
     *
     * The DataStore write is async, so a per-keystroke call does
     * not block the main thread. The call site may debounce if
     * needed; the storage layer does not care.
     */
    suspend fun set(plan: SafetyPlanEntry) {
        store.edit { prefs ->
            prefs[KEYS[0]] = plan.warningSigns
            prefs[KEYS[1]] = plan.internalCoping
            prefs[KEYS[2]] = plan.socialDistractions
            prefs[KEYS[3]] = plan.people
            prefs[KEYS[4]] = plan.professionals
            prefs[KEYS[5]] = plan.meansRestriction
        }
    }

    private companion object {
        // The six Stanley-Brown step keys, in display order. The
        // order MUST match [SafetyPlanEntry]'s field order and
        // [SafetyPlanEntry.LABELS]; the unit test pins the
        // round-trip so a key-order swap fails at first run.
        val KEYS = listOf(
            stringPreferencesKey("warning_signs"),
            stringPreferencesKey("internal_coping"),
            stringPreferencesKey("social_distractions"),
            stringPreferencesKey("people"),
            stringPreferencesKey("professionals"),
            stringPreferencesKey("means_restriction"),
        )
    }
}
