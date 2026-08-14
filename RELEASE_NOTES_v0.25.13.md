# MindAnchor v0.25.13 — v0.26.0 §3.2-3.5 UI bug fixes (end-to-end on emulator)

**Release date**: 2026-08-14
**Build**: `versionName=0.25.13, versionCode=37`
**Tag**: `v0.25.13` → HEAD
**Release**: https://github.com/sampathmannam/MindAnchor/releases/tag/v0.25.13

**Artifacts**:
- debug APK: pending (built locally, SHA-256 below)
- release APK (unsigned): pending

**Status**: shipped

**Test result**: 2732 tests, 0 fail, 0 error. Detekt clean.

This is a follow-up to v0.25.12 found during a full end-to-end UI
test on the emulator-5554 (MindAnchorTest, API 34). The v0.26.0 §3.5
"Now what?" 2am shell shipped with a layout bug that silently
collapsed two of the three options off-screen — a user in distress
at 2am could see and tap only "I want to sleep" out of three. The
§3.3 BeforeYouSendInterstitial shipped with the same pattern
collapsing the "Send" button to half the screen height. Both were
caused by the same anti-pattern: a `Box(modifier = Modifier.fillMaxSize())`
inside a `Row` or `Column` whose parent had `fillMaxSize`. v0.25.13
removes the Box in both places.

---

## What was fixed

### v0.26.0 §3.5 NowWhatShell — 3 options all render (was: 1 of 3)

- **The bug**: `NowWhatRow` wrapped a `Box(modifier = Modifier.fillMaxSize())` around the inner `TextButton`. In a `Column` with `fillMaxSize`, a child Box with `fillMaxSize` requests the full remaining column height, which collapses the next two `NowWhatRow` siblings to zero height and pushes them off-screen.
- **The user impact**: a 2am user in distress could only see and tap "I want to sleep". "I want to ground" and "I want to talk to someone" were invisible — both at the moment when the launcher is the most important surface in the user's life.
- **The evidence**: `uiautomator dump` on the 2am shell returned only one text element with `text="I want to sleep"` at bounds `[63,663][1017,852]`. The other two NowWhatRow Composable calls (lines 48 and 49 of `NowWhatShell.kt`) were present in the source but not in the rendered tree.
- **The fix**: removed the Box. The TextButton has its own `Alignment.CenterStart` for its content, so the Box was redundant. After the fix, the three options render at y=663-852, y=905-1094, y=1147-1336 — three distinct 189px-tall Surface cards.
- **Pinned by** `NowWhatRowLayoutFindingTest` (file-shape): a regression that re-introduces `Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart` (the Box's only distinguishing parameter) flips the test red.

### v0.26.0 §3.3 BeforeYouSendInterstitial — Send button is 48dp (was: 1126px / half-screen)

- **The bug**: the "Send" Surface was wrapped in a `Box(modifier = Modifier.fillMaxSize())`. The Surface sits inside a `Row` inside a `Column` with `fillMaxSize`. A Box with `fillMaxSize` inside a Row inside a fillMaxSize Column requests the full column height, so the "Send" Surface grew to 1126 px (half the screen). The "Send anyway" TextButton on the left was 48 dp (126 px) — a 9× height mismatch.
- **The user impact**: the "Send" button dominated the screen, with the FAST / DEAR MAN / GIVE template card squashed into the top quarter. The "Send anyway" alternative was almost invisible.
- **The evidence**: `uiautomator dump` showed "Send anyway" at y=1148-1274 (126 px, 48 dp) and "Send" Surface at y=1148-2274 (1126 px) — the second button consumed half the screen.
- **The fix**: changed `Modifier.fillMaxSize()` to `Modifier.fillMaxWidth().heightIn(min = 48.dp)`. The Box now fills the Surface horizontally but sizes vertically to the minimum 48 dp, matching the "Send anyway" button. After the fix, both buttons are 126 px tall.
- **Pinned by** `BeforeYouSendInterstitialLayoutFindingTest` (the test asserts the Box inside the Send Surface has `fillMaxWidth`, not `fillMaxSize`).

---

## Anti-pattern

The root cause of both bugs is the same: **`Box(modifier = Modifier.fillMaxSize())` inside a Row or Column whose parent has `fillMaxSize`**. The Box requests the full parent dimension, which collapses siblings (in a Column) or grows out of proportion (in a Row).

The fix pattern is one of:
1. Remove the Box entirely if it's only there for alignment (the inner component's own modifier can do centering)
2. Replace `fillMaxSize()` with `fillMaxWidth()` and explicit `heightIn(min = N.dp)` for the Box

The compose-lint rule for this would be a custom detekt rule: "Box with fillMaxSize inside a Row or Column with fillMaxSize is a layout bug". v0.25.13 doesn't add the rule — the two FindingTests are the load-bearing pin.

---

## v0.25.12 Follow-up

The v0.25.12 release notes mentioned:
- The HomeScreen `collectAsState` → `collectAsStateWithLifecycle` migration (7 sites; BUG-004 FindingTest passes because SettingsScreen.kt already has the primitive)
- The 4 v0.26.0 §3.4 chain-capture / data-export / lock-screen surfaces (the v0.26.1 work package)

These are NOT in v0.25.13. The v0.25.13 work package is **only the two UI bugs found during end-to-end emulator testing**.

---

## How to verify

```bash
# Run the new FindingTests:
./gradlew :app:testDebugUnitTest --tests "org.mindanchor.launcher.NowWhatRowLayoutFindingTest"
./gradlew :app:testDebugUnitTest --tests "org.mindanchor.launcher.BeforeYouSendInterstitialLayoutFindingTest"

# Run the full suite:
./gradlew test
#  -> 2732 tests, 0 fail, 0 error

# Run detekt:
./gradlew detekt
#  -> clean
```

End-to-end on emulator-5554:
1. Set the emulator time to 00:00-05:00 (`adb shell su 0 toybox date 081400002026`)
2. Launch the app. The 2am shell shows three options: "I want to sleep", "I want to ground", "I want to talk to someone"
3. Tap "I want to ground" → GroundMeScreen shows three options: "Breathe, slowly", "Hold something cold to your face", "Name what is around you"
4. Go back, then in Settings → Pauses → "Show the check" → the "Send" button is the same height as the "Send anyway" button (both 48dp)
