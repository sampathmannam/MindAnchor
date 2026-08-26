# AnchorCore — Wellbeing Loop Implementation Plan (v2, verified)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **v2 (2026-08-26):** every referenced API in this plan has been verified against the
> code on this branch, every synthetic test number recomputed against the real math,
> and five defects in v1 fixed (contradictory hysteresis test, impossible cluster
> test data, zero-MAD vitals test data, a DataStore key type mismatch, and a
> midnight-wrap bug in the sleep fact). Design gaps closed: hysteresis is now
> actually wired, Hook B is wired at its two real call sites, SLEEP_IRREGULAR has a
> data source, and Hook A threads through the letter VM without a Context. Do not
> re-derive these decisions; §0 and §0.5 are the ground truth.

**Goal:** One on-device aggregator turns existing signals (sleep onsets, wellness vitals, sleep-regularity trend) into per-day facts and a trailing week picture; the daily letter, friction tone, a sunset proposal card, and PreHome's morning surface adapt around it.

**Architecture:** A new pure-Kotlin package `org.mindanchor.anchorcore` computes facts from data passed in (no new sensing, no timers). Four consumers subscribe: letter context (Hook A), friction tone hold (Hook B), sunset proposal card (Hook C), and the PreHome open-loop handback. Loop state (toggles, clean-streak, SRI snapshot, card suppression) persists in one DataStore.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose, DataStore Preferences, kotlinx.coroutines flows, JUnit4 + Robolectric 4.13 (already dependencies). No new dependencies.

**Spec:** `docs/superpowers/specs/2026-08-26-anchorcore-wellbeing-loop-design.md`

---

## §0 Handoff notes — read before Task 2

**Branch:** `feature/g28-whisper-vendor` on `sampathmannam/MindAnchor`. Work on this
branch; do not rebase or merge anything into it.

**Starting state:** Task 1 is already DONE and committed (`DayFact.kt` +
`DayFactTest.kt`, test verified green). Begin at Task 2. The full unit suite and
detekt were green at handoff (see the commit that carries this plan revision).

**Binding gates** (run from repo root; Windows syntax — on POSIX use `./gradlew`):

- Per task: `.\gradlew.bat testDebugUnitTest --tests "<pattern>"` for that task's suites.
- Before each commit: the task's tests green; before the final commit: `.\gradlew.bat testDebugUnitTest detekt lintDebug` all green.
- `assembleDebug` is NOT a binding gate for this plan: this branch vendors
  whisper.cpp (T-6.1) and its native build needs NDK `27.3.13750724` + CMake. If
  your environment lacks that toolchain, do not chase native errors — they are
  pre-existing T-6.1 territory, out of scope. Unit tests, detekt, and lint do not
  compile the native code.

**Do not modify:** `sleep/Deviation.kt`, `vitals/WellnessSignals.kt`,
`sleep/SleepRepository.kt`, `usage/RhythmRepository.kt`, `llm/LetterPrompt.kt`
(its SYSTEM_PROMPT and template are pinned by `LetterPromptShapeTest`), any
existing test. `friction/FrictionTone.kt` and `data/SunsetPrefs.kt` are modified
only exactly as written here.

**House rules** (from repo CLAUDE.md, enforced): match existing style — heavy KDoc
with citations where design-relevant, `runCatching` at IO boundaries, no new
abstractions, every changed line traces to this plan. New files whose strings
reach the person carry an `@wording-reviewed` KDoc tag (grep `@wording-reviewed`
in `app/src/main` for the convention; `friction/FrictionGate.kt:83` is the
model). All user-facing wording states facts and directions, never verdicts —
no "good", "bad", "should", no interpretation.

**TDD discipline:** each task writes its failing test first, watches it fail for
the stated reason, implements, watches it pass, commits with the given message.
If a referenced symbol does not match what you find in the file, STOP and re-read
§0.5 and the actual file — do not improvise a different API.

## §0.5 Verified API reference (checked against this branch, 2026-08-26)

| Symbol | Where | Shape that matters |
|---|---|---|
| `Deviation` | `sleep/Deviation.kt` | `MIN_NIGHTS = 5`, `LATE_BY_MINUTES = 90`, `minutesAfterSixPm(Int): Int`, `usual(List<Int>): Int?` (median), `laterThanUsual(List<Int>): Int` (count ≥ usual+90), `worthShowing(List<Int>): Boolean` (size ≥ 5 && later > 0). Median of 7 nights ⇒ at most 3 can count late. |
| `WellnessStats` | `vitals/WellnessSignals.kt` | `baseline(signal, values): PersonalBaseline`, `reading(signal, today, baseline): WellnessReading`. `PersonalBaseline.robustZ(v)` = `0.6745*(v-median)/mad`; returns **null when MAD == 0** and does **not** check `sampleCount` — the 14-day floor lives in `isReportable` (`sampleCount >= WellnessSignal.MIN_HISTORY_DAYS` = 14) and callers must check it themselves. |
| `WellnessSignal` | same file | `HRV, RESTING_HEART_RATE, STEPS, SLEEP_MINUTES, MINDFULNESS_MINUTES` |
| `WellnessRepository` | `vitals/WellnessRepository.kt:60` | `suspend fun readingsFor(day: LocalDate): List<WellnessReading>` |
| `WellnessHistoryStore` | `vitals/WellnessHistoryStore.kt:130` | `suspend fun all(): List<WellnessLedger.Entry>` — entries carry `.day: LocalDate`; cheap local read (no Health Connect). |
| `SleepRepository` | `sleep/SleepRepository.kt:32` | `fun estimate(): SleepSummary?` — `windows: List<SleepWindow>` (last ≤ 7, `.startMillis`), `regularityScore: Int?`. Null without usage access. Queries only ~8 days back; **last week's SRI does not exist anywhere** — hence the SriWeekLedger in Task 5. |
| Onset derivation | `settings/SettingsViewModel.kt:684-690` | THE pattern to copy: `Instant.ofEpochMilli(w.startMillis).atZone(zone).toLocalTime()` → `Deviation.minutesAfterSixPm(hour*60+minute)`. Sleep onsets come from sleep windows. `DayRhythm.firstUnlockMinute` is the **morning wake** proxy (first unlock after 03:00), never a bedtime. |
| `RhythmRepository` | `usage/RhythmRepository.kt:19` | `fun rhythms(days: List<LocalDate>): Map<LocalDate, DayRhythm>?` (null without grant); `DayRhythm(firstUnlockMinute: Int?, screenMinutes: Int?)` |
| `FrictionContext` | `friction/FrictionTone.kt:71` | `toneFor(recentOpens: Int, insideSleepWindow: Boolean)`; `REPEATS_BEFORE_BRIEF = 1`, `REPEATS_BEFORE_FEATHER = 3`; inside the sleep window the tone is **already always FULL**. Call sites: `friction/GateActivity.kt:62` and `launcher/FrictionViewModel.kt:119` (`adaptiveTone` — consults the FrictionBandit whenever the deterministic tone is FULL). Ladder pinned by `test/.../friction/FrictionToneTest.kt`. |
| `OpenLoop` / `LoopPhase` | `friction/OpenLoop.kt:108` | `phase(quietHours, note, notedDay, today, postponedAt = null, now = Instant.now()): LoopPhase`; phases `CAPTURE, POSTPONED, RETURN, NONE` |
| `FrictionPrefs` | `data/FrictionPrefs.kt` | `openLoopNote: Flow<String?>` (:295), `openLoopDay: Flow<String?>` (:297), `openLoopPostponedAt: Flow<Instant?>` (:304), `suspend fun clearOpenLoop()` (:339), `prehomeEnabled: Flow<Boolean>` |
| `SunsetPrefs` | `data/SunsetPrefs.kt` | `suspend fun window(): Pair<LocalTime, LocalTime>` (:176), `startTime`/`endTime: Flow<LocalTime>`, `suspend fun isQuietHour(now = LocalTime.now())` (:182 — calls `window()`, so a window override propagates to quiet hours automatically), `timeOf(Int?, LocalTime)` (:216), `DEFAULT_START/END`. **`setWindow` flips `sunset_window_customized` — the temporary override must NOT touch that flag or the base keys.** |
| `LetterContext` | `llm/LetterContext.kt:39` | `build(today, notes, checkIns, now = Instant.now(), zone = systemDefault()): LlmRequest` |
| `LetterPrompt` | `llm/LetterPrompt.kt:95` | `userPrompt(today, dayOfWeek, timeOfDay, quickNoteSection, todayJournalSection, recentNotesSection, checkInSection): String` — a raw string + `trimIndent()`. **Do not add a multi-line param to the template: interpolated lines at column 0 change what trimIndent strips and re-indent the whole prompt.** Hook A splices AFTER trimIndent, in `LetterContext.build` (Task 6). File stays untouched. |
| `LetterViewModel` | `letters/LetterViewModel.kt:49` | Plain `ViewModel` with 5 injected collaborators, **no Context**. `runGeneration` builds the request at :172. Constructed exactly once, at `launcher/LauncherViewModel.kt:595`. |
| `LauncherViewModel` | `launcher/LauncherViewModel.kt:64` | `AndroidViewModel(application)`; fields `sunsetPrefs`, `frictionPrefs`; `weeklyPatterns` StateFlow pattern at :675; `openLoop` combine at :220 |
| `HomeScreen.kt` | `launcher/HomeScreen.kt` | `LauncherRoot` :138, private `HomeSurface` :949 (already `@Suppress("LongParameterList")`), `NOfOnePatternsCard` render site :1561. Home cards keep copy as Kotlin literals (`PhaseFourCards.kt` precedent) + `@wording-reviewed` tag. |
| `PreHomeActivity` | `prehome/PreHomeActivity.kt:92` | Self-skips via `FrictionPrefs.prehomeEnabled`; private `PreHomeSurface(intentions, doomscrollList, onSkipToHome)` :146; `LocalContext.current` available inside. |
| Settings | `settings/SettingsScreen.kt:473` | `enum SettingsGroup { QUIET, PAUSES, MEASURING, READING, PLAN, PHONE }`. AnchorCore rows go inside an `if (group == SettingsGroup.MEASURING)` block. (The PreHome row at :1374 is in PAUSES — v1 of this plan pointed there wrongly.) Toggle composable: `SettingsRowSwitch(title, subtitle, checked, onCheckedChange)`. VM pattern: `prehomeEnabled` at `SettingsViewModel.kt:403`. |
| Gates | — | `NetworkCallsForbiddenTest` lives in `test/.../goinglight/`. There is **no** `ClinicalReviewWordlistTest`; the wording gate is the `clinical-review.yml` CI workflow (strings.xml + `@wording-reviewed` tag discipline), structure-pinned by `test/.../ci/ClinicalReviewGateTest.kt`. `tools/clinician-pack.py` exists. Robolectric harness to mirror: `test/.../backup/BackupPrefsRoundTripFindingTest.kt`. |

## §0.7 Escalation protocol — hand hard problems to Fable 5, hands-free

The implementing agent (MiniMax M3 or any other) is expected to complete most
tasks alone. When it cannot, it must NOT improvise. It escalates to Claude Code
(Fable 5), installed on this machine, via the headless CLI. The repo's
`.claude/settings.json` pre-approves the tools the escalated agent needs, so the
whole loop runs without a human.

**Escalate when — and only when — one of these holds:**

- **E1.** A symbol, signature, or line reference from §0.5 does not match what is
  actually in the file. (Never adapt the plan yourself.)
- **E2.** A task's test is still red after 2 honest fix attempts.
- **E3.** `detekt` or `lintDebug` still reports new issues after 2 fix attempts.
- **E4.** The fix would require touching anything on the §0 "Do not modify"
  list. (Never touch it yourself, not even trivially.)
- **E5.** An integration edit in Tasks 6–10 requires a judgment call the plan's
  snippet does not settle (naming, placement, Compose wiring) and you are not
  confident the result matches the file's existing style.

**How to escalate (exact mechanics):**

1. Write `ESCALATION_REQUEST.md` at the repo root, containing: the task and step
   number; the trigger (E1–E5); the exact command that fails and the last ~50
   lines of its output; the files you touched; what you already tried. Keep it
   factual — no summaries of the plan (Fable reads the plan itself).
2. Run, from the repo root (PowerShell or cmd):

   ```
   claude -p "You are the escalation engineer for the AnchorCore plan. Read ESCALATION_REQUEST.md at the repo root, then docs/superpowers/plans/2026-08-26-anchorcore-wellbeing-loop.md sections 0, 0.5 and the task named in the request. Resolve the request: make the named gate green while honoring the plan's constraints and the repo style. Run the task's test command yourself to prove it. If you complete the task, commit with the plan's commit message; if you only unblocked it, commit nothing and say precisely what remains. Do not push. Finish by writing ESCALATION_RESULT.md at the repo root: STATUS (RESOLVED or NOT-RESOLVED), what you changed, what the implementing agent should do next." --model claude-fable-5 --permission-mode acceptEdits
   ```

   Wait for the process to exit (it can take several minutes — it runs Gradle).
3. Read `ESCALATION_RESULT.md`, delete both escalation files, and re-run the
   task's gate yourself. Green → continue the plan. Red → escalate once more
   with the updated failure.
4. **The brake:** if the same gate is still red after 2 escalations, STOP
   completely. Write `BLOCKED.md` at the repo root (task, step, both escalation
   results, current `git status`), commit nothing further, and end the run.
   A human resumes from BLOCKED.md. Never push a red tree, never skip a task,
   never disable a test to get past it.

**Division of labor:** the implementing agent owns pushes and the task sequence;
escalated Fable owns hard fixes and may commit a completed task, but never
pushes (`git push` is deny-listed in `.claude/settings.json`). Push after each
green task or at natural milestones.

## Global Constraints

- Zero new permissions; zero network calls (`NetworkCallsForbiddenTest` stays green).
- Facts, never labels: direction-and-count wording only, everywhere.
- No new math: robust-z via `WellnessStats`/`PersonalBaseline`; medians via `Deviation.usual`; nothing else.
- Master toggle default OFF (opt-out-by-silence, `prehomeEnabled` precedent). Hooks inert until it is on; each hook individually toggleable.
- Cold start honest: fewer than 7 observed days in the trailing 14 → `WarmingUp`; hooks do nothing.
- Recompute on demand only — PreHome render, letter generation, Home composition. Never a timer.

## File Structure

```
app/src/main/java/org/mindanchor/anchorcore/
    DayFact.kt            — DONE (Task 1): FactKind + DayFact + DayFactRenderer
    AnchorState.kt        — sealed state + WeekPicture hysteresis reducer (pure)
    AnchorCore.kt         — pure fact computation (cluster, vitals, SRI drop, observed days)
    SriWeekLedger.kt      — pure weekly SRI snapshot roll (feeds SLEEP_IRREGULAR)
    AnchorPrefs.kt        — DataStore: toggles, clean streak, week flag, SRI slots, card suppression
    AnchorCoreSource.kt   — Context-facing: pulls sources, rolls ledgers, emits AnchorState
    LetterFactsSection.kt — Hook A composer (pure)
    SunsetProposal.kt     — Hook C decision (pure)
app/src/main/java/org/mindanchor/friction/FrictionTone.kt      — Hook B ladder (modify)
app/src/main/java/org/mindanchor/friction/GateActivity.kt      — Hook B wiring (modify, Task 10)
app/src/main/java/org/mindanchor/launcher/FrictionViewModel.kt — Hook B wiring + bandit bypass (modify, Task 10)
app/src/main/java/org/mindanchor/llm/LetterContext.kt          — Hook A splice (modify)
app/src/main/java/org/mindanchor/letters/LetterViewModel.kt    — Hook A provider param (modify)
app/src/main/java/org/mindanchor/data/SunsetPrefs.kt           — temporary window override (modify)
app/src/main/java/org/mindanchor/launcher/LauncherViewModel.kt — anchor state + proposal card (modify)
app/src/main/java/org/mindanchor/launcher/HomeScreen.kt        — SunsetProposalCard + refresh (modify)
app/src/main/java/org/mindanchor/prehome/MorningHandback.kt    — handback + sleep fact (new)
app/src/main/java/org/mindanchor/prehome/PreHomeActivity.kt    — morning blocks (modify)
app/src/main/java/org/mindanchor/settings/SettingsViewModel.kt — toggles (modify)
app/src/main/java/org/mindanchor/settings/SettingsScreen.kt    — Measuring rows (modify)
app/src/main/res/values/strings.xml                            — settings strings (modify)
app/src/test/java/org/mindanchor/anchorcore/
    DayFactTest.kt (DONE), AnchorStateTest.kt, AnchorCoreTest.kt, SriWeekLedgerTest.kt,
    FrictionToneHoldTest.kt, AnchorPrefsTest.kt, LetterContextFactsTest.kt,
    SunsetProposalTest.kt, AnchorWordingTest.kt
app/src/test/java/org/mindanchor/prehome/PreHomeHandbackTest.kt
docs/CLINICIAN_PACK.md (regenerated), docs/PHASE_4_STATUS.md (updated)
```

---

### Task 1: DayFact — fact kinds and plain-language renderers — **DONE**

- [x] Implemented and committed on this branch (`DayFact.kt`, `DayFactTest.kt`,
  4 tests green). `DayFactRenderer` carries the `@wording-reviewed` tag. Nothing
  to do; do not edit these files except where a later task says so.

---

### Task 2: AnchorState — warm-up gate + week-flag hysteresis

**Files:**
- Create: `app/src/main/java/org/mindanchor/anchorcore/AnchorState.kt`
- Create: `app/src/test/java/org/mindanchor/anchorcore/AnchorStateTest.kt`

**Interfaces produced:**
- `sealed interface AnchorState { WarmingUp(daysObserved: Int); Steady(facts, weekFlagged, computedAtEpochMillis) }` with `AnchorState.of(daysObserved, facts, weekFlagged = facts.isNotEmpty(), now)` — the default keeps pure tests simple; `AnchorCoreSource` (Task 5) passes the real hysteresis flag explicitly.
- `object WeekPicture { CLEAN_DAYS_TO_UNFLAG = 7; reduce(flaggedToday, cleanStreak): Int; isFlagged(flaggedToday, cleanStreak): Boolean }` — callers persist the streak. **Convention: a never-flagged user stores streak = 7 (the AnchorPrefs default, Task 5), so `isFlagged(false, 7) == false` from day one; the streak only drops to 0 when a fact actually fires.**

- [ ] **Step 1: Write the failing test**

```kotlin
package org.mindanchor.anchorcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AnchorStateTest {

    @Test
    fun `fewer than seven observed days warms up`() {
        assertEquals(
            AnchorState.WarmingUp(6),
            AnchorState.of(daysObserved = 6, facts = emptyList(), now = 0L),
        )
    }

    @Test
    fun `seven days with no facts is steady and unflagged`() {
        val s = AnchorState.of(daysObserved = 7, facts = emptyList(), now = 5L)
        assertTrue(s is AnchorState.Steady)
        assertEquals(false, (s as AnchorState.Steady).weekFlagged)
    }

    @Test
    fun `a fact today flags the week by default`() {
        val fact = DayFact(FactKind.LATE_NIGHT_CLUSTER, "3|300", LocalDate.of(2026, 8, 26))
        val s = AnchorState.of(daysObserved = 10, facts = listOf(fact), now = 5L)
        assertEquals(true, (s as AnchorState.Steady).weekFlagged)
    }

    @Test
    fun `an explicit hysteresis flag overrides the default`() {
        // Yesterday's fact keeps the week flagged even on a clean today.
        val s = AnchorState.of(daysObserved = 10, facts = emptyList(), weekFlagged = true, now = 5L)
        assertEquals(true, (s as AnchorState.Steady).weekFlagged)
    }

    @Test
    fun `clean streak resets on a flagged day`() {
        assertEquals(0, WeekPicture.reduce(flaggedToday = true, cleanStreak = 4))
    }

    @Test
    fun `clean streak grows on a clean day and caps at seven`() {
        assertEquals(3, WeekPicture.reduce(flaggedToday = false, cleanStreak = 2))
        assertEquals(7, WeekPicture.reduce(flaggedToday = false, cleanStreak = 7))
    }

    @Test
    fun `seven clean days unflag`() {
        var streak = 0
        repeat(7) { streak = WeekPicture.reduce(flaggedToday = false, cleanStreak = streak) }
        assertEquals(WeekPicture.CLEAN_DAYS_TO_UNFLAG, streak)
        assertEquals(false, WeekPicture.isFlagged(flaggedToday = false, cleanStreak = streak))
    }

    @Test
    fun `flagged while a fact fired today or the streak is short`() {
        assertEquals(true, WeekPicture.isFlagged(flaggedToday = true, cleanStreak = 7))
        assertEquals(true, WeekPicture.isFlagged(flaggedToday = false, cleanStreak = 6))
        assertEquals(false, WeekPicture.isFlagged(flaggedToday = false, cleanStreak = 7))
    }
}
```

- [ ] **Step 2: Run it — expect FAIL, unresolved reference `AnchorState`**

Run: `.\gradlew.bat testDebugUnitTest --tests "org.mindanchor.anchorcore.AnchorStateTest"`

- [ ] **Step 3: Implement**

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
        /** Convenience for the one hook that only cares about late nights. */
        val lateNightCluster: DayFact?
            get() = facts.firstOrNull { it.kind == FactKind.LATE_NIGHT_CLUSTER }
    }

    companion object {
        /**
         * Below this there is no baseline to read anything against — the
         * spec's cold-start rule: the app says nothing until it knows
         * something. Counted over the trailing 14 days (AnchorCoreSource).
         */
        const val MIN_OBSERVED_DAYS = 7

        fun of(
            daysObserved: Int,
            facts: List<DayFact>,
            weekFlagged: Boolean = facts.isNotEmpty(),
            now: Long,
        ): AnchorState =
            if (daysObserved < MIN_OBSERVED_DAYS) {
                WarmingUp(daysObserved)
            } else {
                Steady(facts = facts, weekFlagged = weekFlagged, computedAtEpochMillis = now)
            }
    }
}

/**
 * The flagged-week hysteresis: any fact keeps the week flagged; seven
 * consecutive clean days unflag it. Stored as one int streak
 * (AnchorPrefs, Task 5), whose *default is 7*, so a person whose loop
 * has never flagged anything starts unflagged rather than serving a
 * seven-day sentence for data they never produced.
 */
object WeekPicture {
    const val CLEAN_DAYS_TO_UNFLAG = 7

    /** New streak length after today. A flag resets it to zero. */
    fun reduce(flaggedToday: Boolean, cleanStreak: Int): Int =
        if (flaggedToday) 0 else (cleanStreak + 1).coerceAtMost(CLEAN_DAYS_TO_UNFLAG)

    /** Flagged while a fact fired today, or before the streak completes. */
    fun isFlagged(flaggedToday: Boolean, cleanStreak: Int): Boolean =
        flaggedToday || cleanStreak < CLEAN_DAYS_TO_UNFLAG
}
```

- [ ] **Step 4: Run it — expect PASS (8 tests)**

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/org/mindanchor/anchorcore/AnchorState.kt app/src/test/java/org/mindanchor/anchorcore/AnchorStateTest.kt
git commit -m "feat(anchorcore): AnchorState warm-up gate + week-flag hysteresis"
```

---

### Task 3: AnchorCore — pure fact computation

**Files:**
- Create: `app/src/main/java/org/mindanchor/anchorcore/AnchorCore.kt`
- Create: `app/src/test/java/org/mindanchor/anchorcore/AnchorCoreTest.kt`

**Consumes (verified, §0.5):** `Deviation.worthShowing/laterThanUsual/usual`,
`WellnessStats.baseline/reading`, `WellnessReading.zScore`,
`PersonalBaseline.isReportable`, `WellnessSignal.{STEPS, HRV, RESTING_HEART_RATE}`.

**Produces:** `object AnchorCore { FLAG_Z = 2.0; SRI_DROP_POINTS = 15; observedDays(unlockMinutesByDay, vitalDays): Int; lateNightCluster(onsets, today): DayFact?; vitalFacts(readings, today): List<DayFact>; sleepIrregular(thisWeekSri, lastWeekSri, today): DayFact? }`

Facts about the math you must not fight (from the real implementations):
- `Deviation.usual` is a median: of 7 nights, **at most 3** can be ≥ 90 min past it. Test data reflecting 4-of-7 late nights cannot fire and is wrong by construction.
- `robustZ` returns null when the history's MAD is 0 (perfectly repeated values). Synthetic histories must vary.
- `robustZ` does NOT apply the 14-day floor; `vitalFacts` must check `baseline.isReportable` itself.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.mindanchor.anchorcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.vitals.WellnessSignal
import org.mindanchor.vitals.WellnessStats
import java.time.LocalDate

class AnchorCoreTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 26)

    // Median + MAD = 100 ± 10 over 14 days: reportable, MAD non-zero.
    private fun baselineFor(signal: WellnessSignal) =
        WellnessStats.baseline(signal, List(7) { 90.0 } + List(7) { 110.0 })

    @Test
    fun `observed days counts union of rhythm days and vital-only days`() {
        val rhythm = mapOf(
            today.minusDays(1) to 1380,
            today.minusDays(2) to null,
        )
        val vitals = setOf(today.minusDays(2))
        assertEquals(2, AnchorCore.observedDays(rhythm, vitals))
    }

    @Test
    fun `cluster fires when three of seven onsets run ninety past usual`() {
        // Onsets as minutes-after-18:00. Usual (median of 7) = 300 (23:00).
        // Three nights at 480 (02:00) are >= 390, so laterThanUsual == 3 —
        // the maximum a 7-night median allows.
        val onsets = listOf(300, 300, 300, 300, 480, 480, 480)
        val fact = AnchorCore.lateNightCluster(onsets, today)
        assertNotNull(fact)
        assertEquals("3|300", fact!!.detail)
    }

    @Test
    fun `cluster stays silent under five nights`() {
        assertNull(AnchorCore.lateNightCluster(listOf(480, 480, 480, 480), today))
    }

    @Test
    fun `cluster stays silent when no night ran late`() {
        assertNull(AnchorCore.lateNightCluster(List(7) { 300 }, today))
    }

    @Test
    fun `sleep irregular fires on an eighteen point drop`() {
        val fact = AnchorCore.sleepIrregular(thisWeekSri = 60, lastWeekSri = 78, today = today)
        assertNotNull(fact)
        assertEquals("18", fact!!.detail)
    }

    @Test
    fun `sleep irregular silent on a rise, a small drop, or missing weeks`() {
        assertNull(AnchorCore.sleepIrregular(80, 70, today))
        assertNull(AnchorCore.sleepIrregular(64, 70, today))
        assertNull(AnchorCore.sleepIrregular(null, 70, today))
        assertNull(AnchorCore.sleepIrregular(60, null, today))
    }

    @Test
    fun `steps far below baseline fire MOVEMENT_LOW`() {
        // z = 0.6745 * (20 - 100) / 10 = -5.4
        val reading = WellnessStats.reading(
            WellnessSignal.STEPS,
            today = 20.0,
            baseline = baselineFor(WellnessSignal.STEPS),
        )
        val facts = AnchorCore.vitalFacts(listOf(reading), today)
        assertEquals(1, facts.size)
        assertEquals(FactKind.MOVEMENT_LOW, facts[0].kind)
    }

    @Test
    fun `resting heart rate far above baseline fires RHR_HIGH`() {
        // z = 0.6745 * (150 - 100) / 10 = +3.4
        val reading = WellnessStats.reading(
            WellnessSignal.RESTING_HEART_RATE,
            today = 150.0,
            baseline = baselineFor(WellnessSignal.RESTING_HEART_RATE),
        )
        val facts = AnchorCore.vitalFacts(listOf(reading), today)
        assertEquals(1, facts.size)
        assertEquals(FactKind.RHR_HIGH, facts[0].kind)
    }

    @Test
    fun `vital facts stay silent inside the bands`() {
        // z = 0.6745 * (101 - 100) / 10 = +0.07
        val reading = WellnessStats.reading(
            WellnessSignal.HRV,
            today = 101.0,
            baseline = baselineFor(WellnessSignal.HRV),
        )
        assertTrue(AnchorCore.vitalFacts(listOf(reading), today).isEmpty())
    }

    @Test
    fun `vital facts respect the fourteen-day baseline floor`() {
        // Only 10 days of history: robustZ is computable (-5.4) but the
        // baseline is not reportable, so no fact may fire from it.
        val thin = WellnessStats.baseline(WellnessSignal.STEPS, List(5) { 90.0 } + List(5) { 110.0 })
        val reading = WellnessStats.reading(WellnessSignal.STEPS, today = 20.0, baseline = thin)
        assertTrue(AnchorCore.vitalFacts(listOf(reading), today).isEmpty())
    }
}
```

- [ ] **Step 2: Run it — expect FAIL, unresolved reference `AnchorCore`**

Run: `.\gradlew.bat testDebugUnitTest --tests "org.mindanchor.anchorcore.AnchorCoreTest"`

- [ ] **Step 3: Implement**

```kotlin
package org.mindanchor.anchorcore

import java.time.LocalDate
import java.util.Locale
import org.mindanchor.sleep.Deviation
import org.mindanchor.vitals.WellnessReading
import org.mindanchor.vitals.WellnessSignal

/**
 * The aggregator's arithmetic. Pure functions over data already collected
 * elsewhere; the Context-carrying wrapper is AnchorCoreSource (Task 5) so
 * this stays JVM-testable — the SleepMath/SleepRepository split.
 */
object AnchorCore {

    /**
     * |z| >= 2.0 flags a vital. Jacobson 2019 (J Nerv Ment Dis 207:893-6)
     * uses 2.0-2.5 per-person anomaly cut-offs; 2.0 matches the launcher's
     * MUCH_ABOVE band edge (WellnessDirection), documented for traceability.
     */
    const val FLAG_Z = 2.0

    /** An SRI drop of this many points vs the prior week counts. Design choice. */
    const val SRI_DROP_POINTS = 15

    /**
     * A day is observed when it has a screen-rhythm value (non-null map
     * entry) or any vital-ledger entry. Absent days are absent, never
     * zero-filled. The union: rhythm-observed days, plus vital-only days.
     */
    fun observedDays(
        unlockMinutesByDay: Map<LocalDate, Int?>,
        vitalDays: Set<LocalDate>,
    ): Int =
        unlockMinutesByDay.values.count { it != null } +
            vitalDays.count { unlockMinutesByDay[it] == null }

    /**
     * LATE_NIGHT_CLUSTER when Deviation has enough nights and at least one
     * ran >= 90 min past the person's own median onset. Onsets arrive in
     * the minutes-after-18:00 frame (Deviation.minutesAfterSixPm) so a
     * midnight-crossing bedtime reads as later, never as earlier.
     * Detail payload: "nights|medianOnsetAfterSixPm".
     */
    fun lateNightCluster(onsets: List<Int>, today: LocalDate): DayFact? {
        if (!Deviation.worthShowing(onsets)) return null
        val n = Deviation.laterThanUsual(onsets)
        val usual = Deviation.usual(onsets) ?: return null
        return DayFact(FactKind.LATE_NIGHT_CLUSTER, "$n|$usual", today)
    }

    /** Detail payload: "dropPoints". Silent unless the score actually fell. */
    fun sleepIrregular(thisWeekSri: Int?, lastWeekSri: Int?, today: LocalDate): DayFact? {
        if (thisWeekSri == null || lastWeekSri == null) return null
        val drop = lastWeekSri - thisWeekSri
        if (drop < SRI_DROP_POINTS) return null
        return DayFact(FactKind.SLEEP_IRREGULAR, "$drop", today)
    }

    /**
     * Vital facts for the directional signals: steps and HRV low, resting
     * heart rate high. The baseline must be reportable (the 14-day floor)
     * — robustZ alone does not enforce it, so the check is here.
     */
    fun vitalFacts(readings: List<WellnessReading>, today: LocalDate): List<DayFact> =
        readings.mapNotNull { r ->
            if (!r.baseline.isReportable) return@mapNotNull null
            val z = r.zScore ?: return@mapNotNull null
            val kind = when (r.signal) {
                WellnessSignal.STEPS -> if (z <= -FLAG_Z) FactKind.MOVEMENT_LOW else null
                WellnessSignal.HRV -> if (z <= -FLAG_Z) FactKind.HRV_LOW else null
                WellnessSignal.RESTING_HEART_RATE -> if (z >= FLAG_Z) FactKind.RHR_HIGH else null
                else -> null
            } ?: return@mapNotNull null
            DayFact(kind, String.format(Locale.ROOT, "%.1f", z), today)
        }
}
```

- [ ] **Step 4: Run it — expect PASS (10 tests)**

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/org/mindanchor/anchorcore/AnchorCore.kt app/src/test/java/org/mindanchor/anchorcore/AnchorCoreTest.kt
git commit -m "feat(anchorcore): fact computation from onsets + vitals + SRI trend"
```

---

### Task 4: Hook B — the tone ladder learns about flagged weeks (pure part)

**Files:**
- Modify: `app/src/main/java/org/mindanchor/friction/FrictionTone.kt`
- Create: `app/src/test/java/org/mindanchor/anchorcore/FrictionToneHoldTest.kt`

**Reality check (spec correction):** the existing ladder is BRIEF at 1, FEATHER at
3 (not the 2/4 the spec draft said), and inside the sleep window the tone is
already always FULL. So the hold is: flagged weeks shift 1/3 → 2/5 outside the
sleep window; the sleep window keeps winning unchanged. The spec has been
corrected to match. Call-site wiring (where `weekFlagged` actually comes from)
is Task 10 — this task only widens the pure function, with a default that keeps
both existing call sites and `FrictionToneTest` compiling and passing untouched.

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
    fun `flagged week holds full one reach longer outside the sleep window`() {
        assertEquals(FrictionTone.FULL, FrictionContext.toneFor(1, false, weekFlagged = true))
        assertEquals(FrictionTone.BRIEF, FrictionContext.toneFor(2, false, weekFlagged = true))
        assertEquals(FrictionTone.BRIEF, FrictionContext.toneFor(4, false, weekFlagged = true))
        assertEquals(FrictionTone.FEATHER, FrictionContext.toneFor(5, false, weekFlagged = true))
    }

    @Test
    fun `inside the sleep window full wins regardless`() {
        for (opens in 0..9) {
            assertEquals(FrictionTone.FULL, FrictionContext.toneFor(opens, true, weekFlagged = true))
        }
    }
}
```

- [ ] **Step 2: Run it — expect FAIL, no parameter `weekFlagged`**

Run: `.\gradlew.bat testDebugUnitTest --tests "org.mindanchor.anchorcore.FrictionToneHoldTest"`

- [ ] **Step 3: Implement — widen `toneFor`, add two constants, change nothing else in the file**

```kotlin
    // v-next (AnchorCore Hook B): on a flagged week — AnchorState said
    // something deviated this trailing week — the soften ladder backs off
    // one step. Repetition inside a hard week is more likely the loop
    // talking than weak resolve, so the ceremony earns a longer chance
    // before it demotes itself. The sleep window still wins over
    // everything, exactly as before.
    const val FLAGGED_REPEATS_BEFORE_BRIEF = 2
    const val FLAGGED_REPEATS_BEFORE_FEATHER = 5

    fun toneFor(
        recentOpens: Int,
        insideSleepWindow: Boolean,
        weekFlagged: Boolean = false,
    ): FrictionTone = when {
        insideSleepWindow -> FrictionTone.FULL
        recentOpens >= (if (weekFlagged) FLAGGED_REPEATS_BEFORE_FEATHER else REPEATS_BEFORE_FEATHER) ->
            FrictionTone.FEATHER
        recentOpens >= (if (weekFlagged) FLAGGED_REPEATS_BEFORE_BRIEF else REPEATS_BEFORE_BRIEF) ->
            FrictionTone.BRIEF
        else -> FrictionTone.FULL
    }
```

Leave `GateActivity.kt:62` and `FrictionViewModel.kt:119` untouched in this task
(the default covers them; Task 10 wires them).

- [ ] **Step 4: Run both suites — the old ladder must stay pinned**

Run: `.\gradlew.bat testDebugUnitTest --tests "org.mindanchor.friction.FrictionToneTest" --tests "org.mindanchor.anchorcore.FrictionToneHoldTest"`

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/org/mindanchor/friction/FrictionTone.kt app/src/test/java/org/mindanchor/anchorcore/FrictionToneHoldTest.kt
git commit -m "feat(friction): tone ladder holds FULL longer on flagged weeks (Hook B, pure)"
```

---

### Task 5: SriWeekLedger + AnchorPrefs + AnchorCoreSource — persistence and the Context wrapper

**Files:**
- Create: `app/src/main/java/org/mindanchor/anchorcore/SriWeekLedger.kt`
- Create: `app/src/main/java/org/mindanchor/anchorcore/AnchorPrefs.kt`
- Create: `app/src/main/java/org/mindanchor/anchorcore/AnchorCoreSource.kt`
- Create: `app/src/test/java/org/mindanchor/anchorcore/SriWeekLedgerTest.kt`
- Create: `app/src/test/java/org/mindanchor/anchorcore/AnchorPrefsTest.kt`

**Why SriWeekLedger exists:** `SleepRepository.estimate()` only sees ~8 days of
UsageStats, so "last week's SRI" exists nowhere. The ledger keeps two dated
snapshots of the trailing-7 regularity score and rolls them weekly; the prior
slot, when 7–13 days old, is the honest "last week" figure. Missing/stale slot →
null → SLEEP_IRREGULAR stays silent. No repository is modified.

- [ ] **Step 1: Write the failing ledger test**

```kotlin
package org.mindanchor.anchorcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SriWeekLedgerTest {

    private val d0: LocalDate = LocalDate.of(2026, 8, 1)

    @Test
    fun `first run seeds the current slot and reports no last week`() {
        val r = SriWeekLedger.roll(prev = null, cur = null, today = d0, liveScore = 70)
        assertEquals(SriWeekLedger.Slot(d0, 70), r.cur)
        assertNull(r.prev)
        assertNull(r.lastWeekSri)
    }

    @Test
    fun `inside the week the anchor date holds and the score refreshes`() {
        val r = SriWeekLedger.roll(null, SriWeekLedger.Slot(d0, 70), d0.plusDays(3), liveScore = 66)
        assertEquals(SriWeekLedger.Slot(d0, 66), r.cur)
        assertNull(r.lastWeekSri)
    }

    @Test
    fun `after seven days the slot rolls and last week appears`() {
        val r = SriWeekLedger.roll(null, SriWeekLedger.Slot(d0, 68), d0.plusDays(7), liveScore = 60)
        assertEquals(SriWeekLedger.Slot(d0, 68), r.prev)
        assertEquals(SriWeekLedger.Slot(d0.plusDays(7), 60), r.cur)
        assertEquals(68, r.lastWeekSri)
    }

    @Test
    fun `a prev slot older than thirteen days is stale not last week`() {
        val r = SriWeekLedger.roll(
            SriWeekLedger.Slot(d0, 68),
            SriWeekLedger.Slot(d0.plusDays(30), 60),
            d0.plusDays(30),
            liveScore = 60,
        )
        assertNull(r.lastWeekSri)
    }

    @Test
    fun `a null live score changes nothing`() {
        val cur = SriWeekLedger.Slot(d0, 70)
        val r = SriWeekLedger.roll(null, cur, d0.plusDays(9), liveScore = null)
        assertEquals(cur, r.cur)
        assertNull(r.prev)
    }
}
```

- [ ] **Step 2: Run it — expect FAIL, unresolved reference `SriWeekLedger`. Implement:**

```kotlin
package org.mindanchor.anchorcore

import java.time.LocalDate

/**
 * Two dated snapshots of the trailing-7-night regularity score, rolled
 * weekly, so "vs the prior week" has a real number to point at. The
 * sources only hold ~8 days of screen events; without this ledger the
 * SLEEP_IRREGULAR fact would be a function nothing could ever call.
 * Missing or stale data yields null, and null yields silence.
 */
object SriWeekLedger {

    data class Slot(val day: LocalDate, val score: Int)

    data class Roll(val prev: Slot?, val cur: Slot?, val lastWeekSri: Int?)

    /** A current slot this old hands its score to prev and re-anchors. */
    const val ROLL_AFTER_DAYS = 7L

    /** A prev slot older than this is history, not "last week". */
    const val STALE_AFTER_DAYS = 13L

    fun roll(prev: Slot?, cur: Slot?, today: LocalDate, liveScore: Int?): Roll {
        var p = prev
        var c = cur
        if (liveScore != null) {
            c = when {
                c == null -> Slot(today, liveScore)
                !today.isBefore(c.day.plusDays(ROLL_AFTER_DAYS)) -> {
                    p = c
                    Slot(today, liveScore)
                }
                // Same anchor: keep the date, carry the newest score, so
                // prev ends up holding "the score as of the last roll".
                else -> Slot(c.day, liveScore)
            }
        }
        val last = p?.takeIf { !today.isAfter(it.day.plusDays(STALE_AFTER_DAYS)) }?.score
        return Roll(p, c, last)
    }
}
```

Run: `.\gradlew.bat testDebugUnitTest --tests "org.mindanchor.anchorcore.SriWeekLedgerTest"` — PASS (5 tests).

- [ ] **Step 3: Write the failing prefs test** — Robolectric IS available (4.13);
mirror the harness of `test/.../backup/BackupPrefsRoundTripFindingTest.kt`
exactly (same `@RunWith(RobolectricTestRunner::class)`, same `ApplicationProvider`
usage, same `@Config` annotation if that file carries one — open it and copy its
annotations verbatim).

```kotlin
package org.mindanchor.anchorcore

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class AnchorPrefsTest {

    private val prefs
        get() = AnchorPrefs(ApplicationProvider.getApplicationContext())

    @Test
    fun `master defaults off and streak defaults to unflagged`() = runBlocking {
        assertFalse(prefs.isEnabled())
        assertEquals(WeekPicture.CLEAN_DAYS_TO_UNFLAG, prefs.cleanStreak())
        assertFalse(prefs.weekFlagged())
    }

    @Test
    fun `first enable flips hook defaults on exactly once`() = runBlocking {
        assertFalse(prefs.letterFactsEnabled.first())
        prefs.setEnabled(true)
        assertTrue(prefs.isEnabled())
        assertTrue(prefs.letterFactsEnabled.first())
        assertTrue(prefs.frictionHoldEnabled.first())
        assertTrue(prefs.sunsetProposalEnabled.first())
        // A hook switched off stays off across master off->on.
        prefs.setLetterFactsEnabled(false)
        prefs.setEnabled(false)
        prefs.setEnabled(true)
        assertFalse(prefs.letterFactsEnabled.first())
    }

    @Test
    fun `dismissing the proposal suppresses it for fourteen days`() = runBlocking {
        assertNull(prefs.proposalSuppressedUntil())
        val now = Instant.parse("2026-08-26T10:00:00Z")
        prefs.recordProposalDismissed(now)
        val until = prefs.proposalSuppressedUntil()
        assertNotNull(until)
        assertEquals(now.plusSeconds(14L * 24 * 3600), until)
    }

    @Test
    fun `clean streak clamps to its band`() = runBlocking {
        prefs.setCleanStreak(99)
        assertEquals(WeekPicture.CLEAN_DAYS_TO_UNFLAG, prefs.cleanStreak())
        prefs.setCleanStreak(-3)
        assertEquals(0, prefs.cleanStreak())
    }
}
```

Note on `proposalSuppressedUntil`: to keep the test deterministic, the expiry
check compares against a passed-in `now` (defaulted), not `System.currentTimeMillis()`.

- [ ] **Step 4: Implement AnchorPrefs**

```kotlin
package org.mindanchor.anchorcore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.anchorDataStore by preferencesDataStore(name = "anchorcore")

/**
 * The loop's switches and counters. One DataStore, the SunsetPrefs
 * discipline: typed keys, defaults that match the opt-out-by-silence
 * rule, nothing interpreted.
 *
 * The clean-streak default is 7 (= unflagged): a person whose loop has
 * never flagged anything must not start life inside a flagged week.
 */
class AnchorPrefs(private val context: Context) {

    private val enabledKey = booleanPreferencesKey("anchor_enabled")
    private val letterKey = booleanPreferencesKey("hook_letter_facts")
    private val frictionKey = booleanPreferencesKey("hook_friction_hold")
    private val proposalKey = booleanPreferencesKey("hook_sunset_proposal")
    private val cleanStreakKey = intPreferencesKey("week_clean_streak")
    private val weekFlaggedKey = booleanPreferencesKey("week_flagged")
    private val lastReducedDayKey = stringPreferencesKey("streak_last_reduced_day")
    private val suppressedUntilKey = longPreferencesKey("proposal_suppressed_until_epoch_millis")
    private val sriPrevDayKey = stringPreferencesKey("sri_prev_day")
    private val sriPrevScoreKey = intPreferencesKey("sri_prev_score")
    private val sriCurDayKey = stringPreferencesKey("sri_cur_day")
    private val sriCurScoreKey = intPreferencesKey("sri_cur_score")

    val enabled: Flow<Boolean> = context.anchorDataStore.data.map { it[enabledKey] ?: false }
    val letterFactsEnabled: Flow<Boolean> = context.anchorDataStore.data.map { it[letterKey] ?: false }
    val frictionHoldEnabled: Flow<Boolean> = context.anchorDataStore.data.map { it[frictionKey] ?: false }
    val sunsetProposalEnabled: Flow<Boolean> = context.anchorDataStore.data.map { it[proposalKey] ?: false }

    suspend fun isEnabled(): Boolean = enabled.first()

    /**
     * The first off->on transition flips every hook on (the person asked
     * for the loop); afterwards each hook toggles independently and a
     * hand-set value is never overwritten.
     */
    suspend fun setEnabled(v: Boolean) {
        context.anchorDataStore.edit {
            val was = it[enabledKey] ?: false
            it[enabledKey] = v
            if (v && !was && it[letterKey] == null) it[letterKey] = true
            if (v && !was && it[frictionKey] == null) it[frictionKey] = true
            if (v && !was && it[proposalKey] == null) it[proposalKey] = true
        }
    }

    suspend fun setLetterFactsEnabled(v: Boolean) { context.anchorDataStore.edit { it[letterKey] = v } }
    suspend fun setFrictionHoldEnabled(v: Boolean) { context.anchorDataStore.edit { it[frictionKey] = v } }
    suspend fun setSunsetProposalEnabled(v: Boolean) { context.anchorDataStore.edit { it[proposalKey] = v } }

    suspend fun cleanStreak(): Int =
        context.anchorDataStore.data.first()[cleanStreakKey] ?: WeekPicture.CLEAN_DAYS_TO_UNFLAG

    suspend fun setCleanStreak(v: Int) {
        context.anchorDataStore.edit { it[cleanStreakKey] = v.coerceIn(0, WeekPicture.CLEAN_DAYS_TO_UNFLAG) }
    }

    suspend fun weekFlagged(): Boolean = context.anchorDataStore.data.first()[weekFlaggedKey] ?: false
    suspend fun setWeekFlagged(v: Boolean) { context.anchorDataStore.edit { it[weekFlaggedKey] = v } }

    suspend fun lastReducedDay(): LocalDate? =
        context.anchorDataStore.data.first()[lastReducedDayKey]
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    suspend fun setLastReducedDay(day: LocalDate) {
        context.anchorDataStore.edit { it[lastReducedDayKey] = day.toString() }
    }

    suspend fun recordProposalDismissed(now: Instant = Instant.now()) {
        context.anchorDataStore.edit {
            it[suppressedUntilKey] = now.plusSeconds(SUPPRESS_DAYS * 24 * 3600).toEpochMilli()
        }
    }

    suspend fun proposalSuppressedUntil(now: Instant = Instant.now()): Instant? =
        context.anchorDataStore.data.first()[suppressedUntilKey]
            ?.takeIf { it > now.toEpochMilli() }
            ?.let { Instant.ofEpochMilli(it) }

    fun suppressedUntilFlow(): Flow<Instant?> =
        context.anchorDataStore.data.map { prefs ->
            prefs[suppressedUntilKey]?.let { Instant.ofEpochMilli(it) }
        }

    suspend fun sriSlots(): Pair<SriWeekLedger.Slot?, SriWeekLedger.Slot?> {
        val p = context.anchorDataStore.data.first()
        fun slot(dayKey: androidx.datastore.preferences.core.Preferences.Key<String>, scoreKey: androidx.datastore.preferences.core.Preferences.Key<Int>): SriWeekLedger.Slot? {
            val day = p[dayKey]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
            val score = p[scoreKey] ?: return null
            return SriWeekLedger.Slot(day, score)
        }
        return slot(sriPrevDayKey, sriPrevScoreKey) to slot(sriCurDayKey, sriCurScoreKey)
    }

    suspend fun setSriSlots(prev: SriWeekLedger.Slot?, cur: SriWeekLedger.Slot?) {
        context.anchorDataStore.edit {
            if (prev == null) { it.remove(sriPrevDayKey); it.remove(sriPrevScoreKey) } else {
                it[sriPrevDayKey] = prev.day.toString(); it[sriPrevScoreKey] = prev.score
            }
            if (cur == null) { it.remove(sriCurDayKey); it.remove(sriCurScoreKey) } else {
                it[sriCurDayKey] = cur.day.toString(); it[sriCurScoreKey] = cur.score
            }
        }
    }

    companion object {
        const val SUPPRESS_DAYS = 14L
    }
}
```

(The proposal card reads `suppressedUntilFlow()` raw and lets the pure
`SunsetProposal.decide` compare against `nowMillis` — expiry logic lives in one
place, Task 7.)

- [ ] **Step 5: Implement AnchorCoreSource** — this is where every v1 gap closes:
onsets come from sleep windows (not `firstUnlockMinute`), `observedDays` is
actually called (trailing 14 days), the streak reduces once per day, the SRI
ledger rolls, and the persisted `weekFlagged` feeds Hook B.

```kotlin
package org.mindanchor.anchorcore

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.mindanchor.sleep.Deviation
import org.mindanchor.sleep.SleepRepository
import org.mindanchor.usage.RhythmRepository
import org.mindanchor.vitals.WellnessHistoryStore
import org.mindanchor.vitals.WellnessRepository

/**
 * The Context-carrying face: pulls what the existing repositories already
 * expose, reduces to facts, rolls the day ledgers, and answers with one
 * AnchorState. Recomputed on demand by its callers (PreHome open, letter
 * generation, Home composition) — never on a timer.
 */
class AnchorCoreSource(private val context: Context) {

    suspend fun state(today: LocalDate = LocalDate.now()): AnchorState {
        val prefs = AnchorPrefs(context)
        // Inert sentinel; every hook checks the master toggle before
        // acting, so -1 observed days can never render anywhere.
        if (!prefs.isEnabled()) return AnchorState.WarmingUp(-1)

        val zone = ZoneId.systemDefault()

        // Sleep onsets, the SettingsViewModel:684 pattern: window starts →
        // local time → minutes-after-18:00, so midnight-crossers read late.
        val summary = runCatching { SleepRepository(context).estimate() }.getOrNull()
        val onsets = summary?.windows.orEmpty().map { w ->
            val t = Instant.ofEpochMilli(w.startMillis).atZone(zone).toLocalTime()
            Deviation.minutesAfterSixPm(t.hour * 60 + t.minute)
        }

        // Observed days over the trailing 14: screen-rhythm days union
        // vital-ledger days. Both reads are local and cheap.
        val window = (0L..13L).map { today.minusDays(it) }
        val rhythms = runCatching { RhythmRepository(context).rhythms(window) }.getOrNull()
        val presenceByDay = window.associateWith { d ->
            rhythms?.get(d)?.let { it.firstUnlockMinute ?: it.screenMinutes }
        }
        val vitalDays = runCatching { WellnessHistoryStore(context).all() }
            .getOrDefault(emptyList())
            .map { it.day }
            .filter { it in window }
            .toSet()
        val daysObserved = AnchorCore.observedDays(presenceByDay, vitalDays)

        val readings = runCatching { WellnessRepository(context).readingsFor(today) }
            .getOrDefault(emptyList())

        // Weekly SRI snapshot roll (Task 5 header for why).
        val (prevSlot, curSlot) = prefs.sriSlots()
        val rolled = SriWeekLedger.roll(prevSlot, curSlot, today, summary?.regularityScore)
        prefs.setSriSlots(rolled.prev, rolled.cur)

        val facts = buildList {
            AnchorCore.lateNightCluster(onsets, today)?.let(::add)
            addAll(AnchorCore.vitalFacts(readings, today))
            AnchorCore.sleepIrregular(summary?.regularityScore, rolled.lastWeekSri, today)?.let(::add)
        }

        // Hysteresis: one reduce per calendar day, whatever recomputes first.
        val flaggedToday = facts.isNotEmpty()
        val streak = if (prefs.lastReducedDay() != today) {
            WeekPicture.reduce(flaggedToday, prefs.cleanStreak()).also {
                prefs.setCleanStreak(it)
                prefs.setLastReducedDay(today)
            }
        } else {
            // Same-day recompute: a fact appearing later in the day still
            // resets the streak; a fact disappearing does not refund it.
            if (flaggedToday && prefs.cleanStreak() > 0) {
                prefs.setCleanStreak(0)
                0
            } else prefs.cleanStreak()
        }
        val weekFlagged = WeekPicture.isFlagged(flaggedToday, streak)
        prefs.setWeekFlagged(weekFlagged)

        return AnchorState.of(daysObserved, facts, weekFlagged, System.currentTimeMillis())
    }
}
```

- [ ] **Step 6: Run everything so far**

Run: `.\gradlew.bat testDebugUnitTest --tests "org.mindanchor.anchorcore.*"`
Expected: PASS — DayFact, AnchorState, AnchorCore, SriWeekLedger, FrictionToneHold, AnchorPrefs.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/java/org/mindanchor/anchorcore/SriWeekLedger.kt app/src/main/java/org/mindanchor/anchorcore/AnchorPrefs.kt app/src/main/java/org/mindanchor/anchorcore/AnchorCoreSource.kt app/src/test/java/org/mindanchor/anchorcore/SriWeekLedgerTest.kt app/src/test/java/org/mindanchor/anchorcore/AnchorPrefsTest.kt
git commit -m "feat(anchorcore): prefs + SRI week ledger + context-facing source with live hysteresis"
```

---

### Task 6: Hook A — the letter prompt gains the week's facts

**Files:**
- Create: `app/src/main/java/org/mindanchor/anchorcore/LetterFactsSection.kt`
- Modify: `app/src/main/java/org/mindanchor/llm/LetterContext.kt` (defaulted `factsSection` param + post-trimIndent splice)
- Modify: `app/src/main/java/org/mindanchor/letters/LetterViewModel.kt` (optional facts-provider constructor param)
- Modify: `app/src/main/java/org/mindanchor/launcher/LauncherViewModel.kt` (supply the provider at the :595 construction site)
- Create: `app/src/test/java/org/mindanchor/anchorcore/LetterContextFactsTest.kt`

**Threading design (v1's snippet did not compile):** `LetterViewModel` has no
Context — it is built once, at `LauncherViewModel.kt:595`, from Application-scoped
collaborators. So the VM gains a sixth, defaulted constructor param
`weekFacts: (suspend () -> String?)? = null`; production wiring passes a lambda
that checks the toggles and composes the section; tests and the internal
secondary constructor are untouched by the default.

**Splice design (trimIndent trap, §0.5):** `LetterPrompt.kt` is not modified.
`LetterContext.build` gains `factsSection: String = ""`; when non-blank it splices
the block into the already-trimIndent-ed prompt just before the closing
instruction line. When blank, the output is byte-identical to today —
`LetterPromptShapeTest` stays green by construction.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.mindanchor.anchorcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.llm.LetterContext
import org.mindanchor.llm.LlmMessage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class LetterContextFactsTest {

    private val fact = DayFact(FactKind.LATE_NIGHT_CLUSTER, "3|300", LocalDate.of(2026, 8, 26))

    @Test
    fun `steady with facts composes bullet lines without verdict words`() {
        val state = AnchorState.Steady(listOf(fact), weekFlagged = true, computedAtEpochMillis = 0L)
        val section = LetterFactsSection.compose(state)!!
        assertTrue(section.contains("- "))
        assertTrue(section.contains("3 nights"))
        assertFalse(section.contains("good", ignoreCase = true))
        assertFalse(section.contains("bad", ignoreCase = true))
    }

    @Test
    fun `warming up or factless steady composes nothing`() {
        assertNull(LetterFactsSection.compose(AnchorState.WarmingUp(3)))
        assertNull(LetterFactsSection.compose(AnchorState.Steady(emptyList(), false, 0L)))
    }

    @Test
    fun `an empty facts section leaves the prompt byte-identical`() {
        val now = Instant.parse("2026-08-26T09:00:00Z")
        val without = LetterContext.build(LocalDate.of(2026, 8, 26), emptyList(), emptyList(), now, ZoneOffset.UTC)
        val with = LetterContext.build(LocalDate.of(2026, 8, 26), emptyList(), emptyList(), now, ZoneOffset.UTC, factsSection = "")
        assertEquals(userText(without), userText(with))
    }

    @Test
    fun `a facts section lands before the closing instruction`() {
        val now = Instant.parse("2026-08-26T09:00:00Z")
        val section = "- 3 nights this week ran well past your usual bedtime."
        val prompt = userText(
            LetterContext.build(
                LocalDate.of(2026, 8, 26), emptyList(), emptyList(), now, ZoneOffset.UTC,
                factsSection = section,
            ),
        )
        val factsAt = prompt.indexOf(section)
        val instructionAt = prompt.indexOf("Write today's letter")
        assertTrue(factsAt in 1 until instructionAt)
    }

    private fun userText(request: org.mindanchor.llm.LlmRequest): String =
        request.messages.filterIsInstance<LlmMessage.User>().single().content
}
```

(If `LlmMessage.User`'s payload field is named differently, open
`llm/LlmMessage.kt` and use the real field — do not guess.)

- [ ] **Step 2: Run it — expect FAIL, unresolved `LetterFactsSection`. Implement the composer:**

```kotlin
package org.mindanchor.anchorcore

/**
 * Renders AnchorState into the letter-prompt block (Hook A). Bullets
 * only: the model reads sentences, the person reads the letter, and
 * neither is served by adjectives. Direction-only wording comes from
 * DayFactRenderer; this object just frames the lines as observations
 * of the person's own data.
 *
 * @wording-reviewed — the section header line reaches the model as
 * context for user-visible prose; same review discipline as the
 * renderers it wraps.
 */
object LetterFactsSection {

    fun compose(state: AnchorState): String? {
        val steady = state as? AnchorState.Steady ?: return null
        if (steady.facts.isEmpty()) return null
        return steady.facts.joinToString("\n") { "- ${DayFactRenderer.render(it.kind, it.detail)}" }
    }
}
```

- [ ] **Step 3: Splice in `LetterContext.build`** — add the parameter after `zone`:

```kotlin
    fun build(
        today: LocalDate,
        notes: List<Note>,
        checkIns: List<CheckIn>,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        factsSection: String = "",
    ): LlmRequest {
```

and, where `userPrompt` is assembled (line ~90), splice after the fact:

```kotlin
        val userPrompt = LetterPrompt.userPrompt(
            /* the existing seven args, unchanged */
        ).let { prompt ->
            if (factsSection.isBlank()) {
                prompt
            } else {
                // Splice after trimIndent so the block cannot disturb the
                // template's margin arithmetic (see plan §0.5). The anchor
                // is the template's closing instruction line.
                prompt.replace(
                    FACTS_ANCHOR,
                    "[This week, from the user's own device]\n$factsSection\n\n$FACTS_ANCHOR",
                )
            }
        }
```

with, in the companion area of `LetterContext`:

```kotlin
    /** First words of the template's closing instruction — the splice anchor. */
    private const val FACTS_ANCHOR = "Write today's letter"
```

- [ ] **Step 4: Thread the provider through `LetterViewModel`**

Constructor (after `letterLog`):

```kotlin
    private val letterLog: LetterGenerationLog,
    /**
     * Hook A (AnchorCore): composes the week-facts block, or null when
     * the loop is off, warming, or factless. Injected because this VM
     * deliberately has no Context; LauncherViewModel supplies it.
     */
    private val weekFacts: (suspend () -> String?)? = null,
```

In `runGeneration`, replace line 172:

```kotlin
        val factsSection = runCatching { weekFacts?.invoke() }.getOrNull().orEmpty()
        val request = LetterContext.build(today, notes, checkIns, factsSection = factsSection)
```

In `LauncherViewModel` at the :595 construction site, add the argument:

```kotlin
        letterLog = LetterGenerationLog(application),
        weekFacts = {
            val anchorPrefs = AnchorPrefs(getApplication())
            if (anchorPrefs.isEnabled() && anchorPrefs.letterFactsEnabled.first()) {
                runCatching { AnchorCoreSource(getApplication()).state() }.getOrNull()
                    ?.let { LetterFactsSection.compose(it) }
            } else {
                null
            }
        },
```

(Imports: `org.mindanchor.anchorcore.AnchorPrefs`, `AnchorCoreSource`,
`LetterFactsSection`, and `kotlinx.coroutines.flow.first` if not present.)

- [ ] **Step 5: Run the letter suites — the shape test is the point**

Run: `.\gradlew.bat testDebugUnitTest --tests "org.mindanchor.llm.*" --tests "org.mindanchor.anchorcore.LetterContextFactsTest" --tests "org.mindanchor.letters.*"`
Expected: PASS, including `LetterPromptShapeTest` (empty section ⇒ byte-identical prompt).

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/org/mindanchor/anchorcore/LetterFactsSection.kt app/src/main/java/org/mindanchor/llm/LetterContext.kt app/src/main/java/org/mindanchor/letters/LetterViewModel.kt app/src/main/java/org/mindanchor/launcher/LauncherViewModel.kt app/src/test/java/org/mindanchor/anchorcore/LetterContextFactsTest.kt
git commit -m "feat(llm): letter prompt gains optional week-facts block (Hook A)"
```

---

### Task 7: Hook C — sunset proposal card on Home

**Files:**
- Create: `app/src/main/java/org/mindanchor/anchorcore/SunsetProposal.kt`
- Modify: `app/src/main/java/org/mindanchor/data/SunsetPrefs.kt` (temporary window override)
- Modify: `app/src/main/java/org/mindanchor/launcher/LauncherViewModel.kt` (anchor state + card state + actions)
- Modify: `app/src/main/java/org/mindanchor/launcher/HomeScreen.kt` (card composable + wiring)
- Create: `app/src/test/java/org/mindanchor/anchorcore/SunsetProposalTest.kt`

**Behavioral note (verified):** `SunsetPrefs.isQuietHour()` calls `window()`, so
making `window()` prefer a live override means Accept genuinely moves the
wind-down — quiet hours, grayscale, the gate's `insideSleepWindow` — for 7 days.
That is the intended lever. The override must not touch the base keys or the
`sunset_window_customized` flag, and `clearTemporaryWindow()` restores the usual
window instantly (revocable, per the spec's autonomy law; surfaced in Task 9).

- [ ] **Step 1: Write the failing test**

```kotlin
package org.mindanchor.anchorcore

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SunsetProposalTest {

    private val steadyClustered = AnchorState.Steady(
        facts = listOf(DayFact(FactKind.LATE_NIGHT_CLUSTER, "3|300", LocalDate.of(2026, 8, 26))),
        weekFlagged = true,
        computedAtEpochMillis = 0L,
    )

    @Test
    fun `shows only when enabled steady clustered and not suppressed`() {
        val d = SunsetProposal.decide(true, steadyClustered, suppressedUntil = null, nowMillis = 1000L)
        assertEquals(SunsetProposal.Reason.SHOW, d.reason)
        assertEquals(true, d.show)
    }

    @Test
    fun `disabled never shows`() {
        assertEquals(
            SunsetProposal.Reason.DISABLED,
            SunsetProposal.decide(false, steadyClustered, null, 1000L).reason,
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
    fun `no cluster no card even on a flagged week`() {
        val flaggedNoCluster = AnchorState.Steady(
            facts = listOf(DayFact(FactKind.MOVEMENT_LOW, "-2.4", LocalDate.of(2026, 8, 26))),
            weekFlagged = true,
            computedAtEpochMillis = 0L,
        )
        assertEquals(
            SunsetProposal.Reason.NO_CLUSTER,
            SunsetProposal.decide(true, flaggedNoCluster, null, 1000L).reason,
        )
    }

    @Test
    fun `suppressed hides until the window passes`() {
        assertEquals(
            SunsetProposal.Reason.SUPPRESSED,
            SunsetProposal.decide(true, steadyClustered, Instant.ofEpochMilli(2000L), 1000L).reason,
        )
        assertEquals(
            SunsetProposal.Reason.SHOW,
            SunsetProposal.decide(true, steadyClustered, Instant.ofEpochMilli(500L), 1000L).reason,
        )
    }
}
```

- [ ] **Step 2: Run it — expect FAIL, unresolved `SunsetProposal`. Implement:**

```kotlin
package org.mindanchor.anchorcore

import java.time.Instant

/**
 * Whether the quiet one-card proposal may appear: only when the loop is
 * on, steady, carrying a live late-night cluster, and the person has not
 * recently declined it. Never auto-applies — the autonomy law holds.
 */
object SunsetProposal {

    enum class Reason { DISABLED, WARMING, NO_CLUSTER, SUPPRESSED, SHOW }

    data class Decision(val show: Boolean, val reason: Reason)

    val HIDDEN = Decision(false, Reason.DISABLED)

    const val OVERRIDE_DAYS = 7L
    const val EARLIER_BY_MINUTES = 30L

    fun decide(
        enabled: Boolean,
        state: AnchorState,
        suppressedUntil: Instant?,
        nowMillis: Long,
    ): Decision = when {
        !enabled -> Decision(false, Reason.DISABLED)
        state !is AnchorState.Steady -> Decision(false, Reason.WARMING)
        state.lateNightCluster == null -> Decision(false, Reason.NO_CLUSTER)
        suppressedUntil != null && suppressedUntil.toEpochMilli() > nowMillis ->
            Decision(false, Reason.SUPPRESSED)
        else -> Decision(true, Reason.SHOW)
    }
}
```

- [ ] **Step 3: The override store in `SunsetPrefs`** — note the expiry key is a
**string** key holding an ISO date (v1 declared a long key and wrote a string —
it did not compile):

```kotlin
    private val overrideStartKey = intPreferencesKey("sunset_override_start_minute")
    private val overrideEndKey = intPreferencesKey("sunset_override_end_minute")
    private val overrideExpiryKey = stringPreferencesKey("sunset_override_expiry_day")

    /**
     * The AnchorCore temporary window (Hook C accept), or null when unset
     * or expired. Deliberately separate keys: the person's own window and
     * the customised flag are never touched, so removing the override —
     * or just letting it lapse — restores exactly what was there before.
     */
    suspend fun activeWindowOverride(): Pair<LocalTime, LocalTime>? {
        val prefs = context.dataStore.data.first()
        val expiry = prefs[overrideExpiryKey]
            ?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
            ?: return null
        if (expiry < java.time.LocalDate.now()) return null
        val s = prefs[overrideStartKey] ?: return null
        val e = prefs[overrideEndKey] ?: return null
        if (s !in 0..1439 || e !in 0..1439 || s == e) return null
        return LocalTime.of(s / 60, s % 60) to LocalTime.of(e / 60, e % 60)
    }

    suspend fun setTemporaryWindow(start: LocalTime, end: LocalTime, until: java.time.LocalDate) {
        context.dataStore.edit {
            it[overrideStartKey] = start.hour * 60 + start.minute
            it[overrideEndKey] = end.hour * 60 + end.minute
            it[overrideExpiryKey] = until.toString()
        }
    }

    suspend fun clearTemporaryWindow() {
        context.dataStore.edit {
            it.remove(overrideStartKey)
            it.remove(overrideEndKey)
            it.remove(overrideExpiryKey)
        }
    }
```

and make `window()` prefer it:

```kotlin
    suspend fun window(): Pair<LocalTime, LocalTime> =
        activeWindowOverride() ?: run {
            val prefs = context.dataStore.data.first()
            timeOf(prefs[startKey], DEFAULT_START) to timeOf(prefs[endKey], DEFAULT_END)
        }
```

(`stringPreferencesKey` is already imported in this file.)

- [ ] **Step 4: LauncherViewModel — anchor state, card state, actions**

Beside the other prefs fields:

```kotlin
    private val anchorPrefs = org.mindanchor.anchorcore.AnchorPrefs(application)
    private val anchorCoreSource = org.mindanchor.anchorcore.AnchorCoreSource(application)

    // Recompute-on-demand (spec): Home composition triggers refresh; no timer.
    private val _anchorCoreState =
        MutableStateFlow<org.mindanchor.anchorcore.AnchorState>(org.mindanchor.anchorcore.AnchorState.WarmingUp(0))

    fun refreshAnchorState() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { anchorCoreSource.state() }.onSuccess { _anchorCoreState.value = it }
        }
    }
```

The card decision (mirror the `weeklyPatterns` stateIn shape at :675):

```kotlin
    val sunsetProposalCard: StateFlow<org.mindanchor.anchorcore.SunsetProposal.Decision> = combine(
        anchorPrefs.enabled,
        anchorPrefs.sunsetProposalEnabled,
        _anchorCoreState,
        anchorPrefs.suppressedUntilFlow(),
    ) { master, hookOn, state, suppressed ->
        org.mindanchor.anchorcore.SunsetProposal.decide(
            enabled = master && hookOn,
            state = state,
            suppressedUntil = suppressed,
            nowMillis = System.currentTimeMillis(),
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        org.mindanchor.anchorcore.SunsetProposal.HIDDEN,
    )

    fun acceptSunsetProposal() {
        viewModelScope.launch {
            val (start, end) = sunsetPrefs.window()
            sunsetPrefs.setTemporaryWindow(
                start.minusMinutes(org.mindanchor.anchorcore.SunsetProposal.EARLIER_BY_MINUTES),
                end,
                java.time.LocalDate.now()
                    .plusDays(org.mindanchor.anchorcore.SunsetProposal.OVERRIDE_DAYS),
            )
            refreshAnchorState()
        }
    }

    fun dismissSunsetProposal() {
        viewModelScope.launch { anchorPrefs.recordProposalDismissed() }
    }
```

(Use plain imports instead of qualified names if the file's style prefers it —
match what surrounds you.)

- [ ] **Step 5: The card in `HomeScreen.kt`** — add to `HomeSurface`'s parameters
(defaults keep every existing call site compiling):

```kotlin
    sunsetProposal: org.mindanchor.anchorcore.SunsetProposal.Decision =
        org.mindanchor.anchorcore.SunsetProposal.HIDDEN,
    onAcceptSunsetProposal: () -> Unit = {},
    onDismissSunsetProposal: () -> Unit = {},
```

render directly after the `NOfOnePatternsCard` block (:1561):

```kotlin
            if (sunsetProposal.show) {
                SunsetProposalCard(
                    onAccept = onAcceptSunsetProposal,
                    onDismiss = onDismissSunsetProposal,
                )
            }
```

with the composable near the other cards (copy stays in Kotlin — the
`PhaseFourCards` precedent — and the wording is fact + question, no verdict):

```kotlin
/**
 * Hook C (AnchorCore): the one quiet card a flagged late-night week may
 * earn. Accept applies a 7-day temporary wind-down override (stored,
 * revocable in Settings → Measuring); "Not now" suppresses the card for
 * 14 days. Never notifies, never auto-applies.
 *
 * @wording-reviewed — states a fact about the person's own nights and
 * asks; no evaluation, no directive.
 */
@Composable
private fun SunsetProposalCard(onAccept: () -> Unit, onDismiss: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(0.92f)) {
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

Wire in `LauncherRoot`'s Home branch: collect `sunsetProposalCard` like the other
state (`val sunsetProposal by viewModel.sunsetProposalCard.collectAsState()`),
pass the three new arguments through to `HomeSurface`.

- [ ] **Step 6: Run tests + lint**

Run: `.\gradlew.bat testDebugUnitTest --tests "org.mindanchor.anchorcore.*"`
then `.\gradlew.bat lintDebug`
Expected: unit PASS; lint no new errors.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/java/org/mindanchor/anchorcore/SunsetProposal.kt app/src/main/java/org/mindanchor/data/SunsetPrefs.kt app/src/main/java/org/mindanchor/launcher/LauncherViewModel.kt app/src/main/java/org/mindanchor/launcher/HomeScreen.kt app/src/test/java/org/mindanchor/anchorcore/SunsetProposalTest.kt
git commit -m "feat(launcher): one-card sunset proposal on flagged weeks (Hook C)"
```

---

### Task 8: PreHome — open-loop handback + one sleep fact

**Files:**
- Create: `app/src/main/java/org/mindanchor/prehome/MorningHandback.kt`
- Modify: `app/src/main/java/org/mindanchor/prehome/PreHomeActivity.kt`
- Create: `app/src/test/java/org/mindanchor/prehome/PreHomeHandbackTest.kt`

**Frame correction from v1:** the sleep fact compares **sleep onsets**, and both
sides arrive already in the minutes-after-18:00 frame (`Deviation.minutesAfterSixPm`)
— the codebase's own convention for exactly this midnight-wrap problem. v1
compared raw minutes-of-day and its own test data (1:30am vs 23:00) contradicted
its implementation. In the after-six frame there is nothing to wrap: 23:00 → 300,
01:30 → 450.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.mindanchor.prehome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    fun `other phases and blank notes say nothing`() {
        assertNull(MorningHandback.decide(LoopPhase.NONE, "x"))
        assertNull(MorningHandback.decide(LoopPhase.POSTPONED, "x"))
        assertNull(MorningHandback.decide(LoopPhase.CAPTURE, null))
        assertNull(MorningHandback.decide(LoopPhase.RETURN, "   "))
    }

    @Test
    fun `sleep fact speaks only from fortyfive past usual`() {
        // Minutes after 18:00: usual 23:00 -> 300.
        assertNull(MorningHandback.sleepFact(lastOnsetAfterSixPm = 330, usualOnsetAfterSixPm = 300)) // 23:30
        assertNotNull(MorningHandback.sleepFact(lastOnsetAfterSixPm = 350, usualOnsetAfterSixPm = 300)) // 23:50
        assertNotNull(MorningHandback.sleepFact(lastOnsetAfterSixPm = 450, usualOnsetAfterSixPm = 300)) // 01:30
    }

    @Test
    fun `sleep fact renders both clocks`() {
        val line = MorningHandback.sleepFact(lastOnsetAfterSixPm = 450, usualOnsetAfterSixPm = 330)!!
        assertEquals(true, line.contains("1:30 am"))
        assertEquals(true, line.contains("11:30 pm"))
    }

    @Test
    fun `missing data stays silent`() {
        assertNull(MorningHandback.sleepFact(null, 300))
        assertNull(MorningHandback.sleepFact(450, null))
    }
}
```

- [ ] **Step 2: Run it — expect FAIL, unresolved `MorningHandback`. Implement:**

```kotlin
package org.mindanchor.prehome

import java.util.Locale
import org.mindanchor.friction.LoopPhase

/**
 * PreHome's morning additions: the open-loop handback (Masicampo &
 * Baumeister 2011 — writing the plan releases the loop) and at most one
 * sleep fact. Pure decisions; the activity does the DataStore work.
 *
 * @wording-reviewed — the sleep-fact line reaches the person every
 * deviating morning: two clock readings and a semicolon, no verdict.
 */
object MorningHandback {

    data class Handback(val note: String, val shouldClear: Boolean)

    /** Only RETURN speaks, and speaking means clearing — one handback each. */
    fun decide(phase: LoopPhase, note: String?): Handback? {
        if (phase != LoopPhase.RETURN) return null
        val body = note?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return Handback(body, shouldClear = true)
    }

    /** Late only when 45+ minutes past the person's own usual onset. */
    const val LATE_BY_MINUTES = 45

    /**
     * Both parameters are minutes after 18:00 (Deviation.minutesAfterSixPm),
     * so a bedtime past midnight compares as later, never as earlier —
     * the same frame every sleep surface in this app uses.
     */
    fun sleepFact(lastOnsetAfterSixPm: Int?, usualOnsetAfterSixPm: Int?): String? {
        val last = lastOnsetAfterSixPm ?: return null
        val usual = usualOnsetAfterSixPm ?: return null
        if (last - usual < LATE_BY_MINUTES) return null
        return "Up until ${clock(last)}; your usual is ${clock(usual)}."
    }

    /** Minutes-after-18:00 back to a 12-hour clock reading. */
    private fun clock(afterSixPm: Int): String {
        val minuteOfDay = (afterSixPm + 18 * 60) % 1440
        val hour12 = ((minuteOfDay / 60) % 12).let { if (it == 0) 12 else it }
        val amPm = if (minuteOfDay / 60 >= 12) "pm" else "am"
        return String.format(Locale.ROOT, "%d:%02d %s", hour12, minuteOfDay % 60, amPm)
    }
}
```

- [ ] **Step 3: Wire into `PreHomeSurface`** — above the existing intention field,
loading once on first composition (the activity already holds `applicationContext`
via `LocalContext.current`):

```kotlin
    // AnchorCore morning blocks: the open-loop handback and one sleep
    // fact. Loaded once per open; both silent unless they have something.
    var handback by remember { mutableStateOf<MorningHandback.Handback?>(null) }
    var sleepFactLine by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val app = ctx.applicationContext
        val frictionPrefs = org.mindanchor.data.FrictionPrefs(app)
        val phase = org.mindanchor.friction.OpenLoop.phase(
            quietHours = org.mindanchor.data.SunsetPrefs(app).isQuietHour(),
            note = frictionPrefs.openLoopNote.first(),
            notedDay = frictionPrefs.openLoopDay.first(),
            today = java.time.LocalDate.now(),
            postponedAt = frictionPrefs.openLoopPostponedAt.first(),
        )
        handback = MorningHandback.decide(phase, frictionPrefs.openLoopNote.first())

        val anchorPrefs = org.mindanchor.anchorcore.AnchorPrefs(app)
        if (anchorPrefs.isEnabled()) {
            // Recompute trigger #1 (spec): PreHome render rolls the day.
            runCatching { org.mindanchor.anchorcore.AnchorCoreSource(app).state() }
            val summary = runCatching { org.mindanchor.sleep.SleepRepository(app).estimate() }.getOrNull()
            val onsets = summary?.windows.orEmpty().map { w ->
                val t = java.time.Instant.ofEpochMilli(w.startMillis)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalTime()
                org.mindanchor.sleep.Deviation.minutesAfterSixPm(t.hour * 60 + t.minute)
            }
            sleepFactLine = MorningHandback.sleepFact(
                lastOnsetAfterSixPm = onsets.lastOrNull(),
                usualOnsetAfterSixPm = org.mindanchor.sleep.Deviation.usual(onsets.dropLast(1)),
            )
        }
    }

    handback?.let { hb ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Still open from last night", style = MaterialTheme.typography.titleSmall)
                Text(hb.note, style = MaterialTheme.typography.bodyMedium)
            }
        }
        LaunchedEffect(hb) {
            if (hb.shouldClear) {
                org.mindanchor.data.FrictionPrefs(ctx.applicationContext).clearOpenLoop()
            }
        }
    }
    sleepFactLine?.let {
        Text(it, style = MaterialTheme.typography.bodySmall)
    }
```

Notes: the handback block does not depend on the AnchorCore master toggle — it
belongs to PreHome itself (spec Component 3) and only fires when PreHome is
already enabled. The sleep fact requires the master toggle (it is loop output).
`usual` over `onsets.dropLast(1)` needs `Deviation.usual`'s null on empty —
handled, `sleepFact` returns null. Match the file's existing import style
(top-level imports, not inline qualified names) when you write this for real.

- [ ] **Step 4: Run the prehome suites**

Run: `.\gradlew.bat testDebugUnitTest --tests "org.mindanchor.prehome.*"`

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/org/mindanchor/prehome/MorningHandback.kt app/src/main/java/org/mindanchor/prehome/PreHomeActivity.kt app/src/test/java/org/mindanchor/prehome/PreHomeHandbackTest.kt
git commit -m "feat(prehome): open-loop handback + one-sentence sleep fact"
```

---

### Task 9: Settings toggles, override revoke, wording gate, clinician pack

**Files:**
- Modify: `app/src/main/java/org/mindanchor/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/org/mindanchor/settings/SettingsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/test/java/org/mindanchor/anchorcore/AnchorWordingTest.kt`
- Regenerate: `docs/CLINICIAN_PACK.md` via `python tools/clinician-pack.py`

- [ ] **Step 1: Strings** — in `strings.xml` (settings copy is resource-based;
this edit correctly trips the CI wording-heavy detector):

```xml
    <string name="settings_anchor_title">AnchorCore</string>
    <string name="settings_anchor_subtitle">A quiet weekly picture from your own patterns. Off until you ask.</string>
    <string name="settings_anchor_letter_title">Letter knows the week</string>
    <string name="settings_anchor_letter_subtitle">The daily letter sees this week\'s own-data notes.</string>
    <string name="settings_anchor_friction_title">Gentler repetition in hard weeks</string>
    <string name="settings_anchor_friction_subtitle">Pauses keep their breath longer during flagged weeks.</string>
    <string name="settings_anchor_proposal_title">Wind-down suggestion</string>
    <string name="settings_anchor_proposal_subtitle">One quiet suggestion after late-night weeks.</string>
    <string name="settings_anchor_override_active">Wind-down is 30 minutes earlier until %1$s.</string>
    <string name="settings_anchor_override_remove">Return to usual</string>
```

- [ ] **Step 2: SettingsViewModel** — follow the `prehomeEnabled` pattern
(:403) exactly; construct `anchorPrefs` beside the existing prefs fields and add
a small override surface:

```kotlin
    private val anchorPrefs = org.mindanchor.anchorcore.AnchorPrefs(application)

    // v-next (AnchorCore): the wellbeing loop's master switch + hooks.
    // Default OFF everywhere (opt-out-by-silence); first enable flips
    // hook defaults once (AnchorPrefs.setEnabled owns the latch).
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

    // The Hook C override, so the accept is visible and revocable here.
    private val _sunsetOverride = MutableStateFlow<Pair<java.time.LocalTime, java.time.LocalTime>?>(null)
    val sunsetOverride: StateFlow<Pair<java.time.LocalTime, java.time.LocalTime>?> = _sunsetOverride.asStateFlow()

    fun refreshSunsetOverride() {
        viewModelScope.launch { _sunsetOverride.value = sunsetPrefs.activeWindowOverride() }
    }

    fun clearSunsetOverride() {
        viewModelScope.launch {
            sunsetPrefs.clearTemporaryWindow()
            _sunsetOverride.value = null
        }
    }
```

(`sunsetPrefs` already exists in this VM if the sleep-mirror section uses it —
verify; if the field is named differently, mirror what is there.)

- [ ] **Step 3: SettingsScreen rows** — inside an `if (group == SettingsGroup.MEASURING)`
block (find the existing MEASURING content; the PreHome row at :1374 is PAUSES —
do not put these there). Master toggle first, then the three hook rows gated on
the master, then the override row when active:

```kotlin
        if (group == SettingsGroup.MEASURING) {
            // --- AnchorCore: the wellbeing loop ---
            val anchor by viewModel.anchorEnabled.collectAsState()
            SettingsRowSwitch(
                title = stringResource(R.string.settings_anchor_title),
                subtitle = stringResource(R.string.settings_anchor_subtitle),
                checked = anchor,
                onCheckedChange = { viewModel.setAnchorEnabled(it) },
            )
            if (anchor) {
                val letterFacts by viewModel.anchorLetterFacts.collectAsState()
                val frictionHold by viewModel.anchorFrictionHold.collectAsState()
                val proposal by viewModel.anchorSunsetProposal.collectAsState()
                SettingsRowSwitch(
                    title = stringResource(R.string.settings_anchor_letter_title),
                    subtitle = stringResource(R.string.settings_anchor_letter_subtitle),
                    checked = letterFacts,
                    onCheckedChange = { viewModel.setAnchorLetterFacts(it) },
                )
                SettingsRowSwitch(
                    title = stringResource(R.string.settings_anchor_friction_title),
                    subtitle = stringResource(R.string.settings_anchor_friction_subtitle),
                    checked = frictionHold,
                    onCheckedChange = { viewModel.setAnchorFrictionHold(it) },
                )
                SettingsRowSwitch(
                    title = stringResource(R.string.settings_anchor_proposal_title),
                    subtitle = stringResource(R.string.settings_anchor_proposal_subtitle),
                    checked = proposal,
                    onCheckedChange = { viewModel.setAnchorSunsetProposal(it) },
                )
                val override by viewModel.sunsetOverride.collectAsState()
                LaunchedEffect(Unit) { viewModel.refreshSunsetOverride() }
                override?.let { (start, _) ->
                    Text(
                        stringResource(R.string.settings_anchor_override_active, start.toString()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = { viewModel.clearSunsetOverride() }) {
                        Text(stringResource(R.string.settings_anchor_override_remove))
                    }
                }
            }
        }
```

(Adapt the row/label composable details to what the MEASURING section already
uses — copy a neighbouring block's structure rather than inventing one.)

- [ ] **Step 4: The wording gate, made executable** — the spec's guardrail
demands the fact-rendering strings never carry a verdict. There is no existing
wordlist unit test (the gate is the CI workflow + `@wording-reviewed` tags), so
add one for the new surface, in the LetterPrompt ban-list spirit:

```kotlin
package org.mindanchor.anchorcore

import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate

/**
 * Every string the loop can emit states a count or a direction and
 * stops. The ban list is LetterPrompt's voice-rules vocabulary — the
 * words that turn a fact into a verdict.
 */
class AnchorWordingTest {

    private val banned = listOf(
        "good", "bad", "well done", "great", "proud",
        "should", "must", "try to", "better than", "worse",
    )

    @Test
    fun `no renderer output carries a verdict word`() {
        val samples = FactKind.entries.map { DayFactRenderer.render(it, "3|300") } +
            LetterFactsSection.compose(
                AnchorState.Steady(
                    facts = listOf(DayFact(FactKind.LATE_NIGHT_CLUSTER, "3|300", LocalDate.of(2026, 8, 26))),
                    weekFlagged = true,
                    computedAtEpochMillis = 0L,
                ),
            )!!.let(::listOf)
        for (line in samples) {
            for (word in banned) {
                assertFalse(
                    "banned word '$word' in: $line",
                    line.contains(word, ignoreCase = true),
                )
            }
        }
    }
}
```

- [ ] **Step 5: Regenerate the clinician pack + run the gates**

Run: `python tools/clinician-pack.py` then `git diff --stat -- docs/CLINICIAN_PACK.md`
(commit the regenerated file if it changed), then:
`.\gradlew.bat testDebugUnitTest --tests "org.mindanchor.ci.*" --tests "org.mindanchor.anchorcore.*" --tests "org.mindanchor.settings.*"`

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/org/mindanchor/settings/SettingsViewModel.kt app/src/main/java/org/mindanchor/settings/SettingsScreen.kt app/src/main/res/values/strings.xml app/src/test/java/org/mindanchor/anchorcore/AnchorWordingTest.kt docs/CLINICIAN_PACK.md
git commit -m "feat(settings): AnchorCore master + per-hook toggles, override revoke, wording gate"
```

---

### Task 10: Wiring — refresh triggers, Hook B call sites, final verification

**Files:**
- Modify: `app/src/main/java/org/mindanchor/launcher/HomeScreen.kt` (refresh on Home composition)
- Modify: `app/src/main/java/org/mindanchor/friction/GateActivity.kt` (Hook B at the gate)
- Modify: `app/src/main/java/org/mindanchor/launcher/FrictionViewModel.kt` (Hook B on the bandit path)
- Modify: `docs/PHASE_4_STATUS.md`

- [ ] **Step 1: Refresh on Home composition** — in `LauncherRoot`'s Home branch,
beside its existing `LaunchedEffect` wiring:

```kotlin
    LaunchedEffect(Unit) { viewModel.refreshAnchorState() }
```

(PreHome's open already recomputes — wired in Task 8; letter generation
recomputes through the `weekFacts` provider — wired in Task 6. Those are the
spec's three triggers.)

- [ ] **Step 2: Hook B at `GateActivity.kt:62`** — the gate's coroutine already
does suspend reads there (`prefs.recordGateShown`, `sunsetPrefs.isQuietHour()`).
Add the flag read and pass it:

```kotlin
                        val anchorPrefs = org.mindanchor.anchorcore.AnchorPrefs(applicationContext)
                        val weekFlagged = anchorPrefs.isEnabled() &&
                            anchorPrefs.frictionHoldEnabled.first() &&
                            anchorPrefs.weekFlagged()
                        val tone = FrictionContext.toneFor(
                            recentOpens = prior,
                            insideSleepWindow = quiet,
                            weekFlagged = weekFlagged,
                        )
```

- [ ] **Step 3: Hook B on the bandit path** — `FrictionViewModel.adaptiveTone`
(:118) consults the FrictionBandit whenever the deterministic tone is FULL, and
the bandit may answer BRIEF — which would silently undo the hold. On a flagged
week the deterministic tone stands:

```kotlin
    private suspend fun adaptiveTone(prior: Int, quiet: Boolean): AdaptiveTone {
        val anchorPrefs = org.mindanchor.anchorcore.AnchorPrefs(getApplication())
        val weekFlagged = anchorPrefs.isEnabled() &&
            anchorPrefs.frictionHoldEnabled.first() &&
            anchorPrefs.weekFlagged()
        val deterministic = FrictionContext.toneFor(prior, insideSleepWindow = quiet, weekFlagged = weekFlagged)
        // Hook B: the bandit's arms were reasoned for ordinary weeks
        // (FrictionBandit.kt header); a flagged week is precisely when
        // the ceremony holds its weight, so the deterministic tone wins.
        if (weekFlagged) return AdaptiveTone(deterministic, null)
        if (deterministic != FrictionTone.FULL) return AdaptiveTone(deterministic, null)
        /* existing bandit consultation unchanged */
```

(If `FrictionViewModel` is not an `AndroidViewModel`, take the Context the same
way its other prefs constructions do — read the file first.)

- [ ] **Step 4: Full verification pass**

Run: `.\gradlew.bat testDebugUnitTest detekt lintDebug`
Expected: all green — the full pre-existing suite (1238+) plus every anchorcore
suite, `NetworkCallsForbiddenTest` included. (`assembleDebug` only if your
environment has NDK 27.3.13750724 — see §0.)

- [ ] **Step 5: Manual smoke on emulator (optional but recommended)**

Enable Settings → Measuring → AnchorCore. Confirm: no crash on cold start; the
gate still shows; PreHome hands back an open loop written the previous evening;
with an LLM key configured, `LetterGenerationLog` shows a generation whose
prompt carried the facts block (or none, during warm-up — also correct).

- [ ] **Step 6: Status doc + final commit**

Add an AnchorCore section to `docs/PHASE_4_STATUS.md`: landed commits, the
deliberate non-goals (digest retiming, IME, mood inference, notifications), and
the SriWeekLedger cold-start note (SLEEP_IRREGULAR silent for the first week and
after long gaps — by design).

```powershell
git add app/src/main/java/org/mindanchor/launcher/HomeScreen.kt app/src/main/java/org/mindanchor/friction/GateActivity.kt app/src/main/java/org/mindanchor/launcher/FrictionViewModel.kt docs/PHASE_4_STATUS.md
git commit -m "feat(anchorcore): on-demand refresh + Hook B call-site wiring + status docs"
```
