## v0.22.1 — silent-toggle rollback on POST_NOTIFICATIONS deny

A senior-tester pass on the v0.22.0 emulator build (after the
emulator shipped clean with no P0/P1) found two P2 UX bugs in
`SettingsScreen.kt` and fixes them in v0.22.1. No new features.

### What changed

**Permission launcher rolls back the in-app toggle on denial.**
The `permissionLauncher` callback in `SettingsScreen.kt` was an
empty `{}` lambda — it swallowed the `granted` argument. So when
the user toggled a feature on that needs `POST_NOTIFICATIONS` and
then denied the permission, the in-app toggle stayed ON with no
notifications actually delivered and no feedback explaining why.

Two features were affected:

  1. **"Batch notifications"** in the Quiet group. The user
     enables batching, the launcher asks for `POST_NOTIFICATIONS`,
     the user denies, the in-app toggle stays ON. Batched
     notifications never appear because the system permission
     is denied, but the UI says the feature is on.

  2. **"Ask me how I am"** (EMA) in the Measuring group. Same
     shape. The user enables check-in prompts, the launcher asks
     for `POST_NOTIFICATIONS`, the user denies, the in-app toggle
     stays ON. No check-in prompts ever fire.

The fix introduces a `pendingRollback` state variable that
captures a ViewModel setter closure for the duration of the
permission request. The launcher callback now inspects the
`granted` argument and invokes the rollback on a deny, then
clears the variable so the next toggle tap doesn't replay a stale
closure. The toggles also clear `pendingRollback` on an explicit
OFF tap, so a deny from a previous ON-tap can't accidentally fire
on a later OFF.

### Findings (this version)

P0: none.

P1: none.

P2: none new. The two P2 from v0.22.0 (the chronotype-persona
mapping in code, the optimizer-vs-chronotype 30-minute
consistency note) are unchanged.

### Acceptance (gates)

- `./gradlew :app:detekt :app:testDebugUnitTest :app:testReleaseUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease` — clean
- 904 unit tests, 0 failures (was 899; +5 from the new finding-test class `PostNotificationsRollbackTest`)
- Senior-tester pass on emulator (emulator-5554) — both toggles verified in both directions:
  - Deny permission → toggle rolls back to OFF (was: stuck ON)
  - Allow permission → toggle stays ON, configuration expands

### Verifying the fix

Open the app → Settings → Measuring → "Ask me how I am". Tap
the toggle. The system permission dialog appears. Tap "Don't
allow". The toggle rolls back to OFF. Now tap again — same flow.
Now tap "Allow" instead. The toggle stays ON and the explanation
text remains visible. The EMA scheduler only arms the prompts
when the toggle is actually on AND the system permission is
granted, so this matches what the user sees.

Same test for Settings → Quiet → "Batch notifications".

```
sha256sum app-release.apk
# d8cfc29b1e3d8ac833cb743ac89cbe3c6f1d2a3089fdedc39a59f5229277e9e7
```

### Files

- SHA-256: D8CFC29B1E3D8AC833CB743AC89CBE3C6F1D2A3089FDEDC39A59F5229277E9E7
- Size: 10.6 MB
- Source change: `app/src/main/java/org/mindanchor/settings/SettingsScreen.kt` — `pendingRollback` state, updated launcher callback, 2 toggle-side assignments (+25 / -1 lines)
- Test addition: `app/src/test/java/org/mindanchor/settings/PostNotificationsRollbackTest.kt` — 5 finding tests pinning the file's shape

### Why this was not caught in v0.22.0 testing

The v0.22.0 senior-tester pass covered the home flow, friction
gate, pulse, reading, notes, settings persistence, chronotype
defaults, customized-window guard, support, and the heart-rhythm
permission path. It did not exercise the two Settings toggles
that internally depend on `POST_NOTIFICATIONS`, because the
batching and EMA paths both need a *user-deny* interaction to
reveal the bug — granting permission makes the toggle ON with
no problem. The emulator's first run was done with the
permission already granted by `adb shell pm grant` for unrelated
testing, so the deny path was never traversed.

The fix adds the deny path to the standard pre-release testing
checklist alongside the home flow and the friction gate.
