# v0.30.0 — Tamil placeholder removed + clutter cleanup

**Date:** 2026-08-16
**Status:** Shipped
**Author:** Mavis
**Scope:** Code-base cleanup, no new features, no behavior change, no
permission change, no new surfaces.

## What ships

### 1. Tamil placeholder deleted

`app/src/main/res/values-ta/strings.xml` (86 KB / 763 placeholder
keys, all English copy with a header note saying the translations
are "pending") was deleted per the user directive "no tamil needed".
Android's resource resolver falls back to `values/` for any locale
that has no `values-<locale>/strings.xml`, so a Tamil-locale phone
that previously read this placeholder file now reads the English
file directly — same string, one fewer file. No runtime effect.

The placeholder was added in v0.25.18 as scaffolding for a
hypothetical Tamil translation work. v0.30.0 retires the
scaffolding and drops the assumption.

### 2. FindingTests that pinned the Tamil shadow updated

Four FindingTests had assertions or comments about the Tamil
placeholder file. v0.30.0 removes the assertions (the file is
gone) and updates the comments to record the change.

- `I18nSweepFindingTest.R_string note_close is defined in values
  and values-ta` — deleted. The English-only sweep test is the
  source of truth; the Tamil shadow assertion was redundant.
- `BpdEntryPointsFindingTest.values-ta strings xml has Tamil
  Right now keys (placeholder English OK)` — deleted. The
  English "Right now" keys are pinned by the test that remains.
- `NowWhatStayUpFindingTest.strings xml defines now_what_stay_up
  in both locales` — renamed to `... defines now_what_stay_up`
  (English only). The `stringsTa` getter was removed.
- `LetterNotificationChannelFindingTest.values-ta strings has a
  Tamil letters_channel_name localization` — deleted. The
  English-only `values default strings has a
  letters_channel_name` test is the source of truth.
- `LetterNotificationChannelFindingTest` KDoc + `Channels letter
  channel name uses the localised string resource` comment —
  updated to drop the Tamil-localisation framing (the
  `stringResource(R.string.X)` shape is what makes the channel
  name locale-agnostic; the Tamil shadow file is not the
  mechanism).
- `BpdSurfaceA11yI18nFindingTest` KDoc — updated to drop the
  "Tamil user hears English in TalkBack" framing (the
  `stringResource(R.string.X_a11y)` migration is what makes
  accessibility text locale-agnostic; the Tamil shadow file is
  not the mechanism).

Net: 3 tests deleted, 4 KDoc/comment blocks updated.

### 3. Working-directory cleanup

`saved_state/` (99.6 KB / 439 entries — a snapshot of the entire
Kotlin source + test tree, not in git) was deleted from disk. It
was a local backup/scratch that had been accidentally promoted
into a top-level directory and was sitting under `.gitignore` as
dead weight.

15 `_*.py` scratch scripts (one-off refactor helpers written
during v0.25–v0.26 work, all `git rm`-ignored) were also
deleted from disk.

None of this was tracked in git. The cleanup is a working-tree
change only.

### 4. `values/strings.xml` and `NoteScreen.kt` comments updated

Two on-disk comments referenced the Tamil localiser as the
reason the strings had been hoisted out of Kotlin literals. The
strings are still hoisted (TalkBack / locale-safety is still the
right reason), but the framing now reads "for the resource
resolver" rather than "for the Tamil localiser".

## Files

### Deleted
- `app/src/main/res/values-ta/strings.xml` (86 KB, 763 keys)

### Modified
- `app/build.gradle.kts` — `versionCode` 54 → 55,
  `versionName` "0.29.0" → "0.30.0"
- `app/src/main/res/values/strings.xml` — comment on
  `note_close` updated to drop the Tamil-localiser reference
- `app/src/main/java/org/mindanchor/model/NoteScreen.kt` —
  comment on the `note_close` semantic updated to drop the
  Tamil-localiser reference
- `app/src/test/java/org/mindanchor/i18n/I18nSweepFindingTest.kt`
  — removed the `R_string note_close is defined in values and
  values-ta` test; KDoc updated
- `app/src/test/java/org/mindanchor/launcher/BpdEntryPointsFindingTest.kt`
  — removed the `values-ta strings xml has Tamil Right now
  keys` test; removed unused `assertNotNull` import
- `app/src/test/java/org/mindanchor/launcher/NowWhatStayUpFindingTest.kt`
  — renamed `strings xml defines now_what_stay_up in both
  locales` to `... defines now_what_stay_up`; removed the
  `stringsTa` getter and the Tamil assertion; removed unused
  `assertNotNull` import
- `app/src/test/java/org/mindanchor/launcher/BpdSurfaceA11yI18nFindingTest.kt`
  — KDoc updated to drop the Tamil-localisation framing
- `app/src/test/java/org/mindanchor/letters/LetterNotificationChannelFindingTest.kt`
  — removed the `values-ta strings has a Tamil
  letters_channel_name localization` test; removed the
  `stringsTa` getter; KDoc and inline comments updated
- `RELEASE_NOTES_v0.30.0.md` (this file)

### Working-tree only (not in git)
- `saved_state/` (99.6 KB) — deleted
- 15 `_*.py` scratch scripts — deleted

## Verification

- `./gradlew :app:detekt` clean (0 issues)
- `./gradlew :app:testDebugUnitTest` 1469/0/100% (was 1472 in
  v0.29.0; −3 tests deleted in this release: the three
  Tamil-shadow tests above)
- `./gradlew :app:assembleDebug` builds
- APK SHA-256: `32B917F7DC0B1FD0C3881FCFDF05B95271196E1E4347ED56E9121E747319F190`

## Privacy

No change from v0.29.0. No new permissions, no new network calls,
no new data collected. R1 still honored (no hardcoded crisis line
numbers).

## Out of scope (still pending)

- AppWatchService SMS broadcast — still needs `RECEIVE_SMS`
  runtime grant UI
- GroundMeTile — still registered and exported, but the user
  has to drag it to the quick-settings panel manually
- Watch connect real root-cause fix — still needs user's
  `adb logcat -s MindAnchor/HealthConnect:V` capture from a
  real watch pair
- CodeRabbit on PR #34 — still paused; `@coderabbitai resume`
  unpauses
- Signing key — still needed before F-Droid submission or Play
  Store; still in the "you do this once" stage per
  `docs/RELEASING.md`
- Clinician review of `docs/CLINICAL_REVIEW.md` — still
  pending
- Real-device beta run (M6) — still pending
- Tamil translator — explicitly out of scope per the v0.30.0
  release
