# v0.37.1 — Fine-finish pass: home polish + letter inbox copy

**Tag:** v0.37.1
**versionCode:** 67
**versionName:** 0.37.1
**Date:** 2026-08-18

## What changed

Three small screenwise polish fixes from the v0.37.0 audit pass.
No new features, no API changes, no architecture changes. Just
visible fine-finish work on three of the most-used surfaces.

### 1. Home — TopEnd button style consistency (`f054737`)

The three stacked TopEnd buttons (Letters / notes / history) now
share one quiet visual style. Pre-v0.37.1, "Letters" used the
default `TextButton` style (which reads as a primary CTA), while
"notes" and "history" used `labelMedium` + `sky.textSecondary`
(which reads as quiet nav). The mismatch pulled the eye to the
wrong corner.

**Fix:** `HomeScreen.kt:2209-2239` — applied `labelMedium` +
`sky.textSecondary` to "Letters" so the three buttons read as
one column.

### 2. Home — Needs 2×2 grid uniform height (`f054737` + `f3bfdac`)

The four "needs" doors (Be heard / A moment / Check in / Get
through this) had unequal caption lengths (2-4 lines) which made
the 2×2 grid look like two unrelated rows.

**v0.37.1 first attempt (f054737)** tried `Modifier.height(IntrinsicSize.Min)`
+ per-cell `fillMaxHeight()`. Compiled and installed, but the
4-line "A moment" caption forced both rows to ~140dp, leaving
large empty blocks under the 3-line cells.

**v0.37.1 v2 (f3bfdac)** leaves row heights natural and instead
caps the caption at `maxLines = 3, overflow = Ellipsis`. All
four cells land at a uniform 96dp (the existing
`heightIn(min = 96.dp)` floor). The trade-off: lines 4 of
"A moment" and "Be heard" get a clean ellipsis. The user can
long-press or read the full text in the support hub.

### 3. Letter inbox — drop redundant "No letters yet" (`6703c58`)

The letter inbox empty state had a "No letters yet" title AND
a body that started with "No letters yet." The no-model body
read "No letters yet. Phi-4 isn't installed — open Settings
→ Model to install it." — the same line of text twice on screen.

**Fix:** `strings.xml:839` — `letters_empty_no_model` now starts
with "Phi-4 isn't installed —" so the title is the only
"No letters yet" on the page. The non-no-model body
(`letters_empty_body`) was already correct.

## What didn't change

- `app/src/main/java/org/mindanchor/launcher/NowWhatShell.kt`
  (2am shell) — already clean.
- `app/src/main/java/org/mindanchor/settings/SettingsScreen.kt`
  — already uses thoughtful `GroupRow` design (mint-teal
  titles, no chevrons, primary-coloured tappable rows).
  KDoc at `SettingsScreen.kt:540-551` documents the
  "colour carries the whole job" trade-off.
- `app/src/main/java/org/mindanchor/support/SupportScreen.kt`
  — well-designed DBT-first crisis + skills surface. No
  obvious polish wins.

## Verified on phone ZD2232FCR5

1. **Home (night mode)** — top-right "Letters" matches the
   quiet style of "notes" / "history". 2×2 grid: 4 uniform
   96dp cells. Captions truncated cleanly to 3 lines.
2. **Letter inbox (empty, no model)** — title "No letters yet"
   + body "Phi-4 isn't installed — open Settings → Model to
   install it." (no redundant "No letters yet" prefix).

## Files

**Modified:**
- `app/src/main/java/org/mindanchor/launcher/HomeScreen.kt`
  — Letters TextButton style.
- `app/src/main/java/org/mindanchor/launcher/NeedsCard.kt`
  — caption maxLines + TextOverflow.Ellipsis.
- `app/src/main/res/values/strings.xml` — `letters_empty_no_model`.
- `app/build.gradle.kts` — versionCode 66→67, versionName
  "0.37.0"→"0.37.1".

**New:**
- `RELEASE_NOTES_v0.37.1.md`.

## Tests

The finding test `SetupWizardStepTest` (last touched in v0.37.0)
still passes. No new tests added; the polish is copy + layout,
not behaviour.
