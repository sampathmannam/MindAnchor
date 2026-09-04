#!/usr/bin/env bash
#
# Proves that `assembleRelease` produces the same APK bytes twice in a
# row, given the same source and the same SOURCE_DATE_EPOCH. Run from
# the repository root (or anywhere — it cd's to its own parent).
#
# What this script actually proves depends on the environment it runs
# in:
#
#   - In CI, with MINDANCHOR_KEYSTORE / MINDANCHOR_KEYSTORE_PASSWORD /
#     MINDANCHOR_KEY_ALIAS / MINDANCHOR_KEY_PASSWORD set to the real
#     release secrets, this is a genuine signed-APK reproducibility
#     check.
#   - Run locally, without those secrets, `app/build.gradle.kts` leaves
#     `signingConfig` unset for the release build type and Gradle
#     produces `app-release-unsigned.apk` instead. That is still a real
#     two-clean-builds-same-hash proof — it just proves the *unsigned*
#     content (resources, DEX, native libs, manifest) is reproducible,
#     not that the full signing round-trip is. There is no way to test
#     the signed path without the real keystore, which this script does
#     not have access to.
#
# Documented boundary (see docs/RELEASING.md "Reproducibility"): Android
# release signing (v2/v3 APK Signature Scheme) embeds a signature block
# whose byte layout is deterministic for a fixed keystore + fixed input,
# so a signed APK built twice from identical input *should* be
# byte-identical too — this script's SHA-256 comparison is the same
# either way, on whichever APK `assembleRelease` actually produced. If a
# future signing scheme or plugin ever made the signed bytes vary
# between otherwise-identical builds (e.g. a timestamp embedded in the
# signature), the right fix is to compare the *unsigned* APK content as
# the primary reproducibility proof and verify the certificate
# separately (already a separate step in release.yml) — not to loosen
# this hash comparison to "close enough".
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

GRADLE="./gradlew"
if [ "${OS:-}" = "Windows_NT" ] && [ -f "./gradlew.bat" ]; then
  GRADLE="./gradlew.bat"
fi

# Reproducible builds need a fixed point in time instead of "now". The
# latest commit's timestamp is a real, repo-derived value rather than a
# magic constant that would drift out of sync with the source it
# describes.
SOURCE_DATE_EPOCH="$(git log -1 --format=%ct)"
export SOURCE_DATE_EPOCH
echo "verify-reproducible-release: SOURCE_DATE_EPOCH=$SOURCE_DATE_EPOCH"

WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

find_release_apk() {
  find app/build/outputs/apk/release -maxdepth 1 -name '*.apk' | head -n1
}

# A Gradle daemon keeps its JVM (and the classloaders lint opens jars
# through) alive between invocations. On Windows that JVM can still hold
# an open handle on a file under app/build/ from the *previous* build,
# which makes the next `clean` fail with "Unable to delete directory ...
# Device or resource busy" even though nothing is actually wrong with
# the build. Stopping the daemon before each clean releases those
# handles; it costs a few seconds of daemon warm-up on the next
# invocation and is a no-op-safe call everywhere, including CI.
echo "verify-reproducible-release: build 1/2"
"$GRADLE" --stop
"$GRADLE" clean assembleRelease --stacktrace
apk1="$(find_release_apk)"
if [ -z "$apk1" ]; then
  echo "verify-reproducible-release: no APK found under app/build/outputs/apk/release after build 1" >&2
  exit 1
fi
cp "$apk1" "$WORKDIR/build1.apk"

echo "verify-reproducible-release: build 2/2"
"$GRADLE" --stop
"$GRADLE" clean assembleRelease --stacktrace
apk2="$(find_release_apk)"
if [ -z "$apk2" ]; then
  echo "verify-reproducible-release: no APK found under app/build/outputs/apk/release after build 2" >&2
  exit 1
fi
cp "$apk2" "$WORKDIR/build2.apk"

hash1="$(sha256sum "$WORKDIR/build1.apk" | cut -d' ' -f1)"
hash2="$(sha256sum "$WORKDIR/build2.apk" | cut -d' ' -f1)"

echo "verify-reproducible-release: build 1 sha256=$hash1 ($(basename "$apk1"))"
echo "verify-reproducible-release: build 2 sha256=$hash2 ($(basename "$apk2"))"

if [ "$hash1" != "$hash2" ]; then
  echo "verify-reproducible-release: FAIL — two clean builds from the same source and SOURCE_DATE_EPOCH produced different APK bytes." >&2
  exit 1
fi

echo "verify-reproducible-release: PASS — both builds produced identical APK bytes."
