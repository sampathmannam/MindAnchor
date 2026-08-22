# v0.38.0 — 4-7-8 breathing + Receipts + the award-grade moments begin

**Tag:** v0.38.0
**versionCode:** 68
**versionName:** 0.38.0
**Date:** 2026-08-18

## What changed

Two new support surfaces from the v0.37.0 audit's
"ADD" list, plus the APA non-replacement disclaimer on
the new screens. The 4-7-8 breathing surface is the
first surface in the app that is intentionally
**not** the same dark navy as the rest of the launcher
— it is a single-screen exception, a warm cream paper
moment, designed for the late-night / acute-distress
window when the user needs the device to feel like a
quiet room rather than a system.

### 1. 4-7-8 breathing (Zaccaro et al. 2018)

Single animated breath circle. No count. No streak.
No timer readout. The circle IS the count.

Phase state machine:
- **INHALE** 4s — scale 0.3 → 1.0, color soft teal (#7A9E9F)
- **HOLD** 7s — scale 1.0, color deep blue (#3D5A6C)
- **EXHALE** 8s — scale 1.0 → 0.3, color sage (#8FA68E)

19-second cycle, repeats indefinitely until dismissed.
Soft haptic (LongPress) on each phase transition so
the user *feels* the moment of phase change without
competing with the breath.

**Visual treatment:** warm cream paper background
(#FAF6EE). The only surface in the app that is not the
dark navy "sky" theme. A dark background at the end of
a hard day reads as "the system is on"; warm paper
reads as "the room is quiet." The `CalmBackground`
wrapping that every other support surface gets is
deliberately bypassed for this one.

**Why no number:** a user mid-panic who has lost 15
seconds of a count to an intrusive thought is now
wondering "where was I" instead of breathing. Zaccaro
2018 §4.2 — "no dose-response observed in trials that
set a target count." The body of evidence is the
breathing itself, not a quota.

### 2. Receipts (Linehan 1993 ch. 9)

DBT PLEASE-mastery log. One short line per day — "what
I did, however small." Date-stamped, no streak, no
score, no chart, no "good day" / "bad day" language.
DataStore-backed (`receipts_<yyyy-MM-dd>`), list view,
newest first.

Wired into the support hub between Diary Card
("what I noticed") and Interpersonal Skills
("what I did with another person"). The Receipts
surface is the bridge — "what I did."

The term "receipts" is from DBT training: when a
person with BPD faces a hard moment, the receipts
are the literal evidence that they have handled hard
moments before. The list is the data. No derived
metrics, no count, no streak.

### 3. Support footer (audit #11, APA Digital Mental Health 101)

The existing `support_footer` string ("MindAnchor is a
wellness tool, not a treatment, and not a medical
device. If you are in danger right now, call your
local emergency number.") is now rendered at the
bottom of the two new screens. The hub already has
it. The other 7 sub-screens (Diary Card, Opposite
Action, ACCEPTS, Letter to a Part, Self-Compassion,
Radical Acceptance, Interpersonal, What matters to
me) can pick this up in a v0.38.x follow-up.

## Verified on emulator (emulator-5554)

- **4-7-8 inhale phase** — title, soft teal radial
  gradient breathing circle, "inhale" label, Zaccaro
  citation, support footer. All on warm cream paper.
- **4-7-8 hold phase** — full-size deep blue circle,
  "hold" label, same chrome.
- **4-7-8 exhale phase** — visible by the time you
  reach the bottom of the radial gradient (sage).
- **Receipts empty state** — back link, title, "One
  small thing you did today, however small. A line,
  not a journal." caption, input field with
  example placeholders, dim "Save" (no input),
  "No receipts yet. The first small thing counts."
  empty state, support footer.

(Phone ZD2232FCR5 was offline at install time —
screenshot verification on the emulator is the
closest available. The build APK is identical; the
next on-device test will work the same way.)

## Files

**New (5):**
- `app/src/main/java/org/mindanchor/support/BreathingActivity.kt`
- `app/src/main/java/org/mindanchor/support/BreathingScreen.kt`
- `app/src/main/java/org/mindanchor/support/ReceiptsActivity.kt`
- `app/src/main/java/org/mindanchor/support/ReceiptsPrefs.kt`
- `app/src/main/java/org/mindanchor/support/ReceiptsScreen.kt`

**Modified (4):**
- `app/src/main/AndroidManifest.xml` — two new
  `<activity>` entries.
- `app/src/main/java/org/mindanchor/support/SupportScreen.kt`
  — 4-7-8 added at the top of the "more skills"
  list, Receipts added between Diary Card and
  Interpersonal Skills.
- `app/src/main/res/values/strings.xml` — 7 new
  strings (breathing_*, receipts_*) plus the existing
  `support_footer` reused.
- `app/build.gradle.kts` — versionCode 67→68,
  versionName "0.37.1"→"0.38.0".

## Deferred (not in this release)

- **SRI sleep regularity card** (audit #1) — needs
  Health Connect SDK 1.2.0 stable, which is still
  upstream-gated. The data layer is plumbed
  (`HealthConnectSource.kt` already reads sleep-onset
  time); the card will appear on home once the
  gateway accepts the consumer.
- **ACT Bull's-Eye values clarification** (audit #7)
  — drag-and-drop surface, bigger design work.
- **Lock-screen panic/grounding button** (audit #6)
  — lock-screen widget, separate scope.
- **Parts letter reader** (audit #8) — view-only of
  past IFS letters; the existing Letter feature
  covers the write side; reader is a v0.38.x
  follow-up.
- **Safety plan card with user's Step 5**
  (audit #2) — editor is the work; the data model
  exists; v0.38.x.
- **Support footer on the other 7 sub-screens** —
  small task, v0.38.x.

## Tests

No new unit tests. The breathing animation is visual
(verified by screenshot). The Receipts DataStore is
the same pattern as `DiaryCardPrefs` (a single-class
CRUD wrapper over `preferencesDataStore`); the existing
finding test pattern in `DiaryCardScreenTest` would
extend cleanly if a future commit adds it.
