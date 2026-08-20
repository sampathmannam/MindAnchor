# MindAnchor Shared Components (components.md)

> **Status:** v0.62.7 (2026-08-20) | **Stack:** Kotlin + Compose + Material 3
> **Project rule:** no `material-icons-extended` imports. Custom-drawn icons
> via `androidx.compose.foundation.Canvas` + `drawLine()` / `drawCircle()`.

---

## QuickNotesCard (the heart of the home surface)

**File:** `app/src/main/java/org/mindanchor/launcher/HomeScreen.kt` (function
declaration around line 2068, call site at line 1117)

**What it does:** The 3-chip kind picker (Quick note / Task / Reminder) +
multi-line input + OutlinedButton save. v0.62.7 added `!bang` routing —
typing `!ground` and tapping Save routes to GroundMe instead of saving a
literal note.

**Visual structure (from top to bottom):**
1. **Kind picker** (single row, `Row` + `Arrangement.spacedBy(8.dp)`)
   - 3 `FilterChip`s side by side: "Quick note" (neutral), "Task" (teal),
     "Reminder" (indigo)
   - Selected chip: soft fill + dark teal/indigo label + 1.5dp border
   - Unselected: transparent + secondary-tinted label
2. **Pin-to-home toggle** (Row, only for "Task" / "Reminder" kinds, also
   shown for Quick note since v0.45.0)
   - Label: "Future notes on home" (v0.62.5 wording — was "Pin to home")
   - Subtitle: state-dependent copy ("Pinned notes appear on the home screen.
     Unpinned notes live only in the Notes tab.")
   - `Switch` on the right
3. **Time picker** (FlowRow of chips, only for Task / Reminder)
   - Task: "no due / in 1 hour / in 3 hours / tomorrow / in 3 days"
   - Reminder: "in 5 min / in 15 min / in 1 hour / in 3 hours"
4. **OutlinedTextField** (draft input)
   - `placeholder`: "Jot something down — it saves here, in order, with the time."
   - `minLines = 3, maxLines = 5`
   - trailing `×` icon (TextButton) when draft non-blank — clears draft
5. **OutlinedButton** (save) — pill shape with 1dp teal border
   - Label: "Save" / "Save as task" / "Save reminder" depending on kind
   - Hides entirely when no valid input (v0.53.0 progressive disclosure)
   - On click: captures `nowMs = System.currentTimeMillis()`, fires the
     appropriate onSave* callback, then clears draft + resets pin

**Key behavior:**
- `onSave(draft, pinned)` for Quick note
- `onSaveTask(draft, dueMs, pinned)` for Task
- `onSaveReminder(draft, atMs, pinned)` for Reminder
- `buttonVisible` gate hides button when no valid input

---

## MoodStrip (the mood check-in row)

**File:** same `HomeScreen.kt` (around line 300-700)
**Layout:** A single `Row` of 5 emoji + label pairs + a 6th "?" Skip button.

| Index | Emoji | Label (v0.55.0 readable names) |
|-------|-------|-------------------------------|
| 0 | 🥺 | Crushed |
| 1 | 😕 | Heavy |
| 2 | 😐 | Steady |
| 3 | 🙂 | Light |
| 4 | 😊 | Bright |
| 5 | ? | Skip |

**v0.55.0 typography:** `labelMedium` (was `labelSmall` in pre-v0.55.0).
**v0.62.2 contrast fix:** the Quick note selected chip uses `KindTealFg` label
on light teal fill (was `textPrimary` which made the label invisible on
the light cream sky).

**Interaction:**
- Tap a mood → adds a mood log entry (just the emoji, no reflection)
- Long-press a mood → opens the reflection annotation dialog (v0.58.0)
- The "?" Skip button uses `Role.Button` semantics

---

## NotesTab (the Notes surface)

**File:** `app/src/main/java/org/mindanchor/launcher/NotesTab.kt` (or inline in HomeScreen.kt depending on version)
**Layout:** `LazyColumn` with `stickyHeader` for day groups (planned but
not yet shipping — v0.55+ uses a plain Column forEach).

**Each day group:**
1. Sticky header chip: "Today" / "Yesterday" / "2 days ago" / date in `d MMM` format
2. Notes for that day, ordered by `updatedAt DESC`

**Each note row:**
- `SwipeToDismissBox` (v0.54.0) — left swipe = pin, right swipe = delete
- Background color: sage (pin) / red-300 (delete), direction-driven
- On delete: Snackbar "Note deleted" + Undo → `viewModel.restoreNote(note)`
- On pin: Snackbar "Pinned" + Undo → re-applies pin
- Tap row → opens `NoteDetailScreen` (full markdown render)

**Date filter strip (v0.53.0):** Horizontal row of pill chips at top
representing each day the user has notes. Tap to filter. (Currently
rendering but filter behaviour deferred to v0.55.1+.)

**OnThisDay section:** "X notes from this day in past years" — pulls
notes from prior years with the same MM-DD.

---

## NowWhatShell (the 2 AM shell)

**File:** `app/src/main/java/org/mindanchor/launcher/NowWhatShell.kt`

**Context:** Shown when the user long-presses the home card or the clock at
night. Surfaces three options:
1. "I want to ground" → GroundMe
2. "I want to talk to someone" → opens the named person picker (Settings → Your plan)
3. "I want to sleep" → BreathingScreen with a slower 4-7-8 cadence

**Visual structure:**
- Title: "It's late. What do you need right now?"
- Three big `OutlinedButton`s stacked, each spanning the full width
- Soft pulsing animation (v0.40.0 "flash-pulse" infinite transition)
- Background: the same slow sky, but darkened slightly

**v0.25.13 layout bug fix:** the original v0.25.10 design had a
`Box(modifier = Modifier.fillMaxSize())` inside a Column, which collapsed
the second and third buttons to zero height. v0.25.13 removed the fillMaxSize.

---

## Bottom nav (the only fixed nav)

**File:** `app/src/main/java/org/mindanchor/launcher/HomeScreen.kt` (inline in the `Scaffold`)

**Two icons:** "search" (left) and "Settings" (right). NOT 2-3 tabs.
The "search" icon doesn't actually open a search screen — it focuses the
search bar at the top of the home surface. The Settings icon opens Settings.

**v0.5.5 3-button overlap fix:** `Modifier.navigationBarsPadding()` on the
NavigationBar (the v0.55.0 fix) handles 3-button system nav devices. v0.55.0+
also adds `padding(bottom = 48.dp)` for the 3-button case.

---

## Bang hint UI (the `!` chip)

**Where:** Right end of the search bar at the top of the home card.

**Visual:** A small `Text("!")` styled with `ActionAccentFg` (teal-700),
inside a `Box` with `Modifier.semantics { contentDescription = "..." }`.
Tapping it opens the bang help dialog (a list of all 7 bangs with a
one-line description of each).

---

## Snackbar patterns

**Material 3 SnackbarHost** wrapped in a Box (v0.62.5 change for F1 / F2 Undo).

**Undo pattern (v0.62.4 + v0.62.5):**
- The action (e.g. note delete) fires immediately
- A `Snackbar` with action label "Undo" appears for ~4s
- Tapping Undo dispatches the inverse action (`restoreNote` for delete,
  re-pin for unpin)
- A `Log.d` inside the `LaunchedEffect` after `showSnackbar(...)` is
  added during dev to debug whether the action tap registered

**Save-status subtitle (v0.62.6):**
- After saving a note, the home subtitle reads "✓ Saved 0s ago" with a
  custom-drawn checkmark + 1-second tick that updates the number
- Falls back to the contextual subtitle after 60s
- A11y label: "Saved N seconds ago. Note is on disk." via `Modifier.semantics`

---

## Custom-drawn icons (no material-icons-extended)

All icons are drawn inline with `androidx.compose.foundation.Canvas` +
`drawLine()` / `drawCircle()`. Examples:
- **Chevron** (`›`): two short diagonal lines, `strokeWidth = 1.5.dp`
- **Checkmark** (v0.62.6, for the "Saved Xs ago" subtitle): two lines forming a √, stroke 2dp, color `ActionAccentFg`
  - Coordinates (normalized 0-1):
    - Short arm: `(0.20, 0.55) → (0.42, 0.78)`
    - Long arm:  `(0.42, 0.78) → (0.82, 0.30)`
- **Bang hint "!"**: literal "!" character in a Compose Text, styled

> This project rule (no material-icons-extended) keeps the APK lean and
> lets the design language stay consistent. New icons should be added as
> `Canvas { drawLine(...) }` blocks inline at the call site.
