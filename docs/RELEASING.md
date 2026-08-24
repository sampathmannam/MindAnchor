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

---

## 3. Cut a release

Either push a `v*` tag, or run the **Release** workflow manually with a
tag like `v0.10.0`.

The workflow builds a signed release APK when the secrets are present and
falls back to a debug APK when they are not, so forks and contributors
still get a working build. Every release publishes the artifact's SHA-256
in the release notes — the only way for someone downloading outside a
store to confirm they have the file CI actually built.

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

- [ ] Signing key created and backed up in two offline places
- [ ] `docs/CLINICAL_REVIEW.md` reviewed by a clinician, and the crisis
      numbers confirmed against the operators themselves
- [ ] The app installed and used on a real phone for more than a day
- [ ] Screenshots reviewed at large font scales

The second item is not a formality. This app holds a suicide safety plan,
and no amount of test coverage substitutes for someone qualified having
read what it tells a person in crisis.

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

