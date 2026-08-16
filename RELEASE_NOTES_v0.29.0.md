# v0.29.0 — SupportSurfaceActivity refactor + ACT values clarification

**Date:** 2026-08-16
**Status:** Shipped
**Author:** Mavis
**Scope:** Refactor + one new feature. No new surfaces, no string
changes outside the new feature, no manifest changes outside
the new activity.

## What ships

### 1. New research-grounded surface: ACT values clarification (Hayes 2004)

The 8-domain standard ACT values taxonomy (Hayes et al.
1999/2004; Wilson & Murrell 2004) is the scaffold. The user
writes one sentence per value domain — "in this corner of my
life, what do I want it to be about?" — and the card is saved
to a single DataStore key on Save tap. Revisits load the
saved values back into the input fields.

The 8 domains:

1. **Relationships** — the people I want in my life
2. **Health** — sleep, food, movement, rest
3. **Work** — the contribution I want to make
4. **Growth** — what I want to learn
5. **Leisure** — how I want to play
6. **Spirituality** — faith, awe, nature, meaning
7. **Community** — the world outside my door
8. **Parenting** — the parent I want to be

Sits at the END of the Support group's in-the-moment →
reflective ordering (after Interpersonal skills), as the
*slowest* reflective practice (per the v0.26.6 audit §3.5).

Files:

- `app/src/main/java/org/mindanchor/support/ValuesActivity.kt`
- `app/src/main/java/org/mindanchor/support/ValuesScreen.kt`
- `app/src/main/java/org/mindanchor/support/ValuesPrefs.kt`
- `app/src/main/res/values/strings.xml` — 14 new `values_*` keys
  + `support_values_button`
- `app/src/main/AndroidManifest.xml` — `ValuesActivity` entry,
  non-exported
- `app/src/main/java/org/mindanchor/support/SupportScreen.kt`
  — new `TextButton` for `ValuesActivity` at the end of the
  "More moments" group
- `app/src/test/java/org/mindanchor/support/ValuesFindingTest.kt`
  — 12 FindingTests pinning the new surface

### 2. `SupportSurfaceActivity` — shared scaffold for the nine Support activities

The 9 Support activities (Interpersonal, RadicalAcceptance,
SelfCompassion, OppositeAction, DistressThermometer, Accepts,
LetterToPart, DiaryCard, Values) all share the same
lifecycle scaffold:

```kotlin
class XxxActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CalmBackground { _ ->
                XxxScreen(onDone = { finish() })
            }
        }
    }
}
```

v0.29.0 extracts this into a new abstract class:

```kotlin
abstract class SupportSurfaceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CalmBackground { _ ->
                Surface(onDone = { finish() })
            }
        }
    }

    @Composable
    abstract fun Surface(onDone: () -> Unit)
}
```

Each activity now declares only the surface, not the
lifecycle. The duplication (9 × ~10 lines) is gone, and any
future change to the wrapping (IME padding, system-bar
insets policy, etc.) lives in one place.

`DistressThermometerActivity` keeps its 86+-band
`onOpenSupport` callback (defined inside `Surface()` — the
only place that has both the activity context for
`startActivity` and the screen).

Files:

- `app/src/main/java/org/mindanchor/support/SupportSurfaceActivity.kt`
  (new, abstract base class)
- 9 activity files refactored to extend it: `InterpersonalActivity`,
  `RadicalAcceptanceActivity`, `SelfCompassionActivity`,
  `OppositeActionActivity`, `DistressThermometerActivity`,
  `AcceptsActivity`, `LetterToPartActivity`, `DiaryCardActivity`,
  `ValuesActivity`

### 3. `TestFileUtil.fileAt()` — shared path-resolution helper

28 FindingTests had the same 4-line `private fun fileAt()`
helper. v0.29.0 extracts it to a shared utility:

```kotlin
package org.mindanchor.testing
object TestFileUtil {
    fun fileAt(relative: String): File
}
```

Each test imports the function with a function-level import
(`import org.mindanchor.testing.TestFileUtil.fileAt`), so
call sites stay as `fileAt(...)` — the diff is small per
file, and the helper itself is testable in one place.

Files:

- `app/src/test/java/org/mindanchor/testing/TestFileUtil.kt` (new)
- 28 FindingTests updated to use it across `support/`,
  `launcher/`, `letters/`, `settings/`, `report/`, `i18n/`,
  `accessibility/`, `ci/` packages

## Verification

- `./gradlew :app:detekt` clean (0 issues)
- `./gradlew :app:testDebugUnitTest` 1472/0/100% (was 1460
  in v0.28.2; +12 new tests in `ValuesFindingTest`)
- `./gradlew :app:assembleDebug` builds
- APK SHA-256: `A9C14A88C3380556543002E1A9F232C409C978358509D49C41EDB89D6E3BBA34`
- Phone + emulator install: `pm dump org.mindanchor` reports
  `versionCode=54, versionName=0.29.0`
- End-to-end on emulator (API 34, Android 14, 1080x2400):
  - Home renders Distress Thermometer first
  - "Open Support" → SupportActivity
  - Scroll to "More moments" — render order is **Opposite
    action → Distress thermometer → ACCEPTS → Letter to a part
    → Self-compassion break → Radical acceptance → Today's
    check-in → Interpersonal skills → What matters to me**
  - Tap "What matters to me" → ValuesActivity opens
  - All 8 value fields render (Relationships, Health, Work,
    Growth, Leisure, Spirituality, Community, Parenting)
  - Type a value into Relationships, tap Save → "Saved. Read
    it again when you want to." rendered
  - Back, re-open ValuesActivity → Relationships field shows
    the saved value (DataStore round-trip works)
  - `adb logcat -d -s AndroidRuntime:E *:F` shows 0 FATAL

## Files

### New (5)
- `app/src/main/java/org/mindanchor/support/SupportSurfaceActivity.kt`
- `app/src/main/java/org/mindanchor/support/ValuesActivity.kt`
- `app/src/main/java/org/mindanchor/support/ValuesScreen.kt`
- `app/src/main/java/org/mindanchor/support/ValuesPrefs.kt`
- `app/src/test/java/org/mindanchor/support/ValuesFindingTest.kt`
- `app/src/test/java/org/mindanchor/testing/TestFileUtil.kt`
- `RELEASE_NOTES_v0.29.0.md` (this file)

### Modified
- 9 activity files refactored to extend `SupportSurfaceActivity`
- `app/src/main/java/org/mindanchor/support/SupportScreen.kt`
  — added "What matters to me" entry
- `app/src/main/AndroidManifest.xml` — registered `ValuesActivity`
- `app/src/main/res/values/strings.xml` — added 14 `values_*`
  keys + `support_values_button`
- `app/src/test/java/org/mindanchor/support/SupportOrderFindingTest.kt`
  — added Values to the expected order, updated KDoc
- 28 FindingTests updated to use `TestFileUtil.fileAt`
- `app/build.gradle.kts` — `versionCode` 53 → 54, `versionName`
  "0.28.2" → "0.29.0"

## Privacy

No change from v0.28.0 / v0.28.1 / v0.28.2. The new ACT values
surface is a single-user, on-device, no-network feature. No
telemetry, no analytics, no hardcoded crisis line numbers, no
network calls. R1 still honored.

## Out of scope (still pending)

- AppWatchService SMS broadcast — needs `RECEIVE_SMS` runtime
  grant UI
- GroundMeTile — registered, exported, but not user-draggable
  in quick-settings panel by default
- Watch connect real root-cause fix — needs user's
  `adb logcat -s MindAnchor/HealthConnect:V` capture from real
  watch pair
- CodeRabbit on PR #34 — paused (too many commits);
  `@coderabbitai resume` unpauses
- Signing key — needed before F-Droid submission or Play
  Store; still in the "you do this once" stage per
  `docs/RELEASING.md`
- Clinician review of `docs/CLINICAL_REVIEW.md` — still
  pending; second item on the "Before the first public
  release" checklist
- Real-device beta run (M6) — user + 2-3 SHOs for a week

## Sources

- Hayes, S. C., Strosahl, K. D., & Wilson, K. G. (1999/2004).
  *Acceptance and Commitment Therapy*. Guilford Press.
- Wilson, K. G., & Murrell, A. R. (2004). *Values work in
  Acceptance and Commitment Therapy*. In S. C. Hayes,
  V. M. Follette, & M. M. Linehan (Eds.), *Mindfulness and
  Acceptance: Expanding the Cognitive-Behavioral Tradition*.
  Guilford Press.
- Wilson, K. G., & DuFrene, T. (2009). *Things Might Go Terribly,
  Horribly Wrong*: A guide to ACT. New Harbinger.
- Gratz, K. L., et al. (2006, 2014). ACT for BPD empirical
  studies (cited in `docs/research/14-v0.26.6-audit.md` §3.5).
- `docs/research/14-v0.26.6-audit.md` §3.5 — the
  v0.26.6-audit recommended the addition.
