# Program 2B Physical-Device Validation

Automated acceptance is recorded by `PassivePipelineAcceptanceTest`. The checks below require a real Android phone, Health Connect provider, granted/denied permissions, and elapsed background time. They are pending until a named tester records device evidence; no automated result is presented as physical-device evidence.

## Evidence header

- Tester: Pending
- Device manufacturer/model: Pending
- Android build/API: Pending
- Health Connect provider/version: Pending
- MindAnchor commit/APK: Pending
- Test start/end and local zone: Pending

## Pending checks

- [ ] Pending — grant selected sensor, oxygen, history, and background permissions; capture the system permission screen and the resulting per-family source-read rows.
- [ ] Pending — deny one sensor permission and revoke Usage Access; verify the worker succeeds, records `PERMISSION_DENIED`, and does not write zero-valued features.
- [ ] Pending — leave Health Connect empty for one granted type; verify `SUCCESS` with `recordCount = 0`.
- [ ] Pending — insert or sync a late wearable record after an original run; verify a new backfill revision/decision appears and the earlier row remains unchanged.
- [ ] Pending — allow at least two six-hour periods with MindAnchor closed; record actual start latency, battery state, worker result, p50/p95/p99 observed source lag, longest gap, and backfill count.
- [ ] Pending — cross a local midnight and, where available, a DST transition; verify local-day clipping, wake-date sleep ownership, stored zone/offset, and absolute UTC quarter-hour windows.
- [ ] Pending — restore a v3 encrypted snapshot on a replacement/test phone; verify finalized window/day/decision ids and content hashes match while raw sample values are absent.
- [ ] Pending — export v3 research JSON; verify provenance, coverage, missingness, exclusions, revisions, decisions, versions, and dictionary are present and raw values are absent.

## Evidence attachments

Record artifact paths, timestamps, screenshots, `adb shell dumpsys jobscheduler`/WorkManager diagnostics, exported hashes, and observed deviations here only after each check is performed.
