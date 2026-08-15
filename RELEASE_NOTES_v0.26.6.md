# v0.26.6 — Cognitive-load cut on home + WHO-5 surface dropped (research-weak)

**Tag:** `v0.26.6`
**versionCode:** 48 → 49
**versionName:** `0.26.5` → `0.26.6`
**Branch:** `work/v0.21.0-10of10`

## What this release does

Two cuts, both targeted at reducing cognitive load on the home surface and replacing ad-hoc surfaces with research-grounded ones (per `docs/research/14-v0.26.6-audit.md`).

### Cut 1: `BedtimeListCard` removed from the home surface

The home previously rendered three task-capture cards in sequence (OpenLoop → BedtimeList → OneThing). For a person with BPD, three competing capture affordances on a single screen is the explicit kind of cognitive-load pattern the brief asks to avoid (Linehan 1993; Fruzzetti 2006 — DBT validation requires *one* thing at a time, not three).

The data model (`sleep/BedtimeList.kt`), the DataStore (`data/LauncherPrefs.kt`), the strings, and the `bedtimeList` state flow in `LauncherViewModel` are all kept. Only the home-surface call is gone. A future v0.27.x DBT diary card (see audit §3.5) may re-introduce a bedtime surface under a different shape.

The `QuickNotesCardAboveFoldFindingTest` was updated: it previously pinned the order OpenLoop → QuickNotes → OneThing → BedtimeList; it now also asserts that `BedtimeListCard` is *not* rendered on home.

### Cut 2: `pulse/` package removed (WHO-5 ad-hoc check-in)

The `pulse/` package implemented a 14-day WHO-5 Well-Being Index check-in (Topp 2015; WHO 1998). The instrument itself is research-valid; the *shape* of the surface (a single WHO-5 score every fortnight, separate from the EMA system, separate from the CheckIn system) is the *weak* part. The app had three ad-hoc check-in systems (EMA, CheckIn, Pulse) — none of them shaped like the DBT diary card that the research identifies as the gold standard for BPD (Linehan 1993 chapter 11; Dimeff et al. 2011).

v0.26.6 drops the package. The data model (`pulse_results` table, `BackupCodec.pulseOf`/`toPulseResults`) is kept so existing pulse history is preserved across the v0.26.5→v0.26.6 upgrade. The `Channels.PULSE` notification channel and the `PulseReminderReceiver` are removed (no new pulse notifications will be sent). The `pulse/WhoFive.kt` research-grade scoring is preserved at `docs/research/13-pulse.md` for the v0.27.x DBT diary card re-introduction.

The `pulse_*` string keys are removed from both `values/` and `values-ta/`. The Settings → MEASURING "Wellbeing pulse" section is removed. The `SettingsSection.PULSE` enum value is removed; `Goal.MEASUREMENT` now points only at `HEALTH_CONNECT`.

## Why these cuts

Per `docs/research/14-v0.26.6-audit.md`:

| Cut | Research basis | Concrete evidence |
|---|---|---|
| BedtimeListCard | DBT validation: one thing at a time | Linehan 1993; Fruzzetti 2006 — competing capture affordances invite splitting |
| pulse/ package | DBT diary card > ad-hoc mood tracker | Linehan 1993 ch. 11; Dimeff et al. 2011 — diary card is the gold standard for BPD mood tracking |

## What changed

| File | Change |
| --- | --- |
| `app/src/main/java/org/mindanchor/launcher/HomeScreen.kt` | `BedtimeListCard` composable removed; call site replaced with v0.26.6 comment; `bedtimeList` state collection removed; `BedtimeList`/`BedtimePhase` imports removed |
| `app/src/main/java/org/mindanchor/notifications/Channels.kt` | `Channels.PULSE` constant + `pulse()` function + call in `ensureAll` removed |
| `app/src/main/AndroidManifest.xml` | `.pulse.PulseActivity` activity + `.pulse.PulseReminderReceiver` receiver removed |
| `app/src/main/java/org/mindanchor/Alarms.kt` | `PulseReminder.ensureScheduled` call removed; comment updated |
| `app/src/main/java/org/mindanchor/onboarding/GoalMap.kt` | `SettingsSection.PULSE` enum value removed; `Goal.MEASUREMENT` → `setOf(SettingsSection.HEALTH_CONNECT)` |
| `app/src/main/java/org/mindanchor/settings/SettingsScreen.kt` | "Wellbeing pulse" section in MEASURING removed; `SettingsSection.PULSE` → `null` for the "marked" tags of unrelated sections |
| `app/src/main/res/values/strings.xml` + `values-ta/strings.xml` | `pulse_*` string keys removed; `plan_measurement` updated to point at Health Connect |
| `app/src/main/java/org/mindanchor/diagnostics/LogFile.kt` | Comment updated |
| `app/src/main/java/org/mindanchor/onboarding/Onboarding.kt` | Comment updated |
| `app/src/main/java/org/mindanchor/notifications/BatchAlarms.kt` | Comment updated |
| `app/src/main/java/org/mindanchor/vitals/WellnessRepository.kt` | Comment updated |
| `app/src/test/java/org/mindanchor/launcher/QuickNotesCardAboveFoldFindingTest.kt` | Updated: added assertion that `BedtimeListCard` is NOT rendered on home |
| `app/src/test/java/org/mindanchor/launcher/HapticRichCapturesFindingTest.kt` | Removed "BedtimeList save fires a haptic on tap" test (surface is gone) |
| `app/src/test/java/org/mindanchor/compose/ComposeStateHuntFindingTest.kt` | Removed BUG-008 (BedtimeListCard drafts listSaver) and BUG-011 (PulseScreen) test shapes |
| `app/src/test/java/org/mindanchor/permissions/NotificationChannelCreationFindingTest.kt` | Removed `PulseReminder uses Channels_PULSE constant` test |
| `app/src/test/java/org/mindanchor/permissions/ExactAlarmPermissionCoverageFindingTest.kt` | Removed `pulse/PulseReminder.kt` from schedulers list |
| `app/src/test/java/org/mindanchor/i18n/I18nSweepFindingTest.kt` | Removed `pulse/PulseScreen.kt` from swept files |
| `app/src/test/java/org/mindanchor/accessibility/A11ySurfaceFindingTest.kt` | Removed `pulse/PulseScreen.kt` from swept files (B15 IconButton test) |
| `app/src/test/java/org/mindanchor/onboarding/GoalMapTest.kt` | `measurement points at the pulse and the wearable section` test renamed and updated to assert only HEALTH_CONNECT |
| `app/build.gradle.kts` | `versionCode` 48 → 49, `versionName` "0.26.5" → "0.26.6" |
| `docs/research/14-v0.26.6-audit.md` | **NEW** — full mental-health research audit; flags for research-incompatible patterns; top 5 missing features backed by research |
| `pulse/` (entire package) | **DELETED** via `git rm -r` |

## Files deleted (10)

```
app/src/main/java/org/mindanchor/pulse/PulseActivity.kt
app/src/main/java/org/mindanchor/pulse/PulseCadence.kt
app/src/main/java/org/mindanchor/pulse/PulseReminder.kt
app/src/main/java/org/mindanchor/pulse/PulseScreen.kt
app/src/main/java/org/mindanchor/pulse/WhoFive.kt
app/src/test/java/org/mindanchor/pulse/PulseCadenceTest.kt
app/src/test/java/org/mindanchor/pulse/SignedChangeTest.kt
app/src/test/java/org/mindanchor/pulse/WhoFiveBandsTest.kt
app/src/test/java/org/mindanchor/pulse/WhoFiveTest.kt
app/src/androidTest/java/org/mindanchor/pulse/PulseFlowTest.kt
```

## Tests

- 1412 tests, 0 failed, 0 ignored (was 1449 in v0.26.5; -37 from removed test classes and BUG-011/BUG-008 fix-shape pins)
- detekt clean

## End-to-end on phone (Motorola ZD2232FCR5, Android 17)

1. App launches, no FATAL
2. Home renders **2:06** + "Here, now." (no OpenLoop header, no BedtimeList)
3. Notes card → save works
4. OneThing card → set / clear works
5. Right now section → 3 BPD buttons (chain / IFS / export) all open correctly
6. Settings → MEASURING group no longer has the "Wellbeing pulse" section
7. No notifications from `pulse` channel (channel removed at process start)

APK SHA-256: `389CDD34A868155D03FAA4BBFD810AA5D6AB45E44519DE890320F3FCC93F2DEB`

## What v0.26.6 does NOT do (deferred to v0.27.x per the audit)

1. **Crisis line numbers in the 2am shell** ("I want to talk to someone" → iCall 9152987821, Vandrevala 1860-2662-362, AASRA 9820466726) — audit §2.1
2. **DBT Module 4 (DEAR MAN / GIVE / FAST)** interpersonal-effectiveness scripts — audit §3.1
3. **Self-compassion break** (Neff 2003) — audit §3.2
4. **Radical acceptance** (Linehan 1993) — audit §3.4
5. **DBT diary card** to replace EMA + CheckIn (Linehan 1993 ch. 11) — audit §2.5
6. **ACT values clarification** (Hayes 2004) — audit §3.5

## Why I was wrong in the v0.26.5 round of recommendations

In the v0.26.5-cut recommendations, I flagged `friction.AppWatchService` and `watch.AppWatchService` as "duplicates". On closer look they are not duplicates — `friction.AppWatchService` is an `AccessibilityService` (window-state watcher for the friction gate), and `watch.AppWatchService` is a foreground service for SMS tone-check. They share a name but do different things. The v0.26.5 recommendation to drop the `friction` one would have broken the gate. The lesson (memory: 2026-08-15): "two services with the same name and different APIs are not duplicates — verify the API surface before recommending a cut."

The two real cuts in v0.26.6 are the right ones: BedtimeListCard (third task-capture card) and the pulse package (third ad-hoc check-in).
