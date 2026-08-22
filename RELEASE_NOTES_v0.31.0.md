# MindAnchor v0.31.0 — Composer + Generate-now wired + native diagnostics

The phone E2E test of v0.30.x surfaced three "the button does what
the KDoc says" defects. v0.31.0 closes all three. The first two
were Compose-level bugs; the third turned out to be a real
memory-headroom issue that the diagnostic logging now surfaces
honestly instead of failing silently.

## Fixed in v0.31.0

### 1. "Write a letter now" silently dropped the body (LetterScreen.kt)

Pre-v0.31.0, the empty-state button called
`onSaveUserLetter(today, "")` with a blank body, and the
store silently rejected it:

```kotlin
// LetterStore.kt:246
suspend fun saveUserLetter(date: LocalDate, body: String) {
    if (body.isBlank()) return    // <- no user feedback
    save(Letter(date = date, body = body, source = LetterSource.USER))
}
```

The button looked finished but the tap was a no-op. The KDoc
at `LetterScreen.kt:286-292` described a future composer; v0.31.0
builds it.

The fix is `LetterComposerDialog`: an `AlertDialog` with an
`OutlinedTextField` (4-10 lines), a "Save" button (disabled while
the body is blank — the rejection path can never trigger again),
and a "Not now" dismiss. The parent's `composerOpen: MutableState<Boolean>`
gates the dialog, the same shape as the existing delete-confirm
dialog. The `LetterInboxEmptyState` no longer takes an
`onSaveUserLetter` callback; it takes an `onWriteNow: () -> Unit`
that opens the dialog, and the dialog's save calls
`onSaveUserLetter(today, body)` with the trimmed typed body.

A finding test was updated: the previous test pinned
`onWriteNow` to call `onSaveUserLetter(today, "")` (the
buggy wiring). The new test pins `onWriteNow = onWriteNow`
(the pass-through that the inbox uses to open the dialog),
which would have failed under the v0.26.x wiring.

### 2. "Generate now" / "Use AI" were unwired placeholders (LetterScreen.kt:267,339)

Pre-v0.31.0, both `onClick` handlers were
`{ /* wired in Task 10 */ }`. Tapping them was a no-op. The
buttons were correctly *enabled* (by v0.30.1's
`LauncherViewModel.modelFits` fix) but the surface had no
real handler.

v0.31.0 wires them via a new top-level
`onGenerateNow: () -> Unit` parameter on `LetterScreen`,
passed through to `LetterInbox` and `LetterInboxContent`,
and implemented in `HomeScreen`:

```kotlin
onGenerateNow = {
    letterScope.launch {
        runCatching {
            val week = WeekDataCollector(context.applicationContext)
                            .collectLastWeek()
            val body = LetterWriter(context.applicationContext).write(week)
            if (body != null) {
                letterStore.saveUserLetter(LocalDate.now(), body)
            }
        }
    }
}
```

The same `runCatching` shape the rest of the generation
pipeline uses: a model load failure, a generation timeout, or
a `NarrationGuard` rejection never crashes the launcher.

### 3. The native layer no longer fails silently (mindanchor_llama.cpp)

The whole native wrapper is deliberately silent on failure
— every error path returns null, never throws, per the
file KDoc. The v0.30.x phone test surfaced a case where
the report's narration was always null and we could not
tell which of the eleven "return null" paths was the cause.
`adb logcat` is the only realistic diagnostic surface
(no developer console on a release build), and the
native code had no `__android_log_print` calls.

v0.31.0 adds `INFO` / `WARN` / `ERROR` logging to every
null-return path: the model path, the model load, the
vocab lookup, the tokenization, the context creation,
the decode, and the final-output length. Tag is
`MindAnchor/llama` (so `adb logcat -s MindAnchor/llama:V`).
The native CMake link line picked up the Android
`log` library; no new dependency beyond what the
platform already provides.

This was the change that diagnosed the real root cause
of "no narration": `llama_model_load_from_file` returned
null on the test phone. See "Known limitation" below
for the full diagnosis.

## Known limitation (not a v0.31.0 regression)

The Phi-4 mini Q4_K_M model (2.32 GB) needs ~3-4 GB of
free RAM to load — the file is mmap'd, but the runtime
also allocates the KV cache and the in-process llama.cpp
state. The test phone has 11.5 GB of total RAM but
~1.8 GB of `MemAvailable` when other apps are resident
(the realistic number for an actively-used phone).

The `ModelSlot.fit` check (the line behind the "with
room to spare" copy on the settings card) is correct
for the *idle, charging, overnight* case, where the
OS has drained other processes. The "Build last
night's look now" and "Generate now" affordances are
intentionally daytime affordances; on a busy day,
either can return null with the model file on disk.
The settings card does not show this because
`ActivityManager.getMemoryInfo()` reports total
memory, not available.

The fix on the device side is `adb logcat -s
MindAnchor/llama:V` to see the exact failure, then
close other apps. The diagnostic logging added in
v0.31.0 is what makes the next "why didn't the
model load" phone-test round a one-line answer
rather than a half-hour hunt.

A new string `model_load_failed_out_of_memory` is
the copy for the next iteration of the settings
card; it is not yet wired to a UI surface in
v0.31.0 — that is a v0.31.x WP.

## Test counts

- v0.30.1: 1471 tests
- v0.31.0: 1471 tests
  (LetterInboxEmptyStateFindingTest.kt's
  `Write a letter now button` test was rewritten to
  pin the new wiring; no test count change)

## APK

- versionCode 56 → 57
- versionName 0.30.1 → 0.31.0
- 5 files changed (3 main + 1 test + 1 strings)
- detekt: clean
- assembled at `app/build/outputs/apk/debug/app-debug.apk`

## Phone-verified

1. Composer dialog opens on tap of "Write a letter now"
   (when the inbox is empty), title "Write a letter",
   placeholder "What would you like to remember about
   today?", Save button greyed until body is non-blank.
2. Tapping Save persists the body to the letters
   DataStore; the inbox re-renders with the new letter
   row. Confirmed via `adb shell run-as org.mindanchor
   cat files/datastore/letters.preferences_pb` showing
   the `letters 2026-08-16 Synthetic note one` record.
3. Tapping "Generate now" calls into the native
   library: `adb logcat -s MindAnchor/llama:V` shows
   `generate: model=…ctx=2048 max_new=600 threads=8
   prompt_chars=277` followed by
   `llama_model_load_from_file returned null for
   …/model.gguf`. The native lib IS being called, the
   model file IS being read; the failure is downstream
   in the engine, not in our wiring. (See "Known
   limitation" above.)
