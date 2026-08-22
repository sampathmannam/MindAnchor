# MindAnchor v0.32.0 — Home declutter + Letters UI signal

v0.32.0 is a UI-cut release. The model, the API, the worker, the
overnight report — all unchanged from v0.31.2. The surface on
which the user lands when they unlock the phone is smaller.

## What the user sees

The home surface on a quiet night now has two cards above the
fold instead of three. The Distress Thermometer (validation-
first) is still the first card; the QuickNotesCard is the only
input card. The OpenLoopCard — the last of the three task-
capture cards the v0.26.6 audit counted as "one too many for
a person with BPD" — is removed. The data model is kept, so
a future re-introduction or a "more moments" surface can
render the same composable without any restoration work.

On Letters, tapping "Generate now" now shows a Toast that
tells the user the model is generating and that the Q2_K
decode takes 30-60 minutes on this phone. Without the
Toast, the button was a silent no-op for the full decode
window. A "Letter saved" Toast on success. The failure
path stays silent (the overnight path is the canonical
one, the "Generate now" affordance is for users who want
to see the running signal, not to read the paragraph in
three minutes).

## Fixed in v0.32.0

### 1. OpenLoopCard removed from home

The home scroll order is now: `HomeDistressCard → QuickNotesCard
→ (optional WellnessCard, hidden when no data) → (optional
ReportSection, hidden when no report) → "Right now" section`.
The OpenLoopCard's data model is unchanged — `LauncherViewModel
.openLoop` and the `viewModel::saveOpenLoop / postponeOpenLoop
/ clearOpenLoop` bindings are still there, the `friction/
OpenLoop.kt` data class is still there, and the `friction/
OpenLoopPostponementFindingTest` / `friction/IntegritySealed
CodecTest` are still green. Only the home-surface call site
is removed. A future re-introduction is a one-line render
and a one-line FindingTest pin back.

The reduction is justified by the v0.26.6 audit, §3: three
task-capture cards on a BPD-strict home is one too many.
v0.26.6 cut BedtimeListCard. v0.28.0 cut OneThingCard.
v0.32.0 cuts the last one.

The two FindingTests that pinned the home order are updated
to pin the v0.32.0 order:

- `HomeDistressCardFindingTest.QuickNotesCard call site is
  after HomeDistressCard and the OpenLoopCard is removed in
  v0-32-0` — asserts `OpenLoopCard` is **not** called on
  home, and `QuickNotesCard` follows `HomeDistressCard`.
- `HomeDistressCardFindingTest.HomeDistressCard Composable
  is defined and is the first card on home` — asserts the
  same; this test was written to compare against `OpenLoopCard`'s
  index, which no longer applies.

Both tests would have failed if the v0.32.0 cut had silently
re-introduced the card. They are now the regression net.

### 2. Letters Toast on Generate now

The Compose `onGenerateNow` lambda had no UI feedback. A
user tapping the button on a Q2_K phone got nothing for 30-60
minutes, then either a letter in the inbox (success) or
nothing at all (failure — silently). The v0.32.0 lambda:

1. Fires a `Toast.LENGTH_LONG` "Generating tonight's letter
   — the Q2_K model on this phone takes 30–60 minutes. The
   letter appears in the inbox when it finishes." before
   the generation starts. The duration says the truth —
   that this is an overnight-scale operation, not a tap-and-
   wait one — which is the design the user asked for.

2. Fires a `Toast.LENGTH_SHORT` "Letter saved" on success
   when `letterStore.saveUserLetter(...)` runs.

3. Stays silent on failure. The failure path is the
   "Q2_K decode returned null because the model didn't fit
   in RAM" path, which would have been hit in v0.30.x but
   is no longer reachable in v0.31.2 (the b4792 upgrade +
   the Q2_K + KV-Q4_0 config + flash_attn + n_ubatch=32
   fits in 1.8 GB). The Toast is a "you tapped it, it ran"
   signal, not a "watch the progress bar" widget.

The Toast is the right affordance here because the existing
Letters inbox already shows the resulting letter at the top
of the list when generation finishes — the user has a
visible success state without needing the Toast. The Toast
is the *intermediate* feedback that says "yes, the button
worked".

### 3. Per-5-token progress log in mindanchor_llama.cpp

v0.31.2 logged every 20 tokens. The Q2_K decode at ~0.2
tok/s on the Moto G84 with b4792 + flash_attn + KV-Q4_0
meant a 600-token generation takes ~50 minutes; 20 tokens
of progress = ~100 seconds between log lines, which is fine
for an overnight scheduler but masks "is the decode stuck
or is it just slow?" for the user looking at `adb logcat`.

v0.32.0 logs every 5 tokens instead. 5 tokens at 0.2 tok/s
= ~25 seconds between lines, which is a "this is alive" tick
without flooding the logcat ring buffer. The change is
in `app/src/main/cpp/mindanchor_llama.cpp`, inside the
decode-loop `for (int produced = 0; produced < max_new_tokens;
...)`, the existing `if ((produced + 1) % 20 == 0)` is
now `if ((produced + 1) % 5 == 0)`.

This is a pure diagnostic change — no behaviour change, no
test change. `adb logcat -s MindAnchor/llama:V` shows the new
cadence.

## Coros / Health Connect — nothing changed

The Coros Training Hub web API integration (v0.20.x, in
`app/src/main/java/org/mindanchor/vitals/coros/`) is
unchanged in v0.32.0. The bridge is wired through:

- `SettingsScreen.kt` — the "Connect COROS" form on the
  Reading section's lower half, with email + password +
  region (eu / us / asia). The Keystore-encrypted
  credential store (`CorosCredentialStore.kt`) keeps the
  password on disk in `EncryptedSharedPreferences`.
- `CorosAuth.kt` — single-flight `/account/login` with a
  23h-soft-margin re-login. The web token is in memory
  only; the password is the only thing persisted.
- `CorosSyncWorker.kt` — WorkManager `CoroutineWorker`,
  every 6h periodic + "Sync now" one-shot, never throws.
  Transient failures `Result.retry()`, missing credentials
  `Result.failure()`.
- `CorosVitalSource.kt` — local DataStore cache. Wellness
  card merges HC + Coros: HC wins for what it has, Coros
  fills the gaps (HRV is the canonical example — PPG
  from a phone-measured HRV is more accurate than a wrist
  optical one, but Coros's nightly HRV is what fills the
  days the phone did not measure).

The user has a COROS Pacer 3 (Pace line). The Training Hub
web API does the right thing for it: the API returns the
nightly HRV (RMSSD ms), RHR, training load, VO2max, and the
last 30 activities regardless of model. The MCP server
(`mcp.coros.com/mcp`) is the same data with a different
transport; the v0.20.x-era web API path is what MindAnchor
uses today.

To connect:

1. Settings → Reading → scroll past the model card to the
   "Connect COROS" form.
2. Enter the email + password the user uses to log in at
   `coros.com`. Region: `eu` (the default; `us` for US
   accounts).
3. Tap "Connect". The bridge runs the `/account/login` and
   the first sync (`/dashboard/query`, `/analyse/query`,
   `/activity/query`) immediately. A "Last sync" timestamp
   appears in the form within ~10s on a normal network.
4. The wellness card on home will start showing HRV / RHR
   from the watch within an hour of the first sync.

Disambiguation: this app does **not** talk to Health
Connect for the watch. It talks to Health Connect for the
phone-side sensor (Samsung Health, Google Fit, anything
else on the device), and to Coros for the watch. The
merge in `CorosVitalSource.mergeWith` is the seam.

## File changes

- `M app/src/main/java/org/mindanchor/launcher/HomeScreen.kt`
  (removed OpenLoopCard render; added Toast on Generate
  now)
- `M app/src/main/java/org/mindanchor/letters/LetterWriter.kt`
  (MAX_NEW_TOKENS restored 30 → 600; the v0.31.2 test-only
  30 was a debugging aid that should not ship)
- `M app/src/main/cpp/mindanchor_llama.cpp` (progress log
  every 20 → 5 tokens)
- `M app/src/test/java/org/mindanchor/launcher/HomeDistress
  CardFindingTest.kt` (pin v0.32.0 home: Distress first,
  OpenLoopCard not on home)
- `M app/src/test/java/org/mindanchor/launcher/QuickNotes
  CardAboveFoldFindingTest.kt` (pin v0.32.0 home: Distress
  → QuickNotes; OpenLoopCard not on home)

No native rebuild required for the Kotlin / Compose changes
in 1 and 2; the `mindanchor_llama.cpp` change in 3 does
rebuild the native lib (the file is in the CMake tree).

bumped: versionCode 59→60, versionName 0.31.2→0.32.0
