/*
 * v0.66.0 (DBT-grounded journal) — Task 4.
 *
 * The DataStore round-trip for [SafetyPlanPrefs]. The prefs file is the
 * on-device store for the single-instance Stanley-Brown safety plan
 * (Task 3's [SafetyPlanEntry]). The plan surface reads from it on
 * every screen the user opens; this test pins the read-after-write
 * contract so a typo in the six string keys (or a missing `internal`
 * on the [Context.safetyPlanDataStore] extension) is caught at test
 * time, not at the moment the user opens the crisis surface.
 *
 * One test:
 *  1. `plan round-trips through DataStore` — `set(plan)` followed by
 *     `plan.first()` returns the same struct. Asserting equality on
 *     the whole [SafetyPlanEntry] (six string fields) catches a
 *     key-order swap (e.g. writing `people` to the `professionals`
 *     slot) on the first run, not the day a real user opens the
 *     crisis screen and sees the wrong field.
 *
 * Follows the v0.66.0 Task 2 `DiaryCardPrefsTest` pattern: JUnit 4,
 * Robolectric 4.13 with `@Config(sdk = [34])`, and a `@Before` that
 * isolates tests in the same class. The reset path reaches into
 * `Context.safetyPlanDataStore` directly because the production
 * class intentionally has no `reset()` method — `internal` on a
 * production method is module-wide callable and would let any
 * same-module code wipe the user's plan.
 *
 * The plan's brief used `kotlinx.coroutines.test.runTest` from
 * `kotlinx-coroutines-test`. That artifact is NOT in the project
 * (no entry in `gradle/libs.versions.toml`, and the plan's "no new
 * external dependencies" rule is binding). The project's convention
 * is `kotlinx.coroutines.runBlocking` from the already-transitive
 * `kotlinx-coroutines-core`, as in the v0.25.4 round-trip test.
 */
package org.mindanchor.journal.crisis

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SafetyPlanPrefsTest {

    @Before
    fun resetStore() = runBlocking<Unit> {
        // DataStore is a process-wide singleton keyed on the
        // preferences name ("safety_plan"), so two tests in this
        // class share state without an explicit reset. Wipe before
        // each test so a write in `plan round-trips` does not leak
        // into a later test (or vice versa).
        //
        // We reach into [Context.safetyPlanDataStore] directly rather
        // than going through a `SafetyPlanPrefs.reset()` method. The
        // top-level val is `internal` (not `private`) so this is in
        // scope; adding a `reset()` to the production class would
        // make it module-wide callable, which lets any same-module
        // code wipe the user's plan.
        //
        // `<Unit>` is load-bearing: `edit { it.clear() }` returns
        // the cleared `Preferences`, which would otherwise be the
        // inferred return type of this `@Before` method. JUnit 4
        // requires `@Before` to be `void`-returning, and `Preferences`
        // would compile to a Java `Object` return. Forcing `<Unit>`
        // makes the contract explicit.
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.safetyPlanDataStore.edit { it.clear() }
    }

    @Test
    fun `plan round-trips through DataStore`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SafetyPlanPrefs(ctx)
        // Non-empty values for every field. The iCall number
        // (9152987821) is the v0.27.0 BPD-safe default that lives
        // in the app's resource layer — pin the literal here so a
        // key-typo in [SafetyPlanPrefs] swaps it to a different
        // field and the equality assertion fires.
        val p = SafetyPlanEntry(
            warningSigns = "I notice X",
            internalCoping = "Walk",
            socialDistractions = "Cafe Y",
            people = "Friend Z",
            professionals = "iCall 9152987821",
            meansRestriction = "Lock box",
        )
        prefs.set(p)
        // `first()` subscribes to the flow and awaits the first
        // emission, which includes the new write. If the write did
        // not land (or the six keys were mapped in the wrong order
        // in `set` vs `plan`) the assertion below fails loudly.
        val loaded = prefs.plan.first()
        assertEquals(p, loaded)
    }
}
