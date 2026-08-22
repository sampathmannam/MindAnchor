# v0.28.1 — Bug-fix release on top of v0.28.0

**Date:** 2026-08-16
**APK SHA-256:** `A6D2CE88AFC7E7AA9C695D65E5D13B7A8103FF79DC0166DCAC50AD920A8D84DE`
**Detekt:** clean
**Tests:** 1457 / 0 (unchanged from v0.28.0)

## What this is

A focused bug-fix release on top of v0.28.0. Found by driving the
app end-to-end on the real Motorola phone (ZD2232FCR5) + emulator.
Three real bugs fixed, all BPD-safety related. Plus the §2.4
Settings rename from the audit.

## Fixes

### 1. CRITICAL — DiaryCardScreen crashed on first save

**Symptom.** Tapping Save on the DBT Diary Card crashed the activity
with `ClassCastException: kotlin.collections.EmptyList cannot be
cast to androidx.compose.runtime.snapshots.SnapshotStateList` at
`DiaryCardScreen.kt:79`.

**Cause.** The `week` state was declared as
`mutableStateOf<List<Pair<LocalDate, DiaryCardEntry>>>(emptyList())` —
a plain `List<>`. Inside the `LaunchedEffect(saved)` the code
cast it to `SnapshotStateList` to call `.clear()` / `.add()`, which
threw the moment the cast failed on the initial empty list.

**Fix.** `week` is now `mutableStateListOf<Pair<LocalDate,
DiaryCardEntry>>()` (a real `SnapshotStateList`). The cast is gone.
`.clear()` / `.addAll()` are the public API.

**Verified on phone.** Typed `test_urge_v281` into urge, tapped
Save — got "Saved for today. Tomorrow will be its own." No crash.

### 2. DistressThermometer 86+ "support" button just dismissed

**Symptom.** At the high-distress band (86+), the screen showed an
"Open Support" button. Tapping it dismissed the activity and
returned to home — silent failure, no Support group opened.

**Cause.** The `TextButton`'s `onClick` was wired to `onDone` (the
`finish()` callback). The user-facing copy implied a real
destination. R1 was honored (no numbers) but the affordance was
broken.

**Fix.** New `onOpenSupport: () -> Unit` callback on
`DistressThermometerScreen`. The 86+ button now calls
`onOpenSupport`. `DistressThermometerActivity` wires it to start
`SupportActivity` and finish. R1 still honored.

**Verified on phone.** Slider at 90, "Open Support" button →
`SupportActivity` opens.

### 3. DistressThermometer 86+ copy was a false promise

**Symptom.** At the high-distress band, the suggestion text said
"Tap a number below, or open the lock-screen tile. The line is
free." — but there are no numbers below (R1), no hardcoded
helpline, and no verified lock-screen tile. The text was a
false promise to a user in the high-distress band.

**Cause.** v0.28.0 carried over old copy from a pre-R1 spec.

**Fix.** Replaced with: "You are not alone. The Support group has
DBT skills and a place to add the people you would actually call.
Open it below when you are ready." BPD-safe: no directive
language, no false promise, no hardcoded numbers, no unverified
lock-screen tile claim.

**Verified on phone.** Slider at 90, copy matches.

### 4. §2.4 Settings rename (audit flag)

**Audit flag:** §2.4 of `docs/research/14-v0.26.6-audit.md`.
"Friction / Pauses / Watch" — the user-facing names framed the
behavior as restriction, which BPD literature (Fruzzetti 2006;
Linehan 1993 ch. on validation) flags as the wrong tone for a
person in a pre-crisis state.

**Fix.**
- "Pauses" group → "When you want a breath"
- "Pauses" group description → "A small pause before an app opens,
  on your terms."
- "Where the pause applies" section → "Across all your apps"
- (Internal class names unchanged — `FrictionGate.kt` /
  `AppWatchService.kt` keep their technical names. The user-facing
  labels are what changed.)

**Verified on phone.** Settings → "When you want a breath" group
opens with the new description. Scrolling down, the "Across all
your apps" section shows the same explainer with the new title.

## What did NOT change

- The 5 new research surfaces (Distress Thermometer, Opposite
  Action, ACCEPTS, Letter to a Part, DBT Diary Card) and their
  positions in the Support group.
- The home redesign (Distress Thermometer first, no OneThingCard).
- R1 honored — no hardcoded helpline numbers anywhere.
- All v0.28.0 tests, FindingTests, and release artifacts.
- The 2am shell, the 8 "More moments" entries, BPD-safe design
  defaults.

## Files

```
app/src/main/java/org/mindanchor/support/DiaryCardScreen.kt
app/src/main/java/org/mindanchor/support/DistressThermometerActivity.kt
app/src/main/java/org/mindanchor/support/DistressThermometerScreen.kt
app/src/main/res/values/strings.xml
app/src/main/res/values-ta/strings.xml
app/build.gradle.kts
```

## Privacy

BPD is private to the user. The release notes, README, and PR
descriptions do not name BPD. The audit document
(`docs/research/14-v0.26.6-audit.md`) is the internal map.

## Verified on

- Motorola ZD2232FCR5 (API 37, Android 17, real device)
- Emulator (API 34)

## References

- Linehan 1993, DBT Distress Tolerance, Module 1
- Schwartz 1995, Internal Family Systems Therapy
- Neff 2003, Self-compassion
- Lieberman 2007, affect labelling
- Gross 1998, emotion regulation process model
- Fruzzetti 2006, *The High-Conflict Couple*
- `docs/research/14-v0.26.6-audit.md` §2.3, §2.4

MindAnchor remains adjunct, not treatment.
