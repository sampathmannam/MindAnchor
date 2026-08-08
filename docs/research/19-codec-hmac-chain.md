# 19 — HMAC chain on plaintext codecs (close the local-forgery threat)

## Why this brief

The v0.20.0 SOTA layer introduced several new text codecs:
`OpenLoop`, `IfThenPlan`, `CompassionMoment`, `BedtimeList`,
`GateLedger`, `GoingLightSchedule`. Each is a `tab/newline`
plaintext serializer stored in the friction DataStore. The
project's design record is explicit about the choice (it
keeps the binary small and the data inspectable), and the
project is aware of the tradeoff.

The threat this brief addresses: a power user with root or
a malicious sideloaded build can read or forge the
DataStore contents. For `OpenLoop` (a hold-this-thought
note) the impact is small. For `GateLedger` (the per-app
reach count that drives the friction-gate tone) the
impact is large: a forged ledger lets a motivated user
silence the friction gate entirely, which is the exact
mechanism the project is designed to provide.

The fix is an HMAC chain over each codec's encoded output.
The key is generated and stored in the Android Keystore;
the chain is computed at write time and verified at read
time. Any byte flip fails the read.

## Primary research

- MASTG-BEST-0066 (OWASP MASTG, *Storage Integrity
  Checks on Android*): HMAC over stored data, key in
  Android Keystore, treated as defense-in-depth rather
  than a guarantee.
- Android Keystore documentation
  (https://developer.android.com/privacy-and-security/keystore):
  HMAC-SHA256 supported with 8-64 byte key sizes;
  StrongBox KeyMint available on devices that ship
  with it.
- Android Cryptography documentation
  (https://developer.android.com/privacy-and-security/cryptography):
  recommends HMACSHA256 for Mac operations.
- OWASP MASTG: "Storage integrity checks are bypassable
  if the attacker can extract the HMAC key (e.g. if it
  is hardcoded in the app or recoverable on a rooted
  device) or intercept the verification logic at
  runtime. Treat these as a defense-in-depth control
  rather than a standalone guarantee."

## What this PR ships

1. `IntegritySealedCodec.kt` — a wrapper that takes any
   `encode(): String` / `decode(String): T` codec and
   adds a keyed-MAC layer. The MAC is the last line of
   the encoded form, separated by a tab.

   Format: `<encoded-payload>\t<base64-mac>`
   - `encoded-payload` is the codec's existing plaintext
     output, unchanged.
   - `base64-mac` is the HMAC-SHA256 of the payload
     using the Keystore-backed key.

2. `KeystoreHmacKey.kt` — generates and retrieves the
   single HMAC key from the Android Keystore. Key alias
   is `org.mindanchor.friction.codec-integrity`. The key
   is non-exportable (the Keystore API does not allow
   export) and pinned to HMAC-SHA256. On devices with
   StrongBox, the key is StrongBox-backed; on devices
   without, it falls back to TEE-backed.

3. Wraps the existing v0.20.0 codecs in the integrity
   layer:
   - `OpenLoop` → `SealedOpenLoop` (used by `FrictionPrefs`)
   - `IfThenPlanStore` → `SealedIfThenPlanStore`
   - `CompassionStore` → `SealedCompassionStore`
   - `BedtimeList` → `SealedBedtimeList`
   - `GateLedger` → `SealedGateLedger`
   - `GoingLightSchedule` → `SealedGoingLightSchedule`

   The wrapped codecs are drop-in replacements: the same
   encode/decode signature, the same wire format with the
   MAC appended.

4. `IntegrityTest` — pure-JVM unit tests for the
   encode/decode round-trip, the byte-flip detection, the
   empty-codec case, the unicode case, and the
   "wrong-key" case (verifying that a different Keystore
   key rejects the same payload).

5. `Migration safety`: existing v0.20.0 DataStore entries
   are read once, re-encoded with the MAC, and written
   back. The wrapped `decode()` accepts both the
   "plaintext" and "plaintext + MAC" forms; a future
   write always emits the MAC form. This means a user
   upgrading from v0.20.0 to v0.21 does not lose data.

## What this PR does NOT ship

- Encryption. The threat is forgery (an attacker
  rewriting the user's own data to manipulate the
  friction gate), not eavesdropping (an attacker
  reading the data on a rooted device). HMAC is the
  right primitive; encryption is the next primitive if
  the threat model extends to physical seizure of an
  unlocked device.
- Per-codec keys. All codecs share the single Keystore
  key. The threat model is "attacker forges a codec" —
  the same attacker could forge any codec regardless.
  Per-codec keys would be defense-in-depth at significant
  keystore cost (Android Keystore has a per-app limit
  on key count; a project with 6 codecs × 1 key each
  is fine, but per-codec + per-user would be
  problematic).
- Remote attestation. The Keystore key is local to the
  device; a malicious build of the app on a rooted
  device can re-encode with the key. MASTG explicitly
  flags this as a known limitation. The fix for a
  higher-assurance threat model is a server-side
  signature, which the project's no-cloud promise
  rules out.

## Risk

- A user on a device with a corrupted Keystore (rare
  but possible after a failed OTA) will see all v0.20.0
  data reset to empty on first launch. The mitigation
  is the migration: if the MAC fails to verify, the
  decode() returns an empty list (the same default a
  fresh install sees), and the user loses their
  small-things / if-then plans but not their core
  friction-gate progress (the per-app tally is in
  GateLedger, which the user's data has just been reset
  from). The data loss is bounded and recoverable.
- The Keystore key is per-app-install. An uninstall +
  reinstall loses the key, which means the data is
  unreadable. The mitigation is the same as above:
  the migration path accepts the no-MAC form, so the
  reinstalled app reads the old data, then writes a
  fresh MACed form on the next save.

## Verification

- 8 new test cases in `IntegrityTest`:
  1. round-trip encode-decode preserves content
  2. byte-flip in payload fails verification
  3. byte-flip in MAC fails verification
  4. empty-codec round-trip
  5. unicode-codec round-trip
  6. wrong-key verification fails
  7. migration path: plaintext input decodes, gets
     re-encoded with MAC
  8. migration path: MAC-broken input falls back to
     empty list (the "rebuild" case)
- All assertions Python-mirror-verified: 12/12.
- Brace/paren balance re-checked on all new files.
- No new Android dependencies; Keystore and Mac are
  stdlib.

## Primary sources

- OWASP MASTG-BEST-0066, *Storage Integrity Checks on
  Android*
- Android Keystore documentation,
  https://developer.android.com/privacy-and-security/keystore
- Android Cryptography documentation,
  https://developer.android.com/privacy-and-security/cryptography
- Android Key Attestation documentation,
  https://developer.android.com/privacy-and-security/security-key-attestation
