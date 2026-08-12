# v0.25.3 — backup KDoc + watch-connect diagnostic + per-letter read flag + report reader-mode

A focused iteration that addresses three user-answered pending decisions and one forward-compat item from the v0.25.2 release notes. The most visible user-facing change is the per-letter `read` flag: the "Open inbox (N)" badge in Settings → Reading now decrements when you open a letter, instead of the v0.25.2 install-date stand-in.

## What's in the box

### WP-A: BackupCodec.kt KDoc update

- The stale `BackupCodec.kt:16` "Cloud backup is refused outright" stance is gone. The KDoc now acknowledges the v0.23.0 opt-in WebDAV bridge (`WebDavBackupTarget.kt` + `EncryptedBackupCodec.kt`, AES-256-GCM, the user's own bucket, HTTPS-only) as the project's second outbound channel.
- The local file picker remains the default save path; nothing about the wire format, the encryption, or the user-facing save flow changed.

### WP-B: Watch-connect diagnostic surface (real fix pending `adb logcat`)

- The "Connect to your watch" button now has visible-failure handling: a launch dispatch failure (ActivityNotFoundException, SecurityException, send-cancel) is captured in a `runCatching` block and surfaced to the user as a colored `Text` below the button.
- Logcat-tagged tracing at the `MindAnchor/HealthConnect` tag wraps the launch and the result handler. A user with a failing device can now run `adb logcat -s MindAnchor/HealthConnect:V`, tap the button, and capture the failure mode.
- **The actual root-cause fix is still pending** — it requires `adb logcat` from the failing device. With the diagnostic in place, the next user-side logcat will be informative, not silent.

### WP-C: Per-letter `read` flag

- `LetterStore.readDates: Flow<Set<LocalDate>>` — the dates of letters the user has opened. New `stringSetPreferencesKey` in the same DataStore (`letters_read_dates`), so the existing `LetterLedger` wire format (`date<TAB>body<NEWLINE>` per letter) is unchanged and v0.25.2 installs read their data after the upgrade.
- `LetterStore.setRead(date, read)` — mark a date as read or unread. Idempotent.
- `SettingsViewModel.unreadLetterCount` now derives from `combine(letterStore.letters, letterStore.readDates) { ... }` instead of the v0.25.2 install-date stand-in. The Settings "Open inbox (N)" badge decrements the moment a row is tapped.
- `HomeScreen.LetterSurface.onSelect` calls `letterStore.setRead(date, true)` on every row tap. Mark-as-read happens on tap, not on "scrolled to the end" — the mark is a Set, idempotent on re-tap, and tracks intent (user opened the letter) rather than engagement (user scrolled past the body).
- The unused `installDate()` helper and its `INSTALL_PREFS` / `INSTALL_KEY` constants are removed (detekt's `UnusedPrivateMember`; the helper is no longer called and is not a future-use refactor that the codebase should carry).
- 4 new finding tests in `LetterReadFlagFindingTest` (file-shape) and 5 round-trip tests in `LetterReadStoreRoundTripFindingTest` (Robolectric, follows the v0.25.2 Task 13 pattern with `internal suspend fun reset()` on the store for `@Before` isolation).

### WP-D: Report reader-mode (A- / A / A+)

- The `ReportScreen` header now has the same A- / A / A+ segmented control the letter reader uses. Both surfaces read and write the same `ReaderPrefs` DataStore, so the size picked in one place carries to the other (LARGE in the letter reader is LARGE in the report).
- `ReportScreen` gains `size: ReadingSize = ReadingSize.MEDIUM` and `onSetSize: (ReadingSize) -> Unit = {}` parameters with safe defaults. The existing public wrapper (with the Context read) is updated to thread the size through.
- 3 new finding tests in `ReportScreenReadingSizeFindingTest` (file-shape).
- The 3 existing `CyclomaticComplexMethod` / `FunctionNaming` / `LongMethod` detekt baseline entries for the testable `ReportScreen` overload are re-keyed to the new signature (the signature legitimately changed; the rule still flags the function — baseline entries are not new findings).

## What changed at the file level

- **2 modified source files**: `LetterStore.kt` (`readDates` flow + `setRead` + `reset` + cleanup of the unused `installDate` consumer), `SettingsViewModel.kt` (`unreadLetterCount` rewired to `combine`, unused `installDate` removed).
- **2 modified source files for the wire-through**: `HomeScreen.kt` (onSelect calls `setRead`), `ReportScreen.kt` (header A- / A / A+ control + size/onSetSize params).
- **3 new test files**: `LetterReadFlagFindingTest`, `LetterReadStoreRoundTripFindingTest`, `ReportScreenReadingSizeFindingTest`.
- **3 baseline entries re-keyed** (not added): `CyclomaticComplexMethod` / `FunctionNaming` / `LongMethod` for the testable `ReportScreen` overload. Same rule, new signature.
- **1 plan file**: `docs/superpowers/plans/2026-08-12-v0.25.3-backup-kdoc-and-watch-connect.md`.
- **No new source files**.
- **No new string resources** (the A- / A / A+ labels are locale-safe literals; the visible launch-error text reuses the v0.25.3-WP-B KDoc wording without needing a new string).

## Evidence

- **1120 unit tests pass** (`./gradlew :app:testDebugUnitTest --rerun-tasks` → `BUILD SUCCESSFUL`, 1120 tests, 0 failures, 0 errors, 0 skipped).
- **Detekt clean** (`./gradlew :app:detekt` → `BUILD SUCCESSFUL`; no new findings, no baseline additions, only re-keyings).
- **Debug APK**: SHA-256 `4689F1171F48882EDF73631674297580CDF591221ACDD3C732A52CAE32F4D8B8` (47.0 MB).
- **Release-unsigned APK**: SHA-256 `63548549CD0EB155C9593E9FF36F2AE991509EE9A243F1C3544D83920EECE575` (10.1 MB).
- **4 / 4 v0.25.3 work packages shipped** (WP-A, WP-B diagnostic, WP-C, WP-D). WP-B's real fix is gated on user logcat.

## Forward-compat (for v0.25.4+)

- **WP-B-real**: actual root-cause fix for the watch-connect failure on the user's device, pending the `adb logcat` capture described above.
- **Per-letter `read` UI differentiation**: dim the body of read letters in the inbox, add a marker dot, etc. The data is correct; the row visual is a polish.
- **ReaderPrefs reuse for the PPG / CheckIn screens** (any other long-form copy) — same pattern as the report, deferred.
- **v0.25.1 senior-tester audit deferred items**: worry postponement (Borkovec 1994, Watkins 2008), DST-safe batch reschedule, one-tap "did the report help?" feedback (Linardon 2024), local-only PPG session telemetry, 14-day onboarding recap (Kanfer & Goldstein 1991), "today's one thing" micro-action card (Martell 2013), haptic-rich captures (Brewster CHI 2007).
- **Google Drive backup (v0.25.3 message 2)**: a per-document "all my notes" backup that auto-syncs is feasible with the Google Drive API, but is a different architectural direction from the v0.23.0 WebDAV bridge (Google Sign-In + Drive API + a third-party dependency). Captured for a v0.25.4+ design discussion; not part of v0.25.3.
