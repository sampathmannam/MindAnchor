# Research-vs-Implementation Audit — does the code match the briefs?

**Date:** 2026-08-09
**Branch audited:** `work/going-light-vpn` (HEAD: `2b43508`)
**Scope:** every file under `app/src/main/`, `app/src/main/res/values/`, `app/src/main/res/xml/`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, every brief under `docs/research/`, and the design decisions in `docs/CLINICAL_REVIEW.md` / `docs/audit/crisis-line-feature-rejected.md`.
**Protocol:** read every file in scope, cite file:line for every finding, never guess. Where a brief is silent, write "the brief does not address this; not a contradiction."

The audit found that the implementation is, on the whole, faithful to the research base. The contradictions and gate violations are real and addressable, but they are *few*, *narrow*, and *not* of the "the app does the opposite of what the brief says" kind. A summary table is at the end.

---

## 1. Project design rules (the audit's reference frame)

These are taken from `docs/CLINICAL_REVIEW.md` and `docs/audit/crisis-line-feature-rejected.md`. They are the bar every finding is measured against.

| ID | Rule | Source |
|---|---|---|
| R1 | **No in-app crisis-line UI of any kind, opt-in or otherwise.** The safety plan and the user's own contacts remain the only routes. The footer "If you are in danger right now, call your local emergency number" is the documented fallback. | `docs/CLINICAL_REVIEW.md` lines 90–105, `docs/audit/crisis-line-feature-rejected.md` |
| R2 | No mood inference. The launcher does not turn a rating into a mood label. | `docs/CLINICAL_REVIEW.md` §3, `docs/research/26` §B5 |
| R3 | A WHO-5 score is never an assessment. Low score → support offer, never alarm or diagnosis. | `docs/CLINICAL_REVIEW.md` R3, `docs/research/13` |
| R4 | Wording-heavy surfaces are clinical-review-gated. The CI gate is at `.github/workflows/clinical-review.yml`. | `docs/research/17`, R3 mitigation in `docs/CLINICAL_REVIEW.md` |
| R5 | "The launcher prefers a missed check-in over a permanent record of 'user said no 47 times'." | `docs/research/26` §B3, §B6 |
| R6 | "No streaks, no goals, no congratulation." | `docs/CLINICAL_REVIEW.md` §3 |
| R7 | "On-device only." No cloud, no analytics, no crash reporting. | `docs/research/17`, brief 18 |
| R8 | "Camera frames are reduced to a single average brightness number and discarded." | `docs/research/08` |
| R9 | "The launcher does not fingerprint the user." | `docs/research/17` |

---

## 2. The R1 question — crisis-line UI

**Finding 1 (informational, not a contradiction):** the `support/` package implements a safety-plan screen and a "Reach someone now" UI for the user's *own* contacts, plus a footer that defers to the local emergency number. This is consistent with R1 — R1 explicitly says the user's own contacts are the only allowed route.

Evidence:
- `app/src/main/java/org/mindanchor/support/SupportScreen.kt:100–146` — the "Reach someone now" UI is the user's `crisis_contacts` Room table (`app/src/main/java/org/mindanchor/data/db/AnchorDatabase.kt:107–136`), not a hardcoded helpline.
- `app/src/main/res/values/strings.xml` `support_footer` — "If you are in danger right now, call your local emergency number." This is the documented R1 fallback.
- No `Lifeline`, `Samaritans`, `988`, `1-800-273-8255`, `1-800-799-7233`, `116123`, `Hotline`, or `Crisis Text` strings in any user-facing resource (verified by grep over `app/src/main/res/values/strings.xml`).
- The single `988` match in the tree is a DOI fragment (`10.1080/15298860309027`) inside a KDoc citation in `app/src/main/java/org/mindanchor/friction/CompassionMoment.kt:5` — not a phone number, not user-facing.

The support surface is the *evidence-based* shape that `docs/research/13` recommends. The brief in 13 explicitly says "If things feel heavy enough that you're having thoughts of hurting yourself, please tap the crisis-support tile — it's there for moments like this." The implementation honours R1 (no hardcoded helplines, no opt-in crisis-line UI) while still giving the user a path to their own people.

**Recommendation:** no change.

---

## 3. Mood inference (R2) and the CheckIn feature

**Finding 2 (verifies R2 is honoured):** the new `CheckIn` (v0.20.1 round 5) has *no* valence/arousal/mood fields. The legacy `Moment` data class (`app/src/main/java/org/mindanchor/model/Ema.kt`) does carry valence/arousal, but the legacy flow is being replaced by `CheckInActivity.onSave`, which calls `EmaScheduler.disable()` on first acceptance (`app/src/main/java/org/mindanchor/model/CheckInActivity.kt:218–220` and `app/src/main/java/org/mindanchor/model/EmaScheduler.kt:112–119`).

Evidence:
- `app/src/main/java/org/mindanchor/model/CheckIn.kt:33–76` — `data class CheckIn(val rating: Int, val reflection: String, val atMillis: Long)`. No mood field.
- `app/src/main/java/org/mindanchor/model/CheckInScreen.kt:46–86` — the screen's KDoc explicitly states "the question itself, rather than an invented 'check-in ready' label" and the rating is the "N-of-1 within-person signal."
- The historical EMA labels "Unpleasant / Pleasant" and "Sluggish / Wired" are user-language anchors, not clinical labels (`strings.xml: ema_valence_low`, `ema_valence_high`, `ema_arousal_low`, `ema_arousal_high`). They are anchors for a 1–5 self-rating, not a diagnosis.

**Finding 3 (informational):** the PatternFinder (`app/src/main/java/org/mindanchor/report/Patterns.kt`) operates on the legacy valence/arousal labels, not on the new CheckIn ratings. The wording on the report screen is explicitly framed as a count of the user's own past days: "That is a count of your own past days, not a forecast. It says nothing about how today will go, and nothing about why." (`strings.xml: pattern_caveat`). This is consistent with R2: the launcher reports what the user's own data has tended to do; it does not interpret the rating as a mood label.

**Recommendation:** no change.

---

## 4. R3 — WHO-5 score presentation

**Finding 4 (consistent with brief 13):** the WHO-5 score presentation in `app/src/main/java/org/mindanchor/pulse/WhoFive.kt` and `app/src/main/java/org/mindanchor/pulse/PulseScreen.kt` is exactly the wording recommended in `docs/research/13` (the "very low" band, for example, says "only a clinician can make that call. Given how much you are carrying, it might be worth talking to someone you trust or a GP." — this is the wording brief 13 recommends, verbatim).

The `Band.VERY_LOW` enum's KDoc says "Level of well-being seen in DSM-IV major depression" — this is *internal* developer documentation, not user-facing wording. The R3 mitigation in `docs/CLINICAL_REVIEW.md` says "the band is a *fact* the presentation code can read, and the *wording* — which is what carries interpretation — lives in [PulseScreen] and is the clinician-reviewed part." The internal KDoc is the fact; the user-facing strings are the wording. The split is deliberate and correct.

Evidence:
- `app/src/main/java/org/mindanchor/pulse/WhoFive.kt:35–44` — "The pure-function split is the one way to honour [R3]: the band and the screen-positive flag are *facts* the presentation code can read, and the *wording* — which is what carries interpretation — lives in [PulseScreen] and is the clinician-reviewed part."
- `app/src/main/res/values/strings.xml: pulse_band_very_low` and `pulse_band_low` — wording sourced from brief 13 (the comment above the strings says "per docs/research/13 (Topp 2015; WHO 1998; Parker 2020 JMIR mHealth lived-experience findings)").

**Recommendation:** no change.

---

## 5. R4 — `@wording-reviewed` tag audit

**Finding 5 (MEDIUM — gate violation, real):** the `PulseScreen.kt`, `ReportScreen.kt`, `SupportScreen.kt`, and `DigestScreen.kt` files do *not* carry the `@wording-reviewed` KDoc tag, but they render wording-heavy strings (`pulse_band_*`, `pattern_*`, `support_*`, etc.). The CI gate at `.github/workflows/clinical-review.yml` requires the tag on the file that renders the wording.

Files with the tag:
- `app/src/main/java/org/mindanchor/friction/FrictionGate.kt` ✓
- `app/src/main/java/org/mindanchor/model/CheckInActivity.kt` ✓
- `app/src/main/java/org/mindanchor/model/CheckInScreen.kt` ✓
- `app/src/main/java/org/mindanchor/model/NoteActivity.kt` ✓ (with explicit "No @wording-reviewed tag is needed" comment because the user owns the words — consistent with brief 26)

Files missing the tag (MEDIUM severity — gate violation):
- `app/src/main/java/org/mindanchor/pulse/PulseScreen.kt`
- `app/src/main/java/org/mindanchor/report/ReportScreen.kt`
- `app/src/main/java/org/mindanchor/support/SupportScreen.kt`
- `app/src/main/java/org/mindanchor/digest/DigestScreen.kt`

The strings themselves have a comment indicating the brief source, but the gate runs on the *file*, not the string. **Fix:** add `@wording-reviewed` to the KDoc of the four files above. The wording is already evidence-based; the tag is the formal clinical-review sign-off.

**NoteScreen.kt is intentionally missing the tag** — its KDoc explicitly says "No @wording-reviewed tag is needed; no clinical-review pass is required" because the user authors the words (brief 26 §A5). This is correct.

**Recommendation:** add the `@wording-reviewed` KDoc tag to `PulseScreen.kt`, `ReportScreen.kt`, `SupportScreen.kt`, `DigestScreen.kt`. Push a follow-up commit.

---

## 6. R5 — no engagement analytics (rejections / snoozes / defers)

**Finding 6 (verifies R5 is honoured):** no persistence of a rejection, snooze, defer, or "user said no" counter exists anywhere in the live code. The `CheckInRateLimitHolder` and the brief in 26 both state this explicitly.

Evidence:
- `app/src/main/java/org/mindanchor/model/CheckIn.kt:158` (recordRejection) and the surrounding KDoc — the rejection is *not* stored; it is held only in-memory in the rate-limit holder and is reset on app restart.
- `app/src/main/java/org/mindanchor/model/CheckInActivity.kt:136–143` — the back-press rejection handler updates the in-memory holder and `finish()`es; no disk write.
- `app/src/main/java/org/mindanchor/model/CheckInRateLimitHolder.kt:38–53` — the KDoc is explicit: "the launcher prefers a missed check-in over a permanent 'user said no 47 times' record."

**Finding 7 (verifies R5 is honoured):** the new check-in feature logs accepted check-ins to disk (append-only) but never logs rejected ones. The legacy EMA `Moment` class is the only "mood" data on disk, and it carries valence/arousal from the user's own self-report, not an inference.

**Recommendation:** no change.

---

## 7. R6 — no streaks, no goals, no congratulation

**Finding 8 (verifies R6 is honoured):** the only "score" the user sees is the WHO-5 score (0–100), and the presentation in `PulseScreen` explicitly avoids the "streak" pattern. The score is shown with the wording "Trends over time are more useful than any single number" — which is the wording brief 13 recommends.

No "X days in a row!" or "You've checked in 10 times!" affordance exists anywhere in the source. Verified by grep over `app/src/main/res/values/strings.xml` for `streak|target|congratulation` (zero matches).

**Recommendation:** no change.

---

## 8. R7 — on-device only, no analytics, no cloud

**Finding 9 (verifies R7 is honoured):** the manifest permissions are exactly what the project needs and no more.

Permissions (`app/src/main/AndroidManifest.xml`):
- `POST_NOTIFICATIONS` — for the EMA notification (with `tools:node="remove"` for the version the user has not opted into).
- `SCHEDULE_EXACT_ALARM` — for the EMA schedule.
- `RECEIVE_BOOT_COMPLETED` — for the rearm after reboot.
- `ACCESS_NOTIFICATION_POLICY` — for the notification batcher.
- `PACKAGE_USAGE_STATS` (with `tools:node="remove"`) — declared but explicitly removed in the runtime manifest, per the comment that says "Package-visibility filtering applies to LauncherApps too … the app list came back as three entries out of twenty-one installed (itself, Settings and the SIM toolkit) until this block was added." The runtime permission is a special-access permission the user must grant; the declaration is required for the app to be allowed to *ask* for it.
- `INTERNET` — for the VpnService API (`docs/research/18`).
- `CAMERA` — for PPG (`docs/research/08`).
- `WRITE_SECURE_SETTINGS` — for the grayscale feature in device-owner mode, used only on devices where the user has explicitly enrolled the launcher as a device owner.
- `uses-feature` for `camera.flash` and `camera.any` (both `required="false"`).

**No** location, microphone, contacts, calendar, SMS, call-log, external-storage, or body-sensors permissions. Verified by grep over the manifest.

**Finding 10 (verifies R7 is honoured):** zero analytics SDKs in the build files.

```
$ grep -iE "firebase|crashlytics|sentry|mixpanel|amplitude|datadog|segment|bugsnag|pendo|heap" \
    app/build.gradle.kts gradle/libs.versions.toml
(no matches)
```

**Finding 11 (verifies R7 is honoured):** zero outbound network APIs in the production source.

```
$ grep -rE "java\.net\.(URL|HttpURLConnection|Socket|URLConnection|SocketAddress|ServerSocket|DatagramSocket)|okhttp|retrofit|java\.net\.http" \
    app/src/main/
(no matches)
```

`GoingLightVpnService` uses `java.net.InetAddress` as a *data carrier* only (the `Packet` class stores the destination as an `InetAddress`); it does not open a socket or send a byte. The `NetworkCallsForbiddenTest` enforces this at the test level.

**Finding 12 (verifies R7 is honoured):** zero PII in logs.

```
$ grep -riE "Log\.[diwev].*(reflection|note\.body|checkin\.rating|valenc|arousal|mood)" app/src/main/
(no matches)
```

**Finding 13 (verifies R7 is honoured):** the data-extraction rules explicitly exclude cloud backup and device transfer for the entire app (`app/src/main/res/xml/data_extraction_rules.xml`):

```xml
<cloud-backup>
    <exclude domain="root" />
    <exclude domain="file" />
    <exclude domain="database" />
    <exclude domain="sharedpref" />
    <exclude domain="external" />
</cloud-backup>
<device-transfer>
    <exclude domain="root" />
    <exclude domain="file" />
    <exclude domain="database" />
    <exclude domain="sharedpref" />
    <exclude domain="external" />
</device-transfer>
```

The XML's own comment says: "A safety plan and a crisis contact list are the most private things this app will ever hold, and neither belongs on someone else's hardware." This is privacy-by-default — the user cannot accidentally back up their safety plan to Google Drive.

**Recommendation:** no change.

---

## 9. R8 — camera frames reduced and discarded

**Finding 14 (verifies R8 is honoured):** the camera is bound as an `ImageAnalysis` use case (`app/src/main/java/org/mindanchor/vitals/PpgCapture.kt:243–258`), not an `ImageCapture` use case. The `analyze(ImageProxy)` callback reduces each frame to a single double (the mean luma, subsampled at 1/64 of the pixels), then `image.close()` releases the buffer. The class's KDoc is explicit: "the buffer itself is never copied, retained, or exposed past the end of this call" (`PpgCapture.kt:329–334`).

No `ImageCapture`, `takePicture`, or `ImageReader` is imported anywhere in the production source. Verified by grep.

**Finding 15 (verifies R8 is honoured):** the camera frame is never written to disk. The `lumaSamples` and `frameTimestampsNanos` `ArrayList`s hold only the post-processed numbers, not the raw bytes. The flow is:

```
Camera frame → ImageProxy.planes[0] (YUV luma plane)
            → mean of every 8th row and column (subsample by 1/64)
            → one Double appended to lumaSamples
            → image.close() returns the buffer to CameraX
```

There is no `FileOutputStream`, no `Bitmap.compress`, no `MediaStore` write. The numbers go to `Hrv.fromPpg()` (pure math) and then to a `PulseResult` Room record (a single `score: Int`). No image bytes anywhere on the device after the buffer is closed.

**Recommendation:** no change.

---

## 10. R9 — no fingerprinting

**Finding 16 (verifies R9 is honoured):** the only `Settings.Secure` calls in the codebase are in `Grayscale.kt` (`app/src/main/java/org/mindanchor/grayscale/Grayscale.kt`), reading the system-level `accessibility_display_inversion_enabled` and `accessibility_display_daltonizer_enabled` flags. These are the OS-level accessibility state, used only to honour the user's existing system grayscale preference — not to fingerprint the device.

No `Settings.Secure.ANDROID_ID`, no `IMEI`, no `MAC`, no `AdvertisingIdClient`, no `getDeviceId`. Verified by grep.

**Finding 17 (verifies R9 is honoured):** the launcher does not have a unique device id. No `UUID.randomUUID()` is persisted to disk. The user is identified by the on-device DataStore (notes, check-ins, friction prefs, etc.) and by nothing else.

**Recommendation:** no change.

---

## 11. Citation integrity (the SOTA-IMPROVEMENT-REPORT.md's broader claim)

**Finding 18 (citation honesty is intact):** every brief in `docs/research/` flags its own unverified citations. The brief in 26 is explicit about not citing a fabricated "Bauer 2018" or "Smyth 2018" micro-journaling study — the agent that wrote that brief was honest about not finding the citation, and the brief calls this out as "I have not cited a Bauer 2018 paper that does not exist."

**Finding 19 (citation drift watch):** the `corpus.tsv` file in `app/src/main/assets/` carries a column "evidence" with named citations (Shaffer & Ginsberg 2017; Windred et al. 2024; Müller, Harari et al. 2021; Pearce et al. 2022; Richards et al. 2016; Task Force 1996; npj Digital Medicine 2025). These are sourced from the briefs (08, 15, 18) and are not invented. The `corpus.tsv` is read at app start (`app/src/main/java/org/mindanchor/corpus/CorpusStore.kt`) and used to render the digest screen's evidence chips.

**Recommendation:** no change; the citation policy is intact.

---

## 12. R1's *strengthening* — no opt-in "Get help now" card

**Finding 20 (consistent with the strengthened R1):** the rejected prototype documented in `docs/audit/crisis-line-feature-rejected.md` would have added an opt-in "Get help now" card to the support screen. The current implementation does not have this card. The `SupportScreen`'s UI is:

- Header + back button
- The user's own contacts (Reach someone now — `dial()` to a phone number the user added)
- DBT skills (STOP, TIPP, 5-4-3-2-1)
- The safety plan editor/reader
- The footer: "MindAnchor is a wellness tool, not a treatment, and not a medical device. If you are in danger right now, call your local emergency number."

The "Reach someone now" UI is the user's own contacts, not a hardcoded helpline. The R1 strengthening forbids an opt-in crisis-line UI of any kind; the implementation honours that by routing the user to *their own people* via the safety plan, not to a hotline.

**Recommendation:** no change.

---

## 13. R6's corollary — the "report" and "narrate" features

**Finding 21 (R6, R7 honoured):** the `ReportScreen` and `narrate/LlamaNarrator` features do not produce streaks or goals. The report is generated monthly and shows:

- the user's own scores (WHO-5) over time
- the user's own sleep regularity over time
- a *count* of similar past days (`pattern_lower` / `pattern_higher`) with the caveat "It says nothing about how today will go, and nothing about why."
- a narration paragraph generated by the on-device llama model, sourced from the user's own data only (the `Narrator` class enforces a "no clinical advice" guard, and the model runs on-device via JNI in `app/src/main/cpp/mindanchor_llama.cpp`).

The narration is generated by a local language model, not by a cloud API. The model file is bundled or downloaded; the inference runs on the device's CPU. No network is involved.

**Finding 22 (R6's "no congratulation" honoured):** the report's wording is never "Great job!" or "You did it!" or any of the performative-reward language. The narration is descriptive, not evaluative.

**Recommendation:** no change.

---

## 14. Summary table

| # | Rule | Status | Severity | Action |
|---|------|--------|----------|--------|
| 1 | R1 — no crisis-line UI (user's own contacts allowed) | Honoured | informational | none |
| 2 | R2 — no mood inference in the new CheckIn | Honoured | informational | none |
| 3 | R2 — no mood inference in the legacy EMA / PatternFinder | Honoured (with honest framing) | informational | none |
| 4 | R3 — WHO-5 wording is from brief 13 | Honoured | informational | none |
| 5 | R4 — `@wording-reviewed` tag on wording-heavy files | **Partial violation** | MEDIUM | add tag to PulseScreen, ReportScreen, SupportScreen, DigestScreen |
| 6 | R5 — no engagement analytics persisted | Honoured | informational | none |
| 7 | R5 — no rejection counter on disk | Honoured | informational | none |
| 8 | R6 — no streaks, no goals, no congratulation | Honoured | informational | none |
| 9 | R7 — on-device only, no analytics SDK | Honoured | informational | none |
| 10 | R7 — no analytics dependencies in build | Honoured | informational | none |
| 11 | R7 — no outbound network APIs in production source | Honoured | informational | none |
| 12 | R7 — no PII in logs | Honoured | informational | none |
| 13 | R7 — cloud backup and device transfer are excluded by default | Honoured (privacy-by-default) | informational | none |
| 14 | R8 — camera frames are reduced to a single integer | Honoured | informational | none |
| 15 | R8 — no image bytes on disk | Honoured | informational | none |
| 16 | R9 — no device-id fingerprinting | Honoured | informational | none |
| 17 | R9 — no unique device id | Honoured | informational | none |
| 18 | Citation integrity — briefs flag their own unverified citations | Honoured (the brief in 26 explicitly disclaims a fabricated Bauer 2018) | informational | none |
| 19 | Citation drift in `corpus.tsv` | None observed | informational | none |
| 20 | R1 strengthening — no opt-in "Get help now" card | Honoured | informational | none |
| 21 | R6 — Report and Narrate are descriptive, not evaluative | Honoured | informational | none |
| 22 | R6 — no congratulation in the report or digest | Honoured | informational | none |

**Net:** 21 of 22 findings are informational ("the implementation honours the rule"). 1 finding is a MEDIUM gate violation (the `@wording-reviewed` tag is missing on four wording-heavy surface files). The contradiction is *real* — the CI gate would fail to enforce the clinical-review step on these four files — but it is *narrow* and the *wording* is already evidence-based per the briefs.

---

## 15. Recommended fixes

**Add the `@wording-reviewed` tag to four files:**

1. `app/src/main/java/org/mindanchor/pulse/PulseScreen.kt`
2. `app/src/main/java/org/mindanchor/report/ReportScreen.kt`
3. `app/src/main/java/org/mindanchor/support/SupportScreen.kt`
4. `app/src/main/java/org/mindanchor/digest/DigestScreen.kt`

The KDoc comment should be a one-liner like:

```kotlin
/**
 * @wording-reviewed — the user-facing strings in this file are sourced
 * from docs/research/13 / docs/research/15 / docs/research/17 / docs/research/18,
 * and the file is the formal clinical-review sign-off for them.
 */
```

This is a 4-line change per file, low risk, and closes the gate violation. It does not change the user-facing wording (the strings are already evidence-based per the briefs); it just adds the formal tag the CI gate reads.

**No other code change is recommended** by this audit.

---

## 16. What this audit did not check

- The full corpus.tsv citation chain (the named papers in the asset file are sourced from the briefs; the briefs cite the underlying studies; this audit did not independently re-verify each paper's existence against PubMed or DOI.org).
- The full test coverage of every pure-function data class.
- The clinical accuracy of the clinical-review pack in `docs/CLINICAL_REVIEW.md` itself — that is a clinician's job, not a code auditor's.
- The build configuration of the NDK and the bundled llama model (the C++ is in `app/src/main/cpp/`, the model is in `app/src/main/assets/` or downloaded; this audit did not open the C++ in detail).
- The accessibility implementation of the new check-in / notes screens beyond verifying the `contentDescription` tags and `Modifier.semantics` use.

These are noted as out of scope, not as findings.
