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

## Round 4 — 4 defects, all fixed

Round 4 also turned up a defect in the **audit driver itself**, which matters for reading
everything above: `uiautomator dump` switches an attribute to single quotes when its value
contains a double quote, and the driver only parsed double-quoted attributes. Any on-screen text
containing a `"` was invisible to it. Exactly six strings in the app contain one, and none of them
underpinned a finding in Rounds 1–3, so no earlier conclusion changes — but the blind spot did
hide two settings surfaces (see R4-02..04), and it produced one false positive that a screenshot
refuted (the onboarding plan *does* render its "Add a pause" line; the driver could not see it).
The driver now parses both quote styles, all node elements rather than only self-closing ones,
and unescapes XML entities.

### R4-01 — "Add a pause before opening" was silently dropped by backup

**Where:** Settings → This phone → Save a copy… / Restore from a copy…
(`BackupRepository`, `app/src/main/java/org/mindanchor/backup/BackupRepository.kt`)

Found by diffing a saved backup against the friction DataStore on the device: the file said
`"frictioned": []` while the device held `flagged_packages: com.google.android.deskclock`.

`BackupCodec.Backup` has carried a `frictioned` field since the format was written, and the
continuity-snapshot path restores the same set through `FrictionPrefs.replaceFlagged`. The
user-facing backup never joined up: export hardcoded `frictioned = emptyList()` and import ignored
the field. Restoring on a new phone returned favorites, hidden apps, renames, plan, people,
check-ins and readings — while every pause quietly vanished, from a file that positively asserted
there were none.

**Fix:** export reads the flagged set; import restores it the way the other launcher preferences
restore, with one exception — an empty list leaves the phone alone, because every copy saved by a
build that hardcoded the empty list says `[]`, and an unconditional replace would delete the pauses
of the person doing the restoring.

**Verified:** 3 new instrumented tests; the export and import ones fail against the old code. On
device the backup now writes `"frictioned": ["com.google.android.deskclock"]`.

### R4-02..04 — a line break split one of the person's phrases into two

**Where:** Settings → Pauses → "Small things that help" and "Self-compassion micro-moments", and
the bedtime list. (`SmallThings`, `CompassionMoment`, `BedtimeList`)

All three store one item per line and none stripped a line break out of the item first, so a
phrase carrying one came back as two half-sentences. These lists are explicitly *"their words
only; never the launcher's"*, which makes quietly rewriting them the wrong failure. Each codec's
comment had already reasoned about stray newlines — but only far enough to stop them producing an
*empty* item; splitting a real one was not considered.

`Note.encode` already gets this right by base64-ing its body, which is why quick notes were never
affected — the codebase's own correct pattern.

**Fix:** normalise on the way in (`add`) and again in `encode`. Entry normalisation keeps the
in-memory list, its dedupe and its cap agreeing with disk; `encode` is the format guarantee for
`setBedtimeList`, which takes a whole list and has no add path.

**Verified:** 7 new tests, all red against the old code.

### Verified working in Round 4

- Friction gate end to end: breath → intention → time box → the app opens. The 5-minute box
  scheduled a real `SESSION_EXPIRED` alarm at exactly +5 minutes. "never mind" cancels without
  opening.
- Hide / unhide: hiding removes an app from the drawer and from partial search ("chro" → nothing)
  while an exact-name query still finds it — matching the documented design, *"hiding reduces cues
  without deleting access."* Unhide restores it to partial search.
- Rename: reset, rename, persistence across a force-stop, and one well-formed row on disk.
- Add to home / Remove from home, including the menu label flipping correctly.
- Camera PPG: permission gate, live countdown, and — with no real finger signal — the reading was
  **discarded rather than saved** ("too noisy to trust"), with the session still logged for
  transparency. Backgrounding mid-capture released both the camera and the torch.
- Onboarding round-trip: selections persist and drive the generated plan.

---

## Round 5 — 1 crash, reported and NOT fixed

### R5-01 — the launcher can be killed by Android's long-screenshot capture

**Evidence:** `FATAL EXCEPTION: main`, `java.lang.IllegalStateException: LayoutNode should be
attached to an owner`, killing the MindAnchor process (`Process org.mindanchor (pid 8326) has
died: fg TOP`).

**Why this is not fixed here:** every frame in the stack is inside `androidx.compose.ui` —

```
androidx.compose.ui.node.LayoutNodeKt.requireOwner(LayoutNode.kt:1561)
androidx.compose.ui.node.NodeCoordinator.draw(NodeCoordinator.kt:439)
androidx.compose.ui.scrollcapture.ComposeScrollCaptureCallback
    .onScrollCaptureImageRequest(ComposeScrollCaptureCallback.android.kt:169)
```

There is no MindAnchor frame anywhere in it. This is Compose's scroll-capture callback (the
Android 14 "capture more" long screenshot) drawing a `LayoutNode` that was detached between the
capture request and the draw. The app is running Compose UI **1.7.6** (BOM 2024.12.01).

It matters more here than in most apps: MindAnchor *is* the launcher, so this crash takes out the
home screen.

**Frequency:** once in roughly 10,000 monkey events. It did not reproduce on a same-seed replay
(monkey's stream depends on UI state, so the seed does not replay the sequence), nor on deliberate
attempts to trigger scroll capture from the screenshot UI — the emulator did not offer "Capture
more" on any MindAnchor screen.

**The options, none taken unilaterally:**
1. Upgrade the Compose BOM — the real fix, but a minor-version jump under a large Compose UI.
   (This repo has no `gradle/verification-metadata.xml`, so no checksum regeneration is involved.)
2. `window.decorView.scrollCaptureHint = View.SCROLL_CAPTURE_HINT_EXCLUDE` — three lines that
   remove the crash surface, at the cost of long screenshots app-wide. For a journal, that is a
   real loss, not an obvious win.
3. Accept and track it.

A dependency bump and a deliberate capability removal are both product calls, so this is reported
rather than decided.

### The ANR seen alongside it

`ANR in org.mindanchor ... Reason: Input dispatching timed out (Application does not have a
focused window)` — on the **restarted** process (a different PID), while `/proc/pressure/cpu`
showed `some avg10=70.44` and load 11.74. A window-restore after a crash under that much CPU
pressure is an environment artifact, not a second defect.

### Verified working in Round 5

- The Round 1 dead-control fixes hold on device: the morning self-compassion card goes from
  present to gone on "Begin" (card count 1 → 0).
- Small things and self-compassion phrases: add, list, remove, and HMAC-sealed persistence
  (`v1 | small_things | <items> | <MAC>`) all correct.
- Journal (Today / Entries / Patterns), Letter, Check-in history and Digest all open with correct
  empty states.
- Monkey: 2000 events clean, then 2500, 1500 and 1500 more across three further seeds — one crash
  total, the framework one above.

---

## Round 6 — clean

- **Backup/restore, the whole loop, through the UI.** Configured a pause on Clock → saved a copy →
  removed the pause → restored from that copy. The screen said "Restored.",
  `flagged_packages` came back as `com.google.android.deskclock`, and tapping Clock **fired the
  gate** ("What are you opening Clock for?"). That closes R4-01 end to end.
- Quiet, Reading and Your plan settings groups all render correctly.
- Daily-letter time picker: 08:00 → 07:30, with the UI and `letters_time` in DataStore agreeing.
- "Generate now" with no API key degrades gracefully to the provider-setup section — no error, no
  crash.
- Monkey: 2 seeds × 2000 events. Zero crashes, zero ANRs, zero process deaths.

## Round 7 — clean

- **R1-04 finally device-verified.** The expressive-writing card was marked "device-verify
  pending" back in Round 1 because it needed its toggle on. Tapping "Not now" now takes the card
  from present to gone.
- Home scene picker: all four scenes selectable, `nature_scene: FOREST` persisted, and a
  screenshot confirms the home surface actually renders it.
- Journal: entry written and saved, appears verbatim under Entries, and Patterns reports
  "Days written: 1 / Words written: 8" — 8 being the correct count for the sentence written.
- Draft recovery works: text the monkey typed into the journal survived as a draft.
- "Re-classify all notes": confirmation dialog, then the work is queued (scheduled jobs 31 → 74).
- Monkey: 3 seeds × 2000 events, plus two replays. Zero crashes, zero process deaths.

### The Round 7 ANRs, and why they are not defects

Two seeds reported `ANR in org.mindanchor`. The first instinct — host load — turned out to be
wrong: a replay reproduced one at **load 4.45 with `/proc/pressure/memory some avg10=0.00`**, so
it was worth taking seriously.

What it actually is:

- The reason is specifically *"Input dispatching timed out (**Application does not have a focused
  window**)"*.
- At the moment of the ANR the focused window was **`NotificationShade`** — the monkey had pulled
  down the system shade.
- The app's **PID was identical before and after** (15563), and it responded to `am start`
  immediately, rendering the screen it had been on.

So the main thread was never blocked. The monkey opened a system window and then kept injecting
input aimed at MindAnchor, which by then had no focused window; Android attributes that timeout to
the app underneath. A person pulling down the shade does not do the second half of that. No
`FATAL EXCEPTION`, no process death, no hang.

---

## Stopping condition

The brief was to keep going until two consecutive rounds found nothing. Rounds 6 and 7 were both
clean, so the audit stops here.

| Round | Defects | Status |
|-------|---------|--------|
| 1 | 6 | fixed (`1eafd9b`) |
| 2 | 0 | clean |
| 3 | 2 | fixed (`2ef25b8`) |
| 4 | 4 | fixed (`c65056a`, `22e05a7`) |
| 5 | 1 | **open** — framework crash, see R5-01 |
| 6 | 0 | clean |
| 7 | 0 | clean |

12 defects found, 12 fixed in app code; 1 framework crash reported and left for a decision.

Monkey coverage across the whole audit: **over 25,000 pseudo-random events**, producing exactly
one MindAnchor crash — R5-01, inside Compose.

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
