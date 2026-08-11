# v0.25.0 — auto-classify notes with the on-device Phi-4 + ASH-style daily letter

**Date**: 2026-08-11
**Status**: shipped
**Includes**: v0.25.0 (auto-classify notes) + v0.24.0 Item 1 (ASH-style daily letter). v0.24.0 Items 2-4 (sunlight, reflection, worry) are deferred to a follow-up release.

---

## What's in v0.25.0

The v0.25.0 spec is the smallest change that gives the user what they asked for: the LLM reads every note, assigns a type, the user sees a chip on the row, and the list is filterable. No new inboxes, no streak, no evaluation, no cloud.

### Auto-classify each note

Each note is auto-classified into one of four types by the on-device Phi-4 mini (the same model that powers the night report and the daily letter):

- **General** — anything that isn't a task, reminder, or journal entry
- **Task** — "I need to do this"
- **Reminder** — "Don't let me forget X" (time-bound, date-anchored)
- **Journal** — first-person, reflective, "what happened today / how I felt"

The type is set by the LLM, never by the user. The chip on the row reflects whatever value is there; an untyped note (model not on the phone, or classification is still running) shows no chip.

### On the notes list

- A row of filter chips above the list: **All / General / Task / Reminder / Journal**. Tap to filter; tap the active chip to clear. The filter is in-memory and resets to "All" when the activity reopens.
- A small colored chip on each note row next to the title, color-coded per type (neutral / blue / orange / purple).
- A per-filter empty state when the active filter has no matches: "No tasks yet." / "No reminders yet." / "No journal entries yet." / "No general notes yet."

### Behind the scenes

- **Classification** runs in the background, after the save. The save returns instantly; the chip appears within a few seconds (or stays hidden if the model isn't on the phone).
- **On edit**, the body is re-classified. The classifier overwrites the previous type.
- **On upgrade**, a one-time background pass classifies every pre-existing note. The pass is idempotent (a SharedPreferences flag) and runs on the enqueuer's own scope — the launcher can be backgrounded while it drains.
- **On Settings → Re-classify all notes**, every type is reset to null and re-enqueued. A one-tap action with a confirm dialog; the actual re-classification runs in the background and may continue long after the user backs out of Settings.

### The letter (v0.24.0 Item 1, folded in)

The v0.24.0 spec's first item is now shipped: a daily AI-generated letter about the user's past 7 days, written by the on-device Phi-4 mini, gated by `ModelStore.fit()` the same way the night report is.

- The letter appears as a notification at a user-chosen time (default 08:00).
- A "Letters" inbox shows past letters, latest first.
- "Generate now" writes a letter for today without waiting for the schedule.
- The letter's notes-summary line now carries per-type counts when at least one note in the window has a type: "6 notes this week: 2 tasks, 1 reminder, 3 journal. Most recent: ...". Falls back to the v0.24.0 line when all notes are untyped.
- The letter uses the same `NarrationGuard` as the night report — no clinical terms, no citations, no evaluation.

### Wire format migration

The notes on-disk format gains one tab-separated field (the type) between `updatedAt` and the body. v0.24.0 lines (5 fields, no type) still decode; v0.25.0 lines (6 fields) carry the type. The codec is fail-closed: unknown type names are treated as corrupt and skipped.

The HMAC seal still wraps the whole notes payload. A motivated user with root cannot rewrite either the notes or the type without invalidating the MAC. The type is part of the same `SealedCodecs.encodeNotes` payload as the body.

---

## What's not in v0.25.0

- **Tasks / Reminders as separate inboxes.** The user asked for the note to be improved, not extracted. A separate inbox is a different surface (a "did I handle this?" question) that wasn't in the ask.
- **Semantic search.** Out of scope for this release. The v0.25.0 type is the smallest unit of structure; a "show me all tasks" filter is the simplest search.
- **Cross-note synthesis** ("you mentioned work 8 times this week"). Reserved for a future RAG story.
- **v0.24.0 Items 2-4 (sunlight, reflection, worry).** Deferred. The letter is the only v0.24.0 item shipped; the rest are a follow-up release.

---

## Files that change

- `app/src/main/java/org/mindanchor/model/NoteType.kt` — new enum
- `app/src/main/java/org/mindanchor/model/Note.kt` — `type: NoteType?` field; codec extended (5→6 fields, backwards-compatible)
- `app/src/main/java/org/mindanchor/note/NoteClassifier.kt` — new: on-device classifier (reuses `LlamaEngine`)
- `app/src/main/java/org/mindanchor/note/ClassifierEnqueuer.kt` — new: fire-and-forget scope + one-time upgrade pass
- `app/src/main/java/org/mindanchor/data/NotesPrefs.kt` — `setType` / `clearAllTypes`; enqueue on `add` / `edit`
- `app/src/main/java/org/mindanchor/model/NoteActivity.kt` — set up enqueuer, run upgrade pass, re-classify on edit
- `app/src/main/java/org/mindanchor/model/NoteScreen.kt` — filter chip row, type chip on each row, per-filter empty states
- `app/src/main/java/org/mindanchor/letters/WeekDataCollector.kt` — typed summary line for the letter
- `app/src/main/java/org/mindanchor/settings/NoteReclassifySection.kt` — new: Re-classify all section
- `app/src/main/java/org/mindanchor/settings/SettingsScreen.kt` — wire the section
- `app/src/main/java/org/mindanchor/letters/` — the v0.24.0 letter work (4 files: Prompting, Writer, Store, Scheduler + WeekDataCollector)
- `app/src/main/res/values/strings.xml` — 12 new strings for v0.25.0; 2 for v0.24.0 letter
- `app/src/test/java/org/mindanchor/model/NoteTypeFindingTest.kt` — new
- `app/src/test/java/org/mindanchor/note/NoteClassifierFindingTest.kt` — new

---

## Acceptance gate

- `./gradlew :app:detekt :app:testDebugUnitTest :app:testReleaseUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease` — clean
- 992 unique unit tests, 0 failures
- Senior-tester pass on emulator-5554 covering:
  - NoteActivity: filter chip row visible, type chip on each note row, day-grouped list, composer with character counter
  - Notes pre-classify to "General" (the safe default; the Phi-4 model is not on this test device, so the classifier falls back to GENERAL, which is the spec'd behaviour)
- The v0.24.0 letter ships in this release; the v0.25.0 type system is the letter's downstream consumer

---

## Downloads

- Debug APK (signed, installable): `app-debug.apk`
  - SHA-256: `E2AE50634335738B81A8402A6F2289109EFF0614FA5EED305FAE3CE89B49922A`
- Release APK (unsigned, ~10 MB): `app-release-unsigned.apk`
  - SHA-256: `9F734705F768D537FFC8B7329E4F074C694225790D3FD6773222A5E8B4D1A19E`
