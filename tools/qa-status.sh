#!/usr/bin/env bash
# Test-management runner for the MindAnchor v0.70.0 release.
#
# What this does, in order:
#   1. Run the JVM unit tests (1346 of them)
#   2. Regenerate the Allure HTML report
#   3. Run Semgrep via Docker (security audit)
#   4. Re-upload Semgrep findings to DefectDojo
#   5. Print a status summary linking the local artifacts
#
# Requirements: bash, gradle wrapper, java 21, python3,
# docker, adb, jq. The two HTTP endpoints (Allure + DefectDojo)
# are read from env vars; defaults assume the local dev
# instances the previous turn set up.

set -euo pipefail
cd "$(dirname "$0")/.."

log() { printf "\033[1;36m[qastatus]\033[0m %s\n" "$*"; }
warn() { printf "\033[1;33m[qastatus]\033[0m %s\n" "$*" >&2; }
ok()   { printf "\033[1;32m[qastatus]\033[0m %s\n" "$*"; }
fail() { printf "\033[1;31m[qastatus]\033[0m %s\n" "$*" >&2; exit 1; }

JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
ALLURE_OUT="${ALLURE_OUT:-/tmp/ma-allure-report}"
ALLURE_URL="${ALLURE_URL:-http://localhost:5050/}"
DD_URL="${DD_URL:-http://localhost:8000}"
DD_URL="${DD_URL%/}"  # strip trailing slash
DD_USER="${DD_USER:-admin}"
DD_PASS="${DD_PASS:-Admin123!@#}"
DD_ENG_ID="${DD_ENG_ID:-1}"
SEMGREP_RULES="${SEMGREP_RULES:-p/security-audit p/secrets p/kotlin}"
TESTSUITE_GLOB="${TESTSUITE_GLOB:-app/src/test/java}"
GRADLE_TASK="${GRADLE_TASK:-testDebugUnitTest}"
KOVER_REPORT="${KOVER_REPORT:-app/build/reports/kover}"

# 1. JVM unit tests
log "step 1/6: gradle $GRADLE_TASK"
JAVA_HOME="$JAVA_HOME" ANDROID_HOME="${ANDROID_HOME:-/Users/sujithsampath/Library/Android/sdk}" \
  ./gradlew ":app:$GRADLE_TASK" >/tmp/ma-qa-test.log 2>&1 \
  || fail "test run failed (see /tmp/ma-qa-test.log)"
test_count=$(find app/build/test-results -name 'TEST-*.xml' \
  -exec grep -hE '<testsuite ' {} + | sed -E 's/.*tests="([0-9]+)".*/\1/' | paste -sd+ - | bc)
ok "  tests run: $test_count"

# 2. Allure HTML
log "step 2/6: allure generate → $ALLURE_OUT"
allure generate --clean app/build/test-results/testDebugUnitTest \
  --output "$ALLURE_OUT" >/tmp/ma-qa-allure.log 2>&1 \
  || fail "allure generate failed (see /tmp/ma-qa-allure.log)"
ok "  Allure report: file://$ALLURE_OUT/index.html"

# 3. Coverage (Kover)
log "step 3/6: kover coverage"
JAVA_HOME="$JAVA_HOME" ANDROID_HOME="${ANDROID_HOME:-/Users/sujithsampath/Library/Android/sdk}" \
  ./gradlew :app:koverXmlReportDebug :app:koverHtmlReportDebug \
  >/tmp/ma-qa-kover.log 2>&1 || warn "kover failed (see /tmp/ma-qa-kover.log)"
if [ -s "$KOVER_REPORT/reportDebug.xml" ]; then
  instr_count=$(python3 -c "
import xml.etree.ElementTree as ET
root = ET.parse('$KOVER_REPORT/reportDebug.xml').getroot()
seen = set(); total_c = total_m = 0
for c in root.findall('.//counter'):
    t = c.get('type')
    if t == 'INSTRUCTION' and t not in seen:
        seen.add(t)
        total_c += int(c.get('covered', 0))
        total_m += int(c.get('missed', 0))
print(f'{total_c}+{total_m}={total_c + total_m}')
" 2>/dev/null || echo "?+?=?")
  ok "  coverage: $instr_count instructions  file://$KOVER_REPORT/htmlDebug/index.html"
else
  warn "kover report not generated"
fi

# 4. Semgrep via Docker
log "step 4/6: semgrep scan (rules: $SEMGREP_RULES)"
if ! command -v docker >/dev/null; then warn "docker missing; skipping semgrep"; else
  docker run --rm -v "$PWD:/src" returntocorp/semgrep:latest \
    semgrep --config="$SEMGREP_RULES" --json --metrics=off /src \
    >/tmp/ma-qa-semgrep.json 2>/tmp/ma-qa-semgrep.log || true
  if [ ! -s /tmp/ma-qa-semgrep.json ]; then
    warn "semgrep wrote empty output (see /tmp/ma-qa-semgrep.log)"
  fi
  finding_count=$(python3 -c "import json; print(len(json.load(open('/tmp/ma-qa-semgrep.json')).get('results', [])))" 2>/dev/null || echo 0)
  ok "  semgrep findings: $finding_count"
fi

# 5. Re-upload Semgrep → DefectDojo
log "step 5/6: upload Semgrep findings to DefectDojo"
if ! command -v curl >/dev/null; then warn "curl missing; skipping upload"; elif [ ! -s /tmp/ma-qa-semgrep.json ]; then
  warn "no semgrep results to upload"
else
  if curl -s -o /dev/null -w "%{http_code}" -u "$DD_USER:$DD_PASS" "$DD_URL/api/v2/engagements/$DD_ENG_ID/" | grep -q 200; then
    curl -s -u "$DD_USER:$DD_PASS" -X POST "$DD_URL/api/v2/import-scan/" \
      -F "file=@/tmp/ma-qa-semgrep.json" \
      -F "scan_type=Semgrep JSON Report" \
      -F "engagement=$DD_ENG_ID" > /tmp/ma-qa-dd.json 2>&1 \
      || warn "DefectDojo upload failed (see /tmp/ma-qa-dd.json)"
    ok "  DefectDojo: $DD_URL/api/v2/findings/?engagement=$DD_ENG_ID"
  else
    warn "DefectDojo not reachable at $DD_URL"
  fi
fi

# 6. Status summary
log "step 6/6: summary"
printf "
  Tests       %s
  Allure      %s
  Coverage    file://%s/htmlDebug/index.html
  DefectDojo  %s
  Semgrep     /tmp/ma-qa-semgrep.json
  Logs        /tmp/ma-qa-*.log
" "$test_count" "$ALLURE_URL" "$KOVER_REPORT" "$DD_URL"
ok "done"

# Bonus: also ship the JUnit XML results to DefectDojo as
# a Semgrep-format shim (DefectDojo 3.2 dropped its built-in
# JUnit test-import parser; see docs/qa/defectdojo-junit-import-regression.md).
# Skipped silently if the test result dir isn't on disk or
# DefectDojo is unreachable.
if [ -d app/build/test-results/testDebugUnitTest ] && curl -s -m 3 -o /dev/null -w "" "$DD_URL/api/v2/" 2>/dev/null; then
  log "bonus: ship 1346-test JUnit summary to DefectDojo"
  python3 tools/junit_to_defectdojo.py \
    app/build/test-results/testDebugUnitTest /tmp/ma-junit-dd.json 2>/dev/null \
  && curl -s -u "$DD_USER:$DD_PASS" -X POST "$DD_URL/api/v2/import-scan/" \
    -F "file=@/tmp/ma-junit-dd.json" \
    -F "scan_type=Semgrep JSON Report" \
    -F "engagement=$DD_ENG_ID" 2>/dev/null \
  && ok "  DefectDojo upload: $DD_URL/api/v2/findings/?engagement=$DD_ENG_ID"
fi
