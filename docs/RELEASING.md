# Releasing

## Why this matters more than it looks

Until a release key exists, every build is debug-signed. That is why Play
Protect blocks the install and why the app has to be forced through
"restricted settings" to get onto a phone — a hostile first contact for
something whose entire purpose is to feel calm.

It is also permanent in one direction: **the key that signs the first
public release is the only key that can ever ship an update to it.** Lose
it and every existing install becomes a dead end, upgradeable only by
uninstalling — which for this app means deleting somebody's safety plan.

---

## 1. Create the key (do this yourself, once)

Run this on a machine you control. Do not run it in CI, do not paste the
output anywhere, and do not let anyone else generate it for you — a
signing key is an identity, and it should only ever be held by the person
whose name is on the release.

```sh
keytool -genkeypair -v \
  -keystore mindanchor.jks \
  -alias mindanchor \
  -keyalg RSA -keysize 4096 \
  -validity 10000 \
  -storetype pkcs12
```

Use a long random passphrase from a password manager.

**Back it up before doing anything else.** Two copies, offline, in
different places. The keystore file *and* the passphrase — one without the
other is useless.

---

## 2. Give CI the key

Encode it:

```sh
base64 -w0 mindanchor.jks > mindanchor.jks.base64
```

Then add four repository secrets under **Settings → Secrets and variables
→ Actions**:

| Secret | Value |
|---|---|
| `MINDANCHOR_KEYSTORE_BASE64` | contents of `mindanchor.jks.base64` |
| `MINDANCHOR_KEYSTORE_PASSWORD` | the store passphrase |
| `MINDANCHOR_KEY_ALIAS` | `mindanchor` |
| `MINDANCHOR_KEY_PASSWORD` | the key passphrase |

Delete `mindanchor.jks.base64` afterwards. The `.jks` stays in your
backups, never in the repository — `.gitignore` covers the pattern, but
the real protection is not putting it there.

Then record the certificate's public fingerprint as a repository
**variable** (not a secret — a certificate fingerprint is meant to be
publicly verifiable, that's the point of it) under **Settings → Secrets
and variables → Actions → Variables**:

```sh
keytool -exportcert -alias mindanchor -keystore mindanchor.jks | \
  openssl x509 -inform der -noout -fingerprint -sha256
```

If `openssl` isn't on your `PATH` (plain Windows PowerShell often doesn't have
it), `keytool` alone prints the same fingerprint — no extra tool needed:

```sh
keytool -list -v -keystore mindanchor.jks -alias mindanchor
```

Look for the `SHA256:` line under "Certificate fingerprints:".

| Variable | Value |
|---|---|
| `MINDANCHOR_RELEASE_CERT_SHA256` | the `SHA256` fingerprint from the command above, hex digits only, colons removed (case-insensitive — the workflow uppercases both sides before comparing; `apksigner`'s own output has no colons, which is what it's actually compared against) |

The release keystore now exists (generated 2026-08-29). Its certificate's
SHA-256 fingerprint:

```
DFD147DCCF0E99AE156F79811D3885076129A3B0F57108E724D4FBE6450E87FD
```

This value must also be added as the `MINDANCHOR_RELEASE_CERT_SHA256`
repository **variable** on GitHub (Settings → Secrets and variables →
Actions → Variables) — recording it here alone does not configure the
workflow.

---

## 3. Cut a release

Either push a `v*` tag, or run the **Release** workflow manually with a
tag like `v0.10.0`.

**The workflow fails closed.** If any of the four signing secrets is
missing, the job exits before building anything and no GitHub Release is
created — there is no debug-signed fallback for an official release.
Forks and contributors still get ordinary debug builds from `ci.yml` on
every push; they just never come out of this workflow as something
tagged "official."

When the secrets are present, the workflow:

1. builds the release APK twice, cleanly, with the same
   `SOURCE_DATE_EPOCH` (see `tools/verify-reproducible-release.sh`) and
   fails if the two builds don't produce byte-identical output;
2. runs `apksigner verify --print-certs` on the result and compares the
   certificate's SHA-256 digest against the `MINDANCHOR_RELEASE_CERT_SHA256`
   repository variable recorded above — a mismatch fails the release,
   which is what catches a wrong or compromised keystore being used;
3. publishes the APK's own SHA-256 in the release notes — the only way
   for someone downloading outside a store to confirm they have the file
   CI actually built.

### Reproducibility boundary

`tools/verify-reproducible-release.sh` proves the *unsigned build
content* (resources, DEX, native libraries, manifest) is reproducible
given a fixed `SOURCE_DATE_EPOCH` — that part is fully mechanical and
runnable by anyone, with or without the release keystore. The *signed*
APK reproducibility this task actually cares about additionally depends
on Android's APK Signature Scheme producing a deterministic signature
block for a fixed keystore and fixed input, which this repository cannot
independently verify without the real release keystore in CI. If a
future signing scheme or plugin version ever makes signed output vary
between otherwise-identical builds, the fix is to compare the *unsigned*
APK as the primary reproducibility proof and keep certificate
verification (step 2 above) as a separate, explicit check — not to
loosen the hash comparison to "close enough."

---

## 4. Version numbers

`versionCode` is a plain integer that must increase on every public
release; Android refuses to install an APK whose code is lower than the
one already present. `versionName` is what people read. Both live in
`app/build.gradle.kts`.

---

## 5. F-Droid

F-Droid is the right home for this app: it builds from source, so users do
not have to trust a binary from anyone. It requires the app to be free
software with no proprietary dependencies, which this already satisfies —
GPL-3.0, no Google Play Services, and `dependenciesInfo` disabled so the
APK carries no proprietary metadata block.

Submission is a merge request against `fdroiddata` and is a human process;
it is not automated here, deliberately.

---

## 6. Before the first public release

The signing key setup is an owner-only manual step — no automated task
in this repository can perform it, since it requires holding a private
key and a GitHub Secrets admin login that only the owner has:

- [x] Create one release keystore, once (§1 above) — done 2026-08-29
- [ ] Store two offline copies outside the phone and outside the repository
- [ ] Configure `MINDANCHOR_KEYSTORE_BASE64`, `MINDANCHOR_KEYSTORE_PASSWORD`,
      `MINDANCHOR_KEY_ALIAS`, and `MINDANCHOR_KEY_PASSWORD` in GitHub Secrets
- [ ] Record only the public certificate SHA-256 fingerprint as the
      `MINDANCHOR_RELEASE_CERT_SHA256` repository variable (§2 above) —
      do not consider this step complete until the fingerprint the
      release workflow actually built matches the one recorded here
- [ ] Install two consecutive signed builds over each other on a real
      device and confirm Android accepts the upgrade (see
      `docs/qa/program-0-upgrade-runbook.md` for the full procedure)

And separately:

- [ ] `docs/CLINICAL_REVIEW.md` reviewed by a clinician, and the crisis
      numbers confirmed against the operators themselves
- [ ] The app installed and used on a real phone for more than a day
- [ ] Screenshots reviewed at large font scales

The clinical-review item is not a formality. This app holds a suicide
safety plan, and no amount of test coverage substitutes for someone
qualified having read what it tells a person in crisis.

### 6.1 Program 0 (v0.71.0) readiness

Program 0's own plan (`docs/superpowers/plans/2026-08-28-program-0-continuity-proof.md`)
adds one more gate on top of the checklist above, specific to this
release: **"Program 0 exits only after repeated physical
replacement-phone restores produce matching content hashes."** All
automatable work — the full continuity round trip, offline-startup
pinning, the release-safety/reproducibility tooling, and the whole
JVM/instrumentation test suite — is implemented and passing (see
`docs/qa/program-0-continuity-runbook.md` for the exact automated
coverage already in place). `docs/qa/program-0-continuity-log.md` is
still a template: **zero physical Device A → Device B restores have
been run**, because this was implemented in an environment with no
physical hardware, no second device, and no real Google account. v0.71.0
must not be tagged until the runbook has actually been executed and
`program-0-continuity-log.md` records three successful, hash-matching
runs — this is a hard gate, not a formality, exactly like the
clinical-review item above.

---

## 7. Play-Store declaration forms (for the day the F-Droid-first strategy ships to Play)

MindAnchor is F-Droid-first per the project rule (`docs/PLAN.md` §4).
F-Droid builds from source and does not require Play-Store declaration
forms. **However**, if and when MindAnchor is submitted to Play, the
following declarations are required. The forms are documented here so
they exist in the repo, not because they need to be filed at this release.

### 7.1 `specialUse` foreground-service declaration

`app/src/main/AndroidManifest.xml` declares
`GoingLightVpnService` with `android:foregroundServiceType="specialUse"`
on any future release that adds the Going Light UI affordance, paired
with the permission `android.permission.FOREGROUND_SERVICE_SPECIAL_USE`.
The Play declaration form text is:

> **Why is your app using a foreground service of type `specialUse`?**
>
> MindAnchor is a mental-health-first Android launcher. The
> `GoingLightVpnService` is a local VpnService that drops mobile-internet
> traffic during a user-chosen "Going Light" window (Castelo et al. 2025
> *PNAS Nexus* 4(2):pgaf017, N=467 RCT, *d~z~* = 0.57 mental-health
> effect — larger than the meta-analytic effect of antidepressants). The
> VpnService captures loopback traffic only and decides forward-or-drop
> per packet, locally. The VPN never tunnels anywhere; the loopback
> interface is the only place the captured packet goes. The service
> does not perform any data-sync, location, media, or device-control
> function; the `specialUse` type is the closest match because the
> service is processing loopback packets, not syncing data to a remote
> endpoint. `NetworkCallsForbiddenTest` enforces that no outbound
> network call ever leaves the app.

### 7.2 Health-apps declaration (for the WHO-5 + wearable data)

The launcher reads wearable data from Health Connect and uses it to
populate the per-person wellness signals and the WHO-5 deltas. It never
writes wearable data back. The Play declaration form text is:

> **Is your app a health app?**
>
> MindAnchor reads sleep, heart rate, HRV, steps, and mindfulness
> minutes from Health Connect. It uses this data to surface a per-person
> well-being trend and to trigger the WHO-5 pulse cadence (Topp et al.
> 2015 *Psychother Psychosom* 84(3):167–176, cut-off ≤ 50, sensitivity
> 0.87, specificity 0.76). **MindAnchor is a wellness tool, not a
> medical device.** It does not diagnose, does not treat, and does not
> claim to. The app never interprets a WHO-5 score as a diagnosis. The
> data is **estimates**, never diagnoses, in the wording on the
> wearable section of the launcher.

### 7.3 Sensitive-permission declarations (already in use)

- `BIND_NOTIFICATION_LISTENER_SERVICE` — the launcher batches
  notifications. The user is asked for this consent via the system
  Settings → Notifications → Notification access screen; the launcher
  does not request it from inside the app.
- `BIND_ACCESSIBILITY_SERVICE` — the launcher's `AppWatchService` is
  an optional add-on; `canRetrieveWindowContent` is `false` in
  `res/xml/app_watch.xml`, so the service cannot read screen content.
  Only the package name and the foreground-state transition are
  received. The user consents via the system Settings → Accessibility
  screen.
- `RECEIVE_SENSITIVE_NOTIFICATIONS` — Android 15+ permission,
  `role|signature` protection. Auto-granted to apps holding the
  `HOME` role; MindAnchor as the default launcher is auto-eligible.
  The launcher does **not** read OTP codes or 2FA messages; the
  permission enables the launcher to read *any* notification from
  *any* package for the Held-for-Later batching feature.

---

## 8. v0.26+ release plan (Phase 0 audit, Phase 1 through Phase 4)

The Phase 0 audit is the prerequisite for the v0.26+ protective layer.
The phases are documented in the canonical plan at
`docs/superpowers/specs/2026-08-23-v0.26+-vision.md` (or the most-recent
plan file in `docs/superpowers/specs/`). The release tags are:

| Tag | Phase | Deliverable | Months |
|---|---|---|---|
| `v0.26.0` | Phase 1 | Going Light v1.1 UI affordance, friction-bandit persistence, clinical-review R-words, morning self-compassion break, BA weekly prompt, DEAR MAN / GIVE / FAST, compassionate wrap, notification diet | 1–3 |
| `v0.27.0` | Phase 2 | On-device LLM compassionate reframe (G-3), NFC physical anchor (G-4), Sleep Lock (G-5), per-app session-length UI (G-16), structured if-then builder (G-17), TIPP crisis-survival surfacing (G-24) | 4–7 |
| `v0.28.0` | Phase 3 | Expressive writing (G-8), n-of-1 weekly pattern discovery (G-25), wind-down mode (G-26), gratitude card (G-29), Health Connect mindfulness data type (G-30), privacy flow UI (G-31), pre-merge clinical-review gate (G-34), on-device log scrubber (G-35) | 8–9 |
| `v0.29.0` | Phase 4 | Push-up micro-friction (G-6), voice journaling (G-28), synthetic-persona simulation framework (G-12), onboarding polish (G-13), release engineering (G-14), 2-week live test (G-36) | 9–12 |

Each release bumps `versionCode` monotonically and the `versionName` in
`app/build.gradle.kts`. The 2-week live test (G-36) is parallel to the
release engineering; its log lives at `docs/qa/real-2-week-log.md`.

---

## 9. Program 3 (adaptive protocol delivery) release rules

Program 3 is the disabled-by-default historical advisory feature
(`app/src/main/java/org/mindanchor/advisory/`). It ships in every build,
including public ones, but an ordinary build's protocol allowlist is
empty at compile time, so it can deliver nothing regardless of any
runtime setting. The rules below govern the two ways that could change.
Current state is recorded, honestly, in
`docs/qa/program-3-adaptive-delivery-evidence.md` — read that file for
what is and is not true today, not this section.

**Public release.** A public (ordinary) build may name a protocol in its
allowlist only when all of the following hold, together, not
individually:

- the exact protocol definition named is `REVIEWED_AND_ACCEPTED`
  (`ClinicalReviewStatus`), not `NOT_REVIEWED`, `REVIEW_REQUESTED`, or
  `REVIEWED_WITH_CHANGES`;
- every new user-facing copy surface the feature added (the advisory
  card, the evidence screen, the player screen, the two settings rows,
  and their strings) is separately `REVIEWED_AND_ACCEPTED`;
- the ordinary allowlist itself becomes non-empty only in its own,
  separately reviewed change — never bundled into an unrelated commit;
- the change carries the clinical-review CI label.

**Current public protocol count is zero**, because `cyclic-sighing@1` is
`NOT_REVIEWED` and the ordinary allowlist
(`AdvisoryBuildAuthorization.ordinaryAllowlist`) is empty. This is a hard
gate, not a formality, exactly like the clinical-review item in §6 above.

**Personal-research delivery** (the owner's own device only, never a
public artifact) requires all of the following, together:

- Program 0, Program 2, and Program 3's own physical-device evidence are
  each complete (see `docs/qa/program-3-adaptive-delivery-evidence.md`
  and `docs/qa/program-3-adaptive-delivery-runbook.md`);
- explicit owner activation — a deliberate decision recorded outside
  source control, not inferred from a build succeeding;
- both `PROGRAM3_PERSONAL_RESEARCH=true` and
  `PROGRAM3_OPERATIONAL_EVIDENCE_APPROVED=true` set explicitly at build
  time;
- the master advisory switch and the delivery switch both deliberately
  turned on in Settings — neither defaults to true;
- the protocol started is exactly the one named in
  `AdvisoryBuildAuthorization.personalAllowlist`, by id, version, and
  definition hash — no other protocol may be named there.

**No evidence document and no build property may bypass** source
finality (`AVAILABLE_FINAL` + `SUSTAINED_DEVIATION`), source provenance
completeness, the active-episode-exists check, the cooldown timer, or the
runtime delivery kill switch. Those gates are evaluated from live state
at the moment of the action, every time, regardless of what any document
says has been approved.

Changing `cyclic-sighing@1`'s review status away from `NOT_REVIEWED`, or
adding an entry to either allowlist, is a separate clinical and release
decision — it is not something Program 3's implementation itself
authorizes, and it must not be bundled into an implementation commit.

