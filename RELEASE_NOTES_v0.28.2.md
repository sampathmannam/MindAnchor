# v0.28.2 — Support screen "More moments" order fix

**Date:** 2026-08-16
**Status:** Shipped
**Author:** Mavis
**Scope:** Bug fix on top of v0.28.1. Re-orders the Support screen
"More moments" group; no new surfaces, no string changes, no
data-model changes. Pure fix.

## What ships

### Re-ordered "More moments" group on the Support screen

The v0.28.0 design spec
(`docs/superpowers/specs/2026-08-15-v0.28.0-bpd-strict-design.md` §D)
specified an in-the-moment → reflective ordering for the eight
"More moments" entries:

1. Opposite Action *(new in v0.28.0)*
2. Distress Thermometer *(new in v0.28.0)*
3. ACCEPTS *(new in v0.28.0)*
4. Letter to a Part *(new in v0.28.0)*
5. Self-compassion break *(v0.27.0)*
6. Radical acceptance *(v0.27.0)*
7. DBT Diary Card *(new in v0.28.0)*
8. Interpersonal skills (DEAR MAN / GIVE / FAST) *(v0.27.0)*

v0.28.0 and v0.28.1 shipped the eight entries, but the three
v0.27.0 entries (Self-compassion, Radical acceptance,
Interpersonal) were rendered BEFORE the five v0.28.0 entries.
This is the opposite of the spec's intent and the opposite of
what a person in crisis needs.

Driving v0.28.0–v0.28.1 end-to-end on the phone (Motorola
ZD2232FCR5) surfaced the mismatch: the v0.27.0 reflective
practices were the closest taps above the fold, while the v0.28.0
in-the-moment skills were further down the screen. v0.28.2
re-orders to match the spec.

The fix is a pure reorder of the eight `TextButton { … }` call
sites in `SupportScreen.kt`. No string changes, no
manifest changes, no data-model changes, no new surface. The
activity intent, semantics, and click handlers are byte-for-byte
the same as v0.28.1 — only the position in the Column changed.

### Why ordering matters for the target user

A person in crisis who has just scrolled past the DBT crisis
skills (STOP / TIPP / 5-4-3-2-1) is still in a distressed
window. The reflective practices (Self-compassion, Radical
acceptance, Diary Card, Interpersonal) need a person who has
already settled. Placing them before the in-the-moment skills
(Opposite Action, Distress Thermometer, ACCEPTS, Letter to a
Part) makes the wrong surface the closest tap. The fix puts
the in-the-moment skills first so the right tool is the one a
distressed hand finds.

### New FindingTest: `SupportOrderFindingTest.kt`

Three new positive-shape FindingTests in
`app/src/test/java/org/mindanchor/support/SupportOrderFindingTest.kt`:

1. **in-the-moment skills come before reflective skills in
   SupportScreen** — pins the full expected 8-entry sequence
   in order. A regression that swaps any pair flips the test
   red with a per-step failure message.
2. **no reflective entry appears before any in-the-moment
   entry** — stronger partition pin. A regression that puts
   any reflective entry (Self-compassion, Radical,
   DiaryCard, Interpersonal) ahead of any in-the-moment entry
   (Opposite, Distress, ACCEPTS, Letter) trips this test,
   regardless of how the rest of the list is ordered.
3. **all eight 'More moments' activities are still wired in
   SupportScreen** — coverage pin. The v0.28.2 fix is a
   reorder, not a removal. A regression that drops any of the
   eight activities flips this test red.

The FindingTest scans `SupportScreen.kt` for the
`context.startActivity(Intent(... <Activity>::class.java, ...)`
pattern, scoped to `org.mindanchor.support.*`, and asserts both
presence and order across the eight activities. The same
startActivity pattern is the unique per-entry marker — every
"More moments" entry is its own `TextButton(onClick = { runCatching
{ context.startActivity(Intent(context, X::class.java)…) } })`,
so the byte offset of the startActivity call is a reliable
proxy for the TextButton call site.

## Verification

- `./gradlew :app:detekt` clean (0 issues)
- `./gradlew :app:testDebugUnitTest` 1460/0/100% (was 1457 in
  v0.28.1; +3 new tests in `SupportOrderFindingTest`)
- `./gradlew :app:assembleDebug` builds
- APK SHA-256: `05E49F0E9BA702688ECBCD65EF0DA40A49E1C28E2940AD4DD98C6FAC5D45F64E`
- Phone install (Motorola ZD2232FCR5, API 37, Android 17):
  - `pm dump org.mindanchor` reports `versionCode=53,
    versionName=0.28.2`
  - End-to-end on phone: home renders Distress Thermometer
    first; "Open Support" → SupportActivity; scroll to "More
    moments" → render order is **Opposite action → Distress
    thermometer → ACCEPTS → Letter to a part → Self-compassion
    break → Radical acceptance → Today's check-in →
    Interpersonal skills** (verified by `uiautomator dump`
    bounds y=1189 → y=2197)
  - Diary Card data from v0.28.1 round-trips through the
    upgrade; `diary_card_2026-08-16` still has the saved
    `test_urge_v282_drive` entry
  - `adb logcat -d -s AndroidRuntime:E *:F` shows 0 FATAL
    exceptions

## What is NOT in v0.28.2

This is a pure bug-fix release. No new surfaces, no string
changes, no data-model changes, no manifest changes, no
dependency changes. The five new v0.28.0 activities
(Opposite Action, Distress Thermometer, ACCEPTS, Letter to a
Part, Diary Card) and the three v0.27.0 activities
(Self-compassion, Radical acceptance, Interpersonal skills)
are unchanged from v0.28.1 — only their position in the
"More moments" Column changed.

R1 (no hardcoded crisis line numbers) is still honored. The
Support screen footer remains the generic R1 fallback
("MindAnchor is a wellness tool, not a treatment, and not a
medical device. If you are in danger right now, call your
local emergency number.").

## Files

### Modified
- `app/src/main/java/org/mindanchor/support/SupportScreen.kt` —
  reordered eight `TextButton { … }` call sites in the
  "More moments" group; added a v0.28.2 block comment
  documenting the in-the-moment → reflective rationale and
  the spec reference
- `app/build.gradle.kts` — `versionCode` 52 → 53, `versionName`
  "0.28.1" → "0.28.2"

### New
- `app/src/test/java/org/mindanchor/support/SupportOrderFindingTest.kt`
  — 3 FindingTests pinning the order
- `RELEASE_NOTES_v0.28.2.md` (this file)

## Privacy

No change from v0.28.0 / v0.28.1. This is a pure layout
reorder, not a feature change, so the privacy stance is
unchanged. Still no telemetry, no analytics, no
hardcoded crisis line numbers, no network calls.

## Out of scope (deferred from earlier releases, still pending)

- §3.5 ACT values clarification (Hayes 2004) — v0.29.x
- Tamil translator — `values-ta/strings.xml` still placeholder
  English across ~190 keys
- LICENSE ratification (GPL v3 vs Apache 2.0) — pending user
  decision
- AppWatchService SMS broadcast — needs `RECEIVE_SMS` runtime
  grant UI
- GroundMeTile — registered, exported, but not user-draggable
  in quick-settings panel by default
- Watch connect real root-cause fix — needs user's
  `adb logcat -s MindAnchor/HealthConnect:V` capture from real
  watch pair
- CodeRabbit on PR #34 — paused (too many commits);
  `@coderabbitai resume` unpauses
