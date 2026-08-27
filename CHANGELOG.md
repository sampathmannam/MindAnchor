# Changelog

All notable changes to MindAnchor are documented here. Versions follow
[Semantic Versioning](https://semver.org/). The current version line is
`0.69.x`. Internal version code is in `app/build.gradle.kts` →
`android.defaultConfig.versionCode`.

## 0.69.0 — 2026-08-27 (unreleased at this commit)

### Cloud LLM-backed daily letter

The previous offline-Phi-4 letter pipeline is **removed**. Letters are now
written by a configurable cloud LLM (Google AI Studio, OpenRouter, or
Groq) chosen by the user. The cloud-LLM path is the only path; the
on-device path has been removed and cannot be restored without
re-introducing the offline model + the inference engine.

What's new:
- New `org.mindanchor.llm.LetterVoice` enum with **six voices**:
  Quiet, Warm, Direct, Playful, Insight, Reflective. Each voice is
  a separate system-prompt template; the user picks one in settings
  and previews the sample paragraph before committing.
- New **Insight** voice: names one psychology concept per letter
  in plain language and shows it in the user's day. This is the new
  default voice. Examples: reframe, affect labeling, rumination,
  behavioral activation, window of tolerance. One concept per
  letter, never two, never citations.
- Daily-letter alarm now actually arms. The previous
  `LetterScheduler.ensureScheduled()` was only called from
  `HomeActivity.onCreate`; if the user opened settings, flipped
  the toggle ON, and never restarted the launcher, no alarm was
  ever scheduled. `setLettersEnabled` and `setLettersTime` now
  call `ensureScheduled` themselves. Regression test:
  `LetterAlarmSchedulingFindingTest`.
- Daily-letter notification is delivered with a preview of the
  first two lines and the voice name. Tapping opens the letter
  in the reader.
- Letter-context preserves the notes-and-check-ins shape the
  reader already knows; `voice: String?` is a new column on
  the `Letter` row for the in-reader metadata footer.

What's removed:
- `LlamaNarrator` (the offline Phi-4 / llama.cpp path) and the
  native library it imported.
- `ModelStore` / `ModelSlot` / the "is the model on file" UI
  surface.
- `Phi4ModelDownloadSection` (the "Download Phi-4 mini" button).
- `CorpusStore` / the corpus-import section ("Research on file").
- `GoogleDriveBackupSection` (the deprecated `GoogleSignIn` /
  `play-services-auth` path; cert pinning is back on the working
  LLM HTTPS path).
- `WRITE_SECURE_SETTINGS` from the manifest; the in-app
  `GreyscaleRoot` ColorMatrix does not need it.

API fixes:
- `LlmPrefs.apiKey` is now a process-wide reactive `StateFlow`
  (`companion object` `MutableStateFlow`), not a per-instance
  one-shot `flow { emit }`. Every reader in the process
  (Settings viewmodel, LetterScheduler, "Generate now") sees
  the same value, and the field the user pastes into shows
  the typed value live. A regression test
  (`LlmSettingsTest` round-trip) pins this.
- Cert pinning is back on with the SPKI SHA-256 of each
  provider's issuer intermediate + root, read off each
  host's live TLS handshake on **2026-08-27**. The temporary
  disable has been removed.
- `OpenAiCompatibleClient` now sets `reasoning_effort: "low"`
  by default so the Groq `gpt-oss-20b` / `gpt-oss-120b`
  family returns real content in `content` instead of burning
  the whole budget on `reasoning`. Other providers ignore the
  field.
- The `LetterError` taxonomy now distinguishes `TlsFailed`
  from `NetworkUnreachable`; the old "Network unreachable"
  on a cert-pinning failure was misleading and is gone.

New privacy off-switch: **"Forget this key and start over"** in
the LLM section of settings. Wipes the API key from the
encrypted blob, clears the in-memory cache, and resets the
cached test-result row.

Tests added this round:
- `LlmSettingsTest` — provider / model / apiKey round-trip.
- `OpenAiCompatibleClientTest` — MockWebServer end-to-end;
  401 → `InvalidApiKey`, 404 → `ModelNotFound`, 2xx →
  parsed `LlmResponse`.
- `LetterPromptShapeTest` — per-voice safety invariants (8
  baseline BPD-safe markers, the no-`!` rule, the 250–700
  token ceiling, per-voice contracts for Warm/Direct/
  Playful/Reflective/Insight).
- `CertificatePinningTest` — routing table; each provider
  pins at least 2 keys (issuer + root).
- `LetterAlarmSchedulingFindingTest` — finding-test that
  asserts the `ensureScheduled` call is wired from both
  `setLettersEnabled` and `setLettersTime`.

### Earlier

- (v0.25.x) Quiet / Pauses / Measuring / This phone groups
  shipped. Letter inbox, nightly report, sunset mode all live.
- (v0.25.x) Model-removal: offline Phi-4 / llama.cpp
  dependency was a ~75 MB APK cost with no engine behind it
  in the current build; both removed.
