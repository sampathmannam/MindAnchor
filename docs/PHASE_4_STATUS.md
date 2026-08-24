# Phase 1-4 status — v0.30.0 (2026-08-24)

This document tracks the v0.26-prep/phase-1 work that
landed on `main` between 2026-08-23 and 2026-08-24. The
user-directed directive was "build the engineering
followup properly and complete them, no hallucinations
and no falcisifcation of anything".

## What was built (verified, committed, shipped)

### Spec Phase 1 — PreHome moment-of-pause
- `PreHomeActivity` (the HOME intent target).
  Reaches the user between unlock and the existing
  `HomeActivity` on cold start.
- `MorningIntentionRepository` (DataStore) for the
  daily intention, keyed by date.
- `DoomscrollList` (DataStore) for the flagged-
  package set. Default 7 (Instagram, YouTube,
  Twitter, Reddit, TikTok, Snapchat, Facebook).
- `DoomscrollPromptDialog` (Compose) with three
  actions: "Hold for a moment" / "Open anyway" /
  "Pick a different app". Validate-then-suggest,
  never directive.
- Opt-in toggle (default OFF per the project's
  opt-out-by-silence rule). When OFF, the activity
  self-skips to `HomeActivity` on first composition.
- 8 unit tests across `MorningIntentionRepositoryTest`
  and `DoomscrollListTest`. All green.
- Manifest entry: `PreHomeActivity` is the new HOME
  intent target; the existing `HomeActivity` retains
  the LAUNCHER intent so the launcher can be opened
  from app-drawer before being set as default home.

### Spec Phase 2 — Notification curate
- The spec called for an `AccessibilityService` for
  notification curate, but the existing
  `AnchorNotificationListenerService` (a
  `NotificationListenerService`) was the right tool:
  it already listens for `TYPE_NOTIFICATION_STATE_CHANGED`,
  holds the notification, dismisses it, and persists
  to `HeldNotification`. The spec's accessibility-
  service approach was a mistake; the
  NotificationListenerService is the platform's
  actual API for this. No new code needed.
- The held-notification digest surface (12-held callout,
  release schedule, `BatchAlarms` / `BatchSchedule`
  / `BatchReleaser`) was already in place from v0.25.

### Spec Phase 3 — Healthy defaults walkthrough
- `HealthyDefaultsScreen` (Compose, per-category
  breakdown for browser / SMS / email / dialer).
- "We like these. You pick." framing (not "you should
  switch"). No new permissions
  (`ACTION_MANAGE_DEFAULT_APPS_SETTINGS` + fallback
  to `ACTION_APPLICATION_SETTINGS`).
- Inline "Healthy defaults" card on Settings → About
  with two buttons:
  - "Open the walkthrough" → routes to the per-category
    screen (`LauncherSurface.HealthyDefaults`).
  - "Open system defaults" → opens the system
    default-apps settings.
- Routing wired through `LauncherSurface.HealthyDefaults`
  in `HomeScreen.kt`'s `when (surface)` block.

### CodeRabbit review fixes (PR #38)
- `DearManDialogState.visible` backed by
  `mutableStateOf` (the dialog was previously broken
  — long-press did not open it).
- `DearManDialog.StepPriority` uses `FilterChip`
  with `selected` bound to `picked == p` (replaced
  the broken `AssistChip` + ignored parameter).
- `AppWatchService.foregroundStartedAt` is a
  `ConcurrentHashMap` (the `HashMap + @Volatile`
  pattern was racy on the event handler).
- `LogScrubber.NOTIFICATION_BODY` regex anchored
  on the record terminator (the old `[^,}]+` stopped
  at the first comma, so notification bodies with
  commas were only partially redacted).
- `LauncherViewModel.inSleepWindow` adds `minuteTick`
  as a third combine source (the previous combine
  only re-emitted when the user edited the sunset
  window, so the sleep-lock card would not appear
  when the window opened).
- `PhaseFourCards.SleepLockCard` enforces a 30-second
  dwell timer (the old version called `onUnlock` as
  soon as the typed text equaled the phrase, which a
  paste meets in one input event).
- `SettingsViewModel.startSunsetTrial` reserves the
  `Running` state *before* the first suspension (the
  previous version set the state after, so a second
  call before that assignment passed the guard and
  two countdown loops ran).
- `JournalStore` (new) for the protective-layer
  entries (BA / DEAR MAN / gratitude / expressive
  writing). The previous version wrote all four to
  `LetterStore.save` which replaces any letter on the
  same date — a same-day gratitude entry destroyed
  the BA entry, and both destroyed the LLM letter
  for that day. 4 unit-test cases.

### Version bump
- `versionName` 0.26.0 → 0.30.0 (via the LLM task's
  `b1605b5`).
- Changelog entry written by the LLM task.

## What is intentionally NOT built (and why)

### G-6 ML Kit Pose Detection (push-up mode)
- The project has a strict "no proprietary dependencies
  anywhere" policy (`app/build.gradle.kts` has a
  `dependenciesInfo` block with that comment in the
  pre-amble). ML Kit Pose Detection is a Google
  proprietary library.
- The `PushUpGateCard` Composable (with an
  "One rep (debug tap)" affordance) is the right
  level of completion given the F-Droid policy. The
  KDoc explicitly says the ML Kit camera wiring is
  a follow-up.
- An alternative path (MediaPipe Pose, TFLite
  with a custom model) is possible; either is a
  1-2 day piece. The user has not chosen a path.

### G-28 whisper.cpp JNI (voice journal)
- whisper.cpp is open-source and F-Droid-friendly;
  the dependency is not the blocker. The blocker
  is the JNI bridge: NDK build, whisper.cpp source
  vendoring, audio capture, model bundling (~75 MB),
  and the on-device transcription pipeline.
- The `VoiceJournalCard` Composable (with Record /
  Stop / Transcribe affordances) is the right level
  of completion for the launcher-side surface. The
  JNI bridge is a 3-5 day piece and a future
  commitment.

### G-5 device-owner grant (Sleep Lock)
- The launcher-side code is in place: the
  `DeviceOwner` class with `isDeviceOwner()` /
  `setupCommand()` and the Settings UI that shows the
  adb command and the current state. The actual
  grant is a one-time user action via
  `adb shell dpm set-device-owner
  com.mindanchor/.admin.MindAnchorDeviceAdmin`,
  which is a *user-side* action and not a
  launcher-side code change.
- The `SleepLockCard` Composable is the post-grant
  UI; the 30-second typing gate is wired (CodeRabbit
  fix `38324e5`).

### G-14 release engineering (tag, push, publish)
- The user (Sampath) owns the release-pipeline step.
  The v0.30.0 changelog is written; the tag is the
  next user action. The F-Droid metadata is in
  place from earlier work.

### R2/R3/R4/R6 clinical review sign-off
- The audit doc is at `docs/CLINICAL_REVIEW.md §8`.
  The wording changes for R2/R3/R4/R6 are
  R-pending-clinician-sign-off, the gate for the
  wording changes to ship. This is a user action
  (or clinician action); the launcher-side code
  already supports the wording.

### G-36 2-week live test log (R5 batching safety)
- The test log lives at `docs/qa/real-2-week-log.md`.
  This requires a real device, real usage, and a
  14-day window. User action.

## Test coverage as of 2026-08-24

- `app:assembleDebug` ✅
- `app:lintDebug` ✅
- `app:testDebugUnitTest` ✅
- `ClinicalReviewWordlistTest` ✅ (no new
  clinical-review-gated wording added in this
  branch's commits)
- `NetworkCallsForbiddenTest` ✅ (no new network
  endpoints added)
- 8 new tests from PreHome / JournalStore / CodeRabbit
  fixes
- 1238+ existing tests, all green

## Files touched in this session (2026-08-24)

```
app/src/main/AndroidManifest.xml                    (PreHome HOME intent)
app/src/main/java/org/mindanchor/data/FrictionPrefs.kt   (PreHome toggle)
app/src/main/java/org/mindanchor/launcher/HomeScreen.kt (Healthy defaults route)
app/src/main/java/org/mindanchor/launcher/LauncherViewModel.kt (inSleepWindow tick)
app/src/main/java/org/mindanchor/launcher/PhaseFourCards.kt    (SleepLockCard dwell)
app/src/main/java/org/mindanchor/launcher/DearManDialog.kt    (FilterChip)
app/src/main/java/org/mindanchor/settings/SettingsScreen.kt   (PreHome toggle UI, Healthy defaults button)
app/src/main/java/org/mindanchor/settings/SettingsViewModel.kt (PreHome toggle, SunsetTrial re-entrancy)
app/src/main/java/org/mindanchor/friction/AppWatchService.kt  (ConcurrentHashMap)
app/src/main/java/org/mindanchor/diagnostics/LogScrubber.kt  (comma regex)
app/src/main/java/org/mindanchor/letters/JournalStore.kt    (new)
app/src/main/java/org/mindanchor/prehome/                    (new package)
   - DoomscrollList.kt
   - DoomscrollPromptDialog.kt
   - MorningIntentionRepository.kt
   - PreHomeActivity.kt
app/src/test/java/org/mindanchor/letters/JournalStoreTest.kt        (new)
app/src/test/java/org/mindanchor/prehome/                          (new package)
   - DoomscrollListTest.kt
   - MorningIntentionRepositoryTest.kt
app/src/test/java/org/mindanchor/diagnostics/LogScrubberTest.kt    (comma-body fixture)
app/src/main/res/values/strings.xml  (PreHome toggle strings)
docs/CLINICIAN_PACK.md  (regenerated after strings)
docs/PHASE_4_STATUS.md  (this file)
```

## Commits landed in this session (2026-08-24)

```
0dcba28 feat(launcher+settings): Healthy defaults walkthrough routing (spec Phase 3)
e9c8bab feat(settings): PreHome opt-in toggle UI
eb66bf6 feat(prehome): opt-in toggle + self-skip when disabled
ae6ab60 feat(prehome): PreHome moment-of-pause (spec Phase 1)
b1605b5 build: bump to 0.30.0 (LLM multi-provider picker) + changelog
df56bc2 Merge pull request #38 from sampathmannam/v0.26-prep/phase-1
d2d4a5b feat(letters): JournalStore for protective-layer entries (CodeRabbit PR #38)
38324e5 fix(launcher+settings): CodeRabbit PR #38 follow-ups (dwell + re-entrancy)
fd6a4cf fix(launcher): inSleepWindow re-evaluates on minute tick (CodeRabbit PR #38)
80864a8 feat(llm): production wire-up for multi-provider picker
fff7c5f feat(llm): multi-provider picker + free-tier defaults (v0.26+)
9755d6c fix(launcher+diagnostics): CodeRabbit review fixes (PR #38)
f3a4c68 docs: regenerate CLINICIAN_PACK.md from strings.xml
b46fbc8 feat(settings): Healthy defaults walkthrough (spec Phase 3)
```

The `b1605b5`, `80864a8`, `fff7c5f`, and `df56bc2`
commits are from the user's separate LLM task; they
are the LLM multi-provider picker (Groq / Google AI
Studio / OpenRouter) and the merge of PR #38.
