# 20 — COROS Training Hub side-channel (opt-in bridge)

## Why this brief

A COROS Pacer 3 writes heart-rate and exercise sessions to
Health Connect, but it does not release HRV. The wellness
card (`WellnessSignals`, see `docs/research/08`) reads HRV
from Health Connect when it can and falls back to camera PPG
when no wearable is on the wrist; the third fallback, for
the small set of users who own a COROS watch and want HRV on
the wellness card without holding a finger to the camera, is
the Training Hub web API.

The user has to opt in. The launcher's default promise
("zero outbound network calls") is preserved everywhere
else; this is a single, named, opt-in escape hatch.

## What this brief ships

A third-tier data source for the wellness card:

1. `CorosAuth.kt` — login orchestration, in-memory token
   cache with 23h TTL re-auth, single-flight concurrency on
   the login round-trip.
2. `CorosApi.kt` — OkHttp + kotlinx.serialization over the
   four Training Hub endpoints we need
   (`/account/login`, `/dashboard/query`, `/analyse/query`,
   `/activity/query`).
3. `CorosPasswordHasher.kt` — `md5Hex()` of UTF-8 bytes,
   lowercase output. The COROS API rejects uppercase hex
   (verified once during reverse-engineering, 2025-12-04:
   `result: "1001"` came back when uppercase was sent).
4. `CorosCredentialStore.kt` — EncryptedSharedPreferences
   with `MasterKey.AES256_GCM`. The user's email + password
   + region live here for the lifetime of the bridge.
5. `CorosVitalSource.kt` — DataStore cache of the last sync,
   with the HC-wins-when-present / COROS-fills-the-gap
   merge rule.
6. `CorosSyncWorker.kt` — WorkManager CoroutineWorker,
   6h periodic + on-demand one-shot.
7. Settings UI: a "Wearable bridge (COROS)" section under
   the existing "Wearable" section, with email / password /
   region form, "Connect", "Sync now", and "Disconnect"
   buttons, plus a "what this bridge does" explainer.

## Why the side-channel, not the mobile API

The COROS mobile API exists; it's the one the phone app
uses. We deliberately do *not* use it: per the
`CorosAPIError` KDoc on the open-source `cygnusb/coros-mcp`
reference, acquiring a mobile token **logs the user out of
the COROS phone app**. The user can keep the phone app
signed in and have the side-channel pull data in parallel —
but only if the side-channel uses the Training Hub *web*
API, which is a different auth path with a different token
lifecycle.

This is the core trade-off: the web API covers HRV (7-day
window via `/dashboard/query`), RHR + VO2max (28-day window
via `/analyse/query`), and the activity list
(`/activity/query`). It does not cover sleep — COROS does
not export sleep to any third party. Health Connect's sleep
record is the only route the launcher can use for sleep, and
the merge is HC wins / COROS fills for HRV + RHR.

## What "opt-in" means in code

Three concrete fences:

1. **Settings toggle.** The bridge is *off* by default. The
   user navigates to Settings → Measuring → Wearable bridge
   (COROS), types their email and password, picks a region,
   and taps "Connect to COROS". Only then does the launcher
   make any outbound call.
2. **EncryptedSharedPreferences.** The email + password sit
   in `EncryptedSharedPreferences` keyed by an
   `AES256_GCM` master key in the Android Keystore. A
   rooted attacker can read the encrypted blob but cannot
   decrypt it without the Keystore-backed key.
3. **Disconnect wipes everything.** The "Disconnect"
   button calls `CorosAuth.disconnect()` (in-memory token
   + credential blob) and `CorosVitalSource.clear()`
   (cached data). There is no "disconnect but keep
   credentials" affordance; the trade-off the user agreed
   to is "the launcher holds my password for as long as
   the bridge is on", and disconnect = wipe, every time.

## Why MD5 of the password

The COROS web API expects `pwd` to be a lowercase hex
MD5 of the UTF-8 password bytes. This is the only public
source for the scheme:

```
hashlib.md5(value.encode()).hexdigest()
```

(from `cygnusb/coros-mcp`, the reverse-engineered MCP
server). The launcher hashes the user's password with
`MessageDigest.getInstance("MD5")` before posting. The
plaintext password is in memory only long enough to hash
it; it is never written to disk in cleartext, and it is
cleared from the `corosPasswordDraft` state in the
settings UI the moment the form is submitted.

MD5 is cryptographically broken for collision resistance,
but the threat model here is "what hash does the COROS
server expect" — not "is MD5 a good password hash". The
launcher cannot use a better hash without the server
rejecting the request.

## Why a workmanager worker, not a foreground coroutine

The sync runs every 6 hours. The user is not in the
Settings screen most of the time; a foreground coroutine
would only fire on entry. A `PeriodicWorkRequest` with
`NetworkType.CONNECTED` constraint runs even when the app
is backgrounded, and the WorkManager backoff handles
transient failures (network down, server 503) without
spamming the user.

The on-demand "Sync now" is a `OneTimeWorkRequest` with a
unique name and `ExistingWorkPolicy.REPLACE` so a second
"Sync now" while the first is in flight joins the same
work — the user's intent is "sync now", not "sync twice as
hard".

## What the merge does

The wellness card reads `CorosVitalSource.mergeWith(hcByDate)`.
The merge rule:

- For HRV: HC wins if it has a value for that date, else
  COROS's nightly RMSSD. Reason: a PPG-measured HRV is
  more accurate than a wrist optical one — see
  `Sourcing.pick` for the rationale.
- For RHR: same rule — HC wins, COROS fills.
- For all other signals (steps, sleep, exercise,
  mindfulness): the bridge does not touch them. HC is
  the only source.

The `MergedDay` returned by `mergeWith` carries
`hrvSource` and `rhrSource` enums so the settings panel
can show "this reading came from Health Connect" /
"this reading came from the COROS bridge" if it wants to.

## The clinical-review-surface argument

The strings (`coros_bridge_explainer`, the privacy note,
the "what this bridge does" bullets) are wording-heavy in
the same sense as the Health Connect permission flow: they
declare what data the user is agreeing to send, and to
whom. The clinical-review gate fires on `strings.xml`
changes already; the manifest does not need a new
permission because `INTERNET` was added for the
`VpnService` in v0.20.0 and the side-channel uses the
same permission.

The `NetworkCallsForbiddenTest` carve-out is the second
clinical-review surface: adding a new file to
`corosBridgeFiles` is explicitly called out as a
"clinical-review-surface change" in the test's KDoc. The
test is the only thing standing between a motivated
contributor and a privacy-breaking `import okhttp3.OkHttpClient`
in some other file. The carve-out is scoped to
`vitals/coros/` and pinned to 5 file paths; any new file
in that package must be added to the set, which the
clinical-review gate then catches as a `strings.xml` /
wording review trigger or as a manifest change.
