# MindAnchor Extractable Components (extractable-components.md)

> **Status:** v0.62.7 (2026-08-20) | **Stack:** Kotlin + Compose + Material 3
>
> The redesign workflow reads this file to decide which UI primitives to
> extract as `<sd-component>` tags in design drafts. **Focus is on layout
> components first** — those appear on every page and benefit most from
> extraction. Skip basic M3 primitives (Button, Card) — those are too
> simple to extract and are better as inline HTML in drafts.

---

## Layout Components (appear on most pages)

### SlowSky (the signature background)

- **Source:** `app/src/main/java/org/mindanchor/ui/CalmBackground.kt` + `ui/SkyMath.kt` + `ui/SkyColorScheme.kt`
- **Category:** layout
- **Description:** The time-of-day-driven sky gradient behind every MindAnchor surface. The M3 `background` is `Color.Transparent` so the sky never gets covered.
- **Extractable props:**
  - `clock` (LocalDateTime, default: `LocalDateTime.now()`)
  - `darkTheme` (Boolean, default: derived from clock + sunset hours)
- **Hardcoded elements:** the exact gradient stops, the color tokens, the noon-sun position math, the 8% / 12% white tints for layer elevations
- **Why extract:** The sky is the *signature* of the app. Every mockup must use it, otherwise the result is a generic Android launcher, not MindAnchor.

### BottomNav (the only fixed nav)

- **Source:** `app/src/main/java/org/mindanchor/launcher/HomeScreen.kt` (inline in the `Scaffold`)
- **Category:** layout
- **Description:** Two NavigationBarItem icons — "search" (left) and "Settings" (right). Tapping "search" focuses the home search bar; tapping "Settings" opens Settings.
- **Extractable props:**
  - `onSearch` (callback, default: empty)
  - `onSettings` (callback, default: empty)
  - `activeItem` (string, default: empty — there is no active highlight)
- **Hardcoded elements:** "search" and "Settings" labels, the `Icons.Outlined.Search` and `Icons.Outlined.Settings` icons (could be custom-drawn for consistency with project rule)
- **Why extract:** Always at the bottom of the home + notes surfaces. The redesign should consider whether this two-icon nav is still right, or whether the redesign should move to a single FAB / hamburger / sheet.

### Drawer (the everything-else nav)

- **Source:** `app/src/main/java/org/mindanchor/launcher/HomeScreen.kt` (the `ModalNavigationDrawer`)
- **Category:** layout
- **Description:** Left-edge swipe reveals a full drawer with all surfaces + a search/bang bar at the top + a "Set as home" CTA at the bottom.
- **Extractable props:**
  - `isOpen` (Boolean, default: false)
  - `onDismiss` (callback)
  - `onNavigate(surface)` (callback)
  - `onBang(bang)` (callback)
  - `isHomeLauncher` (Boolean, default: checked from `RoleManager`)
- **Hardcoded elements:** the list of surfaces and their order, the bang help copy
- **Why extract:** The Drawer is the only way to reach most surfaces. The redesign should consider whether to flatten it (more bottom nav items) or restructure (sheet-based nav).

### TopBar (the home card's top)

- **Source:** inline in `HomeScreen.kt` — a `Column` with the clock, greeting, and date
- **Category:** layout
- **Description:** A clock (`displayLarge`), greeting (`titleLarge`), date (`bodyMedium`), and the v0.62.6 save-status subtitle. NOT a Material 3 TopAppBar.
- **Extractable props:**
  - `clock` (LocalDateTime, default: `LocalDateTime.now()`)
  - `greeting` (string, default: derived from clock)
  - `date` (LocalDate, default: `LocalDate.now()`)
  - `saveStatus` (SaveStatus enum: Idle / JustSaved, default: Idle)
  - `secondsAgo` (int, default: 0)
- **Hardcoded elements:** the time-of-day → greeting mapping, the 1-second tick, the custom checkmark Canvas
- **Why extract:** The "what's the time + what just happened" content is identical on every redesign variant. Don't redesign this; redesign the rest of the home card around it.

---

## Basic Components (used across pages)

### QuickNotesCard (the composer)

- **Source:** `app/src/main/java/org/mindanchor/launcher/HomeScreen.kt` (function declaration around line 2068)
- **Category:** basic
- **Description:** 3-chip kind picker (Quick note / Task / Reminder) + pin toggle + time picker + multi-line input + OutlinedButton save.
- **Extractable props:**
  - `kind` (int: 0 = Quick, 1 = Task, 2 = Reminder, default: 0)
  - `draft` (string, default: "")
  - `pinned` (Boolean, default: false)
  - `onKindChange(int)` (callback)
  - `onPinnedChange(Boolean)` (callback)
  - `onDraftChange(string)` (callback)
  - `onSave(draft, pinned)` (callback)
  - `onSaveTask(draft, dueMs, pinned)` (callback)
  - `onSaveReminder(draft, atMs, pinned)` (callback)
- **Hardcoded elements:** chip labels, "Future notes on home" copy, time-picker chip labels ("in 5 min", "tomorrow", etc.), save button label
- **Why extract:** The composer is a self-contained component. The redesign should consider whether to keep it inline in the home card or surface it as a full page.

### MoodStrip (the mood row)

- **Source:** inline in `HomeScreen.kt`
- **Category:** basic
- **Description:** A row of 5 emoji + label pairs (Crushed / Heavy / Steady / Light / Bright) + a "?" Skip button.
- **Extractable props:**
  - `moods` (List<{emoji, label, key}>, default: 5 hardcoded)
  - `onMoodTap(key)` (callback)
  - `onMoodLongPress(key)` (callback, opens reflection dialog)
  - `onSkip()` (callback)
- **Hardcoded elements:** the 5 emoji + label names, the typography (`labelMedium` per v0.55.0)
- **Why extract:** The mood strip is the daily check-in trigger. The redesign should consider whether to drop the emoji (move to text-only "names") or keep them.

### NotesDayStrip (the date filter row)

- **Source:** `app/src/main/java/org/mindanchor/launcher/NotesDayStrip.kt` (or inline; varies by version)
- **Category:** basic
- **Description:** A horizontal row of pill chips, one per day the user has notes, used to filter the Notes list.
- **Extractable props:**
  - `days` (List<LocalDate>, default: all days the user has notes)
  - `selected` (LocalDate?, default: null)
  - `onSelect(date)` (callback)
- **Hardcoded elements:** the "Today" / "Yesterday" / "2 days ago" / "d MMM" labels
- **Why extract:** It's a small self-contained widget. The redesign should keep the date-strip pattern; consider whether to also include week-level chips.

### BangHint (the `!` chip)

- **Source:** `app/src/main/java/org/mindanchor/launcher/HomeScreen.kt` (the `Text("!")` styled with `ActionAccentFg`)
- **Category:** basic
- **Description:** A small `!` character inside a `Box` at the right end of the home search bar. Tapping opens the bang help dialog.
- **Extractable props:**
  - `onTap` (callback)
- **Hardcoded elements:** the "!" character, the `ActionAccentFg` color
- **Why extract:** Bang commands are a key UX. The redesign should consider whether to keep the `!` chip or replace it with a more discoverable affordance.

### SettingsCard (a single settings row)

- **Source:** `app/src/main/java/org/mindanchor/settings/SettingsScreen.kt` (inline; ~100s of them)
- **Category:** basic
- **Description:** A `Surface` with a 1dp outline and 8dp corner radius, containing a label + sub-label + an action (Switch, OutlinedButton, etc.).
- **Extractable props:**
  - `title` (string)
  - `subtitle` (string, default: null)
  - `action` (@Composable slot, default: empty)
  - `onClick` (callback, default: empty — makes the whole card tappable)
- **Hardcoded elements:** the outline color (from sky), the corner radius, the typography (titleMedium + bodySmall)
- **Why extract:** Used 30+ times across Settings. A redesign should establish a clean SettingsCard pattern first, then build the sections.

---

## Components to NOT extract (too simple, inline in drafts)

- **OutlinedButton:** Material 3 primitive. Use inline.
- **FilterChip:** Material 3 primitive. Use inline.
- **Switch:** Material 3 primitive. Use inline.
- **OutlinedTextField:** Material 3 primitive. Use inline.
- **Snackbar / SnackbarHost:** Material 3 primitive. Use inline.

---

## Summary — what to extract first

For the redesign, extract in this order:

1. **SlowSky** — every mockup uses it; this is the signature
2. **BottomNav** — every home / notes surface uses it
3. **TopBar** — every home / notes surface uses it (clock + greeting + date + save status)
4. **Drawer** — every sub-surface uses it as a back / nav affordance
5. **QuickNotesCard** — the heart of the home surface
6. **MoodStrip** — the daily check-in trigger
7. **NotesDayStrip** — the Notes tab date filter
8. **SettingsCard** — the Settings pattern
9. **BangHint** — the `!` chip in the home search bar

The MoodStrip + BangHint are the two redesign decisions worth the
most time. The "fresh visual" brief allows for changing these
fundamentally — the mood emoji could become text-only names
("Crushed / Heavy / Steady / Light / Bright" without the emoji),
and the bang hint could become a more discoverable affordance.
