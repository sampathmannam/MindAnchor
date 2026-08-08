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
  data reset to empty on first launch. The integrity
  layer returns the empty/reset value on any read where
  the envelope marker is missing, the MAC fails to
  verify, or the base64 MAC part is malformed. The user
  loses their small-things / if-then plans /
  compassion moments but not their core friction-gate
  progress (the per-app tally is in GateLedger, which
  the v0.20.0 form remains readable — the integrity
  layer is layered on top, not the underlying codec
  path). The data loss is bounded and recoverable on
  the next write.
- The Keystore key is per-app-install. An uninstall +
  reinstall loses the key, which means v0.20.1+ data
  is unreadable after a reinstall. v0.20.1 is
  intentionally fail-closed: a reinstalled app sees an
  empty list, not the v0.20.0 form. The user can
  re-enter their small-things. There is no automatic
  backup (the project's no-cloud promise); the
  threat model is "a motivated user forges the gate
  tally," not "a user reinstalls the app."
- A v0.20.0 → v0.20.1 upgrade resets the user's
  small-things / if-then plans / compassion moments
  to empty. This is the *intended* security fix:
  the v0.20.0 plaintext form is treated as either a
  forge or an unverified record, and the right
  behaviour is to start over. (CodeRabbit audit
  2026-08-08: the v0.20.0 fall-through to plaintext
  was a CRITICAL security issue — a power user with
  root could rewrite the friction-gate ledger and
  silence the gate.)

## Migration behavior (v0.20.1 — final)

A v0.20.0 form on disk is *rejected* on read. The
integrity layer's decode() returns the reset value
(an empty list, an empty plan). The first write after
the upgrade produces a sealed record. The user
experiences this as "my small-things are gone" — the
data is unrecoverable from a v0.20.0 form because
the v0.20.0 form had no integrity tag. This is the
correct trade-off: the threat is forgery, and a
v0.20.0 form cannot be distinguished from a forged
record.

The alternative — a v0.20.0 → v0.20.1 migration that
accepts the plaintext on read — was the v0.20.0
behavior. It was a vulnerability. v0.20.1 is
fail-closed by design.

## Verification

- 14 new test cases in `IntegritySealedCodecTest`:
  1. round-trip encode-decode preserves content
  2. encoded form is v1 envelope, payload, base64 MAC
  3. byte-flip in payload fails verification, returns reset
  4. byte-flip in MAC fails verification, returns reset
  5. empty-codec round-trip
  6. unicode-codec round-trip
  7. wrong-key verification fails, returns reset
  8. plaintext without envelope is rejected, returns reset
  9. v0.20.0 tab-mac form (no v1 prefix) is rejected
  10. re-encoding a reset value produces a sealed record
  11. MAC is deterministic
  12. different payloads produce different MACs
  13. empty string is a valid default reset
  14. MAC tag length is 32 bytes (SHA-256 output)
- All assertions Python-mirror-verified: 22/22 (logic
  mirror) + 14/14 (JVM test).
- Brace/paren balance re-checked on all new files.
- No new Android dependencies; Keystore and Mac are
  stdlib.
- Production wiring: FrictionPrefs uses
  `SealedCodecs.encodeSmallThings` /
  `SealedCodecs.decodeSmallThings` (and the bedtime,
  compassion, and if-then equivalents) on every
  read/write. The raw `SmallThings.encode` /
  `BedtimeList.encode` / `CompassionStore.encode` /
  `IfThenPlanStore.encode` are not used in
  FrictionPrefs anymore — they are still
  `internal`-visible to the sealed codec layer.

## Primary sources

- OWASP MASTG-BEST-0066, *Storage Integrity Checks on
  Android*
- Android Keystore documentation,
  https://developer.android.com/privacy-and-security/keystore
- Android Cryptography documentation,
  https://developer.android.com/privacy-and-security/cryptography
- Android Key Attestation documentation,
  https://developer.android.com/privacy-and-security/security-key-attestation
