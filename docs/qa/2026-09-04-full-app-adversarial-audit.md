# Full-app adversarial UI audit — 2026-09-04

**Build under test:** `main` @ `ce53c99` (the Program 3 merge), debug APK.

**Device:** an isolated Android 14 / API 34 emulator (`audit_isolated_9f2a`), bound to a
**private adb server on port 5137**. Two earlier emulators were hijacked mid-test by other
Claude sessions running on the same Mac — one had an unrelated app installed and launched over
the session, another had `org.mindanchor.test` instrumentation driven onto it by a different
worktree's Gradle run. Any adb-visible device is fair game to every session's
`connectedAndroidTest`, so the audit device was made invisible to the default adb server.

**Method.** Drive every surface with `adb` (taps, swipes, text, rotation, process kill),
read state back with `uiautomator dump` and by inspecting DataStore/Room directly rather than
trusting pixels, and stress with `adb shell monkey`. Rounds repeat until two consecutive rounds
find nothing.

**Stopping condition:** two consecutive rounds with zero new defects.

---

## Round 1 — 6 defects, all fixed

Five of the six were the same shape: a control that renders, responds to touch, and does
nothing. Each traced to a call site passing an empty lambda with a comment deferring the work
to a later version.

| # | Surface | Defect | Root cause |
|---|---------|--------|-----------|
| R1-01 | Home → intro callout → "Got it" | Dead button; card never dismissed | `HomeScreen.kt` passed `onDismiss = { /* comment only */ }`; visibility came solely from `LauncherPrefs.showIntroCallout` |
| R1-02 | Home → morning self-compassion card | **Both** buttons dead; card returned on every home display, permanently | `onStart` / `onSkip` were both empty lambdas |
| R1-03 | Home → Friday BA picker → "Not today" | Dead; card only dismissable by saving something you didn't want to save | `onSkip` empty lambda |
| R1-04 | Home → expressive-writing card | Dismiss dead | `onDismiss` empty lambda |
| R1-05 | Home → wind-down card | Both actions dead — and the render site's own comment already *claimed* "the 'Not now' dismisses for this session" | The **call site** passed no-ops; documented intent never implemented |
| R1-06 | NFC tag tap → `NfcArmActivity` | Blank, exit-less screen on top of whatever you were doing | The activity never calls `setContent`, and `renderIdle()` was an empty stub that returned without finishing |

**Fixes.** R1-01..05 got session-scoped dismissal state, matching the `defaultHomeCalloutDismissed`
idiom already used ten lines above the first offender. Launch counting deliberately stayed outside
the dismissal gate so the "spread the intro over the first 3 launches" design is preserved.
R1-06: the activity is a headless intent handler, so it now behaves like one — it finishes instead
of lingering, and shows a Toast naming what was armed (or that Sleep Lock isn't built yet).

Committed as `1eafd9b`.

---

## Round 2 — clean

- Quiet-hours steppers (21:00 → 20:30, summary line stayed in sync).
- Support / safety plan: field persisted ("Audit test warning sign").
- Crisis contacts: blank phone correctly **rejected**; valid contact added and displayed.
- Settings groups Reading and This phone; onboarding → Settings goals round-trip.
- Monkey: 1500 events, **zero** MindAnchor crashes or ANRs.

One candidate — "goals aren't persisting" — was **investigated and refuted**: my own onboarding
run had skipped the goal taps. DataStore held `goals: INTERRUPTIONS, SLEEP` and Settings displayed
them. Not reported as a defect.

---

## Round 3 — 2 defects, all fixed

Round 3 added rotation, process death and deep navigation, and turned up a **class** of bug that
tapping alone cannot reach: user-controlled text written into hand-rolled delimited formats
without stripping the delimiters. Both instances are pinned by tests that are red against the
old code and green against the new.

### R3-01 — a rename could silently truncate, or rename a *different* app

**Where:** long-press any app → Rename → type or paste a label → Save.
(`LauncherPrefs.rename`, `app/src/main/java/org/mindanchor/data/LauncherPrefs.kt`)

The renames map is stored as newline-delimited `component<TAB>label` rows. The *restore* path
(`replaceRenames`) stripped tab and newline from the label and carried a comment explaining
why — *"a rename comes from a text field the user controls."* The path that actually carries
the typed text (`rename`) wrote the label through **unchanged**. The invariant was documented
on the one writer that didn't need it and missing from the one that did.

A newline in a label ends the row early. Best case the label is silently truncated; worst case
the tail is read back as a row of its own and **renames whatever component it names** — renaming
one app quietly retitles another. A lone carriage return counts too: `lineSequence()` treats it
as a terminator, which even the restore-path sanitizer missed.

**Fix:** the row format is now a single `RenameRows` object (`decode` / `encode` / `upsert`) that
both writers go through, sanitizing tab, newline and carriage return.

**Verified:** 11 tests in `RenameRowsTest`. Reverting only the sanitizer turns **5** of them red,
including `a newline in a label cannot rename a different app`.

### R3-02 — a tab in a letter body forged its LLM metadata

**Where:** `LetterLedger.encode`, `app/src/main/java/org/mindanchor/letters/LetterLedger.kt`

A letter is one line: `date`, `body`, `provider`, `model`, `promptTokens`, `completionTokens`,
`durationMs`, tab-separated. `encode` stripped newlines from the body — there was even a test
pinning that (`encode strips embedded newlines from a body`) — but not tab or carriage return.

The decoder splits on tab and counts fields, so a tab inside a body **truncates the body there
and reads the tail back as the metadata columns**: a letter could arrive claiming a provider,
model and token counts it never had. A carriage return ends the row early and the remainder is
dropped as an undated line.

Reachable two ways, neither exotic: the body is either raw LLM output (a model can emit a tab)
or text the person typed into the BA mastery/pleasure prompt, which `LetterStore.saveBaEntry`
stores as a letter body.

**Fix:** every string field is flattened through one helper before joining.

**Verified:** 4 new tests in `LetterLedgerTest`, red before the fix and green after; the 6
pre-existing tests still pass.

### Verified working in Round 3

- Rotation stress on Home and Journal (4 orientation changes each): no crash, no state loss.
- Process death mid-entry (`am kill`) then relaunch: recovers to Home cleanly.
- Drawer opens and lists apps; search filters correctly ("cloc" → Clock only).
- App long-press menu renders all five actions.
- Full unit suite after the fixes: **2011 tests, 0 failures**; detekt clean.

---

## Cumulative "verified working"

- Onboarding, all three steps, including that selections actually drive the generated plan.
- Home quick-note save; two notes persisted with correct timestamps and ordering.
- Journal entry create → appears verbatim in Entries.
- Journal → Patterns: correct counts and exactly the four structural facts the extractor emits.
- Journal → morning check-in round-tripped exactly (mood 4 / anxiety 2 / anger 1 / energy 3 /
  sleep 4 read back identically).
- Settings navigation, group open/close, Pauses toggles persist and drive their home cards.
- Digest, Check-in history, Letter surfaces: correct empty states.
- Letter generation with no API key fails **gracefully** with an "Open settings" action, no crash.
- Monkey stress across rounds: 800 + 1200 + 1500 events, zero MindAnchor crashes or ANRs.

---

## Known-unimplemented features — reported, deliberately NOT "fixed"

These are unfinished features, not defects to invent implementations for. Their *controls* are
now honest (they dismiss or respond), but the underlying capability does not exist. Whether to
build them or hide the toggles is a product decision.

- **Push-up mode** and **Voice journal** (Settings → Pauses): both toggles persist, but
  `PushUpGateCard` / `VoiceJournalCard` are never invoked from `HomeScreen.kt`, so enabling
  either changes nothing anywhere. `WhisperEngine.kt` even refers to "the commit that wires
  [VoiceJournalCard]".
- **Sleep Lock** — the NFC arm path dispatches to an empty branch; the G-5 feature is unbuilt.
- **`SleepWindowOptimizerCard`** — zero call sites; orphaned dead code.
- **`PreHomeActivity.pendingLaunch`** — set to `null` in three places and never assigned a
  non-null value, so `DoomscrollPromptDialog` is unreachable and no app-icon grid renders,
  despite the class KDoc describing one.

---

## Environment notes (not app defects)

- SystemUI / NexusLauncher ANRs on the emulator are host-load artifacts (Mac load average peaked
  above 25 with several concurrent sessions and three emulators). MindAnchor itself never ANR'd,
  never hit a `FATAL EXCEPTION`, and its process never died unprompted.
- One monkey run aborted at event 1520 because `com.google.android.bluetooth` and `gservices`
  died under host load — an emulator failure, not an app failure. Re-run clean at 1500 events.
- A single full-suite run failed once at load ~30 while a second Gradle daemon was active; a
  forced full re-run at load 4 passed 2011/2011. Treated as load-induced, not a flaky test.
- `uiautomator dump` times out under heavy host load and reads as a phantom app hang; the audit
  driver retries rather than reporting one.
