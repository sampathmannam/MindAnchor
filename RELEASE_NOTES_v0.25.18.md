# v0.25.18 — i18n + a11y sweep across 12 surfaces

**Tag**: `v0.25.18` on `feature/v0.25.18-19-i18n-secondary`
**Version code**: 44 (was 38, +6 = six surface groups)
**Version name**: 0.25.18 (was 0.25.14)
**Test count**: 1372 debug + 1372 release = **2744 / 0 failed** (was 1369+1369=2738 in v0.25.14; +3 tests from the I18nSweepFindingTest × 2 and A11ySurfaceFindingTest B15)
**Detekt**: clean

## What this release does

Two follow-up sweeps to v0.25.12's BPD-only i18n pass:

**Part 1 — i18n sweep across the rest of the app.** v0.25.12
migrated 3 hardcoded English strings on 5 BPD surfaces and
moved on. The remaining 12 surfaces (the v0.26.0 §3.2/3.3/3.5
list expanded) still had scattered hardcoded `text = "..."`
and `contentDescription = "..."` literals. v0.25.18 walks
each of them, migrates what is hardcoded English, and pins
the rest with a FindingTest.

The actual count of hardcoded English `text = "..."` literals
on the 12 swept files turned out to be **one**: the
`contentDescription = "Close"` on the NoteScreen TopAppBar
navigationIcon. The other `text = "..."` patterns in those
files are pure symbol decorations (back arrow `←`, close `×`,
pin `★`/`☆`, bullet `•`, separator `·`, suffix `→`) or
dynamic strings (`"$time · $dur"`), all of which the
FindingTest whitelists. The migration:

- `NoteScreen.kt` `contentDescription = "Close"` →
  `contentDescription = closeDesc` where
  `val closeDesc = stringResource(R.string.note_close)` is
  hoisted in the Composable scope (stringResource is
  @Composable and cannot be called inside a Modifier.semantics
  lambda)
- New `<string name="note_close">Close</string>` in both
  `values/strings.xml` and `values-ta/strings.xml`

The Tamil strings file did not exist before this release —
`app/src/main/res/values-ta/strings.xml` is created here as a
placeholder copy of the English file. The header comment
documents the placeholder status. Translation is a future
work item.

**Part 2 — a11y sweep.** Extended `A11ySurfaceFindingTest`
with a new B15 test that walks the same 12 files, finds
every `IconButton` call site, and asserts the next 1200
chars contain either a `contentDescription =`, a
`role = Role.Button`, or a `stringResource(` call. The 1200
window covers the worst-case in-file call site (a hoisted
`val foo = stringResource(...)` plus a 6-line comment block).
The test passed on the first run after the i18n migration —
the existing IconButtons on these surfaces already had
`contentDescription` set (the NoteScreen close button was
the only outlier, and that is the one the i18n fix touched).

## Why

v0.25.12 was a BPD-only pass; the user noted in the brief
that "v0.25.12 only swept 3 hardcoded English strings on 5
BPD surfaces." A Tamil user running a Tamil-localised build
in a non-BPD surface hears English in TalkBack. The fix is
mechanical: hoist `val foo = stringResource(R.string.X)`
into the Composable scope, assign to `contentDescription`
or `text` inside the lambda.

The FindingTest pins the file shape so a future regression
that re-introduces a hardcoded `text = "Close"` or
`contentDescription = "Foo"` flips the build red. The
whitelist is narrow: empty string, dynamic string, pure
symbol, separator decoration. Anything else is a hardcoded
literal.

The Tamil placeholder is a copy of the English file by
design — without it, the `app/src/main/res/values-ta/`
resource directory does not exist, the build would still
succeed (Android falls back to `values/`), and a future
translator would have to set up the directory from
scratch. With the placeholder, the localiser can
incrementally translate one `<string>` at a time without
breaking the build.

## Test flips

- **`I18nSweepFindingTest`** — new class, 2 tests:
  - `every swept file passes the i18n sweep` — the bulk
    pin that walks the 12 files and asserts no hardcoded
    English `text = "..."` or `contentDescription = "..."`
    survives outside `stringResource(`. The whitelist
    covers empty strings, dynamic strings, pure symbols,
    and separator decorations.
  - `R_string note_close is defined in values and
    values-ta` — pins the new key so a future commit that
    drops the string flips this test red.
- **`A11ySurfaceFindingTest.B15`** — new test:
  - `every IconButton on the v0_25_18 surfaces has
    contentDescription or stringResource` — walks the 12
    files, finds every `IconButton(`, asserts the next
    1200 chars contain `contentDescription =` or
    `role = Role.Button` or `stringResource(`. The
    stringResource check is the catch-all for the case
    where the label is a hoisted local val.

## Files changed

- `app/src/main/java/org/mindanchor/model/NoteScreen.kt`
  - `contentDescription = "Close"` →
    `contentDescription = closeDesc`
  - Added `val closeDesc = stringResource(R.string.note_close)`
    in the Composable scope (above the Surface)
  - Multi-line comment explaining the v0.25.18 i18n sweep
    and why the val is hoisted
- `app/src/main/res/values/strings.xml`
  - Added `<string name="note_close">Close</string>` with
    a 4-line comment explaining the migration
- `app/src/main/res/values-ta/strings.xml` — **NEW**
  - Placeholder copy of `values/strings.xml` with the same
    `note_close` entry. Header comment documents the
    placeholder status (Tamil translations pending).
- `app/src/test/java/org/mindanchor/i18n/I18nSweepFindingTest.kt`
  — **NEW** (2 tests)
- `app/src/test/java/org/mindanchor/accessibility/A11ySurfaceFindingTest.kt`
  - Added B15 IconButton contentDescription test
- `app/build.gradle.kts`
  - `versionCode 38 → 44`
  - `versionName "0.25.14" → "0.25.18"`

## Verification

- `:app:compileDebugKotlin` — clean
- `:app:compileDebugUnitTestKotlin` — clean
- `:app:testDebugUnitTest` — 1372 tests, 0 failed, 0 errored
- `:app:testReleaseUnitTest` — 1372 tests, 0 failed, 0 errored
- `:app:detekt` — clean
- `:app:assembleDebug` — `app-debug.apk` (52,467,980 bytes)

## What this is NOT

- **Not a full Tamil translation.** The values-ta/ file is a
  copy of values/. Translation is a future work item.
- **Not a full a11y migration.** B15 only covers IconButton
  on the 12 v0.25.18 surfaces. The B6 tests in
  A11ySurfaceFindingTest cover TextButton / clickable rows
  for the same surfaces. There is no test for
  IconButton on the GoingLightVpnService notification
  buttons or on the AppActionsDialog (those are out of
  the 12-file scope).
- **Not a complete i18n sweep.** The 12 files swept are
  the ones the v0.25.18 brief enumerates. The
  GoingLightVpnService notification text, the
  FrictionGate's hardcoded English fragments, the
  AnchorNotificationListenerService classification
  strings, and the BatchSchedule time-slot labels are
  not in scope here; they are B2 in the existing
  A11ySurfaceFindingTest.
- **Not a build of the release APK.** The release APK is
  not built in this release (key not in env). The debug
  APK is sufficient for the FindingTest pin and for the
  PR review.

## Next steps (v0.25.19+)

1. The operational + integrations work in v0.25.19
   (CrashReporter, CI, branch protection, store listing,
   LICENSE / CoC, notification channels, Health Connect /
   COROS / Google Drive smoke tests).
2. The Tamil translation work — open a follow-up issue
   for a translator to fill in values-ta/.
3. B2 (GoingLightVpnService notification text) — already
   pinned by A11ySurfaceFindingTest B2; the fix is
   mechanical (`getString(R.string.X)`).
