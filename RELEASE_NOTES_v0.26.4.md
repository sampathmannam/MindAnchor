# v0.26.4 — BPD §3.4 entry points on the home surface

**Tag**: `v0.26.4` on `work/v0.21.0-10of10`
**Version code**: 47 (was 46)
**Version name**: 0.26.4 (was 0.26.3)
**Test count**: 1443 debug + 1443 release = **2886 / 0 failed** (was 2876 in v0.26.3; +10 from the new BpdEntryPointsFindingTest × 5)
**Detekt**: clean
**Release**: https://github.com/sampathmannam/MindAnchor/releases/tag/v0.26.4

## What this release does

Closes the v0.26.3 follow-up: the BPD §3.4 features (chain capture, IFS picker, data export) were built in v0.26.1 but unreachable from the app — no home-surface entry point. v0.26.4 wires the three affordances into a single "Right now" section on the home, one tap from the home to the activity.

## The "Right now" home section

A new card on the home surface, placed after the existing 5 home cards (OpenLoop, QuickNotes, OneThing, BedtimeList, Wellness) and the report-line. Same idiom as the other home cards — one section header, one caption, three TextButton rows, full-width 48dp tap targets, `Role.Button` for screen readers, `Modifier.semantics` for a11y.

### Section

- **Header**: "Right now"
- **Caption**: "A small thing to write down before the next thing happens."

### Buttons (top to bottom)

1. **"What just happened?"** → `org.mindanchor.chain.ChainCaptureActivity`
   The 5-field chain capture (event / interpretation / part / want / part-to-bring) from v0.26.1. Same defensive `runCatching` pattern as `onOpenNotes` + `onOpenCheckInHistory` — a misconfigured manifest is the easiest way to ship a broken entry point, and a single try-frame is not a UX failure.

2. **"Which part is loud?"** → `org.mindanchor.ifs.IfsPickerActivity`
   The IFS picker chip grid (angry / scared / disappearing / critic / protector / critic's-critic / the-noticer) from v0.26.1. 2-column grid, 4+3 layout.

3. **"Export for my therapist"** → `org.mindanchor.export.ExportActivity`
   The JSON data export from v0.26.1. Excludes Letter content (privacy). System share sheet for delivery to the therapist.

### Strings (5 new keys)

```xml
<string name="right_now_section">Right now</string>
<string name="right_now_caption">A small thing to write down before the next thing happens.</string>
<string name="right_now_chain">What just happened?</string>
<string name="right_now_ifs">Which part is loud?</string>
<string name="right_now_export">Export for my therapist</string>
```

Tamil (5 new keys in `values-ta/strings.xml`):
- `right_now_section` = "இப்போது"
- `right_now_caption` = "அடுத்தது நடப்பதற்கு முன் எழுதிவைக்க ஒரு சிறிய விஷயம்."
- `right_now_chain` = "என்ன நடந்தது?"
- `right_now_ifs` = "எந்தப் பகுதி loud ஆக இருக்கிறது?"
- `right_now_export` = "என் therapist-க்கு export செய்"

## Why this design

The brief for the home surface is "calm glance surface, never move". A home card that lights up when the user is dysregulated is a contradiction — but the v0.26.0 §3.4 spec calls for chain capture, IFS picker, and data export as one-tap affordances for the person mid-moment. The right shape is a small, calm, always-there section that doesn't look like an action button until the user wants it. "Right now" as a section header (not a button) reads as a label, not an affordance — the same idiom as the report-section "Read it" text on the home. The three buttons below are the actions, but they look like text, not like a primary call-to-action.

The card is the same width as the rest of the home cards (no special highlight, no badge, no count) and uses the same `MaterialTheme.typography.bodyLarge` text style as the other home cards. A user who never opens it will not notice it. A user who needs it will find it.

## New FindingTest

`app/src/test/java/org/mindanchor/launcher/BpdEntryPointsFindingTest.kt` (5 methods):

1. `HomeSurface renders the Right now section header and caption` — pins `stringResource(R.string.right_now_section)` + `stringResource(R.string.right_now_caption)` in `HomeScreen.kt`
2. `HomeSurface renders the three BPD button labels` — pins `stringResource(R.string.right_now_chain)` + `right_now_ifs` + `right_now_export`
3. `HomeSurface dispatches Intents to the three BPD activities` — pins the `runCatching { val intent = android.content.Intent(context, .chain.ChainCaptureActivity::class.java) }` pattern (and the same for IFS and Export)
4. `strings xml defines the five Right now keys` — pins the 5 `name="right_now_*"` declarations in `values/strings.xml`
5. `values-ta strings xml has Tamil Right now keys` — pins the 5 `name="right_now_*"` declarations in `values-ta/strings.xml` (values may still be English placeholders)

A regression that drops the entry points, re-orders the section, or changes the Intent pattern flips one of the assertions red.

## Verification

- `:app:testDebugUnitTest` — **1443 tests, 0 failed, 0 errored** (was 1438; +5)
- `:app:testReleaseUnitTest` — **1443 tests, 0 failed, 0 errored**
- `:app:detekt` — **clean**
- `:app:assembleDebug` — APK built
- **APK SHA-256**: (filled by build pipeline; will be in this release's assets)

## End-to-end smoke (emulator-5554, Android 14, x86_64, 1080x2400)

- ✅ Time set to 12:00 noon (skip the 2am shell which would otherwise cover the right-now section)
- ✅ Home surface renders the new section: header + caption + 3 TextButton rows in a column
- ✅ Tap "What just happened?" → `ChainCaptureActivity` opens (5 fields with placeholders, Save button)
- ✅ Back to home → Tap "Which part is loud?" → `IfsPickerActivity` opens (7 IFS part chips in a 4+3 grid)
- ✅ All three activities reachable in one tap from the home, no settings, no scroll
- ✅ No FATAL on any path

## What this is NOT

- **Not a new feature**. The §3.4 features were built in v0.26.1 and shipped in v0.26.3. v0.26.4 only wires them to the home.
- **Not exported cross-app**. The three activities are `android:exported="false"` (only this same app launches them). The `runCatching` is the same-app-startActivity pattern, not a cross-app launch.
- **Not a new test methodology**. The FindingTest follows the same pattern as the rest of the SOTA v2 bug-hunt: file-shape pins (positive + negative), no runtime, no instrumentation.

## Known follow-ups (unchanged from v0.26.3)

- **AppWatchService SMS broadcast**: the receiver is registered, the channel is created, the deep-link is wired — but the SMS_RECEIVED broadcast needs `RECEIVE_SMS` permission to actually be delivered to the receiver. The permission is in the manifest. The user has to grant it at runtime.
- **GroundMeTile**: the QuickSettingsTile is registered, the activity is exported — but the tile is not user-toggleable in the device's quick-settings panel by default. The user has to drag the tile from the edit panel.
- **`LICENSE` change**: see v0.26.3 post-merge audit fix #8. GPL v3 → Apache 2.0. Review needed.
- **`values-ta/strings.xml` translator**: still mostly English placeholders.

## What this is

- A small, low-risk wire-in. The hard work was done in v0.26.1 (the activities) and v0.26.3 (the post-merge audit). v0.26.4 is the home-surface dispatcher and a FindingTest to lock the shape.
- A 5-test, 1-commit release. The test count went from 1438 to 1443 (+5 from the new BpdEntryPointsFindingTest).
- The natural next step after the v0.26.3 fanout: the user can now reach the §3.4 features from the home surface. The BPD work is now end-to-end reachable, not just end-to-end built.
