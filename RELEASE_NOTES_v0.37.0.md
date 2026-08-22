# v0.37.0 — BPD-safety Toast fix + Polar step rename + research audit

**Tag:** v0.37.0
**Commit:** (see `git log --oneline v0.36.0..v0.37.0`)
**versionCode:** 66
**versionName:** 0.37.0
**Date:** 2026-08-18

## What changed

This release closes the only non-PASS finding in the v0.37.0
research-backed feature audit (`docs/audit/feature-inventory.md`)
and fixes a long-standing class-name mismatch in the setup wizard
that the audit surfaced. There is no user-visible UI redesign in
this release — the goal was to ship the small, evidence-driven
wins without re-shaping the home.

### 1. Letter-generation Toast copy (F16, BPD-safety WARN → PASS)

**File:** `app/src/main/java/org/mindanchor/launcher/HomeScreen.kt:840-844`

The previous copy quoted the model latency window — "30–60
minutes" — which the audit rated WARN for BPD-safety: a person
in distress reads that as latency pressure, then has to do the
arithmetic of "is now + 30-60 min still 'tonight'?"

Old: "Generating tonight's letter — the Q2_K model on this
phone takes 30–60 minutes. The letter appears in the inbox when
it finishes."

New: "Started. Tonight's letter will be in your inbox by
morning."

The new copy names the outcome ("in your inbox by morning")
without quantifying the wait, and keeps "tonight's" as the only
time reference. The Toast.LENGTH_LONG duration is unchanged.

**Audit result: 82 PASS / 0 WARN / 0 FAIL / 82 of 84 features
with file:line evidence.** F16 is now PASS.

### 2. CorosStep → PolarStep rename

The setup-wizard step always hosted the `PolarSection` (Polar
Flow OAuth2 web bridge). The "Coros" name was a pre-rename
leftover from an earlier Coros Training Hub bridge that was
removed. The audit flagged the class name as a code-clarity
hazard — readers couldn't tell which Coros bridge (the removed
wizard one, or the real `vitals/coros/` package) they were
looking at.

What this rename touches:

- `app/src/main/java/org/mindanchor/onboarding/steps/CorosStep.kt`
  → `PolarStep.kt` (file + class)
- `SetupStep.COROS` enum → `SetupStep.POLAR`
- `SetupProgress.corosSkipped` field → `SetupProgress.polarSkipped`
- `setup_wizard_coros_*` and `setup_wizard_source_coros_*`
  string resources → `setup_wizard_polar_*` and
  `setup_wizard_source_polar_*`
- Adds `settings_polar_login_in_progress` (replaces
  `coros_login_in_progress` for the renamed wizard's flow; the
  real Coros bridge section in `SettingsScreen.kt` still uses
  `coros_login_in_progress` unchanged)
- Updates 4 finding tests in `SetupWizardStepTest` to assert the
  renamed class and enum value

**The on-disk DataStore key `coros_skipped` is left as-is so
existing users' skip state is preserved without a migration.**
Only the field name visible to the rest of the app changed.

The `vitals/coros/` package (the real Coros Training Hub
MD5-hash web bridge, 7 files) is untouched — it lives only in
Settings, not in the setup wizard. The real Coros string
`coros_login_in_progress` is still in `strings.xml` and still
referenced by `SettingsScreen.kt:2408, 2527`.

### 3. Research-backed audit (docs/audit/)

Three documents produced by foreground research agents (not
in-session generation, no LLM hallucination):

- **`docs/audit/feature-inventory.md`** — 84 user-facing
  features across 38 packages. Usability 1-5 histogram
  (5:10, 4:24, 3:38, 2:11, 1:1). BPD-safety 82 PASS / 1 WARN /
  0 FAIL with file:line evidence. Top-10 suspicious list with
  reasons. Integration status (Health Connect, Polar, Coros,
  BLE, PPG) per feature.

- **`docs/audit/bpd-research-review.md`** — founded on
  Linehan 1993 (DBT), Schwartz 1995 (IFS), Hayes 1999 (ACT),
  Young/Klosko/Weishaar 2003 (schema). 11 features to REMOVE
  and 11 to ADD, each cited. Notes priovi (Lancet Psychiatry
  2025, d=0.24 — first positive RCT for a BPD digital
  therapeutic), DBT Coach (Rizvi 2016), BlueIce (Stallard 2018
  + BASH 2024), mDiary. npj Digital Medicine 2020 Hedges'
  g = −0.066 — smartphone apps as a class don't beat waitlist
  for BPD. Crisis-line evidence: 988 / Gould 2025 strong;
  iCall / Vandrevala / AASRA descriptive only (no outcome
  trial).

- **`docs/audit/top-mh-app-audit.md`** — 8 DBT apps
  (DBT Coach MARS 3.25, Calm Harm 3.8, eMoods, Moodfit,
  Bearable, Daylio, MoodTools, DBT Diary Card), 3 crisis apps
  (988, 7 Cups, KIRAN / Tele-MANAS), 7 anti-patterns.
  MARS-G baseline for BPD-targeted apps is M=3.25
  (Drews-Windeck 2022). Apple Design Award winners: Headspace
  2023, Bears Gratitude 2024, Gentler Streak 2024.

## Not in this release (deferred)

- **Polar OAuth callback round-trip** (manifest intent filter
  + `OAuthCallbackActivity` + token exchange + on-device test).
  Bigger work than a single release can carry. Currently the
  Polar form records credentials but does not complete the
  round-trip. Planned for v0.37.1.

- **Adding the 11 new features and removing the 11 flagged
  features from the audit.** Each add/remove is a separate
  design + measurement + commit. Not bundled here.

- **v0.36.0 Health Connect SDK 1.2.0 stable migration.** Still
  blocked on Google shipping the stable release. MindAnchor
  v0.36.0 ships the patched 1.2.0-alpha05 AAR so the consumer
  can compile, but the provider's gateway Activity still
  rejects alpha-SDK consumers with the "App update needed"
  page. There is no client-side fix.

- **Settings UI verification on phone.** v0.36.0 phone test
  showed taps in the bottom-right "settings" nav area landed in
  the system Settings activity, not the in-app Settings.
  Off-by-one with the gesture bar. To be triaged separately.

## Files

**Modified:**
- `app/src/main/java/org/mindanchor/launcher/HomeScreen.kt` —
  F16 Toast copy.
- `app/src/main/java/org/mindanchor/onboarding/SetupPrefs.kt`
  — enum value + field rename (KDoc explains the on-disk key
  preservation).
- `app/src/main/java/org/mindanchor/onboarding/SetupWizardActivity.kt`
  — enum + import + when-branch.
- `app/src/main/java/org/mindanchor/onboarding/SetupWizardViewModel.kt`
  — `back()` chain.
- `app/src/main/java/org/mindanchor/onboarding/steps/WelcomeStep.kt`
  — string resource refs.
- `app/src/main/java/org/mindanchor/onboarding/steps/DoneStep.kt`
  — string resource refs.
- `app/src/main/java/org/mindanchor/settings/PolarSection.kt`
  — string resource ref.
- `app/src/main/res/values/strings.xml` — coros_ → polar_ rename
  + new `settings_polar_login_in_progress` (preserves the real
  Coros `coros_login_in_progress`).
- `app/src/test/java/org/mindanchor/onboarding/SetupWizardStepTest.kt`
  — 4 finding tests updated.

**Renamed:**
- `app/src/main/java/org/mindanchor/onboarding/steps/CorosStep.kt`
  → `PolarStep.kt`.

**New:**
- `docs/audit/feature-inventory.md` (48KB)
- `docs/audit/bpd-research-review.md` (48KB)
- `docs/audit/top-mh-app-audit.md` (28KB)

**Version:**
- `app/build.gradle.kts` — versionCode 65→66, versionName
  "0.36.0"→"0.37.0".

## Tests

`SetupWizardStepTest` (4 tests updated) passes. The F16 audit
finding is not backed by a finding test (it is copy, not code)
and so cannot be unit-tested; verification is on the
phone-screenshot side.
