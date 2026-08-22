## v0.21.0 — the 10/10 candidate

The 10/10 plan (see `docs/research/22-10-of-10-roadmap.md`) is feature-complete
in v0.21.0. Every WP-1..WP-8 deliverable has shipped, detekt + lint + 892
unit tests are green, and the simulation report (`docs/research/22-simulation-report-v0.21.0.md`)
identifies no P0 issues.

### What's new

**Chronotype-aware default quiet hours (WP-8).** A 5-case
`Chronotype` enum (MORNING_LARK, NEUTRAL, NIGHT_OWL, SHIFT_WORKER,
UNKNOWN) replaces the hardcoded 22:00 → 07:00 default. The
chronotype is captured at onboarding (a one-tap step between
goals and plan) and editable in settings. The default window
is set from the chronotype *only* on first answer; a hand-set
window is left alone (`SunsetPrefs.newWindowFor` is the pure
helper that pins the guard).

**Sleep window optimizer (WP-8).** A suggestion driven by the
user's own recent sleep onsets, anchored in Windred 2024's
"regularity beats duration" finding. Renders in settings as
"From your last N nights, your nights cluster around X. A
wind-down at Y would land an hour and a half before that."
with a single `Use this` button. `SleepWindowOptimizer.suggest`
is pure — 9 unit tests cover the midnight-crosser, the day
sleeper, and the all-nighter-doesn't-move-the-suggestion cases.

**Expressive writing prompt (WP-8, Pennebaker 1997).** The
existing check-in reflection field is the launcher's
expressive-writing surface. Two new tests pin the contract:
empty reflection is a legal check-in (not a missing one), and
the reflection is stored verbatim, never summarised or
mood-tagged. `docs/research/22-research-index.md` cites
Pennebaker 1997 + the empirical Pennebaker & Stone 1977 paper.

**Connect-to-your-watch fix (v0.20.10).** The button used to
render during the `Unknown` loading state because the guard
was `!is Unavailable`. Changed to `is Available`. Regression
test in `SettingsHealthConnectButtonTest.kt`.

**Research index (WP-1).** Every feature KDoc now has a verified
citation: Iglewicz & Hoaglin 1993, Scullin 2018, Roenneberg
2007, Wittmann 2006, Åkerstedt 2003, Kecklund 2016, Baglioni 2016,
Harvey 2002, Dimidjian 2006, Brosschot 2006, Thayer & Lane 2000,
Killingsworth & Gilbert 2010, Lally 2010, Neff 2003, Pennebaker
1997, Grüning 2023, Gollwitzer 1999 + 2006, Balban 2023,
Bernardi 2001, Linardon 2020, Liu 2023, Windred 2024, Masicampo
& Baumeister 2011. The 17 UNANCHORED citations (Wilson 2014,
"Bernardi 2018", "Carney 2010", "aan het Rot 2012 BA", etc.)
are excluded from code and listed in the index with the
replacement reference.

**Simulation library (WP-2..7).** 8 personas (5
research-anchored + 3 adversarial), 14-day warmup + 14-day
test, respects the home card's `isReportable` gate.
`WellnessSimulationRunner.kt` + `SimulationReport.kt` +
`AdversarialPersonaTest.kt` (13 tests) +
`PersonaChronotypeTest.kt` (8 tests).

### Acceptance (gates)

- `./gradlew :app:detekt :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` — clean
- 892 unit tests, 0 failures
- 8 personas × 14 days — no P0 simulation findings

### Open follow-ups (out of v0.21.0 scope)

- **WP-10 acceptance:** the 3rd-party walkthrough script lives at
  `docs/qa/3rd-party-onboarding-test.md`. Two non-developers
  need to be timed through it; the actual walkthroughs are a
  human task that has to happen outside this codebase.
- **P1 simulation finding:** the runner cannot yet drive
  `SleepWindowOptimizer` end-to-end on persona data because
  the personas emit sleep durations but not sleep onsets.
  Follow-up: add `sleepOnsetMinutes: Int?` to `DailyVitals`
  and the persona library, then wire the runner.
- **Live 2-week field test (WP-9, in progress from v0.20.9).**
  Continues on the physical device.

### Files

- SHA-256: 511A577546A53954487FD0EDD87A5C8D8FB0A82134F01D9D70E504272F89BDF9
- Size: 10.6 MB
- Min SDK: 33 (Android 13); Target SDK: 35 (Android 15)

### Verifying

```
sha256sum app-release.apk
# 511a577546a53954487fd0edd87a5c8d8fb0a82134f01d9d70e504272f89bdf9
```
