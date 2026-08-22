# v0.25.1 — 6 visible bugs from the senior-tester audit

**Date**: 2026-08-11
**Status**: ready to ship
**Scope**: bug fixes only. No new features, no schema changes, no migration.

---

## What changed

The senior-tester audit (`SENIOR_TESTER_REPORT_v0.25.1.md`) walked through 90+ features across 27 packages on a real emulator and found 5 visible bugs plus 1 architectural surprise. This release fixes all 6. Each fix is pinned by a finding test so the bug cannot regress silently.

### Bug 1 (P1) — Duplicate time on the batching screen

**Before**: Quiet → Batching showed `"Arrives at 08:00  08:00"` — the time appeared in both the label slot and the value slot of the same row.

**After**: The string resource `batching_time_slot` is just `"Arrives at"` (no `%1$s` placeholder). The call site passes the time only to the value slot. Matches the `sunset_starts` / `sunset_ends` pattern.

**Surface**: `strings.xml`, `SettingsScreen.kt`
**Finding test**: `BatchingTimeSlotFormatFindingTest` (5 tests)

### Bug 2 (P2) — Note timestamps inconsistent between home card and list

**Before**: The home card showed `note.createdAt`. The notes list view showed `note.updatedAt`. Editing a note (no body change, just open + close) bumped `updatedAt` and the timestamp in the list shifted, even though nothing about the note's content had changed.

**After**: The list view also uses `createdAt`. The moment of capture is the more meaningful anchor for a wellness app — re-opening a note should not change when it appears to have been written. The shared formatter is extracted to a single `internal fun formatNoteTimestamp(note, zone, formatter)` so both surfaces stay in lockstep.

**Surface**: `NoteScreen.kt` (list view + new helper)
**Finding test**: `NoteTimestampFormatFindingTest` (5 tests)

### Bug 3 (P3) — "Allow exact timing" looked like text, not a button

**Before**: The row to grant the `SCHEDULE_EXACT_ALARM` permission read as static text. There was no visual hint that tapping it would do anything.

**After**: Vertical padding (4 dp) so the touch target is a proper TextButton, and a `→` chevron appended in code (not in the string resource, so screen readers and right-to-left locales see the right thing).

**Surface**: `SettingsScreen.kt` (exact-alarms TextButton)
**Finding test**: `ExactTimingAffordanceFindingTest` (5 tests)

### Bug 4 (P2) — Save fired silently

**Before**: Tapping Save in the quick-notes composer produced no haptic confirmation. On a launcher that is mostly silent, a user can't tell whether the save registered without leaving the composer to check the list.

**After**: `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.LongPress)` fires AFTER `onSave(draft)` and `draft = ""`. The shortest available tick (~5 ms on most devices), so it's a confirmation pulse, not a buzz.

> **Verification note**: the Android emulator stubs the `vibrator` service (`Can't find service: vibrator`), so this fix is verified by finding test (which pins the call site, the import, and the ordering) and by a Compose UI test on a real device in a later pass. The behavior is conservative — only on the quick-notes save surface, not on every button.

**Surface**: `HomeScreen.kt` (QuickNotesCard Save button)
**Finding test**: `SaveHapticsFindingTest` (5 tests)

### Bug 5 (P3) — System back from a Settings sub-section skipped the index

**Before**: Settings → Quiet → system back. The HomeScreen's `BackHandler` was active while the Settings surface was on top, so back skipped the Settings index and went straight to the home screen.

**After**: HomeScreen's `BackHandler` excludes `LauncherSurface.Settings` so it doesn't steal back. SettingsScreen registers its own `BackHandler(enabled = true) { if (group != null) group = null else onBack() }` — a sub-section collapses to its parent first, then to home, exactly matching the visible back TextButton.

**Surface**: `HomeScreen.kt` (BackHandler predicate), `SettingsScreen.kt` (new BackHandler)
**Finding test**: `SettingsBackFindingTest` (5 tests)

### Bug 6 (P3) — Top-right and bottom-end hit targets clipped the screen edge

**Before**: The "notes" and "history" TextButtons in the top-right corner, and the "settings" TextButton in the bottom-right corner, were aligned `End` with no end padding. On a 1080-wide display, the rightmost text was flush against the bezel — easy to mis-tap, hard to read.

**After**: Both containers get `.padding(end = 8.dp)` after the existing `statusBarsPadding()` / `navigationBarsPadding()`. Additive, doesn't shift the visible label position, gives the touch target breathing room.

**Surface**: `HomeScreen.kt` (TopEnd Column + BottomEnd settings TextButton)
**Finding test**: `EdgePaddingFindingTest` (5 tests)

---

## Architectural surprise (also fixed)

The `NoteTypeFindingTest` shimmer regression tests for v0.25.0's `isClassifying(note, now)` helper had not landed in the v0.25.0 commit. They are folded into v0.25.1 so the classifier's inclusive-on-both-ends window is now properly pinned by JVM unit tests.

**Finding test** (added in v0.25.0, committed in v0.25.1): `NoteTypeFindingTest` `isClassifying` block (5 tests)

---

## What's NOT in v0.25.1

The audit surfaced 8 research-backed improvement suggestions (worry postponement per Borkovec 1994 + Watkins 2008, DST-safe batch reschedule, one-tap "did the report help?" feedback per Linardon 2024, local-only PPG session telemetry, 14-day onboarding recap per Kanfer & Goldstein 1991, letter reader-mode text sizing per WCAG 2.2 SC 1.4.4, "today's one thing" micro-action card per Martell 2013, and haptic-rich captures per Brewster CHI 2007). These are deferred to v0.25.2 — see `SENIOR_TESTER_REPORT_v0.25.1.md` §5.

The 2 remaining edge cases from the audit (multi-back from nested settings, the "edit note doesn't bump updatedAt" semantic in the helper) are also deferred to v0.25.2.

---

## Test summary

- **Unit tests**: 1027 (up from 992 in v0.25.0; +35 from 6 new finding tests + 5 folded-in classifier tests, minus any renames)
- **Detekt**: clean
- **`assembleDebug` + `assembleRelease` + `testDebugUnitTest`**: all pass
- **Manual smoke (emulator-5554)**: 5/6 fixes visually confirmed; Bug 4 haptics requires a real device (vibrator is stubbed on emulator)

---

## Why this release is its own version

Each of the 6 bugs was a polish/safety defect, not a new feature. Bumping to v0.25.1 (rather than rolling into v0.25.0) makes the fix surface auditable: the diff between v0.25.0 and v0.25.1 is exactly 6 small, named, tested, research-backed bug fixes. Users on v0.25.0 should upgrade.
