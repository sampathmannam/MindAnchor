# AnchorCore — the wellbeing loop (design)

> Date: 2026-08-26 · Status: approved design, pending implementation plan
> Companion to [CONCEPT.md](../../../CONCEPT.md) §3.4 (sensorless phenotyping),
> [research/08](../../research/08-sensing-architecture.md), and
> [research/07](../../research/07-techniques-invented.md) §1–3.

## Problem

MindAnchor collects everything a personal sensing layer needs — screen rhythms
(SRI), five wellness vitals with per-person robust-z baselines, the friction
GateLedger, COROS wearable sync, camera PPG HRV — but nothing connects them.
Features fire on fixed rules regardless of the person's state. The project's own
research (`07-techniques-invented.md`) predicted this failure mode: an
intervention that cannot read the moment will stop working and keep firing.

The user's complaint, verbatim: "no coherent wellbeing loop."

## Goal

One on-device aggregator (**AnchorCore**) turns existing signals into per-day
facts and a trailing week picture; existing features adapt around it. Plus the
morning surface (PreHome) gains the open-loop handback so the loop has one owned
daily touchpoint.

Constraints carried from project law:

- Zero new permissions. Zero network. `NetworkCallsForbiddenTest` stays green.
- Facts, never labels: no interpretation of any reading
  (`CLINICAL_REVIEW.md` invariant).
- Everything toggleable; the loop is passive-first and silent.
- No new math: median + MAD robust-z from `WellnessSignals.kt` only.

## Architecture

```
RhythmRepository ──┐
WellnessRepository ├─→ AnchorCore ─→ AnchorState (StateFlow)
GateLedger ────────┤     (pure,
SleepRepository ───┘      testable)
                          │
        ┌─────────────────┼──────────────────┐
        ↓                 ↓                  ↓
  letter context   friction tone hold   sunset proposal card
```

New package `org.mindanchor.anchorcore`. AnchorCore is a pure orchestrator: it
asks existing repositories for readings, reduces them to facts, exposes one
`StateFlow<AnchorState>`. No repository is modified beyond what it already
exposes.

## Component 1 — AnchorCore aggregator

### DayFacts

Per-day facts, each carrying its own numbers (a fact without its numbers is a
label in disguise):

| Fact | Source | Example |
|---|---|---|
| `LATE_NIGHT_CLUSTER` | sleep-window onsets (`SleepRepository.estimate()`) | "3 nights past 01:00 vs usual 23:30" — the median-of-7 rule caps the count at 3 |
| `MOVEMENT_LOW` | `WellnessSignal.STEPS` | robust z = -2.1 |
| `SLEEP_IRREGULAR` | SRI trend | SRI dropped 18 points vs prior week |
| `HRV_LOW` | HRV (PPG or wearable) | robust z = -2.3 |
| `RHR_HIGH` | resting heart rate | robust z = +2.0 |

A fact exists only when its deviation crosses the direction-band threshold the
codebase already uses (|z| ≥ 2.0, per `WellnessSignals.kt` and Jacobson 2019 as
the closest published reference). Facts are recomputed on demand — when PreHome
renders, when the daily-letter job runs, when Home composes the proposal-card
check — never on a timer of their own. They are not stored as state; the
sources are the store.

A day counts as *observed* for warm-up purposes when it has at least one screen
event in `RhythmRepository` or any vital reading in `WellnessRepository`. Days
with neither are absent from the baseline count, not zero-filled.

### Week picture

The trailing-7-day window defines one boolean: **flagged**. The week is flagged
when any fact fired within it. It unflags after 7 consecutive clean days. This
hysteresis prevents flapping and gives adaptation hooks a stable signal.

### Cold start

Fewer than 7 days of usable history → `AnchorState.WARMING_UP(daysObserved)`.
All hooks do nothing differently during warm-up. No baseline hallucination: the
app says nothing until it knows something.

### State shape

```kotlin
sealed interface AnchorState {
    data class WarmingUp(val daysObserved: Int) : AnchorState
    data class Steady(
        val facts: List<DayFact>,      // active facts only
        val weekFlagged: Boolean,
        val computedAt: Instant,
    ) : AnchorState
}
```

## Component 2 — Adaptation hooks

Ordered by risk. Each hook reads `AnchorState`; none writes to it.

### Hook A — Letter context (zero risk)

The daily letter's generation prompt (the live path: `LetterContext.build` →
`LetterPrompt.userPrompt` → `LetterViewModel.runGeneration`) gains an optional
block: this week's facts, rendered as plain sentences ("3 nights this week ran
well past your usual bedtime."). Letters stop being generic. When
`AnchorState` is `WarmingUp`, there are no facts, or the master toggle is off,
the prompt is byte-identical to today (the pinned prompt-shape test proves it).

Guardrail: the block passes through the same wording discipline as all
user-facing copy — the files carrying it take the `@wording-reviewed` KDoc tag
(the `clinical-review.yml` CI gate's convention; there is no wordlist unit test
in the codebase), and a new `AnchorWordingTest` pins that no rendered fact line
carries a verdict word.

### Hook B — Friction tone hold (low risk)

On a flagged week the `FrictionTone` softening ladder holds at `FULL`
longer: the soften thresholds move from the existing repeats 1/3 to 2/5.
(Inside the sleep window the tone is already always `FULL` — that rule is
untouched and keeps winning.) On clean weeks behavior is unchanged.
Unit-testable as a pure function of `(recentOpens, insideSleepWindow,
weekFlagged)`. The bandit path (`FrictionViewModel.adaptiveTone`) defers to
the deterministic tone on flagged weeks, since its arms were reasoned for
ordinary weeks.

### Hook C — Sunset proposal card (medium risk)

On a flagged week where `LATE_NIGHT_CLUSTER` is active, Home shows **one**
quiet card: "Your last four nights ran late. Want sunset 30 minutes earlier
this week?" Accept applies a temporary override for 7 days (stored, visible,
revocable); Dismiss suppresses the card for 14 days. Never auto-applied — the
autonomy law holds. One card at a time; no notification, no badge.

## Component 3 — Morning surface (extend PreHome)

PreHomeActivity gains two blocks above the existing intention field:

1. **Open-loop handback** — items parked via `OpenLoop` the previous evening
   render here with their if-then plans, then clear. The Masicampo & Baumeister
   mechanism: the plan releases the loop.
2. **One sleep fact** — from `ScreenRhythm`: "Up until 1:40am; your usual is
   11:30." Fact + nothing else. Rendered only when it deviates; silence is the
   default.

Both respect PreHome's existing self-skip-when-disabled behavior.

## Explicitly not doing

- Digest retiming or pulse rescheduling (measurement cadence must stay fixed to
  stay honest).
- Mood inference, sentiment analysis, any cross-person model.
- Notifications about deviations (the loop is silent; surfaces speak only when
  opened).
- New sensors, new permissions, IME (deliberate v-next).

## Settings surface

One master toggle ("AnchorCore") plus per-hook toggles (letter context default
ON; friction hold default ON; sunset proposal default ON). All under Settings →
Measuring.

The master toggle defaults OFF, matching PreHome's precedent
(opt-out-by-silence rule: nothing new runs until asked). The hooks are inert
until it is on.

## Testing

Pure-function unit tests throughout:

- Fact computation: threshold crossing, cold-start, missing-source tolerance
  (any single source failing yields fewer facts, never a crash).
- Week flagging + hysteresis (7-day clean rule).
- Each hook: letter-context rendering (incl. wordlist gate), tone-ladder shift,
  proposal-card accept/dismiss/suppress lifecycle.
- PreHome handback: parked items render once and clear; empty morning renders
  nothing extra.

All 1238+ existing tests stay green; `detekt`, lint, and the clinical-review
gate pass unchanged.

## Files expected to change

```
app/src/main/java/org/mindanchor/anchorcore/          (new)
    DayFact.kt, AnchorState.kt (incl. WeekPicture), AnchorCore.kt,
    SriWeekLedger.kt, AnchorPrefs.kt, AnchorCoreSource.kt,
    LetterFactsSection.kt, SunsetProposal.kt
app/src/main/java/org/mindanchor/llm/LetterContext.kt      (Hook A splice)
app/src/main/java/org/mindanchor/letters/LetterViewModel.kt (Hook A provider)
app/src/main/java/org/mindanchor/friction/FrictionTone.kt   (Hook B ladder)
app/src/main/java/org/mindanchor/friction/GateActivity.kt   (Hook B wiring)
app/src/main/java/org/mindanchor/launcher/FrictionViewModel.kt (Hook B wiring)
app/src/main/java/org/mindanchor/data/SunsetPrefs.kt        (Hook C override)
app/src/main/java/org/mindanchor/launcher/HomeScreen.kt     (Hook C card)
app/src/main/java/org/mindanchor/launcher/LauncherViewModel.kt (state + card)
app/src/main/java/org/mindanchor/prehome/MorningHandback.kt (new) + PreHomeActivity.kt
app/src/main/java/org/mindanchor/settings/            (toggles + override revoke)
app/src/test/java/org/mindanchor/anchorcore/          (new tests)
docs/CLINICIAN_PACK.md                                (regenerated)
```

## Success criteria

1. After 7+ days of use, letters reference the week's real facts.
2. Flagged weeks measurably hold friction FULL longer in the sleep window
   (unit-proven) and produce exactly one sunset proposal card.
3. Morning surface returns open loops and states at most one sleep fact.
4. No interpretation language anywhere; wordlist gate green.
5. Zero new permissions; network-forbidden test green; all hooks individually
   disable-able.
