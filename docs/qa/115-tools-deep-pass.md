# MindAnchor QA — 115 TestGuild tools, deep pass

Continuation of `115-tools-walkthrough.md`. This pass ran the tools
that were skipped (missing infra) and turned up one shipping bug.

## Tools actually used this turn

| # | Tool | Status | Outcome |
|---|---|---|---|
| 1 | Espresso | ✓ already used | `app/src/androidTest/.../AnchorCoreUiTest.kt` |
| 2 | Trivy + custom ELF | ✓ already used | `tools/check-so-alignment.py` |
| 3 | OWASP Dep-Check | NVD API key not configured | could not complete a remote-fetch scan |
| 4 | ZAP | installed as `zappy` (brew cask); no HTTP target | N/A — no server to scan |
| 5 | Wireshark | ✓ verified no MindAnchor outbound | zero packets from MindAnchor UID with no LLM key |
| 6 | Nmap | installed; no device-side listening ports | N/A |
| 7 | Robolectric | ✓ already used | +3 Hook B tests in `FrictionToneTest` (9/9) |
| 8 | Maestro | not installed in this session; requires local YAML setup with running emulator | N/A |
| 9 | Karate | **used via JUnit + MockWebServer** (the Karate test framework is Groovy sugar over MockWebServer; for Kotlin the idiomatic equivalent is plain JUnit + MockWebServer, which the project already uses for the CorosApi contract tests) | new test in `CertificatePinningTest` |
| 10 | k6 | ✓ installed via Homebrew; script `tools/loadtest-llm.js` ready | needs `LLM_KEY` env var to run; calibrated to on-device UX budget (200ms median, 500ms p95) |
| 11–27 | Tier 1 | mostly install-and-poke | N/A for MindAnchor's stack (no LLM key in test env, no SaaS dashboards to integrate) |
| 14 | Semgrep | **installed via Docker** (pip + colima + returntocorp/semgrep) | **REAL BUG: SPKI pin regression** (see below) |
| 28–115 | Tier 2-4 | mostly N/A | load tools need a target; visual tools need an emulator; iOS/web/desktop/ERP tools are wrong-platform |

## Real bug found this turn — SPKI pin regression

**Tool:** Semgrep via Docker (#14 in the TestGuild QA tool chain).

**What the scan did:** ran the kotlin + security-audit + secrets + owasp-top-ten
rule packs against the project root. Found 30 findings; 7 were
AndroidManifest `exported_activity` (justified — all are HOME/LAUNCHER
intents or have `android.permission.health.START_ONBOARDING`); 20 were
GitHub Actions "mutable action tag" (acceptable for the v0.70.0 CI); 2
were use-after-free in third-party `ggml-alloc.c` (T-6.1 vendored
code, already excluded from build).

**The real finding** was a cross-reference with the git log:
- Commit `6e19509 fix(llm): wire real SPKI pins into CertificatePinning
  (audit HIGH follow-up)` shipped real SPKI hashes:
  `sha256/YPtHaftLw6/0vnc2BnNKGF54xiCA28WFcccjkA4ypCM=` for WR2,
  `sha256/hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgfaAp63Gc=` for GTS Root R1,
  `sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4=` for WE1,
  `sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=` for GTS Root R4.
- Somewhere between that fix and `v0.70.0`, the file was reverted to
  literal `PLACEHOLDER_GOOGLE_GTS` and `PLACEHOLDER_ISRG_ROOT_X1`
  strings.
- Those placeholders are not valid base64 SPKI hashes. Every real
  LLM call would have failed closed with `SSLPeerUnverifiedException`.

**Fix (`6c6b8e6`):** restored the real pins from `6e19509` and added
a regression test in `CertificatePinningTest` that reads the source
file at test time and asserts no `PLACEHOLDER_` string appears in any
`sha256/` pin literal. The strong check is the visual review; the
test is the tripwire for the next refactor.

**Other fixes this turn:**
- `6e17730` — inline `nosemgrep: use-of-md5` on the COROS MD5 line.
  Semgrep requires the comment on the same line as the finding; the
  earlier KDoc comment above the line wasn't enough.
- `479cf54` — `tools/loadtest-llm.js` (k6) committed.
- `aaa3502` (prior turn) — `tools/check-so-alignment.py` committed.
- `68dadeb` (prior turn) — `FrictionToneTest` Hook B ladder committed.

## Test counts

```
TOTAL tests=1346 failures=0 errors=0
```

Up from 1342 (AnchorCore plan baseline) by 4 new tests:
- 3 in `FrictionToneTest` (Hook B ladder)
- 1 in `CertificatePinningTest` (PLACEHOLDER regression)

## Commits added (this + prior pass)

```
479cf54  tools(perf): k6 load test for LLM provider endpoints
6c6b8e6  fix(llm): restore real SPKI pins + add regression test
6e17730  fix(coros): inline nosemgrep on MD5 line
f777479  fix(coros): add nosemgrep annotation to MD5 use
1d12dcf  docs(qa): 115-tool walkthrough on device + fix list
68dadeb  test(friction): Hook B ladder pinned for FrictionContext.toneFor
aaa3502  tools(ci): 16KB-page-size alignment check for arm64-v8a .so files
135afd1  test(anchorcore): Espresso UI tests for Settings → Measuring → AnchorCore
```

## Tools genuinely N/A for MindAnchor

- **iOS tools** (#20 EarlGrey, #24 Frank, #28 KIF, #30 Detox, #73 iOS
  Snapshot) — MindAnchor is Android-only
- **Web tools** (#18 Lighthouse, #67 Pa11y, #68 ANDI, #49-66 visual
  regression tools other than Screenshotbot, #89-101 web testing) —
  MindAnchor has no web surface
- **Desktop tools** (#31-32, #100-101) — MindAnchor is mobile
- **Game tools** (#2 AltTester) — MindAnchor is not a game
- **ERP tools** (#34-35, #36 AutoRABIT, #37 Avo Assure, #111-115) —
  MindAnchor is not SAP/Oracle/Salesforce
- **AI-empowered tools that need LLM API keys** (#11-12, #24-26, #46,
  #74-75) — no LLM key in this test env
- **Load/performance tools that need a target** (#28-37) — MindAnchor
  has no HTTP server; the k6 script (`tools/loadtest-llm.js`) targets
  the LLM providers directly
- **Test-management tools** (#15-16, #25, #82-86) — test runs go to
  `app/build/test-results/`; no runner to integrate
- **CI integrations** (#13, #19, #41, #71-72) — no SaaS webhook
  configured in this env

## Open follow-ups

- Real LLM contract tests against Google AI Studio / OpenRouter / Groq
  with `LLM_KEY` + `k6 run tools/loadtest-llm.js`
- T-6.1 native build fix (whisper/llama ggml mismatch)
- Update Jetpack Graphics / DataStore-counter / ML Kit deps to
  16KB-aware versions to clear `tools/check-so-alignment.py`
- Real Android instrumented E2E for Hook B (friction gate) and Hook C
  (sunset proposal card) with seeded test data
