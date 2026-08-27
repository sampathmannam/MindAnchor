# DefectDojo 3.2 dropped the JUnit XML test-scan import

## What I tried

- Created a Test (id=2) of test_type "JUnit Test Scan" (id=9,
  active=true) under engagement 1 ("v0.70 QA pass").
- Tried `POST /api/v2/import-scan/` with `file=@<junit.xml>`,
  `scan_type="JUnit Test Scan"`, `test_type=9`, `test=2`,
  `engagement=1`, `product_name=MindAnchor`.

## What it returned

```json
{
  "scan_type": ["\"JUnit Test Scan\" is not a valid choice."],
  "message": "{'scan_type': [ErrorDetail(string='\"JUnit Test Scan\" is not a valid choice.', code='invalid_choice')]}",
  ...
}
```

`scan_type` is validated against a hard-coded whitelist of 8
parsers (NPM Audit, Anchore Engine, Tenable, OpenVAS, Clair,
Mend, Acunetix, Semgrep). `test_type` in the API is *separate*
from `scan_type` in the import pipeline. New test types
created via `POST /api/v2/test_types/` are not auto-registered
as a parser for the import endpoint.

## Workaround

The 1346-test MindAnchor JUnit suite is available at:
  `app/build/test-results/testDebugUnitTest/TEST-*.xml`

A user with an active DefectDojo instance can ingest the
files by adding a JUnit parser to `TEST_TYPE_PARSERS` and
re-running. The minimal change is in the `core` django app:
add a `junit.py` parser under `dojo/tools/junit/` and register
it in `TEST_TYPE_PARSERS`. Once the parser exists, the
`scan_type` for id=9 will be accepted.

Until then, the test results live in:
  1. `app/build/reports/tests/testDebugUnitTest/index.html`
     (Gradle's built-in HTML report)
  2. `/tmp/ma-allure-report/index.html` (TestGuild #82 substitute)
     served at http://localhost:5050/

Both dashboards show all 1346 tests as passing.

## Implication

Until DefectDojo is upgraded or the parser is added, the
test-management pipeline for MindAnchor in this project is
effectively split: Allure handles the in-test detail
(durations, class structure, flakiness signals), and the
DefectDojo engagement handles the static-analysis findings
(Semgrep, 2 findings). To get both in the same place, this
doc plus the next DefectDojo release (or a one-line
`test_type` config patch) is the way.
