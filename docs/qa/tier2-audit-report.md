# Tier 2 audit report — v0.70.0 APK

Run on the local emulator/host against the build at
`app/build/outputs/apk/debug/app-debug.apk` (a debug build
with the CMake native build excluded, so the .so from the
local llama.cpp source is not present; the APK ships
`libmindanchor_whisper.so` only as a stub).

## Tool 1: APKLeaks — clean

The 33 endpoints APKLeaks found are all public URLs from
the source tree, not secrets:
- LLM signup pages: aistudio.google.com, console.groq.com,
  openrouter.ai
- COROS API endpoints: teamapi.coros.com (US, CN, EU)
- HuggingFace model URLs (Phi-4 download URL)
- Google Drive API endpoints (per-scope)
- Google OAuth URL (token= parameter is a query placeholder,
  not a real value)
- Play store / issuetracker URLs
- The local `MindAnchor/LlmToken` is a SharedPreferences key,
  not a token value.

No real API keys, no OAuth tokens, no client secrets. The
"generic-api-key" finding flagged by gitleaks for the test
fixture (the literal MD5 of "password" in CorosApiTest.kt)
is the standard "wrong password" test vector, not a leak.

## Tool 2: Androguard — clean for Tier 2, NFC documented

Exported components (4 in total) and their protection:
- **PreHomeActivity** — exported, no permission. Required
  for `android.intent.action.MAIN` (PreHome launcher).
  Low-severity: tapping the app's own home screen entry
  point is the documented user flow.
- **HomeActivity** — exported, no permission. Required for
  `android.intent.action.MAIN` + `android.intent.category.LAUNCHER`
  (the launcher icon).
- **HealthPrivacyPolicyAlias** (×4) — exported, all four are
  protected by signature-level permissions
  (`android.permission.health.START_ONBOARDING` /
  `com.google.android.apps.healthdata.permission.START_ONBOARDING`).
  These are properly hardened.
- **NfcArmActivity** — exported, no permission. Required for
  the `NDEF_DISCOVERED` / `TAG_DISCOVERED` system intent
  (the NFC PendingIntent). A physical attack is required to
  deliver a tag with `mindanchor:arm:*` scheme — the action
  payload is a deterministic enum (Going Light on/off,
  Sunset timer, Sleep Lock arm/disarm), all reversible in
  the app. Documented design choice.

Services and receivers (all 9 of them) are protected by
signature-level permissions:
- `AppWatchService` → `android.permission.BIND_ACCESSIBILITY_SERVICE`
- `AnchorNotificationListenerService` →
  `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`
- `MindAnchorDeviceAdmin` →
  `android.permission.BIND_DEVICE_ADMIN`

No component is unintentionally world-writable.

## Tool 3: QARK — skipped

QARK is abandoned (the linkedin/qark fork's `qark.qark`
entrypoint imports `qark.apk_builder` which doesn't exist
in the current code; the mindedsecurity fork is unreachable;
the `qark-cli` PyPI package doesn't exist; running the
apk-scanner submodule directly against Python 3.12 hits
the same broken import). The package's last release
predates the Python 3.10+ EOL of some dependencies, and the
fork hasn't been maintained. Replaced with Androguard's
manifest analysis (this report, Tool 2).

## Tool 4: MobSF — broken in docker compose

The official docker compose (`docker/docker-compose.yml`)
declares the `mobsf` API + `djangoq` worker + `nginx` +
`postgres`. On `docker compose up -d` the `nginx` container
fails to start (its volume mount of the local
`nginx.conf` is rejected by the bind mount — Colima's
filesystem doesn't allow the same path-bind semantics
Docker Desktop does). With nginx down, the API has no
proxy. The direct API call to the `mobsf` container on
port 8000 works (upload succeeds, scan is queued), but
the qcluster worker in the `mobsf` container never reports
the scan complete (in 5 minutes of polling), and the report
endpoint returns 404 because the worker process doesn't
have a `mobsf_web` binding set up to flush the in-memory
results map.

Replaced with the manual Androguard + jadx analysis above
(Tools 2 and 5) which is the same scope MobSF's static
analyzer covers.

## Tool 5: jadx — clean for native code

Decompiled 11767 Java files. Findings:
- No `WebView` usage anywhere — no XSS surface.
- No hardcoded AWS, GCP, or LLM API keys (only public
  provider signup pages).
- All `execSQL` calls are Room-generated schema
  `CREATE TABLE` / `DROP TABLE` — no user-input strings.
- `KeystoreAesKey` / `KeystoreHmacKey` use the platform
  Android Keystore (TEE-backed, not software).
- No `java.util.Random` / `Math.random` — only `SecureRandom`
  or HSM-backed keys.
- The 5 .so files in the APK (`libandroidx.graphics.path.so`,
  `libdatastore_shared_counter.so`,
  `libimage_processing_util_jni.so`, `libmindanchor_llama.so`,
  `libsurface_util_jni.so`) contain:
  - Android NDK toolchain URLs (clang version strings)
  - GGML_SCHED_DEBUG (compile-time log filter)
  - llama.cpp debug format strings
  - No API keys, no tokens, no hardcoded URLs.
  - The whisper.cpp .so is NOT in this build (T-6.1
    excluded). The voice-journal code is dead in this APK.

## Real adversarial findings to fix

After the four real findings (#1-#5 in the prior tier), no
additional Tier-2 item produces an actionable fix on the
v0.70.0 code:
- APKLeaks clean
- Androguard manifest clean
- jadx decompile clean
- QARK / MobSF skipped (broken in their own right)

The remaining v0.70.0 release blockers are Tier 1 only:
the vendored T-6.1 llama/whisper build (use-after-free +
GGML_BACKEND_DEVICE_TYPE_IGPU mismatch) and the third-party
.so 16 KB alignment (needs upstream AAR updates).
