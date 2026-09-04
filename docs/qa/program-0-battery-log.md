# Program 0 battery log (template)

## Standing instruction — read before filling in a single field

**Do not invent a battery threshold after seeing the result.** Record the
observation only — what `dumpsys batterystats` actually showed, what
actually ran, what actually uploaded. Defining a formal "acceptable"
battery-cost threshold is Program 2 sensor-work territory, not this
task's; do not retroactively declare a number "the bar" just because it's
the number this run happened to produce.

## What this document is

The record of a real 24-hour on-device battery and background-work audit,
per `docs/qa/program-0-continuity-runbook.md` Step 6. **This is a
template.** Every field below is blank or marked "pending physical
execution" — none of it is a real observation, and this environment (one
Android emulator, no physical device, no ability to hold a 24-hour
wall-clock window open) cannot produce one. Whoever runs the runbook
fills this in with real values as they go.

---

## Device and environment

| Field | Value |
|---|---|
| Device model | pending physical execution |
| Android version | pending physical execution |
| App commit / version | pending physical execution |
| Battery-optimization state (app on the OS's restricted / unrestricted / default list) | pending physical execution |
| Network conditions during the window (Wi-Fi, mobile data, or mixed — note any switches) | pending physical execution |
| Window start (date / time) | pending physical execution |
| Window end (date / time) | pending physical execution — must be a real 24 hours later, not compressed |

## `dumpsys batterystats` capture

| Field | Value |
|---|---|
| Baseline capture command / timestamp (`adb shell dumpsys batterystats --reset`) | pending physical execution |
| End-of-window capture command / timestamp (`adb shell dumpsys batterystats > after.txt`) | pending physical execution |
| Estimated battery cost attributable to the app (from the "after" dump's per-app breakdown) | pending physical execution — record the raw observed number; do not round it into a claimed threshold |

## Background work

| Field | Value |
|---|---|
| Checkpoint (`CheckpointBackupWorker`) run count over the window | pending physical execution |
| Checkpoint runs: succeeded / failed | pending physical execution |
| Nightly snapshot (`NightlySnapshotWorker`, targeted local 02:00) completed? | pending physical execution |
| Nightly snapshot: succeeded / failed / deferred by `setRequiresBatteryNotLow` | pending physical execution |
| Total bytes uploaded to Drive over the window | pending physical execution |

## Wake-lock / exact-alarm / foreground-service / permission audit

Continuity backup is implemented entirely as ordinary WorkManager jobs
with `NetworkType.CONNECTED` (checkpoint) and
`NetworkType.CONNECTED` + `setRequiresBatteryNotLow` (nightly) constraints
— see `ContinuityWorkScheduler.kt`. There is no `setExactAndAllowWhileIdle`
call and no held wake lock anywhere in the continuity subsystem's source.
This section is the real-device confirmation of that, not a place to
document a newly-discovered wake lock as if it were expected.

| Field | Value |
|---|---|
| Partial wake locks held by the app (`adb shell dumpsys power` / `adb shell dumpsys batterystats` wake-lock section) | pending physical execution — expected: none held by the continuity subsystem |
| Exact alarms scheduled (`adb shell dumpsys alarm`) | pending physical execution — expected: none from continuity backup |
| Foreground services running during the window | pending physical execution |
| Permissions actually exercised during the window (cross-check against the manifest) | pending physical execution |

## Observations (facts only, no threshold judgment)

pending physical execution
