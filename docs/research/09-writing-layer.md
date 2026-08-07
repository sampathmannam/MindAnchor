# 09 — The writing layer

The design record for the part that turns a night's measurements into
something to read, and for the boundaries around the language model that
will eventually write it.

This continues [08-sensing-architecture.md](08-sensing-architecture.md),
which covers where the numbers come from. This document covers what is
done with them.

---

## 1. The rule everything here is shaped by

Restated once more because every decision below follows from it:

> Research says **what a signal is**. A person's own history says **it
> moved**. Nothing says **what that means about them**.

The reason is not caution for its own sake. It is the finding in
[01-sensing-and-jitai-sota.md](01-sensing-and-jitai-sota.md): Müller,
Harari et al. (*Scientific Reports*, 2021) reported a GPS-mobility model
for depression scoring AUC 0.82 in a homogeneous sample of 57 students
and **0.57 — chance — in a heterogeneous sample of 5,262**. The 2025
JMIR review of mobile sensing for depression found that of nine studies,
only one could distinguish worsening from relapse from recovery.

The literature that would supply an interpretation is precisely the
literature that collapses when applied to an individual. So the report is
built as two separable claims — a count from somebody's own history, and
an explanation of what the thing counted is — and the join between them
is left to the person, who has context no amount of measurement supplies.

Henson, Torous et al. (*npj Schizophrenia*, 2023) is why the first half
is worth anything at all: anomaly detection against a person's **own**
passive-data baseline was more frequent in the month before relapse than
at other times. Within-person deviation generalises where cross-person
prediction does not.

---

## 2. Retrieval, and why not embeddings

`Retrieval` is BM25 — the classical ranking function — over a corpus of
short passages. The obvious modern answer is a sentence-embedding model
and cosine similarity. It was rejected for three reasons:

1. A second model on the phone, another few hundred megabytes, another
   thing to import.
2. A scoring function nobody can inspect. When the report is asked why a
   passage came up, "arithmetic you can follow" is a better answer than
   "a vector space".
3. BM25 is strong on corpora of this size, and the whole retrieval layer
   is then testable without a device, a model or a network.

One implementation note that cost a bug: the idf term is
`ln(1 + (N − df + 0.5) / (df + 0.5))`. Without the `+1`, a term appearing
in nearly every passage contributes a **negative** score, and a passage
that genuinely matched can rank below one that matched nothing.

### The queries are constrained, and there is a test that says so

Each `Signal` carries a `corpusQuery` describing *what the signal is* —
`"heart rate variability RMSSD vagal parasympathetic"`. It must never
describe what a change in it means. `ReportComposerTest` asserts that no
query contains `depress`, `anxi`, `risk`, `relapse`, `episode`,
`disorder`, `warning`, `concern`, `abnormal` or `unhealthy`. A query
containing an interpretation would smuggle population inference back in
through the retrieval layer.

---

## 3. The corpus

Twenty-six seed passages ship as an asset, each with a source, drawn from
`docs/research/01`–`08`. A seed, not a library.

`CorpusImport` lets it grow from a tab-separated file on the phone. Three
decisions worth recording:

**Merged, not swapped.** Importing one file about sleep must not cost
every passage about HRV. An imported passage whose id matches a seed one
wins outright — that is the escape hatch for a bundled passage somebody
thinks is wrong.

**The difference is stored, not the merged whole.** Writing the whole
merged corpus would be simpler and self-contained, and it would freeze the
seed: an app update correcting a bundled passage would be shadowed forever
by the copy written on the day of the first import, invisibly. Storing the
difference keeps both halves live — untouched seed passages track the app,
additions and deliberate corrections track the person.

**Nothing reads the passages.** An imported passage is shown verbatim.
Nothing checks whether it explains or interprets. The settings copy says
so rather than implying a check that is not there.

---

## 4. What the report is allowed to be

`ReportComposer` produces at most three sections, ranked by how far a
signal sits from that person's own robust baseline.

**An empty report is the success case.** A steady week produces nothing.
Something that finds something to say every night is not observant, it is
noise, and it teaches people to stop reading — which costs the one night
it would have mattered. Baumel et al. (*JMIR*, 2019): median 15-day
retention across 93 mental-health apps was under 4%.

**Three sections, hard cap.** A report listing seven simultaneous
abnormalities reads as an emergency whatever its wording, and the likeliest
cause of seven at once is one bad measurement day.

**No z-score reaches the screen.** `Observation` has no z field at all.
The robust z is a screening device whose magnitude stops meaning anything
once the dispersion floor binds — in a degenerate history it reaches ~44 —
and "you are 44 standard deviations from normal" is alarming and
meaningless. Sign and notability are the output; the magnitude is not.

**Not measured ≠ normal.** A signal absent from today's values is skipped.
A signal with too little history goes into `notYetKnown` rather than being
guessed at.

### Sleep onset needed its own frame

Sleep onset is a clock reading, and clock readings wrap. Fed to the
baseline as a raw minute-of-day, bedtimes of 23:50 and 00:10 — twenty
minutes apart — arrive as 1430 and 10, a 1420-minute gap in a series whose
real spread is twenty minutes. Simulated over fourteen midnight-straddling
nights, a 3:30am night scored **z = −0.48**: unremarkable, and the wrong
sign. In `Deviation`'s minutes-after-18:00 frame the same night scores
**+12.9**.

Anyone whose bedtime straddles midnight is most people, so this was the
ordinary case failing rather than an edge one. `ClockTimeTest` pins the
frame and its inverse against each other for all 1440 minutes.

---

## 5. When the report is written

The report runs at **03:00**, and only when the phone is **charging** and
**not interactive**. Either constraint alone would let it run while
somebody is mid-task on their own phone. If both are not met it retries
hourly until 08:00, then waits for the next night — past that somebody is
plausibly awake, and last night's news has gone stale.

### This was WorkManager, for exactly one commit

`setRequiresCharging(true)` and `setRequiresDeviceIdle(true)` express
those two constraints in two lines, which is why WorkManager was the
obvious choice. It also merges **`android.permission.ACCESS_NETWORK_STATE`**
into the manifest, because it supports network constraints whether or not
anything asks for one.

This app declares **no network permission at all**. That is the whole
basis of the About screen's promise that nothing leaves the phone, and
`PrivacyTest` asserts it against the app *as actually installed* rather
than against the manifest as written — which is exactly how the silent
merge was caught, on the emulator job, and not before.

The permission could have been stripped back out with
`tools:node="remove"`. That was rejected. WorkManager's constraint
trackers read connectivity through `ConnectivityManager`, and whether any
given path does so when no work declares a network constraint is a
question that can only be settled on a device. There is no device here.
Trading a structural, tested privacy guarantee for an untestable
assumption about a library's internals is a bad trade at any odds.

So `ReportSchedule` checks the two conditions itself — `EXTRA_PLUGGED`
from the sticky `ACTION_BATTERY_CHANGED` broadcast, and
`PowerManager.isInteractive`, neither of which needs a permission — and
`ReportScheduler` arms a plain `AlarmManager` alarm, the same idiom
`BatchAlarms` and `EmaScheduler` already use. It is more code. It also
turns a property of a scheduler that could not be tested into a pure
function that is: `ReportScheduleTest` covers both constraints
independently, the retry window, the already-ran guard, and the invariant
that every armed alarm is in the future.

Both readings err towards waiting when they cannot be taken. Waiting
costs a night; running while somebody is using the phone is the thing the
constraints exist to prevent.

Every firing arms the next one, whatever it decided. Nothing ever leaves
AlarmManager holding nothing, which is the failure that ends a feature
silently rather than loudly. Nothing throws either: this runs at 3am with
nobody watching, and an empty report is success for the reason in §4.

---

## 6. The model, and the four things holding it

The model will be the least trustworthy component in the system: small
enough to fit on a phone, quantised, and willing to confabulate. Every
other component can be reasoned about from its code. This one can only be
constrained at its edges, and those edges are pure functions of strings, so
they are tested without a model, a device or a network — and they were
built **before** the engine rather than after it.

### 6.1 The prompt withholds sources

The model is never shown an author or a year. Citations are rendered by
the app from the passages actually retrieved. A model asked to cite
produces plausible author-year pairs that do not exist, and a fabricated
citation is worse than none: it converts an unfalsifiable claim into one
that looks checked. Shown none, it has none to reuse.

The system instruction is written as concrete prohibitions with reasons
rather than a request for care, because a 3B model at four bits follows
rules considerably better than it follows tone.

### 6.2 Unusable passages are filtered before the prompt, not only after

Six of the twenty-six bundled passages name depression, anxiety, relapse
or treatment — because that is what those studies were about. Hand one to
the model and it echoes the word and is rejected on the way out, every
night. So `Prompting.usable` filters them out of the prompt, sharing
`NarrationGuard.FORBIDDEN` so the two lists cannot drift apart.

Checked against the shipped corpus: **20 of 26 passages survive, and all
seven signals still retrieve at least one usable passage**, so no signal is
starved by the filter. The excluded passages are not lost — the report
still shows them verbatim, with sources, which is where they do the most
good.

### 6.3 The guard is deliberately over-broad

`NarrationGuard` rejects output on five grounds: too short, too long, a
forbidden term, a source-shaped string, or a repeated 6-word window
(the characteristic degeneration of a small quantised model).

The forbidden list is over-broad on purpose. *"The research does not say
whether this indicates depression"* is a model being careful and is still
rejected, because a list narrow enough to admit it is a list with gaps.
The costs are lopsided: a rejected paragraph costs a paragraph — the
report still shows the counts and passages, exactly as it did before any
model existed — while an accepted bad one is a phone telling somebody
something about their mental health that nothing here is entitled to say.
It fails towards silence on purpose.

### 6.4 Whether the model can run is decided before it is loaded

A background process that asks for more memory than the system will spare
is killed, and from the outside that is indistinguishable from a quiet
night with nothing to report. It would fail invisibly, every night. So
`ModelSlot` decides first.

The budget is **45% of total RAM** — a background process cannot expect to
hold more, with the launcher, system UI and whatever was left open all
resident — plus a **512 MB** allowance for the KV cache and runtime
overhead. A GGUF is mmap'd and need not all be resident in principle, but
a model paging off storage per token is too slow to finish, so the file is
budgeted as though it must be.

| Total RAM | Budget | 1.8 GB model | 2.4 GB model | 3.2 GB model |
|-----------|--------|--------------|--------------|--------------|
| 6 GB      | 2.7 GB | TIGHT        | TOO_LARGE    | TOO_LARGE    |
| 8 GB      | 3.6 GB | FITS         | **TIGHT**    | TOO_LARGE    |
| 12 GB     | 5.4 GB | FITS         | **FITS**     | FITS         |

A 2.4 GB four-bit 3–4B model is TIGHT on 8 GB and comfortable on 12 GB —
the two sizes this has to work on. `isLowRamDevice` is taken at Android's
word and refuses at any size. Anything unmeasurable refuses too: being
told a model will not run is recoverable, because a smaller one can be
imported. Being killed nightly is not, because nothing says it happened.

---

## 7. What is not built

**The inference engine.** llama.cpp over JNI needs the NDK, a native
arm64 build and a device. None of those exist in this environment — CI is
the only compiler available and it has no NDK — so this is the one piece
that cannot be written or verified here.

`NoEngineNarrator` returns null, every time. It does not fabricate a
paragraph, does not simulate one from a template, and does not claim to
have tried and failed. The settings copy tells the person the same thing:
importing a model today records the file and reports whether the phone
could run it, and produces no writing.

Everything the engine would sit inside is built and exercised: the prompt
it would be given, the guard on the output it would produce, the memory
decision about whether it could run, the storage for what it wrote, and
the screen that shows it. Swapping in a real engine is one line in
`Narrators.forDevice`, and the comment marks it.

**Also outstanding, and outside what can be done here:**

- A signing key. It must be generated by the user, never by this
  environment or by CI. Every release so far is debug-signed.
- Clinical review of the wording. The phrasing in `Deviation`,
  `ReportScreen` and the settings copy is the part of this app most in
  want of somebody clinically trained reading it before it reaches anybody
  who is struggling.

---

## 8. Everything verified without a compiler

There is no Android SDK in this environment and `dl.google.com` is
blocked. CI is the only compiler, and there is no device at all. The
method that replaced them:

- **Pure logic in Kotlin, mirrored in Python before pushing.** Every
  numerical claim in this document — the sleep-onset z-scores, the
  ModelSlot fit table, the 20-of-26 corpus survival, the per-signal
  retrieval check, the guard verdicts — was run in Python first and
  matched against the Kotlin by hand.
- **Compiler errors read from full logs, never diagnosed by inspection.**
  This was learned the expensive way: one CI failure was "fixed" by
  changing code that was never the problem, and the real error —
  `@Composable invocations can only happen from the context of a
  @Composable function`, a `stringResource` inside a non-inline
  `joinToString` lambda — was sitting in the log the whole time.
- **`// NOTE(ci):` markers** on every Android API call site that could not
  be verified here, naming the exact symbols to check first if the file
  fails to compile.
