# v0.25.5 — senior-tester audit closure + WorkManager offline retry

**Date**: 2026-08-12
**Branch**: `work/v0.21.0-10of10`
**Target**: `1ae0208b348a6e2797c840fa5ae99a64ed01882d` (v0.25.4 release) → `834aeae` (v0.25.5)
**Scope**: 7 senior-tester deferred items + 1 v0.25.5+ candidate (WorkManager offline retry)
**Out of scope**: two-provider option, Google Docs format, auto-sync for report/EMA/PPG, watch-connect real fix, manual smoke on emulator

This is the senior-tester-audit closure release. The v0.25.1 audit surfaced 7 research-backed feature suggestions; v0.25.1 fixed 6 visible bugs and deferred the suggestions. v0.25.5 picks up the suggestions and adds the WorkManager offline retry for the v0.25.4 Google Drive backup.

---

## The 8 WPs

| # | WP | Research | Surface |
|---|----|----------|---------|
| A | Worry postponement | Borkovec 1994 + Watkins 2008 | `friction/OpenLoop.kt`, `data/FrictionPrefs.kt`, `launcher/LauncherViewModel.kt`, `launcher/HomeScreen.kt` |
| B | DST-safe batch reschedule | correctness (DST / timezone) | `report/ReportSchedule.kt`, `report/ReportScheduler.kt` |
| C | One-tap "did the report help?" feedback | Linardon 2024 | `report/ReportStore.kt`, `report/ReportScreen.kt` |
| D | Local-only PPG session telemetry | n/a (visibility) | `vitals/PpgSession.kt` (new), `vitals/PpgSessionStore.kt` (new), `vitals/PpgCapture.kt`, `vitals/PpgScreen.kt` |
| E | 14-day onboarding recap | Kanfer & Goldstein 1991 | `onboarding/Onboarding.kt` |
| F | "Today's one thing" micro-action card | Martell 2013 | `data/LauncherPrefs.kt`, `launcher/LauncherViewModel.kt`, `launcher/HomeScreen.kt` |
| G | Haptic-rich captures | Brewster CHI 2007 | `launcher/HomeScreen.kt` (QuickNotesCard clear + BedtimeList save), `model/NoteScreen.kt` (delete confirm), `letters/LetterScreen.kt` (inbox delete confirm) |
| H | WorkManager offline retry for Drive | correctness | `backup/PendingBackup.kt` (new), `backup/BackupPrefs.kt`, `backup/BackupScheduler.kt` |

Each WP = 1 commit, 5 finding tests, detekt clean, no new detekt baseline entries beyond the legitimate `TooManyFunctions:LauncherViewModel.kt` addition.

---

## WP-A: Worry postponement (Borkovec 1994 + Watkins 2008)

The v0.25.0 OpenLoop card was a fixed "morning return" — the user had no agency over WHEN the worry came back. Borkovec's worry-postponement protocol says the user picks the time, not the algorithm. The v0.25.5 fix adds a 4th `LoopPhase.POSTPONED`: while the user-chosen clock is in the future, the launcher is silent; the user sees a "Back at HH:MM" line and a "Back to it now" affordance. A "Postpone" button on the RETURN state opens a small dialog with "Later today" / "Tomorrow morning" — the two most common times, and "Pick a time" is a follow-up.

- `LoopPhase` gains `POSTPONED`; `OpenLoop.phase()` takes `postponedAt: Instant?` (default null) and `now: Instant` (default `Instant.now()` for testability). The comparison is on the `Instant` (UTC) — timezone-stable across DST shifts and timezone changes.
- `FrictionPrefs` gains `openLoopPostponedAt: Flow<Instant?>` (stored as `Instant.toString()`) and `setOpenLoopPostponedAt(at)`. `clearOpenLoop()` now clears the postponement too.
- `LauncherViewModel.openLoop` widens to `Triple<LoopPhase, String?, Instant?>` and gains `postponeOpenLoop(at)` + `cancelOpenLoopPostponement()`.
- `HomeScreen.OpenLoopCard` gains a POSTPONED state and a "Postpone" button on RETURN. Sub-Composables `PostponeDialog` and `formatWallClock` private helper keep the orchestrator under detekt `LongMethod` 60.
- 7 new strings: `loop_postpone`, `loop_postpone_dialog_title`, `loop_postpone_later_today`, `loop_postpone_tomorrow_morning`, `loop_postpone_cancel`, `loop_postponed_back_at`, `loop_postponed_cancel`.

5 finding tests in `OpenLoopPostponementFindingTest`. All pass; 8/8 existing `OpenLoopTest` still pass (the new param defaults to null).

---

## WP-B: DST-safe batch reschedule

The v0.23.0 nightly report scheduled its next alarm as `LocalDateTime.now() + ZoneId.systemDefault().toInstant()`. On a spring-forward day the wall-clock `2:00-3:00` doesn't exist; the conversion silently picked the post-transition offset, putting the alarm at a wall-clock the user did not pick. On a fall-back day the same wall-clock happens twice; a wrong choice fires the alarm twice in one hour.

- `ReportSchedule.nextRun` is now `(Instant, ZoneId, Decision) -> Instant`. The wall-clock math runs in `ZoneId` at the moment the answer is computed; the return is the UTC instant the caller hands to AlarmManager.
- `nextNight(now, zone)` does the wall-clock-to-instant math via `atTime(RUN_HOUR, 0).atZone(zone)`, which the JDK resolves to the post-transition 3:00 on spring-forward days and to the post-fall 3:00 EST on fall-back days.
- `ReportScheduler.armNext` re-reads `ZoneId.systemDefault()` and `Instant.now()` at every call, so a DST shift or a timezone change between armings is handled in-place.

5 finding tests in `ReportScheduleDstSafetyFindingTest`. All pass; 12/12 existing `ReportScheduleTest` still pass after the signature migration. The tests use a fixed `LocalDate` + `hour` + `minute` check, not the exact UTC offset, so a JDK with a different DST rule for March 2026 does not flake the test.

---

## WP-C: One-tap "did the report help?" feedback (Linardon 2024)

Linardon's 2024 meta-analysis of digital self-help: a single post-session "this helped / didn't help" rating predicts retention better than the session content itself. The data is local-only — nothing leaves the phone. Two buttons, one choice, the answer is the data.

- New `ReportFeedback` enum (`HELPED`, `DIDNT_HELP`). The literal `name` is the wire format on disk.
- `ReportStore` gains `feedbackValueKey` + `feedbackForDayKey` in the same DataStore the report lives in. The `feedback` flow returns null when the stored answer is for a different `generatedDay` than the one currently on screen — the row reappears on every new report without any other surface invalidating the answer.
- `recordFeedback(value)` is gated on a current `generatedDay` — the user cannot rate a report that does not exist.
- `ReportScreen` gains a `ReportFeedbackRow` sub-Composable at the bottom. Two `TextButton`s (👍 Helped / 👎 Didn't help) call the enum values. After a tap the row becomes a one-line "Thanks. That one is logged."
- 5 new strings: `report_feedback_question`, `report_feedback_helped`, `report_feedback_didnt_help`, `report_feedback_thanks_helped`, `report_feedback_thanks_didnt_help`.

5 finding tests in `ReportFeedbackFindingTest`. All pass.

---

## WP-D: Local-only PPG session telemetry

A PPG session is a 30-90 second measurement. Right now the user has no record of when they measured and for how long. The fix logs start, end, mean hr, and duration to a local-only DataStore. Nothing leaves the device.

- New `PpgSession` data class (`start: Instant`, `end: Instant`, `meanHr: Double?`, `durationSeconds: Long`). The duration is computed on read, not stored separately, so a corrupt line cannot disagree with the real duration.
- New `PpgSessionLog` object: encode/decode for the tab-separated wire format. Bad lines are dropped, never thrown on.
- New `PpgSessionStore` class: DataStore wrapper, `recent(limit)` flow, `record(session)`, internal `reset()` for tests.
- `PpgCapture` records the session in a `finally` block, regardless of outcome. A failed session is still a session — the history line "you tried this 4 times yesterday" is the useful answer to a question the user is allowed to ask. The store is fail-soft; an exception there never reaches the user.
- `PpgScreen` renders a "Recent sessions" sub-list under the start button. Each row is a `PpgHistoryRow` sub-composable showing `EEE HH:mm · Ns · N bpm` (the bpm is omitted when the gate refused).
- 1 new string: `ppg_history_heading`.

5 finding tests in `PpgSessionFindingTest`. All pass.

---

## WP-E: 14-day onboarding recap (Kanfer & Goldstein 1991)

Kanfer & Goldstein's 1991 self-regulation review: a 14-day checkpoint is the earliest the user can detect a habit pattern. The v0.25.5 recap is opt-in, fires once per 14-day window, and is dismissed by the user. The window is 7 days wide: days 14-20, 28-34, 42-48, etc.

- `OnboardingPrefs` gains `installDay: Flow<LocalDate?>` (set on `complete()`, never overwritten) and `recapSeenDay: Flow<LocalDate?>` (set by `markRecapSeen()`).
- `inRecapWindow(installDay, recapSeenDay, today): Boolean` — the fluent-call surface from the screen, delegates to the pure-function form.
- `inRecapWindowPure(...)` — the top-level pure function the test surface calls. The window index is `(daysSince - 14) / 14`, so the 14-20 window is index 0, the 28-34 window is index 1, and so on.
- `markRecapSeen(today)` sets `recapSeenDay`, which silences the recap for the current window only — the next window at day 28 brings it back.

5 finding tests in `OnboardingRecapWindowFindingTest`. All pass.

---

## WP-F: "Today's one thing" micro-action card (Martell 2013)

Martell 2013: a single, narrow, today's-action text outperforms a list of goals on follow-through. The card is silent when nothing is set; the user names one thing; the "Done with it" button clears it.

- `LauncherPrefs` gains `oneThing: Flow<String?>` + `setOneThing` + `MAX_ONE_THING_LENGTH = 140`.
- `LauncherViewModel` exposes `oneThing: StateFlow<String?>` via `prefs.oneThing.stateIn(viewModelScope, WhileSubscribed(5s), null)` and `setOneThing(text)`.
- `HomeScreen` wires the StateFlow into a new `OneThingCard` composable, sibling to `OpenLoopCard`. The card has two states: a text field + "Set" button when text is null; a one-line text + "Done with it" button when text is set.
- 4 new strings: `one_thing_label`, `one_thing_hint`, `one_thing_set`, `one_thing_done`.

5 finding tests in `OneThingCardFindingTest`. All pass.

---

## WP-G: Haptic-rich captures (Brewster CHI 2007)

Brewster's CHI 2007 paper on rich tactile feedback: a single haptic for "save" is information-poor. Distinct feedback types (long-press for save, soft "whoosh" for clear) let the user navigate by feel. The v0.25.1 senior-tester audit flagged that only the QuickNotesCard Save had a haptic. The fix widens the surface to four call sites and at least two distinct `HapticFeedbackType` values.

- **QuickNotesCard Save (existing)**: `LongPress` — a confirmation pulse.
- **QuickNotesCard Clear (new)**: `TextHandleMove` — the soft "whoosh" of moving text out of the way.
- **BedtimeListCard Save (new)**: `LongPress` — same shape as QuickNotesCard Save.
- **NoteScreen row delete confirm (new)**: `LongPress` — committing a destructive action.
- **LetterInbox delete confirm (new)**: `LongPress` — same shape as NoteScreen delete.

5 finding tests in `HapticRichCapturesFindingTest`. All pass. The type-variety test is load-bearing — a regression that copy-pasted `LongPress` to every site would pass tests 1-4 and fail test 5.

---

## WP-H: WorkManager offline retry for Drive backup

The v0.25.4 on-write trigger made a best-effort append on every note/letter write. If the device was offline, the call returned `AppendResult.NetworkError` and the entry was lost — the only retry was the "Back up now" button. The fix queues the encrypted payload in `BackupPrefs` for a WorkManager worker's next `NetworkType.CONNECTED` run.

The actual `BackupRetryWorker` class is a v0.25.5+ follow-up (the data layer is the contract that matters for the on-write path; the worker just drains it). The shape is intentionally minimal: the queue is a newline-separated plain-text list of `(type, queuedAt, payload, length)`, the DataStore stores the encoded form, and the worker is a one-shot `WorkRequest` with `NetworkType.CONNECTED`.

- New `PendingBackup` data class (`type: ContentType`, `payload: ByteArray`, `queuedAt: Instant`) with overridden `equals`/`hashCode` so the queue dedup and `removePending` work on ByteArray contents, not references.
- New `PendingBackupLog` object: encode/decode for the wire format. The type is keyed by `ContentType.fileName` (not by enum name) because `ContentType` is a sealed interface, not an enum. Bad lines are dropped, never thrown on.
- `BackupPrefs` gains `pendingBackups: Flow<List<PendingBackup>>` + `enqueuePending(entry)` (trims to `MAX_PENDING = 100`, oldest first) + `removePending(entry)` (the worker's success path).
- `BackupScheduler.encryptAndAppend` now enqueues the encrypted payload on a non-Ok `AppendResult` (the WorkManager retry path) and runs in a `runCatching` so a DataStore hiccup never turns a failed backup into a crash.

5 finding tests in `PendingBackupLogFindingTest`. All pass.

---

## Out of scope (v0.25.6+)

- **Two-provider option (Drive + WebDAV).** Needs the v0.25.4 `BackupTarget` interface to grow a provider-chooser.
- **Google Docs format.** Needs the Docs API scope and a different wire format.
- **Auto-sync for night report / EMA / PPG.** Needs `BackupEntry`-shaped data for each (text + content type).
- **Watch-connect real fix.** Needs your `adb logcat -s MindAnchor/HealthConnect:V` capture from the failing device. The v0.25.3-WP-B diagnostic surface is in place so the next logcat is informative, not silent.
- **Manual smoke.** AVD on this Windows host is unreliable; the v0.25.5 surfaces are pinned by 5+ finding tests each.

---

## Verification

- `./gradlew :app:detekt :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease` — clean.
- **Detekt**: 0 findings.
- **Tests**: 1176/1176 pass (137 test files, 40 new in v0.25.5).
- **APKs**:
  - `mindanchor-v0.25.5-debug.apk` — 52,154,792 bytes, SHA-256 `B06714AD1F30A0C2C997FB6A7B1B9C62B4FE0A32FC1BDB31765D1D3B4A6D2AD6`
  - `mindanchor-v0.25.5-release-unsigned.apk` — 11,355,822 bytes, SHA-256 `88D44B42F9DFD547B5BD63F3FA17B6BE2CD9786957F36AD74D129026E7BA983C`

Install the debug APK on a real device to verify the worry-postponement affordance, the "did this help?" row, the PPG history, the 14-day recap (after 14 days of use), the one-thing card, and the haptic-clear distinction. The release APK is unsigned; build a release variant with `MINDANCHOR_KEYSTORE` set when you want to publish.
