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

# 1. JVM unit tests
log "step 1/5: gradle $GRADLE_TASK"
JAVA_HOME="$JAVA_HOME" ANDROID_HOME="${ANDROID_HOME:-/Users/sujithsampath/Library/Android/sdk}" \
  ./gradlew ":app:$GRADLE_TASK" >/tmp/ma-qa-test.log 2>&1 \
  || fail "test run failed (see /tmp/ma-qa-test.log)"
test_count=$(find app/build/test-results -name 'TEST-*.xml' \
  -exec grep -hE '<testsuite ' {} + | sed -E 's/.*tests="([0-9]+)".*/\1/' | paste -sd+ - | bc)
ok "  tests run: $test_count"

# 2. Allure HTML
log "step 2/5: allure generate → $ALLURE_OUT"
allure generate --clean app/build/test-results/testDebugUnitTest \
  --output "$ALLURE_OUT" >/tmp/ma-qa-allure.log 2>&1 \
  || fail "allure generate failed (see /tmp/ma-qa-allure.log)"
ok "  Allure report: file://$ALLURE_OUT/index.html"

# 3. Semgrep via Docker
log "step 3/5: semgrep scan (rules: $SEMGREP_RULES)"
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

# 4. Re-upload Semgrep → DefectDojo
log "step 4/5: upload Semgrep findings to DefectDojo"
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

# 5. Status summary
log "step 5/5: summary"
printf "
  Tests       %s
  Allure      %s
  DefectDojo  %s
  Semgrep     /tmp/ma-qa-semgrep.json
  Logs        /tmp/ma-qa-*.log
" "$test_count" "$ALLURE_URL" "$DD_URL"
ok "done"
