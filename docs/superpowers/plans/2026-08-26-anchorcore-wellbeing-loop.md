# AnchorCore — Wellbeing Loop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One on-device aggregator turns existing signals (screen rhythm, wellness vitals, sleep deviation) into per-day facts and a trailing week picture; the daily letter, friction tone, a sunset proposal card, and PreHome's morning surface adapt around it.

**Architecture:** A new pure-Kotlin package `org.mindanchor.anchorcore` computes facts from data passed in (no new sensing, no timers). Four consumers subscribe: letter context (Hook A), friction tone hold (Hook B), sunset proposal card (Hook C), and the PreHome open-loop handback. All state persists in DataStore using the codebase's existing ledger discipline.

**Tech Stack:** Kotlin, Jetpack Compose, DataStore Preferences, kotlinx.coroutines flows. No new dependencies. JUnit4 for tests.

**Spec:** `docs/superpowers/specs/2026-08-26-anchorcore-wellbeing-loop-design.md`

## Global Constraints

- Zero new permissions; zero network calls (`NetworkCallsForbiddenTest` must stay green).
- Facts, never labels: no interpretation wording anywhere. All user-visible copy passes the clinical-review wordlist conventions (no "good"/"bad", no diagnosis language, no directives).
- No new math: reuse `WellnessStats` (median + MAD + 0.6745) from `vitals/WellnessSignals.kt`.
- Master toggle default OFF (opt-out-by-silence rule, matching `prehomeEnabled` precedent).
- Cold start honest: < 7 observed days → `WARMING_UP`; hooks do nothing.
- Match existing code style: heavy KDoc with citations where design-relevant, `runCatching` at IO boundaries, tab-ledger codecs only when persisting lists.
- Every task ends with its unit tests green via `gradlew testDebugUnitTest --tests "..."`.
- Build command (from repo root): `.\gradlew.bat testDebugUnitTest --tests "<pattern>"` on Windows PowerShell; use `--tests "org.mindanchor.anchorcore.*"` to run the whole package.

## File Structure

```
app/src/main/java/org/mindanchor/anchorcore/
    DayFact.kt          — fact enum + one renderer per fact (pure)
    AnchorState.kt      — sealed state + WeekPicture hysteresis reducer (pure)
    AnchorCore.kt       — orchestrator: pulls sources, reduces to AnchorState
    AnchorPrefs.kt      — DataStore: master toggle + per-hook toggles + card dismissals + sunset override
app/src/main/java/org/mindanchor/llm/LetterContext.kt      — Hook A: facts section in prompt
app/src/main/java/org/mindanchor/friction/FrictionTone.kt  — Hook B: flagged-week ladder shift
app/src/main/java/org/mindanchor/launcher/LauncherViewModel.kt — wire AnchorCore + proposal card state
app/src/main/java/org/mindanchor/launcher/HomeScreen.kt        — SunsetProposalCard composable + wiring
app/src/main/java/org/mindanchor/prehome/PreHomeActivity.kt    — open-loop handback block
app/src/main/java/org/mindanchor/settings/SettingsScreen.kt    — toggles UI
app/src/main/java/org/mindanchor/settings/SettingsViewModel.kt — toggle surface
app/src/test/java/org/mindanchor/anchorcore/
    DayFactTest.kt, AnchorStateTest.kt, FrictionToneHoldTest.kt,
    LetterContextFactsTest.kt, AnchorCoreTest.kt, AnchorPrefsTest.kt
app/src/test/java/org/mindanchor/prehome/PreHomeHandbackTest.kt
```

---

### Task 1: DayFact — the fact types and their plain-language renderers

**Files:**
- Create: `app/src/main/java/org/mindanchor/anchorcore/DayFact.kt`
- Test: `app/src/test/java/org/mindanchor/anchorcore/DayFactTest.kt`

**Interfaces:**
- Produces: `enum class FactKind { LATE_NIGHT_CLUSTER, SLEEP_IRREGULAR, MOVEMENT_LOW, HRV_LOW, RHR_HIGH }`, `data class DayFact(val kind: FactKind, val detail: String, val day: LocalDate)`. Later tasks construct `DayFact`s and call `DayFact.render(kind, detail)`.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.mindanchor.anchorcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DayFactTest {

    @Test
    fun `late night cluster renders nights and usual without interpreting`() {
        val line = DayFactRenderer.render(FactKind.LATE_NIGHT_CLUSTER, "4|75")
        // "4 nights this week ran well past your usual bedtime."
        assertTrue(line.contains("4"))
        assertTrue(line.contains("usual"))
    }

    @Test
    fun `sleep irregular renders the sri drop`() {
        val line = DayFactRenderer.render(FactKind.SLEEP_IRREGULAR, "18")
        assertTrue(line.contains("18"))
        assertTrue(line.contains("regularity", ignoreCase = true))
    }

    @Test
    fun `movement low renders the direction not a verdict`() {
        val line = DayFactRenderer.render(FactKind.MOVEMENT_LOW, "-2.1")
        assertTrue(line.contains("below", ignoreCase = true))
    }

    @Test
    fun `every kind has a renderer`() {
        for (kind in FactKind.entries) {
            val line = DayFactRenderer.render(kind, "1|x")
            assertTrue(line.isNotBlank())
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "org.mindanchor.anchorcore.DayFactTest"`
Expected: FAIL — unresolved reference `FactKind`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package org.mindanchor.anchorcore

import java.time.LocalDate

/**
 * The five deviations AnchorCore can name, each carrying its own numbers.
 *
 * A fact without its numbers is a label in disguise, so every [DayFact]
 * carries a `detail` payload the renderer unpacks. The renderers state a
 * count or a direction and stop — the same law as `Deviation.worthShowing`
 * ("states a fact about somebody's own screen and never interprets it")
 * and `GateLedger` ("reports a fact and declines to interpret it").
 */
enum class FactKind {
    /** N nights this week ran ≥90 min past the person's own median onset. */
    LATE_NIGHT_CLUSTER,

    /** The sleep-regularity score dropped by N points vs the prior week. */
    SLEEP_IRREGULAR,

    /** Steps robust-z below -2 against the person's own baseline. */
    MOVEMENT_LOW,

    /** HRV robust-z below -2 against the person's own baseline. */
    HRV_LOW,

    /** Resting heart rate robust-z above +2 against their own baseline. */
    RHR_HIGH,
}

/** One deviation, on one day, with the numbers that make it checkable. */
data class DayFact(
    val kind: FactKind,
    /** Pipe-separated numeric payload; each renderer documents its slots. */
    val detail: String,
    val day: LocalDate,
)

object DayFactRenderer {

    /**
     * Plain sentence per kind. Detail slots:
     *  - LATE_NIGHT_CLUSTER: "nights|medianOnsetMinute" (unused slot kept
     *    for future renderers so the payload shape stays uniform)
     *  - SLEEP_IRREGULAR: "sriDropPoints"
     *  - MOVEMENT_LOW / HRV_LOW / RHR_HIGH: "robustZ"
     *
     * Direction-only wording ("below your usual"), never evaluative
     * ("bad week") — the band vocabulary the launcher already uses on
     * the wellness card.
     */
    fun render(kind: FactKind, detail: String): String = when (kind) {
        FactKind.LATE_NIGHT_CLUSTER -> {
            val nights = detail.substringBefore('|')
            "$nights nights this week ran well past your usual bedtime."
        }
        FactKind.SLEEP_IRREGULAR ->
            "Your sleep regularity dropped about ${detail} points from last week."
        FactKind.MOVEMENT_LOW ->
            "Steps have been below your usual range."
        FactKind.HRV_LOW ->
            "Resting heart-rate variability is below your usual range."
        FactKind.RHR_HIGH ->
            "Resting heart rate is above your usual range."
    }
}
```

Note: the test calls `DayFactRenderer.render(...)` (the implementation object name), not `DayFact.render`. Keep this naming in the implementation.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "org.mindanchor.anchorcore.DayFactTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/org/mindanchor/anchorcore/DayFact.kt app/src/test/java/org/mindanchor/anchorcore/DayFactTest.kt
git commit -m "feat(anchorcore): DayFact kinds + direction-only renderers"
```

---

### Task 2: AnchorState — week flagging with 7-day clean hysteresis

**Files:**
- Create: `app/src/main/java/org/mindanchor/anchorcore/AnchorState.kt`
- Test: `app/src/test/java/org/mindanchor/anchorcore/AnchorStateTest.kt`

**Interfaces:**
- Produces: `sealed interface AnchorState { WarmingUp(daysObserved: Int); Steady(facts: List<DayFact>, weekFlagged: Boolean, computedAtEpochMillis: Long) }`, `object WeekPicture { const val CLEAN_DAYS_TO_UNFLAG = 7; fun reduce(flaggedToday: Boolean, cleanStreak: Int): Int }` — returns the new clean-streak length; callers persist it.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.mindanchor.anchorcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnchorStateTest {

    @Test
    fun `fewer than seven observed days warm up`() {
        assertEquals(AnchorState.WarmingUp(6), AnchorState.of(daysObserved = 6, facts = emptyList(), now = 0L))
    }

    @Test
    fun `seven days with no facts is steady and unflagged`() {
        val s = AnchorState.of(daysObserved = 7, facts = emptyList(), now = 5L)
        assertTrue(s is AnchorState.Steady)
        assertEquals(false, (s as AnchorState.Steady).weekFlagged)
    }

    @Test
    fun `a fact today flags the week`() {
        val fact = DayFact(FactKind.LATE_NIGHT_CLUSTER, "4|75", java.time.LocalDate.now())
        val s = AnchorState.of(daysObserved = 10, facts = listOf(fact), now = 5L)
        assertEquals(true, (s as AnchorState.Steady).weekFlagged)
    }

    @Test
    fun `clean streak resets on a flagged day`() {
        assertEquals(0, WeekPicture.reduce(flaggedToday = true, cleanStreak = 4))
    }

    @Test
    fun `clean streak grows on a clean day`() {
        assertEquals(3, WeekPicture.reduce(flaggedToday = false, cleanStreak = 2))
    }

    @Test
    fun `streak of seven unflags`() {
        var streak = 0
        streak = WeekPicture.reduce(flaggedToday = false, cleanStreak = streak)
        streak = WeekPicture.reduce(flaggedToday = false, cleanStreak = streak)
        streak = WeekPicture.reduce(flaggedToday = false, cleanStreak = streak)
        streak = WeekPicture.reduce(flaggedToday = false, cleanStreak = streak)
        streak = WeekPicture.reduce(flaggedToday = false, cleanStreak = streak)
        streak = WeekPicture.reduce(flaggedToday = false, cleanStreak = streak)
        streak = WeekPicture.reduce(flaggedToday = false, cleanStreak = streak)
        assertEquals(WeekPicture.CLEAN_DAYS_TO_UNFLAG, streak)
        assertEquals(true, WeekPicture.isFlagged(flaggedToday = false, cleanStreak = streak))
    }

    @Test
    fun `isFlagged is true while any fact within window or streak short`() {
        assertEquals(true, WeekPicture.isFlagged(flaggedToday = true, cleanStreak = 6))
        assertEquals(false, WeekPicture.isFlagged(flaggedToday = false, cleanStreak = 7))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "org.mindanchor.anchorcore.AnchorStateTest"`
Expected: FAIL — unresolved reference `AnchorState`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package org.mindanchor.anchorcore

/**
 * The loop's whole output: either warming up or steady, and when steady,
 * which facts are live and whether the trailing week is flagged.
 */
sealed interface AnchorState {
    data class WarmingUp(val daysObserved: Int) : AnchorState

    data class Steady(
        val facts: List<DayFact>,
        val weekFlagged: Boolean,
        val computedAtEpochMillis: Long,
    ) : AnchorState {
        /** Convenience for hooks that only care about late-night clustering. */
        val lateNightCluster: DayFact?
            get() = facts.firstOrNull { it.kind == FactKind.LATE_NIGHT_CLUSTER }
    }

    companion object {
        /**
         * Below [MIN_OBSERVED_DAYS] there is no baseline to read anything
         * against — the spec's cold-start rule: the app says nothing until
         * it knows something.
         */
        const val MIN_OBSERVED_DAYS = 7

        fun of(daysObserved: Int, facts: List<DayFact>, now: Long): AnchorState =
            if (daysObserved < MIN_OBSERVED_DAYS) {
                WarmingUp(daysObserved)
            } else {
                Steady(facts = facts, weekFlagged = facts.isNotEmpty(), computedAtEpochMillis = now)
            }
    }
}

/**
 * The flagged-week hysteresis: any fact keeps the week flagged; seven
 * consecutive clean days unflag it. Stored as an int streak so persistence
 * is one number (AnchorPrefs, Task 5).
 */
object WeekPicture {
    const val CLEAN_DAYS_TO_UNFLAG = 7

    /** New streak length after today. Flag resets it to zero. */
    fun reduce(flaggedToday: Boolean, cleanStreak: Int): Int =
        if (flaggedToday) 0 else (cleanStreak + 1).coerceAtMost(CLEAN_DAYS_TO_UNFLAG)

    /** Flagged while any fact fired today, or before the streak completes. */
    fun isFlagged(flaggedToday: Boolean, cleanStreak: Int): Boolean =
        flaggedToday || cleanStreak < CLEAN_DAYS_TO_UNFLAG
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "org.mindanchor.anchorcore.AnchorStateTest"`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/org/mindanchor/anchorcore/AnchorState.kt app/src/test/java/org/mindanchor/anchorcore/AnchorStateTest.kt
git commit -m "feat(anchorcore): AnchorState warm-up gate + week-flag hysteresis"
```

---

### Task 3: AnchorCore — compute facts from screen rhythm + vitals inputs

**Files:**
- Create: `app/src/main/java/org/mindanchor/anchorcore/AnchorCore.kt`
- Test: `app/src/test/java/org/mindanchor/anchorcore/AnchorCoreTest.kt`

**Interfaces:**
- Consumes: `WellnessStats.median/mad/baseline/reading`, `PersonalBaseline.robustZ(value)`, `WellnessSignal.MIN_HISTORY_DAYS` (14), `Deviation.usual/laterThanUsual/MIN_NIGHTS` (all existing, `org.mindanchor.vitals` / `org.mindanchor.sleep`), `SleepMath.regularityScore`.
- Produces: `object AnchorCore { const val FLAG_Z = 2.0; fun observedDays(unlockMinutesByDay: Map<LocalDate, Int?>, vitalDays: Set<LocalDate>): Int; fun lateNightCluster(onsetsMinutesAfterSixPm: List<Int>, today: LocalDate): DayFact?; fun vitalFacts(readings: List<WellnessReading>, today: LocalDate): List<DayFact>; fun sleepIrregular(thisWeekSri: Int?, lastWeekSri: Int?, today: LocalDate): DayFact? }`

- [ ] **Step 1: Write the failing test**

```kotlin
package org.mindanchor.anchorcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.sleep.Deviation
import java.time.LocalDate

class AnchorCoreTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 26)

    @Test
    fun `observed days counts union of rhythm days and vital days`() {
        val rhythm = mapOf(today.minusDays(1) to 1380, today.minusDays(2) to null)
        val vitals = setOf(today.minusDays(2))
        assertEquals(2, AnchorCore.observedDays(rhythm, vitals))
    }

    @Test
    fun `cluster fires when four of seven onsets run ninety past usual`() {
        // Usual onset ~23:00 => minutes-after-18:00 = 300. Four nights at
        // >=450 (+90 min) fire Deviation.laterThanUsual == 4.
        val onsets = listOf(300, 300, 480, 480, 480, 480, 300)
        val fact = AnchorCore.lateNightCluster(onsets, today)
        assertNotNull(fact)
        assertTrue(fact!!.detail.startsWith("4|"))
    }

    @Test
    fun `cluster stays silent under five nights`() {
        val onsets = listOf(480, 480, 480, 480)
        assertNull(AnchorCore.lateNightCluster(onsets, today))
    }

    @Test
    fun `sleep irregular fires on an eighteen point drop`() {
        val fact = AnchorCore.sleepIrregular(thisWeekSri = 60, lastWeekSri = 78, today = today)
        assertNotNull(fact)
        assertEquals("18", fact!!.detail)
    }

    @Test
    fun `sleep irregular silent on rise`() {
        assertNull(AnchorCore.sleepIrregular(thisWeekSri = 80, lastWeekSri = 70, today = today))
    }

    @Test
    fun `vital facts need z beyond two`() {
        // Build readings directly through WellnessStats with a synthetic
        // history whose MAD makes z crossable.
        val history = List(20) { 100.0 } + 150.0
        val baseline = org.mindanchor.vitals.WellnessStats.baseline(
            org.mindanchor.vitals.WellnessSignal.STEPS,
            history.dropLast(1),
        )
        val reading = org.mindanchor.vitals.WellnessStats.reading(
            org.mindanchor.vitals.WellnessSignal.STEPS,
            today = 20.0, // far below median 100
            baseline = baseline,
        )
        val facts = AnchorCore.vitalFacts(listOf(reading), today)
        assertEquals(1, facts.size)
        assertEquals(FactKind.MOVEMENT_LOW, facts[0].kind)
    }

    @Test
    fun `vital facts stay silent inside the bands`() {
        val history = List(20) { 100.0 }
        val baseline = org.mindanchor.vitals.WellnessStats.baseline(
            org.mindanchor.vitals.WellnessSignal.HRV,
            history,
        )
        val reading = org.mindanchor.vitals.WellnessStats.reading(
            org.mindanchor.vitals.WellnessSignal.HRV,
            today = 101.0,
            baseline = baseline,
        )
        assertTrue(AnchorCore.vitalFacts(listOf(reading), today).isEmpty())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "org.mindanchor.anchorcore.AnchorCoreTest"`
Expected: FAIL — unresolved reference `AnchorCore`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package org.mindanchor.anchorcore

import java.time.LocalDate
import org.mindanchor.sleep.Deviation
import org.mindanchor.vitals.PersonalBaseline
import org.mindanchor.vitals.WellnessSignal
import org.mindanchor.vitals.WellnessStats

/**
 * The aggregator. Pure functions over data already collected elsewhere;
 * the Context-carrying wrapper lives in AnchorCoreSource (Task 5) so the
 * arithmetic stays JVM-testable, the same split as SleepMath/SleepRepository
 * and ScreenRhythm/RhythmRepository.
 */
object AnchorCore {

    /**
     |z| >= 2.0 flags a vital. Jacobson 2019 (J Nerv Ment Dis 207:893-6)
     uses 2.0-2.5 per-person anomaly cut-offs; 2.0 is the launcher's
     choice, documented here for traceability.
     */
    const val FLAG_Z = 2.0

    /** Sri drop below this many points counts as irregular. Design choice. */
    const val SRI_DROP_POINTS = 15

    /**
     * A day counts as observed when it has a first-unlock value (non-null
     * entry in the map) or any vital reading. Absent days are absent, not
     * zero-filled — the spec's definition. The union: rhythm days with a
     * value, plus vital-only days (days whose unlock entry is null/absent).
     */
    fun observedDays(
        unlockMinutesByDay: Map<LocalDate, Int?>,
        vitalDays: Set<LocalDate>,
    ): Int =
        unlockMinutesByDay.values.count { it != null } +
            vitalDays.count { unlockMinutesByDay[it] == null }

    /**
     * LATE_NIGHT_CLUSTER when enough nights exist and at least one runs
     * ≥90 min past the person's own median onset (Deviation's rule).
     * Detail payload: "nights|medianOnset".
     */
    fun lateNightCluster(onsets: List<Int>, today: LocalDate): DayFact? {
        if (!Deviation.worthShowing(onsets)) return null
        val n = Deviation.laterThanUsual(onsets)
        if (n <= 0) return null
        return DayFact(
            kind = FactKind.LATE_NIGHT_CLUSTER,
            detail = "$n|${Deviation.usual(onsets)}",
            day = today,
        )
    }

    /** Detail payload: "drop". Silent unless the score actually fell. */
    fun sleepIrregular(thisWeekSri: Int?, lastWeekSri: Int?, today: LocalDate): DayFact? {
        if (thisWeekSri == null || lastWeekSri == null) return null
        val drop = lastWeekSri - thisWeekSri
        if (drop < SRI_DROP_POINTS) return null
        return DayFact(FactKind.SLEEP_IRREGULAR, "$drop", today)
    }

    /**
     * Vital facts for the low-direction signals (steps, HRV down; RHR up).
     * Requires the baseline to be reportable (>= MIN_HISTORY_DAYS) — the
     * same floor WellnessReading already applies, so this adds none.
     */
    fun vitalFacts(
        readings: List<org.mindanchor.vitals.WellnessReading>,
        today: LocalDate,
    ): List<DayFact> = readings.mapNotNull { r ->
        val z = r.zScore ?: return@mapNotNull null
        val kind = when (r.signal) {
            WellnessSignal.STEPS ->
                if (z <= -FLAG_Z) FactKind.MOVEMENT_LOW else null
            WellnessSignal.HRV ->
                if (z <= -FLAG_Z) FactKind.HRV_LOW else null
            WellnessSignal.RESTING_HEART_RATE ->
                if (z >= FLAG_Z) FactKind.RHR_HIGH else null
            else -> null
        } ?: return@mapNotNull null
        DayFact(kind, "%.1f".format(z), today)
    }
}
```

Fix `observedDays` before committing (remove the placeholder line):

```kotlin
fun observedDays(
    unlockMinutesByDay: Map<LocalDate, Int?>,
    vitalDays: Set<LocalDate>,
): Int =
    unlockMinutesByDay.values.count { it != null } +
        vitalDays.count { unlockMinutesByDay[it] == null }
```

Also delete the unused imports (`PersonalBaseline`, `WellnessStats`) if detekt flags them — keep only what compiles cleanly.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "org.mindanchor.anchorcore.AnchorCoreTest"`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/org/mindanchor/anchorcore/AnchorCore.kt app/src/test/java/org/mindanchor/anchorcore/AnchorCoreTest.kt
git commit -m "feat(anchorcore): fact computation from rhythms + vitals"
```

---

### Task 4: Hook B — friction tone holds FULL longer on flagged weeks

**Files:**
- Modify: `app/src/main/java/org/mindanchor/friction/FrictionTone.kt`
- Create: `app/src/test/java/org/mindanchor/anchorcore/FrictionToneHoldTest.kt` (test lives beside the other anchorcore tests)

**Interfaces:**
- Consumes: existing `FrictionContext.toneFor(recentOpens: Int, insideSleepWindow: Boolean): FrictionTone`.
- Produces: `FrictionContext.toneFor(recentOpens: Int, insideSleepWindow: Boolean, weekFlagged: Boolean = false): FrictionTone` — default parameter keeps all three existing call sites compiling unchanged.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.mindanchor.anchorcore

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mindanchor.friction.FrictionContext
import org.mindanchor.friction.FrictionTone

class FrictionToneHoldTest {

    @Test
    fun `unflagged weeks keep the current ladder`() {
        assertEquals(FrictionTone.BRIEF, FrictionContext.toneFor(1, false, weekFlagged = false))
        assertEquals(FrictionTone.FEATHER, FrictionContext.toneFor(3, false, weekFlagged = false))
    }

    @Test
    fun `flagged week holds full longer outside the sleep window`() {
        // Second reach: still FULL on a flagged week (would be BRIEF otherwise).
        assertEquals(FrictionTone.FULL, FrictionContext.toneFor(1, false, weekFlagged = true))
        // Third reach: BRIEF now arrives one repeat later than usual.
        assertEquals(FrictionTone.BRIEF, FrictionContext.toneFor(2, false, weekFlagged = true))
        assertEquals(FrictionTone.FEATHER, FrictionContext.toneFor(5, false, weekFlagged = true))
    }

    @Test
    fun `inside the sleep window full wins regardless`() {
        for (opens in 0..9) {
            assertEquals(
                FrictionTone.FULL,
                FrictionContext.toneFor(opens, true, weekFlagged = true),
            )
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "org.mindanchor.anchorcore.FrictionToneHoldTest"`
Expected: FAIL — no parameter `weekFlagged`.

- [ ] **Step 3: Implement**

In `FrictionTone.kt`, add two constants and widen `toneFor`:

```kotlin
object FrictionContext {

    const val RECENT_WINDOW_MILLIS = 10 * 60 * 1000L
    const val REPEATS_BEFORE_FEATHER = 3
    const val REPEATS_BEFORE_BRIEF = 1

    // v-next (AnchorCore Hook B): on a flagged week — AnchorState said
    // something deviated this trailing week — the soften ladder backs
    // off one step. Repetition inside a hard week is more likely the
    // loop talking than weak resolve, so the ceremony earns a longer
    // chance before it demotes itself. Sleep window still wins over
    // everything, exactly as before.
    const val FLAGGED_REPEATS_BEFORE_BRIEF = 2
    const val FLAGGED_REPEATS_BEFORE_FEATHER = 5

    fun toneFor(
        recentOpens: Int,
        insideSleepWindow: Boolean,
        weekFlagged: Boolean = false,
    ): FrictionTone = when {
        insideSleepWindow -> FrictionTone.FULL
        recentOpens >= (if (weekFlagged) FLAGGED_REPEATS_BEFORE_FEATHER else REPEATS_BEFORE_FEATHER) -> FrictionTone.FEATHER
        recentOpens >= (if (weekFlagged) FLAGGED_REPEATS_BEFORE_BRIEF else REPEATS_BEFORE_BRIEF) -> FrictionTone.BRIEF
        else -> FrictionTone.FULL
    }
}
```

Leave the three existing call sites untouched (default parameter covers them).

- [ ] **Step 4: Run both suites**

Run: `.\gradlew.bat testDebugUnitTest --tests "org.mindanchor.friction.FrictionToneTest" --tests "org.mindanchor.anchorcore.FrictionToneHoldTest"`
Expected: PASS — old ladder pinned by `FrictionToneTest`, new behaviour by the hold test.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/org/mindanchor/friction/FrictionTone.kt app/src/test/java/org/mindanchor/anchorcore/FrictionToneHoldTest.kt
git commit -m "feat(friction): tone ladder holds FULL longer on flagged weeks (Hook B)"
```

---

### Task 5: AnchorPrefs + AnchorCoreSource — persistence and the Context wrapper

**Files:**
- Create: `app/src/main/java/org/mindanchor/anchorcore/AnchorPrefs.kt`
- Create: `app/src/main/java/org/mindanchor/anchorcore/AnchorCoreSource.kt`
- Create: `app/src/test/java/org/mindanchor/anchorcore/AnchorPrefsTest.kt`

**Interfaces:**
- Produces: `class AnchorPrefs(context) { val enabled: Flow<Boolean>; suspend fun setEnabled(v: Boolean); val letterFactsEnabled/frictionHoldEnabled/sunsetProposalEnabled: Flow<Boolean> + setters; suspend fun cleanStreak(): Int; suspend fun setCleanStreak(v: Int); suspend fun recordProposalDismissed(); suspend fun proposalSuppressedUntil(): Instant?; suspend fun recordOverrideAccepted(days: Int): Pair<LocalTime, LocalTime>? }` and `class AnchorCoreSource(context) { suspend fun state(): AnchorState }`.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.mindanchor.anchorcore

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AnchorPrefsTest {

    private val prefs
        get() = AnchorPrefs(ApplicationProvider.getApplicationContext())

    @Test
    fun `master defaults off`() = runBlocking {
        assertFalse(prefs.isEnabled())
    }

    @Test
    fun `hooks default off until master asked`() = runBlocking {
        assertFalse(prefs.letterFactsFirst())
        prefs.setEnabled(true)
        assertTrue(prefs.isEnabled())
        assertTrue(prefs.letterFactsFirst()) // enabling master flips hook defaults on once
    }
}
```

Note: if Robolectric is not already a test dependency in this project, replace `ApplicationProvider` with the pattern used by an existing DataStore test in the repo — search `preferencesDataStore` usages under `app/src/test` first and mirror whichever harness exists. If no such harness exists, make `AnchorPrefs` methods take a `DataStore<Preferences>` constructor param and test the pure logic (`clean-streak` clamp, suppression-window arithmetic) extracted into a small `companion object` helper instead.

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "org.mindanchor.anchorcore.AnchorPrefsTest"`
Expected: FAIL — unresolved reference `AnchorPrefs`.

- [ ] **Step 3: Implement AnchorPrefs**

```kotlin
package org.mindanchor.anchorcore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.anchorDataStore by preferencesDataStore(name = "anchorcore")

/**
 * The loop's switches and counters. One DataStore, same discipline as
 * SunsetPrefs: typed keys, defaults that match the opt-out-by-silence
 * rule, and nothing interpreted.
 */
class AnchorPrefs(private val context: Context) {

    private val enabledKey = booleanPreferencesKey("anchor_enabled")
    private val letterKey = booleanPreferencesKey("hook_letter_facts")
    private val frictionKey = booleanPreferencesKey("hook_friction_hold")
    private val proposalKey = booleanPreferencesKey("hook_sunset_proposal")
    private val cleanStreakKey = intPreferencesKey("week_clean_streak")
    private val suppressedUntilKey = longPreferencesKey("proposal_suppressed_until_epoch_millis")

    val enabled: Flow<Boolean> = context.anchorDataStore.data.map { it[enabledKey] ?: false }
    val letterFactsEnabled: Flow<Boolean> = context.anchorDataStore.data.map { it[letterKey] ?: false }
    val frictionHoldEnabled: Flow<Boolean> = context.anchorDataStore.data.map { it[frictionKey] ?: false }
    val sunsetProposalEnabled: Flow<Boolean> = context.anchorDataStore.data.map { it[proposalKey] ?: false }

    suspend fun isEnabled(): Boolean = enabled.first()

    /**
     * First enable flips every hook on (the user asked for the loop);
     * afterwards each hook toggles independently. The `was` check makes
     * the latch one-way: only the transition off->on initialises hooks.
     */
    suspend fun setEnabled(v: Boolean) {
        val was = context.anchorDataStore.data.first()[enabledKey] ?: false
        context.anchorDataStore.edit {
            it[enabledKey] = v
            if (v && !was && it[letterKey] == null) it[letterKey] = true
            if (v && !was && it[frictionKey] == null) it[frictionKey] = true
            if (v && !was && it[proposalKey] == null) it[proposalKey] = true
        }
    }

    suspend fun setLetterFactsEnabled(v: Boolean) { context.anchorDataStore.edit { it[letterKey] = v } }
    suspend fun setFrictionHoldEnabled(v: Boolean) { context.anchorDataStore.edit { it[frictionKey] = v } }
    suspend fun setSunsetProposalEnabled(v: Boolean) { context.anchorDataStore.edit { it[proposalKey] = v } }

    suspend fun cleanStreak(): Int = context.anchorDataStore.data.first()[cleanStreakKey] ?: 0
    suspend fun setCleanStreak(v: Int) {
        context.anchorDataStore.edit { it[cleanStreakKey] = v.coerceIn(0, WeekPicture.CLEAN_DAYS_TO_UNFLAG) }
    }

    suspend fun recordProposalDismissed(now: Instant = Instant.now()) {
        context.anchorDataStore.edit {
            it[suppressedUntilKey] = now.plusSeconds(14L * 24 * 3600).toEpochMilli()
        }
    }

    suspend fun proposalSuppressedUntil(): Instant? =
        context.anchorDataStore.data.first()[suppressedUntilKey]
            ?.takeIf { it > System.currentTimeMillis() }
            ?.let { Instant.ofEpochMilli(it) }
}
```

Also add the flow the proposal card needs:

```kotlin
fun suppressedUntilFlow(): Flow<Instant?> =
    context.anchorDataStore.data.map { prefs ->
        prefs[suppressedUntilKey]
            ?.takeIf { it > System.currentTimeMillis() }
            ?.let { Instant.ofEpochMilli(it) }
    }
```

- [ ] **Step 4: Implement AnchorCoreSource**

```kotlin
package org.mindanchor.anchorcore

import android.content.Context
import java.time.LocalDate
import org.mindanchor.usage.RhythmRepository
import org.mindanchor.vitals.WellnessRepository

/**
 * The Context-carrying face: pulls what the existing repositories already
 * expose and hands it to [AnchorCore]. Recomputed on demand by its callers
 * (PreHome render, letter generation, Home compose) — never on a timer.
 */
class AnchorCoreSource(private val context: Context) {

    suspend fun state(today: LocalDate = LocalDate.now()): AnchorState {
        val prefs = AnchorPrefs(context)
        if (!prefs.isEnabled()) return AnchorState.WarmingUp(-1) // inert; callers check enabled first

        val zone = java.time.ZoneId.systemDefault()
        val week = (0L..6L).map { today.minusDays(it) }
        val rhythms = RhythmRepository(context).rhythms(week)
        val readings = runCatching { WellnessRepository(context).readingsFor(today) }.getOrDefault(emptyList())

        val onsets = rhythms?.entries
            ?.sortedBy { it.key }
            ?.mapNotNull { e -> e.value.firstUnlockMinute?.let { org.mindanchor.sleep.Deviation.minutesAfterSixPm(it) } }
            .orEmpty()
        val observedRhythmDays = rhythms?.count { it.value.firstUnlockMinute != null || it.value.screenMinutes != null } ?: 0
        val observedVitalDays = readings.count { it.today != null }

        val facts = buildList {
            AnchorCore.lateNightCluster(onsets, today)?.let { add(it) }
            addAll(AnchorCore.vitalFacts(readings, today))
        }
        return AnchorState.of(
            daysObserved = maxOf(observedRhythmDays, observedVitalDays),
            facts = facts,
            now = System.currentTimeMillis(),
        )
    }
}
```

- [ ] **Step 5: Run tests**

Run: `.\gradlew.bat testDebugUnitTest --tests "org.mindanchor.anchorcore.*"`
Expected: PASS — all anchorcore suites green (Robolectric path) or AnchorPrefsTest adjusted to the pure-helper fallback noted in Step 1.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/org/mindanchor/anchorcore/AnchorPrefs.kt app/src/main/java/org/mindanchor/anchorcore/AnchorCoreSource.kt app/src/test/java/org/mindanchor/anchorcore/AnchorPrefsTest.kt
git commit -m "feat(anchorcore): prefs + context-facing source"
```

---

### Task 6: Hook A — letter prompt gains the week's facts

**Files:**
- Modify: `app/src/main/java/org/mindanchor/llm/LetterContext.kt` (add optional `factsSection` param threaded into the prompt)
- Modify: `app/src/main/java/org/mindanchor/letters/LetterViewModel.kt` (compute the section when enabled)
- Create: `app/src/test/java/org/mindanchor/anchorcore/LetterContextFactsTest.kt`

**Interfaces:**
- Consumes: `LetterContext.build(today, notes, checkIns, now, zone)`, `LetterPrompt.userPrompt(...)` (existing signatures — extend `userPrompt` with `factsSection: String = ""` defaulted so the shape test keeps passing).
- Produces: `LetterContext.build(..., factsSection: String = "")`; helper `object LetterFactsSection { fun compose(state: AnchorState): String? }` returning `"This week's own-data notes:\n- ...\n"` or null.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.mindanchor.anchorcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LetterContextFactsTest {

    @Test
    fun `steady with facts composes bullet lines`() {
        val fact = DayFact(FactKind.LATE_NIGHT_CLUSTER, "4|300", LocalDate.now())
        val state = AnchorState.Steady(listOf(fact), weekFlagged = true, computedAtEpochMillis = 0L)
        val section = LetterFactsSection.compose(state)!!
        assertTrue(section.contains("own-data"))
        assertTrue(section.startsWith("- ") || section.contains("\n- "))
        assertFalse(section.contains("good", ignoreCase = true))
        assertFalse(section.contains("bad", ignoreCase = true))
    }

    @Test
    fun `warming up composes nothing`() {
        assertNull(LetterFactsSection.compose(AnchorState.WarmingUp(3)))
    }

    @Test
    fun `steady without facts composes nothing`() {
        val state = AnchorState.Steady(emptyList(), weekFlagged = false, computedAtEpochMillis = 0L)
        assertNull(LetterFactsSection.compose(state))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "org.mindanchor.anchorcore.LetterContextFactsTest"`
Expected: FAIL — unresolved reference `LetterFactsSection`.

- [ ] **Step 3: Implement the composer + thread the parameter**

New file `app/src/main/java/org/mindanchor/anchorcore/LetterFactsSection.kt`:

```kotlin
package org.mindanchor.anchorcore

/**
 * Renders AnchorState into the letter-prompt block (Hook A). Bullets only:
 * the model reads sentences, the person reads the letter, and neither is
 * served by adjectives. Direction-only wording comes from the renderers;
 * this composer just frames them as observations of the person's own data.
 */
object LetterFactsSection {

    fun compose(state: AnchorState): String? {
        val steady = state as? AnchorState.Steady ?: return null
        if (steady.facts.isEmpty()) return null
        return steady.facts.joinToString("\n") { "- ${DayFactRenderer.render(it.kind, it.detail)}" }
            .let { "Notes from this week's own data:\n$it\n" }
    }
}
```

In `LetterContext.build`, add `factsSection: String = ""` as the last parameter; append after the check-in section:

```kotlin
val userPrompt = LetterPrompt.userPrompt(
    /* existing args unchanged */
    factsSection = factsSection,
)
```

In `LetterPrompt.userPrompt`, add `factsSection: String = ""` as the last parameter and insert between `[Most recent check-in]` and the closing instruction:

```kotlin
${if (factsSection.isBlank()) "" else "\n[facts from the week]\n  $factsSection"}
```

In `LetterViewModel.runGeneration`, after `checkIns` are read:

```kotlin
val anchorPrefs = AnchorPrefs(context /* application */)
val factsSection = if (anchorPrefs.isEnabled() && anchorPrefs.letterFactsEnabled.first()) {
    runCatching { AnchorCoreSource(context).state(today) }.getOrNull()
        ?.let { LetterFactsSection.compose(it) }
} else null
val request = LetterContext.build(today, notes, checkIns, factsSection = factsSection.orEmpty())
```

(`context` here is whatever Application reference the VM already carries — mirror how `letterLog` gets its context.)

- [ ] **Step 4: Run tests**

Run: `.\gradlew.bat testDebugUnitTest --tests "org.mindanchor.llm.*" --tests "org.mindanchor.anchorcore.LetterContextFactsTest"`
Expected: PASS — including the existing `LetterPromptShapeTest` (defaulted parameter leaves the pinned prompt byte-identical when empty).

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/org/mindanchor/anchorcore/LetterFactsSection.kt app/src/main/java/org/mindanchor/llm/LetterContext.kt app/src/main/java/org/mindanchor/llm/LetterPrompt.kt app/src/main/java/org/mindanchor/letters/LetterViewModel.kt app/src/test/java/org/mindanchor/anchorcore/LetterContextFactsTest.kt
git commit -m "feat(llm): letter prompt gains optional week-facts block (Hook A)"
```

---

### Task 7: Hook C — sunset proposal card on Home

**Files:**
- Modify: `app/src/main/java/org/mindanchor/data/SunsetPrefs.kt` (temporary override keys)
- Modify: `app/src/main/java/org/mindanchor/launcher/LauncherViewModel.kt` (proposal StateFlow + actions)
- Modify: `app/src/main/java/org/mindanchor/launcher/HomeScreen.kt` (card composable + wiring)
- Create: `app/src/test/java/org/mindanchor/anchorcore/SunsetProposalTest.kt` (pure decision function tested)

**Interfaces:**
- Produces: `object SunsetProposal { data class Decision(val show: Boolean, val reason: Reason); enum class Reason { DISABLED, WARMING, NO_CLUSTER, SUPPRESSED, SHOW }; fun decide(enabled: Boolean, state: AnchorState, suppressedUntil: Instant?, nowMillis: Long): Decision }`; VM exposes `val sunsetProposalCard: StateFlow<SunsetProposal.Decision>` + `fun acceptSunsetProposal()` + `fun dismissSunsetProposal()`.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.mindanchor.anchorcore

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SunsetProposalTest {

    private val steadyFlagged = AnchorState.Steady(
        facts = listOf(DayFact(FactKind.LATE_NIGHT_CLUSTER, "4|300", LocalDate.now())),
        weekFlagged = true,
        computedAtEpochMillis = 0L,
    )

    @Test
    fun `shows only when enabled steady clustered and not suppressed`() {
        assertEquals(
            SunsetProposal.Reason.SHOW,
            SunsetProposal.decide(true, steadyFlagged, null, 1000L).reason,
        )
    }

    @Test
    fun `warming never shows`() {
        assertEquals(
            SunsetProposal.Reason.WARMING,
            SunsetProposal.decide(true, AnchorState.WarmingUp(9), null, 1000L).reason,
        )
    }

    @Test
    fun `no cluster no card`() {
        val calm = AnchorState.Steady(emptyList(), false, 0L)
        assertEquals(SunsetProposal.Reason.NO_CLUSTER, SunsetProposal.decide(true, calm, null, 1000L).reason)
    }

    @Test
    fun `suppressed hides until the window passes`() {
        assertEquals(
            SunsetProposal.Reason.SUPPRESSED,
            SunsetProposal.decide(true, steadyFlagged, java.time.Instant.ofEpochMilli(999_999_999L), 1000L).reason,
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "org.mindanchor.anchorcore.SunsetProposalTest"`
Expected: FAIL — unresolved `SunsetProposal`.

- [ ] **Step 3: Implement the pure decision + override store**

New file `app/src/main/java/org/mindanchor/anchorcore/SunsetProposal.kt`:

```kotlin
package org.mindanchor.anchorcore

import java.time.Instant

/**
 * Whether the quiet one-card proposal may appear: only when the loop is
 * on, steady, carrying a live late-night cluster, and the person has not
 * recently dismissed it. Never auto-applies — the autonomy law holds.
 */
object SunsetProposal {

    enum class Reason { DISABLED, WARMING, NO_CLUSTER, SUPPRESSED, SHOW }

    data class Decision(val show: Boolean, val reason: Reason)

    const val SUPPRESS_DAYS = 14L
    const val OVERRIDE_DAYS = 7

    fun decide(
        enabled: Boolean,
        state: AnchorState,
        suppressedUntil: Instant?,
        nowMillis: Long,
    ): Decision = when {
        !enabled -> Decision(false, Reason.DISABLED)
        state !is AnchorState.Steady -> Decision(false, Reason.WARMING)
        state.lateNightCluster == null -> Decision(false, Reason.NO_CLUSTER)
        suppressedUntil != null && suppressedUntil.toEpochMilli() > nowMillis -> Decision(false, Reason.SUPPRESSED)
        else -> Decision(true, Reason.SHOW)
    }
}
```

In `SunsetPrefs`, add:

```kotlin
private val overrideStartKey = intPreferencesKey("sunset_override_start_minute")
private val overrideEndKey = intPreferencesKey("sunset_override_end_minute")
private val overrideExpiryKey = longPreferencesKey("sunset_override_expiry_day")

/** ISO date string; null when expired or unset. */
suspend fun activeWindowOverride(): Pair<LocalTime, LocalTime>? {
    val prefs = context.dataStore.data.first()
    val expiry = prefs[overrideExpiryKey]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: return null
    if (expiry < LocalDate.now()) return null
    val s = prefs[overrideStartKey] ?: return null
    val e = prefs[overrideEndKey] ?: return null
    return LocalTime.of(s / 60, s % 60) to LocalTime.of(e / 60, e % 60)
}

suspend fun setTemporaryWindow(start: LocalTime, end: LocalTime, until: LocalDate) {
    context.dataStore.edit {
        it[overrideStartKey] = start.hour * 60 + start.minute
        it[overrideEndKey] = end.hour * 60 + end.minute
        it[overrideExpiryKey] = until.toString()
    }
}
```

Then change `window()` to prefer the live override:

```kotlin
suspend fun window(): Pair<LocalTime, LocalTime> =
    activeWindowOverride() ?: run {
        val prefs = context.dataStore.data.first()
        timeOf(prefs[startKey], DEFAULT_START) to timeOf(prefs[endKey], DEFAULT_END)
    }
```

In `LauncherViewModel` add (mirroring the `weeklyPatterns` pattern):

```kotlin
val sunsetProposalCard: StateFlow<SunsetProposal.Decision> = combine(
    anchorPrefs.sunsetProposalEnabled,
    anchorCoreState,           // see below
    anchorPrefs.suppressedUntilFlow(),
) { hookOn, state, suppressed ->
    SunsetProposal.decide(hookOn && anchorPrefs.isEnabled(), state, suppressed, System.currentTimeMillis())
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SunsetProposal.Decision(false, SunsetProposal.Reason.DISABLED))

fun acceptSunsetProposal() = viewModelScope.launch {
    val (start, _) = sunsetPrefs.window()
    val earlier = start.minusMinutes(30)
    sunsetPrefs.setTemporaryWindow(earlier, /* keep current end */ sunsetPrefs.endTime.first(), LocalDate.now().plusDays(SunsetProposal.OVERRIDE_DAYS))
}
fun dismissSunsetProposal() = viewModelScope.launch { anchorPrefs.recordProposalDismissed() }
```

Where `anchorCoreState` is a lazily-refreshed StateFlow refreshed on home-surface composition via `viewModel.refreshAnchorState()` calling `AnchorCoreSource(application).state()` into a MutableStateFlow — recompute-on-demand per the spec, triggered from `LauncherRoot`'s `LaunchedEffect(Unit)`.

In `HomeSurface`, add params `sunsetProposal: SunsetProposal.Decision = SunsetProposal.Decision(false, SunsetProposal.Reason.DISABLED)`, `onAcceptSunsetProposal: () -> Unit = {}`, `onDismissSunsetProposal: () -> Unit = {}`, and render after `NOfOnePatternsCard`:

```kotlin
if (sunsetProposal.show) {
    SunsetProposalCard(
        onAccept = onAcceptSunsetProposal,
        onDismiss = onDismissSunsetProposal,
    )
}
```

with the composable (placed near the other cards):

```kotlin
@Composable
private fun SunsetProposalCard(onAccept: () -> Unit, onDismiss: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(.92f)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Some recent nights ran late.", style = MaterialTheme.typography.titleSmall)
            Text(
                "Want the wind-down to begin 30 minutes earlier this week?",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onAccept) { Text("Yes") }
                TextButton(onClick = onDismiss) { Text("Not now") }
            }
        }
    }
}
```

- [ ] **Step 4: Run tests + lint**

Run: `.\gradlew.bat testDebugUnitTest --tests "org.mindanchor.anchorcore.*" ; .\gradlew.bat lintDebug`
Expected: unit tests PASS; lint reports no new errors.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/org/mindanchor/anchorcore/SunsetProposal.kt app/src/main/java/org/mindanchor/data/SunsetPrefs.kt app/src/main/java/org/mindanchor/launcher/LauncherViewModel.kt app/src/main/java/org/mindanchor/launcher/HomeScreen.kt app/src/test/java/org/mindanchor/anchorcore/SunsetProposalTest.kt
git commit -m "feat(launcher): one-card sunset proposal on flagged weeks (Hook C)"
```

---

### Task 8: PreHome open-loop handback + one sleep fact

**Files:**
- Modify: `app/src/main/java/org/mindanchor/prehome/PreHomeActivity.kt`
- Create: `app/src/test/java/org/mindanchor/prehome/PreHomeHandbackTest.kt` (pure logic tested)

**Interfaces:**
- Consumes: `FrictionPrefs.openLoopNote/openLoopDay/openLoopPostponedAt/clearOpenLoop()`, `OpenLoop.phase(...)`, `RhythmRepository.rhythms(days)`, `MorningIntentionRepository` (existing PreHome wiring).
- Produces: `object MorningHandback { data class Handback(val note: String, val shouldClear: Boolean); fun decide(phase: LoopPhase, note: String?): Handback?; fun sleepFact(lastNightUnlockMinute: Int?, usualUnlockMinute: Int?): String? }` — used by `PreHomeSurface`'s new LaunchedEffect + card.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.mindanchor.prehome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mindanchor.friction.LoopPhase

class PreHomeHandbackTest {

    @Test
    fun `return phase hands the note back and clears`() {
        val hb = MorningHandback.decide(LoopPhase.RETURN, "call the bank")
        assertEquals("call the bank", hb!!.note)
        assertEquals(true, hb.shouldClear)
    }

    @Test
    fun `other phases say nothing`() {
        assertNull(MorningHandback.decide(LoopPhase.NONE, "x"))
        assertNull(MorningHandback.decide(LoopPhase.POSTPONED, "x"))
        assertNull(MorningHandback.decide(LoopPhase.CAPTURE, null))
    }

    @Test
    fun `sleep fact speaks only when fortyfive past usual`() {
        val fact = MorningHandback.sleepFact(lastNightUnlockMinute = 23 * 60 + 50, usualUnlockMinute = 23 * 60 + 0)
        assertNull(fact)
        val late = MorningHandback.sleepFact(lastNightUnlockMinute = 1 * 60 + 30, usualUnlockMinute = 23 * 60 + 0)
        org.junit.Assert.assertNotNull(late)
    }

    @Test
    fun `missing data stays silent`() {
        assertNull(MorningHandback.sleepFact(null, 1380))
        assertNull(MorningHandback.sleepFact(1400, null))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "org.mindanchor.prehome.PreHomeHandbackTest"`
Expected: FAIL — unresolved `MorningHandback`.

- [ ] **Step 3: Implement**

New file `app/src/main/java/org/mindanchor/prehome/MorningHandback.kt`:

```kotlin
package org.mindanchor.prehome

import org.mindanchor.friction.LoopPhase

/**
 * PreHome's morning additions: the open-loop handback (Masicampo &
 * Baumeister 2011 — writing the plan releases the loop) and at most one
 * sleep fact. Pure decisions; the activity does the DataStore work.
 */
object MorningHandback {

    data class Handback(val note: String, val shouldClear: Boolean)

    /** Only RETURN speaks, and speaking means clearing — one handback each. */
    fun decide(phase: LoopPhase, note: String?): Handback? {
        if (phase != LoopPhase.RETURN) return null
        val body = note?.takeIf { it.isNotBlank() } ?: return null
        return Handback(body, shouldClear = true)
    }

    /** Late only when 45+ minutes past the person's own usual first unlock. */
    const val LATE_BY_MINUTES = 45

    fun sleepFact(lastNightUnlockMinute: Int?, usualUnlockMinute: Int?): String? {
        val last = lastNightUnlockMinute ?: return null
        val usual = usualUnlockMinute ?: return null
        if (last - usual < LATE_BY_MINUTES) return null
        fun fmt(m: Int): String {
            val t = m % 1440
            return "%d:%02d %s".format(
                if (t / 60 % 12 == 0) 12 else t / 60 % 12,
                t % 60,
                if (t / 60 >= 12) "pm" else "am",
            )
        }
        return "Up until ${fmt(last)}; your usual is ${fmt(usual)}."
    }
}
```

In `PreHomeActivity`'s `PreHomeSurface`, load the loop phase the way `LauncherViewModel.openLoop` does (read `openLoopNote/Day/PostponedAt` via a `FrictionPrefs(applicationContext)`, evaluate `OpenLoop.phase` once on first composition), then render above the intention field when `MorningHandback.decide(...)` returns non-null:

```kotlin
handback?.let { hb ->
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Still open from last night", style = MaterialTheme.typography.titleSmall)
            Text(hb.note, style = MaterialTheme.typography.bodyMedium)
        }
    }
    LaunchedEffect(hb) {
        if (hb.shouldClear) frictionPrefs.clearOpenLoop()
    }
}
sleepFactLine?.let {
    Text(it, style = MaterialTheme.typography.bodySmall)
}
```

Compute `usualUnlockMinute` as `Deviation.usual(onsets)` over the prior week's first-unlock values (same `minutesAfterSixPm` caveat accepted: compare raw minutes-of-day here, both sides from the same clock, so the wrap issue cancels out for typical cases; skip the fact when either side is missing).

- [ ] **Step 4: Run tests**

Run: `.\gradlew.bat testDebugUnitTest --tests "org.mindanchor.prehome.*"`
Expected: PASS (new + existing prehome tests).

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/org/mindanchor/prehome/MorningHandback.kt app/src/main/java/org/mindanchor/prehome/PreHomeActivity.kt app/src/test/java/org/mindanchor/prehome/PreHomeHandbackTest.kt
git commit -m "feat(prehome): open-loop handback + one-sentence sleep fact"
```

---

### Task 9: Settings toggles + CLINICIAN_PACK regeneration

**Files:**
- Modify: `app/src/main/java/org/mindanchor/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/org/mindanchor/settings/SettingsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Regenerate: `docs/CLINICIAN_PACK.md` via `python tools/clinician-pack.py` (CI enforces freshness)

**Interfaces:**
- Consumes: `AnchorPrefs` (Task 5), SettingsViewModel's existing pattern (`val x = prefs.x.stateIn(...); fun setX(v) = viewModelScope.launch { ... }`), SettingsScreen's existing toggle-row composable (same one `settings_prehome_title` uses).
- Produces: five StateFlows/setters (`anchorEnabled`, `anchorLetterFacts`, `anchorFrictionHold`, `anchorSunsetProposal`) rendered under the Measuring section.

- [ ] **Step 1: Add strings**

In `strings.xml`, alongside the prehome strings:

```xml
<string name="settings_anchor_title">AnchorCore</string>
<string name="settings_anchor_subtitle">A quiet weekly picture from your own patterns. Off until you ask.</string>
<string name="settings_anchor_letter_title">Letter knows the week</string>
<string name="settings_anchor_letter_subtitle">The daily letter sees this week\'s own-data notes.</string>
<string name="settings_anchor_friction_title">Gentler repetition in hard weeks</string>
<string name="settings_anchor_friction_subtitle">Pauses keep their breath longer during flagged weeks.</string>
<string name="settings_anchor_proposal_title">Wind-down suggestion</string>
<string name="settings_anchor_proposal_subtitle">One quiet suggestion after late-night weeks.</string>
```

- [ ] **Step 2: ViewModel surface**

In `SettingsViewModel`, following the `prehomeEnabled` pattern exactly:

```kotlin
// v-next (AnchorCore): the wellbeing loop's master switch + hooks.
// Default OFF everywhere (opt-out-by-silence); enabling the master
// flips hook defaults once (AnchorPrefs.setEnabled owns the latch).
val anchorEnabled = anchorPrefs.enabled
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
val anchorLetterFacts = anchorPrefs.letterFactsEnabled
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
val anchorFrictionHold = anchorPrefs.frictionHoldEnabled
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
val anchorSunsetProposal = anchorPrefs.sunsetProposalEnabled
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

fun setAnchorEnabled(v: Boolean) = viewModelScope.launch { anchorPrefs.setEnabled(v) }
fun setAnchorLetterFacts(v: Boolean) = viewModelScope.launch { anchorPrefs.setLetterFactsEnabled(v) }
fun setAnchorFrictionHold(v: Boolean) = viewModelScope.launch { anchorPrefs.setFrictionHoldEnabled(v) }
fun setAnchorSunsetProposal(v: Boolean) = viewModelScope.launch { anchorPrefs.setSunsetProposalEnabled(v) }
```

(`anchorPrefs` constructed beside `frictionPrefs` in the VM.)

- [ ] **Step 3: Screen rows**

In `SettingsScreen`, in the Measuring section (beside the PreHome row at ~line 1374), render: master toggle; then the three hook rows indented, each gated `if (anchor)` where `val anchor by viewModel.anchorEnabled.collectAsState()`. Reuse the exact toggle-row composable the prehome row uses (`title`/`subtitle`/`checked`/`onCheckedChange`).

- [ ] **Step 4: Regenerate the clinician pack + run gates**

Run: `python tools/clinician-pack.py; git diff --quiet -- docs/CLINICIAN_PACK.md`
If the diff is non-empty, commit it. Then run the CI-equivalent locally:

Run: `.\gradlew.bat testDebugUnitTest --tests "org.mindanchor.ci.*"`
Expected: PASS.

- [ ] **Step 5: Full suite + commit**

Run: `.\gradlew.bat testDebugUnitTest detekt`
Expected: all green (1238+ existing tests plus new ones).

```powershell
git add app/src/main/java/org/mindanchor/settings/SettingsViewModel.kt app/src/main/java/org/mindanchor/settings/SettingsScreen.kt app/src/main/res/values/strings.xml docs/CLINICIAN_PACK.md
git commit -m "feat(settings): AnchorCore master + per-hook toggles"
```

---

### Task 10: Wire LauncherRoot refresh + final verification

**Files:**
- Modify: `app/src/main/java/org/mindanchor/launcher/HomeScreen.kt` (LaunchedEffect triggering `viewModel.refreshAnchorState()`)

**Interfaces:**
- Consumes: everything above.
- Produces: recomputed-on-demand AnchorState — refreshed when Home composes and when PreHome opens; no timers.

- [ ] **Step 1: Trigger refresh on home composition**

In `LauncherRoot`'s Home branch, next to the existing `LaunchedEffect` block:

```kotlin
LaunchedEffect(Unit) { viewModel.refreshAnchorState() }
```

Implement in `LauncherViewModel`:

```kotlin
private val _anchorCoreState = MutableStateFlow<AnchorState>(AnchorState.WarmingUp(0))
private val anchorCoreSource by lazy { AnchorCoreSource(application) }

fun refreshAnchorState() = viewModelScope.launch {
    _anchorCoreState.value = runCatching { anchorCoreSource.state() }.getOrDefault(_anchorCoreState.value)
}
```

- [ ] **Step 2: Full verification pass**

Run: `.\gradlew.bat assembleDebug testDebugUnitTest detekt lintDebug`
Expected: all green. Confirm `NetworkCallsForbiddenTest` included in the pass.

- [ ] **Step 3: Manual smoke on emulator (optional but recommended)**

Install debug APK, enable Settings → Measuring → AnchorCore, confirm: no crash on cold start; PreHome shows handback when an open loop was written the previous evening; letter generation includes the facts block when a model/key is configured (check `LetterGenerationLog`).

- [ ] **Step 4: Commit + docs**

Update `docs/PHASE_4_STATUS.md` with an AnchorCore section listing landed commits and the deliberate non-goals (digest retiming, IME, mood inference).

```powershell
git add app/src/main/java/org/mindanchor/launcher/HomeScreen.kt app/src/main/java/org/mindanchor/launcher/LauncherViewModel.kt docs/PHASE_4_STATUS.md
git commit -m "feat(anchorcore): on-demand refresh wiring + status docs"
```
