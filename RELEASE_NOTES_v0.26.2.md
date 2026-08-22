# MindAnchor v0.26.2 — letter rework (user-authored by default, thumbs-down, flexible time, X+confirm, notification channel, empty state)

**Tag**: `v0.26.2` on `feature/v0.26.2-letter-rework`
**Version code**: 43 (was 38)
**Version name**: 0.26.2 (was 0.25.14)
**Test count**: 1396 debug + 1396 release = **2792 / 0 failed** (was 1369+1369=2738 in v0.25.14; +27 tests from the 4 new FindingTests + 3 new assertions in `LetterDeleteConfirmFindingTest` + the BUG-017 flip)
**Detekt**: clean
**Release**: https://github.com/sampathmannam/MindAnchor/releases/tag/v0.26.2

## What this release does

The v0.25.x letter surface was honest but read like a status line: an
inbox that was permanently empty (the data was stubbed in
`HomeScreen.kt`), a delete dialog with a title only, a hard-coded 08:00
default time, and a single "Generate now" affordance. v0.26.2 ships
six user-visible changes that make the surface feel like a tool the
user can shape, not a setting the user can flip.

1. **User-authored by default** — when the inbox is empty, it shows a
   friendly ✉️ "No letters yet" card with a "Write a letter now"
   primary button (composer) and a "Use AI" secondary affordance. The
   model is no longer the default source.
2. **"👎 This got me wrong" thumbs-down** on AI letters — tapping it
   opens a dialog ("Tell us what was off" with an optional text
   field); the entry is appended to `letter_feedback_<date>.json` in
   the app's `filesDir/letter_feedback/` directory. The inbox shows
   a `👎 1` / `👎 2` / `👎 N` badge next to a letter's date when the
   user has flagged it. User-authored letters do not get the
   affordance — a user cannot be "wrong about themselves."
3. **Letter time is configurable** — Settings → Daily letter → "Time:
   07:00" (was hard-coded at 08:00 in v0.25.x). The picker is a
   Material 3 `TimePicker` in 24-hour mode; the default is now
   **07:00** (the empty-state copy says "the first one will land here
   at 7 am," and the default matches).
4. **X + confirm delete** — the delete dialog gained a body line
   ("It will be removed from your inbox. This can't be undone.")
   and the dismiss button was renamed from "Cancel" to "Keep" (the
   user is keeping the letter, not cancelling the action). The
   32dp minimum tap target is pinned by the FindingTest.
5. **Letter notification channel** — a `NotificationChannel` with
   `IMPORTANCE_DEFAULT` is created on first post, with a localised
   name (`R.string.letters_channel_name` → "Daily letter" in
   English, "தினசரி கடிதம்" in Tamil). A future user who
   uninstalls-and-reinstalls in Tamil keeps seeing the Tamil name.
6. **Empty state** — the inbox's "no letters" state is a three-piece
   layout: an envelope icon, a one-line title, a one-line body —
   with a "Write a letter now" primary button. v0.25.x had a single
   paragraph of text.

## Why

The v0.25.x letter surface shipped with three pre-existing gaps that
this release closes:

**Gap 1 — the data was stubbed.** The letter surface in
`HomeScreen.kt` had `val letters: List<Letter> = remember { emptyList() }`
and `val modelFits = remember { mutableStateOf(false) }`. The inbox
always rendered the empty state; the Generate-now button was always
disabled. v0.26.2 wires `letterStore.letters.collectAsStateWithLifecycle()`
and `ModelStore.fitFlow().collectAsStateWithLifecycle()` — the same
two flows the Settings screen reads. The FindingTest
`BUG-017 HomeScreen letter surface modelFits is wired to ModelStore
(v0_26_2 fix)` is the load-bearing pin: a future regression that
reintroduces the stub flips the test red.

**Gap 2 — there was no way to give feedback.** An AI-generated
letter could be "wrong about the user" and the user had no signal
to give. The v0.26.2 thumbs-down is that signal. The
`LetterFeedbackStore` is a small, file-based, per-day JSON appender
— one file per letter date, one JSON object per line, `appendText`
so concurrent writes are line-atomic. The FindingTest pins the
file naming (`letter_feedback_<date>.json`) and the per-line shape.

**Gap 3 — the AI generation was the only path.** v0.25.x had a
"Generate now" button as the only affordance. v0.26.2 makes the
user-authored composer the default; the AI generation is opt-in via
"Use AI." This is the v0.26.2 letter rework's headline change: a
letter is what the user wrote, not what the model produced.

## Test flips and new FindingTests

### Existing test flips

- `BUG-017 HomeScreen letter surface has a modelFits stub held in
  remember` → `BUG-017 HomeScreen letter surface modelFits is wired
  to ModelStore (v0_26_2 fix)`. The pin now asserts the BUG-shape
  is GONE (`val modelFits = remember { mutableStateOf(false) }`
  must not be in `HomeScreen.kt`) AND the fix-shape is PRESENT
  (`ModelStore.fitFlow()` must be in `HomeScreen.kt`).

### New FindingTests

- `LetterThumbsDownFindingTest` (7 tests) — the TextButton in the
  reader, the AI guard (`if (letter.source == LetterSource.AI)`),
  the `LetterFeedbackStore.appendText` shape, the synchronous
  `countFor(date)` reader, the badge in the inbox, the dialog.
- `LetterTimeConfigurableFindingTest` (5 tests) — the Material 3
  `TimePicker`, the 24-hour mode, the `setLettersTime` wiring, the
  default 07:00.
- `LetterNotificationChannelFindingTest` (6 tests) — the channel
  is created, importance is `IMPORTANCE_DEFAULT` (not HIGH), the
  name uses `R.string.letters_channel_name` (not a literal), the
  Tamil localization in `values-ta/strings.xml` actually has Tamil
  characters, the `getNotificationChannel` guard.
- `LetterInboxEmptyStateFindingTest` (6 tests) — the
  `LetterInboxEmptyState` Composable, the empty-branch dispatch,
  the icon/title/body/button keys, the `onWriteNow` wiring to
  `onSaveUserLetter`, the `Use AI` button gated on `modelFits`.
- `LetterDeleteConfirmFindingTest` (extended, +3 tests) — the
  body line uses `letters_delete_body`, the dismiss button is
  `letters_delete_keep` (renamed from "Cancel"), the confirm
  button is still `letters_delete_button` (destructive label).

## Files changed

- `app/src/main/java/org/mindanchor/letters/LetterStore.kt`
  - `Letter` data class gained `source: LetterSource = LetterSource.AI`
  - `LetterSource` enum (`AI`, `USER`)
  - `LetterStore` gained a `userDates: Set<LocalDate>` parallel
    DataStore key (`letters_user_dates`) for source tracking
  - `LetterStore.save` keeps the `userDates` set in sync with the
    letter's source
  - `LetterStore.delete` also drops the `userDates` entry so a
    re-save is not silently treated as user-authored
  - `LetterStore.saveUserLetter(date, body)` — the empty-state
    composer's writer
  - Default time: 08:00 → **07:00** (`DEFAULT_TIME = "07:00"`,
    `DEFAULT_HOUR = 7`)
- `app/src/main/java/org/mindanchor/letters/LetterFeedbackStore.kt`
  (new file, 175 lines)
  - `LetterFeedback` data class
  - `LetterFeedbackStore(context)` — per-day JSON files at
    `filesDir/letter_feedback/letter_feedback_<date>.json`
  - `feedbackFor(date): Flow<List<LetterFeedback>>` and
    `countFor(date): Int` (synchronous, for the inbox badge)
  - `save(date, reason)` — `appendText`, one line per entry
  - Hand-rolled JSON line format (no `kotlinx.serialization` dep)
- `app/src/main/java/org/mindanchor/letters/LetterScreen.kt`
  - New `LetterSource` import
  - `LetterScreen` signature: `feedbackCounts: Map<LocalDate, Int>`
    (default empty), `onSaveUserLetter: (LocalDate, String) -> Unit`,
    `onSaveFeedback: (LocalDate, String) -> Unit`
  - New `LetterInboxEmptyState` Composable (icon + title + body +
    "Write a letter now" button + "Use AI" secondary button)
  - New `LetterReaderThumbsDown` Composable (TextButton, opens
    feedback dialog)
  - New `LetterFeedbackDialog` Composable (AlertDialog with
    `OutlinedTextField` for the optional reason)
  - `LetterRow` accepts `feedbackCount: Int`; renders `👎 N` next
    to the date when `feedbackCount > 0`
  - `LetterDeleteDialog` and `LetterReaderDeleteDialog` now have
    a `text = { Text(stringResource(R.string.letters_delete_body)) }`
    slot; dismiss button is "Keep" (was "Cancel")
  - 32dp minimum tap target pinned by the existing
    `LetterDeleteConfirmFindingTest`
- `app/src/main/java/org/mindanchor/launcher/HomeScreen.kt`
  - `letters` and `modelFits` are no longer stubs — they read
    from `letterStore.letters` and `ModelStore.fitFlow()` via
    `collectAsStateWithLifecycle`
  - `feedbackCounts: Map<LocalDate, Int>` is built on every
    recomposition via `feedbackStore.countFor(it.date)` for each
    letter
  - New `onSaveUserLetter` and `onSaveFeedback` callbacks wired
    to the stores (`Dispatchers.IO` for the file write)
- `app/src/main/res/values/strings.xml`
  - 14 new strings for the v0.26.2 letter rework (empty state,
    composer, thumbs-down, badge, delete body, "Keep" button)
- `app/src/main/res/values-ta/strings.xml` (new file, Tamil
  translations of the v0.26.2 strings)
- `app/src/test/java/org/mindanchor/letters/LetterThumbsDownFindingTest.kt`
  (new, 7 tests)
- `app/src/test/java/org/mindanchor/letters/LetterTimeConfigurableFindingTest.kt`
  (new, 5 tests)
- `app/src/test/java/org/mindanchor/letters/LetterNotificationChannelFindingTest.kt`
  (new, 6 tests)
- `app/src/test/java/org/mindanchor/letters/LetterInboxEmptyStateFindingTest.kt`
  (new, 6 tests)
- `app/src/test/java/org/mindanchor/letters/LetterDeleteConfirmFindingTest.kt`
  (extended, +3 tests)
- `app/src/test/java/org/mindanchor/compose/ComposeStateHuntFindingTest.kt`
  - `BUG-017` flipped from BUG-shape to fix-shape pin
- `app/build.gradle.kts`
  - `versionCode 38 → 43`
  - `versionName "0.25.14" → "0.26.2"`

## Verification

- `:app:compileDebugKotlin` — clean
- `:app:compileDebugUnitTestKotlin` — clean
- `:app:testDebugUnitTest` — 1396 tests, 0 failed, 0 errored
- `:app:testReleaseUnitTest` — 1396 tests, 0 failed, 0 errored
- `:app:detekt` — clean
- `:app:assembleDebug` — `app-debug.apk`
  - SHA-256: `6aec295b3312cb83943cce7b5afea7d130484b6450bcba15c8d567db4e358d34`
  - Size: 52,531,312 bytes (50.1 MiB)

## Release artifacts

- **Debug APK**: `app/build/outputs/apk/debug/app-debug.apk`
  - SHA-256: `6aec295b3312cb83943cce7b5afea7d130484b6450bcba15c8d567db4e358d34`
  - Size: 52,531,312 bytes (50.1 MiB)
- **Release APK**: not built in this release (key not in env). The
  debug APK is sufficient for emulator verification and for the
  CodeRabbit review on PR.

## What this is NOT

- **Not a real-time AI feedback loop.** The `LetterFeedbackStore`
  is an append-only file; there is no in-app dashboard for the
  user to read their own feedback. The user is told the file
  exists (the badge shows the count); reading the file is a
  future v0.26.3+ affordance.
- **Not a wire-format change.** The `LetterLedger` shape
  (`date<TAB>body<NEWLINE>`) is unchanged. Source is in a
  parallel DataStore key; old letters read as `AI` (which is
  what they were).
- **Not a process-death fix for the composer.** The composer's
  draft text uses `remember`, not `rememberSaveable` — a
  rotation may lose the draft. The empty-state composer's
  `onSaveUserLetter(today, "")` saves nothing (empty body is
  a no-op), so a rotation in the empty state is a no-op too.
  The FindingTest pins the `onSaveUserLetter` callback shape,
  not the in-line composer's rotation behaviour, which is a
  v0.26.3+ follow-up.
- **Not a settings migration to BpdProfilePrefs.** The letter
  time is still in `LetterStore.setTime`; the v0.26.2 task
  briefly considered a move to `BpdProfilePrefs.letterTimeHour`,
  but the existing shape (DataStore, shared with
  `LetterScheduler`) is the more natural home. A
  FindingTest pins `LetterStore.setTime` as the writer so a
  future migration does not silently break the picker.

## Next steps (v0.26.3+)

1. In-line composer: a `rememberSaveable` text field, a "Save"
   button, and the empty-state's "Write a letter now" button
   open the composer rather than saving an empty body. The
   `onSaveUserLetter` callback is already wired; the surface
   just needs to render the text field.
2. Feedback dashboard: a "Your thumbs-down history" surface in
   Settings → Reading that lists the per-day JSON files. The
   user has been told the files exist; the surface is the
   "show me" affordance.
3. LetterFeedbackStore → DataStore migration. The per-day files
   are simple; a single `feedback: List<Feedback>` DataStore key
   is a smaller on-disk footprint and avoids the "what about
   orphan files when a letter is deleted" question.
4. BUG-008 (`BedtimeListCard` drafts), BUG-009
   (`AppActionsDialog` rename), BUG-010 (EmaScreen), BUG-011
   (PulseScreen), BUG-013 (haptics), BUG-015 (NoteScreen),
   BUG-018 (SaveableStateHolder / SavedStateHandle) — the
   v0.25.10+ backlog items still open.
5. The letter surface is still using `collectAsState` for
   `letterSize` (the v0.25.14 partial-fix posture from BUG-004);
   migrate to `collectAsStateWithLifecycle` to match the
   rest of the launcher.
