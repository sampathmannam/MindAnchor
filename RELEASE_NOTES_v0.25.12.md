# MindAnchor v0.25.12 — BPD-surface i18n SOTA sweep

**Release date**: 2026-08-14
**Build**: `versionName=0.25.12, versionCode=36`
**Tag**: `v0.25.12` → HEAD
**Release**: https://github.com/sampathmannam/MindAnchor/releases/tag/v0.25.12

**Artifacts**:
- debug APK: pending (built locally, SHA-256 below)
- release APK (unsigned): pending

**Status**: shipped

**Test result**: 2728 tests, 0 fail, 0 error. Detekt clean.

This is a focused SOTA follow-up to v0.25.11. It closes the i18n gap
on the 3 v0.26.0 §3.2/3.3/3.5 BPD-launcher surfaces — the
`contentDescription` text in `Modifier.semantics { … }` blocks was
hardcoded English, so a Tamil user running a Tamil-localised build
would have heard English in TalkBack on the "Ground me", "Before
you send", and "Now what?" 2am shells. v0.25.12 hoists each
contentDescription to a `val a11y = stringResource(R.string.X_a11y, …)`
in the @Composable scope, then assigns `contentDescription = a11y`
inside the `Modifier.semantics` lambda (the lambda is not
@Composable, so `stringResource` must be hoisted).

The fix is pinned by `BpdSurfaceA11yI18nFindingTest`, a 4-method
FindingTest that asserts:

1. `NowWhatShell` uses `stringResource(R.string.now_what_a11y)`
2. `GroundMeScreen` uses `stringResource(R.string.ground_me_a11y)`
3. `BeforeYouSendInterstitial` uses `stringResource(R.string.bys_a11y, template.label)`
4. `strings.xml` defines all three `*_a11y` keys (the `bys_a11y` one with a `%1$s` placeholder)

---

## What was fixed

### v0.26.0 §3.2 GroundMeScreen — i18n for the 2-min ground-me picker

- The picker's `Modifier.semantics(mergeDescendants = false) { contentDescription = … }` was set to the literal English string "Ground me right now. Three options.". A Tamil user would have heard English.
- Now: `val a11y = stringResource(R.string.ground_me_a11y)` (computed in the Composable scope) and `contentDescription = a11y` inside the lambda.
- The `ground_me_a11y` string is defined in `app/src/main/res/values/strings.xml` as "Ground me right now. Three options." (and is ready to be translated into Tamil / Hindi / etc. in the existing `values-ta/`, `values-hi/` etc. resource folders).

### v0.26.0 §3.3 BeforeYouSendInterstitial — i18n for the DEAR MAN / GIVE / FAST interstitial

- The interstitial's `contentDescription = "Before you send. ${template.label}."` interpolated the template label (DEAR MAN / GIVE / FAST) into a hardcoded English prefix. Same Tamil-blindness.
- Now: `val a11y = stringResource(R.string.bys_a11y, template.label)` hoists the formatted string out of the lambda. The `bys_a11y` string is "Before you send. %1$s." in `strings.xml` — translators get one key with one placeholder, not two keys per template.

### v0.26.0 §3.5 NowWhatShell — i18n for the 2am shell

- The 2am shell's `contentDescription = "It's late. What do you need right now?"` was hardcoded English. Same Tamil-blindness — and the 2am shell is the surface most likely to be seen by a user in distress, where the Tamil TalkBack voice is the difference between "the launcher is speaking to me" and "the launcher is foreign".
- Now: `val a11y = stringResource(R.string.now_what_a11y)` and `contentDescription = a11y` inside the lambda. `now_what_a11y` is "It's late. What do you need right now?" in `strings.xml`.

### FindingTest

- **New file**: `app/src/test/java/org/mindanchor/launcher/BpdSurfaceA11yI18nFindingTest.kt` (4 methods, 1 file). The test reads each source file as text and asserts both the positive shape (`source.contains("stringResource(R.string.X_a11y)")`) and the negative shape (`!source.contains("…hardcoded English…")`). A regression that re-hardcodes any of the three strings (e.g. a copy-edit pass that converts `R.string.x_a11y` back to a literal) flips the test red.

---

## Anti-pattern audit

- `BpdSurfaceA11yI18nFindingTest` is the second FindingTest in the v0.25.x SOTA wave that pins *positive shapes* (the fix exists) AND *negative shapes* (the bug is gone). The first was the v0.25.11 V2BugHunt flips (3 KeystoreHmacKey / GoogleDriveAuth / FrictionPrefs tests that flipped from `assertFalse(!source.contains("fix"))` to `assertTrue(source.contains("fix"))`).
- The pattern: for a fix that replaces a hardcoded bad shape with a hardcoded good shape, the test pins both. A regression that re-introduces the bad shape (negative pin) OR drops the good shape (positive pin) fails the test.

---

## Deferred to v0.26.1 (per the bpd_plan_v0_26.md §5)

- The HomeScreen `collectAsState` → `collectAsStateWithLifecycle` migration (7 sites; BUG-004 FindingTest currently passes because SettingsScreen.kt already has the primitive). Touching it now would cascade into the BUG-005/006/007/008/012 FindingTests, all of which are bug-shape pins. Bigger surface, needs a dedicated PR.
- The 4 v0.26.0 §3.4 chain-capture / data-export / lock-screen surfaces. These are the v0.26.1 work package.

---

## How to verify

```bash
# Run the new FindingTest:
./gradlew :app:testDebugUnitTest --tests "org.mindanchor.launcher.BpdSurfaceA11yI18nFindingTest"

# Run the full suite:
./gradlew test
#  -> 2728 tests, 0 fail, 0 error

# Run detekt:
./gradlew detekt
#  -> clean
```

Install on the emulator (`adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk`) and check the TalkBack content description for the 2am shell (set the system clock to 00:00-05:00 and enable the "late-night impulses" BPD profile flag). The content description should match `now_what_a11y`.
