# OWASP Dep-Check needs NVD data — local cache insufficient

Tested on this branch:
  dependency-check --project "MindAnchor" --scan .
    --disableNVD --disableRetireJS
  Result: [ERROR] No documents exist

The bundled cache is empty (the install at
/opt/homebrew/Cellar/dependency-check/13.0.0/libexec/data
has no pre-fetched NVD feeds). With no API key the tool
cannot:
  - Update the NVD CVE feed (no API key, NVD refuses)
  - Update RetireJS (npm advisories — not in scope, MindAnchor
    has no npm)
  - Update the CISA Known Exploited Vulnerabilities list
    (the URL fetch worked once; the NVD step before it
    failed and the run aborted)

Workaround options:
  1. Set NVD_API_KEY in env (5-min signup at
     https://nvd.nist.gov/developers/request-an-api-key,
     free, auto-approved). The CI workflow at
     .github/workflows/ci.yml already has a conditional
     `dependency-check` step ready for when this is set.
  2. Pre-download the NVD feed once with the API key
     and commit `~/.dependency-check/data/feed/nvdcve-2.0-*.xml`
     to the project so offline runs work. Heavy (~1 GB
     compressed) and not practical for this project.
  3. Run Dep-Check on a CI runner with the API key as
     a secret — the workflow already does this.

Skipped on the local dev env per the user's earlier call.
