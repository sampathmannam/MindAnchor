# Check-in redesign

Date: 2026-08-24
Status: approved (user, 2026-08-24)
Scope: 4 changes in 1 release

## Problem

The check-ins surface has three issues the user reported in one
message:

1. The **wellbeing pulse** (a 2-min WHO-5 every two weeks) is a
   separate, heavier flow that the user no longer wants in the
   app.
2. The micro check-ins (the "Ask me how I am" valence × arousal
   prompts) fire as **silent** notifications
   (`IMPORTANCE_LOW`). The user wants a banner/heads-up so the
   prompt is actually seen.
3. The check-in design is described in code as a momentary
   assessment, but the **research basis is not visible** in the
   UI. The user wants the design to read as evidence-anchored
   (citations in KDoc + a one-line settings link), and a **small
   text-only patterns dashboard** that surfaces "what the data
   is implying" without a chart.

## Decisions

- **Wellbeing pulse**: remove entirely. Delete the package,
  the strings, the settings entry, the plan_measurement
  reference, the WHO-5 array, the pulse scheduler, and the
  pulse tests.
- **Notification**: `ema` channel → `ema_prompts_v2` channel
  with `IMPORTANCE_DEFAULT` (heads-up), no sound, no
  vibration. Old channel deleted on first post.
- **Research basis**: Russell 1980 (circumplex), Shiffman 2008
  (EMA), Csikszentmihalyi & Hunter 2003 (ESM). Citations in
  `Ema.kt` KDoc. One-line settings link: "Russell's circumplex
  model (1980) — the same axes psychology research uses for
  momentary affect."
- **Dashboard**: text-only. Four patterns via a sealed
  `Insight` type. Below threshold, the pattern is *absent*
  (not a "not enough data" line — same shape as fewer
  patterns). Empty state when no data at all.
- **BPD-safe copy**: descriptive, never directive, never
  compared to a norm, never says "concerning" or "low". Uses
  "about your usual", "a little brighter", etc.

## Build order

1. **Pulse removal** — deletions + string cleanup +
   `Alarms.kt` cleanup + `WellnessRepository.kt` docstring
   update + androidTest cleanup + `gradlew test`.
2. **Banner notification** — channel rename, importance,
   old-channel deletion, notification no-sound/no-vibration
   config. Add a `gradlew test` to verify the new channel
   config string is present.
3. **Research citations** — `Ema.kt` KDoc + a one-line
   `R.string.ema_research_link` + a `Text(...)` on the
   Check-ins section in `SettingsScreen.kt`.
4. **Patterns engine + dashboard** — new
   `org.mindanchor.insights` package with `CheckInPatterns`
   (pure), `CheckInPatternsTest`, `CheckInInsightsSection`
   (Composable). New strings. New "What your check-ins
   show" section in `SettingsScreen.kt`, gated on
   `MomentStore.enabled`.

## Files affected (summary)

### Removed
- `app/src/main/java/org/mindanchor/pulse/` (5 files)
- `app/src/test/java/org/mindanchor/pulse/` (4 files)
- `app/src/androidTest/java/org/mindanchor/pulse/` (1 file)
- `pulse_*` strings (≈20)
- `who5_items` array
- `plan_measurement` line that references pulse

### Modified
- `app/src/main/java/org/mindanchor/Alarms.kt` — drop
  `PulseReminder.ensureScheduled` call + import
- `app/src/main/java/org/mindanchor/vitals/WellnessRepository.kt`
  — drop `pulse` reference in KDoc
- `app/src/main/java/org/mindanchor/model/EmaScheduler.kt` —
  new channel id, importance, sound/vibration
- `app/src/main/java/org/mindanchor/model/Ema.kt` — research
  citations in KDoc
- `app/src/main/java/org/mindanchor/settings/SettingsScreen.kt`
  — drop pulse section; add research link; add
  "What your check-ins show" section
- `app/src/main/java/org/mindanchor/onboarding/GoalMap.kt` —
  drop `SettingsSection.PULSE` enum entry + its
  `pulseSection` references; clean up `MEASUREMENT` mapping
  if needed
- `app/src/androidTest/java/org/mindanchor/AppSmokeTest.kt` —
  drop `pulseActivityOpens` test
- `app/src/androidTest/java/org/mindanchor/ui/SemanticsTest.kt`
  — drop `pulse()` test
- `app/src/androidTest/java/org/mindanchor/ui/ScreenshotTest.kt`
  — drop `pulse()` test
- `app/src/main/res/values/strings.xml` — strings cleanup +
  new strings
- `docs/CLINICIAN_PACK.md` — keep in sync
- `app/src/test/java/org/mindanchor/onboarding/GoalMapTest.kt`
  — drop any `PULSE` references in expectations

### Added
- `app/src/main/java/org/mindanchor/insights/CheckInPatterns.kt`
- `app/src/main/java/org/mindanchor/insights/CheckInPatternsTest.kt`
- `app/src/main/java/org/mindanchor/insights/CheckInInsightsSection.kt`

## Verification

- `gradlew test` after every commit
- `gradlew test` full pass at the end (1237/1237 baseline)
- `NetworkCallsForbiddenTest` still 8/8 (no new network
  symbols)
- The patterns engine has unit tests for: empty input,
  threshold gating, recent trend (brighter/rougher/about the
  same), best-hours aggregation, baseline comparison

## Out of scope

- Replacing the entire momentary model (the 2D
  valence × arousal axes stay; the engine reads from them)
- WHO-5 weekly (the user chose "text-only patterns",
  not "full WHO-5")
- A chart library (text only, by user choice)
- A streak/shame counter (BPD-safe: missed days are just
  missed days, never scored)
- Cross-feature changes to other check-in surfaces
  (`CheckIn.kt`, `CheckInActivity.kt`, etc.)
