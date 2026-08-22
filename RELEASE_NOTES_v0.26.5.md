# v0.26.5 — "I'm up late tonight" 4th option on the 2am shell

**Tag:** `v0.26.5`
**versionCode:** 47 → 48
**versionName:** `0.26.4` → `0.26.5`
**Branch:** `work/v0.21.0-10of10`

## What this release does

The 2am "Now what?" shell covered home between 00:00 and 05:00. Before v0.26.5, the only paths out were the 3 main options (sleep / ground / talk) and "wait until 5 AM". The `okAtNight` flag in BpdProfile was already wired through DataStore → `NowWhatHeuristic` → `isTwoAmWindow`, and the Settings → PAUSES → BPD profile checkbox was already there to toggle it — but a user stuck at 1 AM with the shell covering home had no way to know that an opt-in in Settings would unblock them.

v0.26.5 adds a **4th option on the shell itself**: "I'm up late tonight". Tapping it sets `okAtNight = true` in BpdProfile via the existing DataStore path; the next composition reads the new value through `collectAsStateWithLifecycle`, `isTwoAmWindow` recomputes to `false`, and the shell disappears. Persistent: the user reverts via Settings → PAUSES → BPD profile → uncheck "I'm OK at night".

The 4th option is visually subordinate — a plain `TextButton` rather than a `NowWhatRow` Surface — so the calm "pick one" framing of the 3 main options is preserved. The 4th option reads as a meta-toggle, not a 4th "what do I need right now" choice.

## Why this release

- **Unblocks late-night verification on the user's phone.** The 2am shell was the only thing standing between the user and a manual smoke of the v0.26.4 Right now section (BPD entry points on home). A long-press on the shell's title didn't dismiss; tapping "I want to sleep" re-set `surface = Home` but the shell re-shows because `isTwoAmWindow` is still true. The 4th option makes the dismiss explicit and discoverable.
- **Real user feature, not a debug escape hatch.** Night-shift workers, irregular sleep schedules, travelers in different time zones, and people testing the app at 1 AM all benefit. The label "I'm up late tonight" is honest about scope, the Settings checkbox ("I'm OK at night") is the canonical way to revert, and the persistence is the same shape as every other BpdProfile flag.
- **Reuses the existing wiring.** No new pref key, no new flag, no new state class. `BpdProfile.okAtNight` was already there; v0.26.5 only adds the affordance to set it from the shell.

## What changed

| File | Change |
| --- | --- |
| `app/src/main/java/org/mindanchor/launcher/NowWhatShell.kt` | Added 4th `onStayUp: () -> Unit` callback + 4th `TextButton` reading `R.string.now_what_stay_up` |
| `app/src/main/java/org/mindanchor/launcher/HomeScreen.kt` | Added `val bpdProfileScope = rememberCoroutineScope()` near the BpdProfilePrefs setup; wired `onStayUp` to `bpdProfileScope.launch { bpdProfilePrefs.update(bpdProfile.copy(okAtNight = true)) }` |
| `app/src/main/res/values/strings.xml` | Added `<string name="now_what_stay_up">I\'m up late tonight</string>` |
| `app/src/main/res/values-ta/strings.xml` | Added the same key (English placeholder, pending translation) |
| `app/build.gradle.kts` | `versionCode` 47 → 48, `versionName` "0.26.4" → "0.26.5" |
| `app/src/test/java/org/mindanchor/launcher/NowWhatStayUpFindingTest.kt` | **NEW** — 6 FindingTests pinning the 4-callback signature, the stay-up label, the TextButton wiring, the HomeScreen `bpdProfileScope.launch` + `update(... okAtNight = true)` pattern, and the dual-locale string key |

## Tests

- **6 new FindingTests** in `NowWhatStayUpFindingTest.kt`. All 6 pass.
- **Full suite:** 1443 (v0.26.4) + 6 = **1449/0** after v0.26.5 (target).

## Detekt

Clean.

## End-to-end verification (planned, on phone)

The user connected the Motorola signature (ZD2232FCR5, API 37, Android 17). v0.26.4 is installed; the 2am shell is showing (phone time 1:11 AM IST, within 00:00–05:00). v0.26.5 will be:
1. `adb install -r` over v0.26.4
2. Launch, see shell
3. Tap the 4th option ("I'm up late tonight")
4. Verify shell disappears, home surface renders with the v0.26.4 Right now section
5. Tap "What just happened?" → ChainCaptureActivity
6. Back → "Which part is loud?" → IfsPickerActivity
7. Back → "Export for my therapist" → ExportActivity
8. Verify no FATAL on any path

## Why now

The 2am shell was the only thing standing between the user and a real-device smoke of v0.26.4. The fix is small, the wire is reuse-only (no new pref key, no new flag, no new state class), and the affordance is a real user feature for non-standard schedules — not just a debug hatch.

## Files NOT changed

- `AndroidManifest.xml` — no new activity, service, or permission
- `BpdProfile.kt` / `BpdProfilePrefs.kt` — `okAtNight` was already there
- `SettingsScreen.kt` — the "I'm OK at night" checkbox is the canonical revert path
- `NowWhatHeuristic.kt` — `QUIET_START_HOUR = 0`, `QUIET_END_HOUR = 5` unchanged; `okAtNight = true` already short-circuits to `false`
- `Channels.kt` — no new channel
- Any other release-blocking change

## Open questions

- **Tamil placeholder.** The `now_what_stay_up` Tamil value is the English string. Same state as every other `values-ta/strings.xml` entry — translator is a v0.26.5+ follow-up.
- **Persistent vs. one-shot.** v0.26.5 makes the toggle persistent (same as the Settings checkbox). A user who taps "I'm up late tonight" at 1 AM and forgets to revert will not see the 2am shell on subsequent nights. The Settings revert is one extra step; an automatic "reset at 5 AM" was considered and rejected for v0.26.5 (would require a WorkManager job or an AlarmManager receiver for a one-bit flag — not worth the complexity).
- **`LICENSE` change review** (v0.25.19). Still pending. Substantive change to redistribution terms — Stream 4 changed GPL v3 → Apache 2.0. v0.26.5 does not touch the LICENSE.
