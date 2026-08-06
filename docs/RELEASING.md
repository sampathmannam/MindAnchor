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
