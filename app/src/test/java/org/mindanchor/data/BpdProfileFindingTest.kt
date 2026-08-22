package org.mindanchor.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.26.0 FindingTest: the BpdProfile data class + BpdProfilePrefs
 * store exist, and the §3.3 / §3.5 heuristic gates read from them.
 *
 * File-shape pin: the prefs class lives at
 * `app/src/main/java/org/mindanchor/data/BpdProfile.kt` and exposes
 * a `profile: Flow<BpdProfile>` plus an `update(profile)` suspend
 * function. The data class has the five opt-in flags the plan
 * names (longMessagesIRegret, lateNightImpulses, sometimesISplit,
 * namedPersonToCall, okAtNight) — never labelled "BPD" in
 * user-facing copy.
 *
 * Behavioural test: defaults are all false. A fresh install
 * never auto-enables any heuristic.
 */
class BpdProfileFindingTest {

    @Test
    fun `BpdProfile data class shape — five opt-in flags, all default false`() {
        val profile = BpdProfile()
        assertFalse("longMessagesIRegret must default to false", profile.longMessagesIRegret)
        assertFalse("lateNightImpulses must default to false", profile.lateNightImpulses)
        assertFalse("sometimesISplit must default to false", profile.sometimesISplit)
        assertFalse("namedPersonToCall must default to false", profile.namedPersonToCall)
        assertFalse("okAtNight must default to false", profile.okAtNight)
    }

    @Test
    fun `BpdProfile data class shape — five fields, not more`() {
        // v0.26.0 ships with exactly five opt-in flags. A
        // future flag is a one-line data class addition +
        // a one-line prefs key + a one-line settings row,
        // not a v0.26.0 surface change. This assertion is
        // the regression guard for "no flag creep".
        //
        // Uses java reflection (no kotlin-reflect dependency)
        // so the test runs on a plain JVM classpath.
        val profile = BpdProfile()
        // Kotlin compiler adds a synthetic $stable field for data
        // classes; filter it out.
        val fieldNames = profile.javaClass.declaredFields
            .map { it.name }
            .filter { !it.startsWith("$") }
            .toSet()
        assertEquals(
            "BpdProfile must have exactly 5 fields (the v0.26.0 plan's five flags)",
            setOf(
                "longMessagesIRegret",
                "lateNightImpulses",
                "sometimesISplit",
                "namedPersonToCall",
                "okAtNight",
            ),
            fieldNames,
        )
    }

    @Test
    fun `BpdProfile copy is value-semantic`() {
        val original = BpdProfile()
        val updated = original.copy(longMessagesIRegret = true)
        assertTrue(updated.longMessagesIRegret)
        assertFalse("Other flags must not change", updated.lateNightImpulses)
        assertFalse("Other flags must not change", updated.okAtNight)
    }
}
