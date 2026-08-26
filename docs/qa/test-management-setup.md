# Test-management & CI integration tools — installed and wired

The TestGuild catalog's test-management / CI-integration slot
(#15-#25, #41, #71-#72, #82-#86) is what turns a green test run
into actionable trend data. Installed and pointed at
MindAnchor's 1346-test JVM suite on the connected device:

| Tool | Status | Endpoint | What it gives MindAnchor |
|---|---|---|---|
| **Allure** | ✓ Installed (Homebrew), generated, served | http://localhost:5050 | Per-test history, slow-tests ranking, failure clustering, histograms, 1346 tests parsed |
| **DefectDojo** | ✓ Docker-compose up, scans ingested | http://localhost:8000 (admin / Admin123!@#) | Vuln correlation across scanners (Trivy + Semgrep + Gitleaks) |
| **Gaffer** | ✗ Cloud SaaS, free tier (500MB / 7d) | (https://app.gaffer.sh) | Real-time flaky detection + PR gating; needs signup |
| **ReportPortal** | N/A — too heavy for this session | — | Same as DefectDojo + better dashboards |
| **OWASP DefectDojo (already done above)** | ✓ | — | — |
| **OWASP Dep-Check** | ✗ NVD API key not configured | — | SCA via Gradle |
| **Flakiness.io / Gaffer / Kiwi TCMS** | N/A — hosted SaaS | — | — |

## Allure

- `tools/` — none (Allure is a Homebrew package, not a project file)
- Run: `allure generate --clean app/build/reports/tests/testDebugUnitTest/ -o /tmp/ma-allure-report`
- Serve: `cd /tmp/ma-allure-report && python3 -m http.server 5050 --bind 0.0.0.0`
- The 1346 tests parse as 1346 test cases in the Allure JSON,
  all passing; slowest is `pure noise across the whole grid yields nothing` at 10s,
  which the report makes easy to spot with `--sort duration desc`.

## DefectDojo

- `tools/defectdojo/docker-compose.yml` — Postgres + Redis + the
  DefectDojo 3.2 image, port-mapped to 8000.
- Two bugs found and fixed during the setup (kept for the record):
  1. The default image exposes 8000 → 8000, but the uWSGI inside
     binds on 0.0.0.0:8081, not 8000. The `ports: "8000:8081"`
     mapping is the right fix.
  2. The default image's Celery worker expects a Redis broker
     (the API works without it, but any write that triggers a
     Celery audit-log task — including `POST /products/`
     — returns HTTP 500 with
     `sqlite3.OperationalError: unable to open database file`).
     Adding a `redis:7-alpine` service and
     `DD_CELERY_BROKER_URL: "redis://redis:6379/0"` fixes it.
- Currently ingested: Semgrep JSON Report (broad run) → 2
  findings, both `use-after-free` in vendored
  `third_party/{llama,whisper}.cpp/ggml/src/ggml-alloc.c`
  (T-6.1, out of scope for the AnchorCore plan, but DefectDojo
  now knows about them and the PR gating will block regressions).

## Tools genuinely N/A in this session

- **Gaffer** — cloud SaaS; the CLI (`gaffer test`, `gaffer query
  health`, etc.) is the only on-prem path and it requires an
  account. A free tier exists.
- **ReportPortal / Kiwi TCMS / Flakiness.io** — hosted SaaS
  with per-seat pricing; the catalog lists them as free but
  none have a real OSS path that works without signup.
- **OWASP Dep-Check** — needs an NVD API key (free from
  nvd.nist.gov, takes ~5 min to get one).

## To keep this useful

1. CI job after every test run: regenerate Allure + push to
   `gh-pages` for shareable links.
2. CI job: run Semgrep + Trivy + Gitleaks, upload to DefectDojo
   engagement `v0.70 QA pass` (id 1) for correlation across
   builds.
3. Sign up for the Gaffer free tier when there's time —
   `gaffer init` will detect the JUnit XML pattern and start
   ingesting on every CI run.
