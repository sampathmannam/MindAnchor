# MindAnchor Layouts (layouts.md)

> **Status:** v0.62.7 (2026-08-20) | **Stack:** Kotlin + Compose + Material 3

---

## AppShell (the only top-level layout)

**File:** `app/src/main/java/org/mindanchor/HomeActivity.kt` + the
`HomeScreen(...)` call inside it.

**Structure:**
- `enableEdgeToEdge()` (v0.55.0) — the app draws under the status bar
  and the system navigation bar
- `WindowCompat.setDecorFitsSystemWindows(window, false)`
- `setContent { MindAnchorApp() }`
- `MindAnchorApp()` reads from `LauncherViewModel` and dispatches to a
  `Scaffold { padding -> when (surface) { ... } }`

### The `Scaffold`'s `when (surface)` block

A single `when` over `LauncherSurface` (private enum) renders ONE surface at
a time. There is no back stack — `BackHandler` returns to `Home` from
any sub-surface. The user's mental model is: "I am always in Home, but
sometimes a sub-surface is on top."

### TopBar (the home card top)

A `Column` is the home card's "topbar" — NOT a Material 3 TopAppBar.
The top of the home card shows:

1. **Clock** (giant numeric time, e.g. "8:32" — `displayLarge` or
   `displayMedium` typography, derived from `LocalTime.now()` and
   updated by `rememberMinuteTick()` which ticks every 60s, lifecycle-aware
   via `repeatOnLifecycle(RESUMED)`.
2. **Greeting** ("Morning" / "Afternoon" / "Evening" / "Night") —
   `titleLarge` typography, derived from clock.
3. **Date** (e.g. "Thu, Aug 20") — `bodyMedium` typography, on a
   separate line below the greeting.

The home card sits in a `Box` with the slow-sky `CalmBackground` underneath.
The Box's `fillMaxSize` puts the sky behind the home card; the home card
itself is `Box(modifier = Modifier.fillMaxWidth())` with a translucent
`LayerSecondary` background.

### BottomBar (the only fixed nav)

A `NavigationBar` with two items:
- "search" (left, ~25% from the left edge)
- "Settings" (right, ~75% from the left edge)

Tapping "search" focuses the search bar at the top of the home card.
Tapping "Settings" opens `Settings` surface.

```kotlin
NavigationBar(
    modifier = Modifier.navigationBarsPadding(),
) {
    NavigationBarItem(
        selected = false, onClick = { ... },
        icon = { Icon(Icons.Outlined.Search, "search") },
        label = { Text("search") },
    )
    NavigationBarItem(
        selected = false, onClick = { ... },
        icon = { Icon(Icons.Outlined.Settings, "Settings") },
        label = { Text("Settings") },
    )
}
```

**v0.55.0 3-button overlap fix:** `Modifier.navigationBarsPadding()` puts
the NavigationBar above the system 3-button nav on devices that have
one. v0.55.0 also adds `padding(bottom = 48.dp)` for the case where the
inset is not honored.

### Drawer (the "everything else" nav)

A `ModalNavigationDrawer` opened by a left-edge swipe gesture. Contains:
- A list of all surfaces (Notes, Settings, Get through this, Letter to
  the part that is struggling, Breathe, Ground me, I might do something
  I regret, etc.)
- A search bar at the top that ALSO accepts `!bang` commands
- A "Set MindAnchor as your home screen" CTA at the bottom (for
  first-time users who haven't set the launcher yet)

The Drawer's `onBang` callback is the **source of truth** for bang
routing. The Quick note composer's `onAddQuickNote` lambda mirrors it
(per the v0.62.7 F6 fix comment: "the surface map mirrors the Drawer's
`onBang` callback a few lines down so the two entry points stay in sync").

---

## HomeCard layout (the home surface in detail)

```
┌─────────────────────────────────────────────────────┐
│  [Bang hint "!" chip]                  [search bar]  │  ← top of home card
│  ─────────────────────────────────────────────────  │
│   8:32                                               │  ← clock (displayLarge)
│   Morning                                            │  ← greeting (titleLarge)
│   Thu, Aug 20                                        │  ← date (bodyMedium)
│                                                     │
│   [Save status subtitle: ✓ Saved 0s ago]            │  ← v0.62.6 contextual
│                                                     │
│   🥺   😕   😐   🙂   😊   ?                        │  ← mood strip
│   Crushed Heavy Steady Light Bright Skip            │
│                                                     │
│   Notes                                              │  ← section header
│   No notes yet                                       │  ← empty state
│   (or: pinned notes)                                 │
│                                                     │
│   [Quick note] [Task] [Reminder]                    │  ← kind picker
│   Future notes on home                          [○] │  ← pin toggle
│   Pinned notes appear on the home screen.            │
│   Unpinned notes live only in the Notes tab.         │
│                                                     │
│   [OutlinedTextField — multi-line]                  │  ← draft input
│   ┌─────────────────────────────────────────────┐  │
│   │                                             │  │
│   │                                             │  │
│   └─────────────────────────────────────────────┘  │
│   [        Save        ]                            │  ← OutlinedButton
└─────────────────────────────────────────────────────┘
  [search]                          [Settings]       ← bottom nav
```

The home card is a single `Column` with the elements listed above,
in a `Box` so the `SnackbarHost` can overlay at `BottomCenter` without
affecting the Column layout (v0.62.5 change for F2 Undo).

The card has TWO visual layers (v0.53.0):
- **Layer 1 (background):** clock + mood — the moment-of-glance content
- **Layer 2 (LayerSecondary, 8% white tint, 4dp elev):** mood + notes — the
  "what's here" content
- **Layer 3 (LayerTertiary, 12% white tint, 8dp elev):** input + picker +
  save — the "touch here" content

The three layers are visually nested but Compose-wise flat (each is a
`Surface` with a translucent `Color` and a `tonalElevation`).

---

## SettingsScreen layout

A single `LazyColumn` of grouped sections. Each section has:
- A `titleLarge` section title (e.g. "Quiet", "Sources", "Your plan")
- A `bodyMedium` one-line subtitle (e.g. "Batching, quiet hours, and colour.")
- One or more `SettingsCard`s below

SettingsCard is a `Surface` with a 1dp outline and an 8dp corner radius.
Each card is a single composable that wraps:
- A label (e.g. "Take a reading")
- An optional sub-label
- An optional action (Switch, OutlinedButton, slider, etc.)

---

## NotesTab layout

A `LazyColumn` of day groups. Each day group:
- A `stickyHeader` chip with the date label (e.g. "Today", "Yesterday",
  "2 days ago", or "15 Aug" for older dates)
- A list of note rows for that day, ordered by `updatedAt DESC`

Each note row is a `SwipeToDismissBox` (Material 3 1.3.x) with:
- StartToEnd (left swipe) = pin (sage background, "Pin" label)
- EndToStart (right swipe) = delete (red-300 background, "Delete" label)
- On settle: fires the appropriate `NotesSwipeAction` enum

Top of the tab: a `NotesDayStrip` (v0.53.0, date filter chips) and a
search field.

> **v0.55.1+ planned:** actual date-filter behavior, sticky day headers
> (LazyColumn `stickyHeader` needs foundation 1.5+ BOM).
