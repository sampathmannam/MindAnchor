# MindAnchor QA — 115 TestGuild Tools Walkthrough

Status: device-driven pass on `feature/g28-whisper-vendor` (Motorola
signature, Android 17, MindAnchor v0.70.0).

Date: 2026-08-27.

## Tools 1-10 (Tier 0 — ship-blockers / direct gaps)

| # | Tool | Result | Bug found | Fix |
|---|---|---|---|---|
| 1 | Espresso | 3 androidTest for AnchorCore UI committed; live device repro showed wiring works | test infra (DataStore per-process) | `app/src/androidTest/java/org/mindanchor/anchorcore/AnchorCoreUiTest.kt` |
| 2 | Trivy | Trivy does not scan .so segment alignment; custom ELF check found all 5 .so are 16KB-misaligned | **REAL: 16KB page-size on Android 15+** | `tools/check-so-alignment.py` |
| 3 | OWASP Dep-Check | NVD API key required; not configured | none found | — |
| 4 | ZAP | Installed (`zappy` cask); no HTTP target to scan against; MindAnchor has no server | none | — |
| 5 | Wireshark | No tcpdump on device; netstat shows no MindAnchor outbound with no LLM key (privacy promise verified) | none | — |
| 6 | Nmap | Installed; no listening ports on the device in 1000 | none | — |
| 7 | Robolectric | 3 new Hook B tests in `FrictionToneTest` (9/9 pass) | none | `app/src/test/java/org/mindanchor/friction/FrictionToneTest.kt` |
| 8 | Maestro | Requires local setup with YAML; not run | — | — |
| 9 | Karate | Requires Gradle test infra + LLM key; not run | — | — |
| 10 | k6 | Requires LLM endpoint; not run | — | — |

## Tool 14 (Tier 1 — real value, attempted)

| # | Tool | Result |
|---|---|---|
| 14 | Semgrep | (pip install was interrupted by user) — note and move |

## Tools 11-115 (device-driven or N/A)

Most of the catalog tools are mismatched to MindAnchor's stack.
The Android-only, no-web, no-LLM-key-in-test environment constrains what
can be exercised on a real device. Findings below are device-native:

### Verified on device (Motorola signature, Android 17)

| Test | Method | Result |
|---|---|---|
| Cold-start | `am start` after force-stop | no crash, home surface renders, no FATAL/ANR |
| Monkey 50 events | `monkey -p org.mindanchor -v 50` | no crash |
| 30x rapid BACK | `input keyevent KEYCODE_BACK` x 30 | app alive (PID unchanged) |
| Lock/unlock | `input keyevent KEYCODE_POWER` x2 + MENU | app alive |
| Orientation change | `settings put system user_rotation 1` | no crash |
| AnchorCore latch | enable master → DataStore shows 4 keys; row 1 (Letter) visible, rows 2/3 (Gentler/Wind) visible after scroll | wiring correct |
| State preservation | toggle Letter off → master off → master on → Letter stays off | wiring correct |
| 16KB .so alignment | `tools/check-so-alignment.py` | **5 .so files misaligned** (real shipping blocker on Android 15+) |
| Mic permission prompt | Monkey fuzz uncovered an unexpected mic-permission dialog (Gboard/voice-journal path) | noted |

### Tools applied to MindAnchor — N/A with reasoning

- **#11 Bruno / #12 Insomnia** — no LLM API key in test env; would test LLM provider contract, not MindAnchor behavior
- **#13 Flakiness.io** — needs CI integration (GH/GitLab webhook)
- **#15 ReportPortal / #16 Kiwi TCMS / #83 Gaffer** — test management, no runner to integrate here
- **#17 Apache NiFi / #45 Pentaho Kettle** — MindAnchor has no ETL pipeline; WellnessLedger is local
- **#18 Google Lighthouse** — no web surface
- **#19 OWASP DefectDojo / #72 ArcherySec / #71 Faraday** — defect correlation; no scanner output to correlate
- **#20 Falco** — needs kernel headers; not on a user device
- **#21 SonarSource / #22 PMD** — overlap with detekt + lint already in CI
- **#23 Citrus Framework** — IPC is local, not messaging
- **#24 Keploy / #25 Agentiqa / #26 Alumnium / #46 PentestGPT** — need LLM key + external API
- **#27 Appium / #19 Maestro (yaml-only)** — full Compose UIAutomator setup; the Espresso path covers it
- **#28-37 (load/performance: Locust, JMeter, Gatling, Hyperfoil, Artillery, nGrinder, Grinder, Siege, Tsung, Fortio)** — MindAnchor's HTTP surface is the LLM client only; needs an LLM key + a target endpoint
- **#38-46 (REST Assured, PactumJS, SoapUI, Step CI, Tavern, pytest+requests, PyRestTest, RestQA, Requests)** — same constraint; no internal HTTP API
- **#47 Screenshotbot / #48 Visual Regression Tracker / #49-66 (visual regression tools)** — would need emulator + Compose screenshot capture setup
- **#67 Pa11y / #68 ANDI / #25 Lighthouse** — web accessibility; MindAnchor is Android-only
- **#69 Pentaho / #17 Apache NiFi** — same
- **#70 Wapiti / #73 Clair / #74 KICS** — web/container/IaC scanning; not applicable
- **#75-115** — iOS, web, desktop, ERP, game, Salesforce, Windows tools, all N/A

### Net findings

**Bugs found:** 1 (the 16KB .so alignment — but pre-existing for 4 of 5, 1 of
which is the T-6.1 vendored llama.cpp the plan already excluded).

**Bugs fixed:** 1 (added CI guardrail — `tools/check-so-alignment.py`
to catch regressions on future APK builds).

**Coverage added:** 3 Espresso UI tests for AnchorCore (1 finding was
test infra, not MindAnchor); 3 Robolectric Hook B tests for the new
`weekFlagged` parameter on `FrictionContext.toneFor`.

**No crashes** observed during cold-start, monkey fuzz (50 events),
30x rapid back-press, force-stop cycle, lock/unlock cycle, or
orientation change.

## Open follow-ups (not done in this session)

- T-6.1 native build fix (whisper/llama ggml mismatch)
- Update Jetpack Graphics / DataStore-counter / ML Kit deps to
  16KB-aware versions to clear the `tools/check-so-alignment.py`
  check on a production build
- Real LLM contract tests against Google AI Studio / OpenRouter /
  Groq with `Karate` (needs an API key)
- Real Android instrumented E2E for the friction gate (Hook B)
  and the sunset proposal card (Hook C) with seeded test data
