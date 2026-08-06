#!/usr/bin/env bash
#
# Runs the instrumented tests on a booted emulator, then photographs the
# app and prints the pictures into the job log.
#
# Why this is a file rather than an inline script:
#
# reactivecircus/android-emulator-runner executes the `script:` input one
# LINE AT A TIME, each in its own `sh -c`. Variables do not survive between
# lines. An inline version of this looked completely reasonable and was
# quietly broken end to end — `TEST_STATUS=$?` was assigned in a shell that
# then exited, `SHOTS=...` likewise, and the `adb pull "$SHOTS"` line ran
# with an empty variable, failed, and failed the whole job seconds after
# BUILD SUCCESSFUL. Every screenshot run had passed its tests and reported
# failure, and never emitted a single image.
#
# Being one file invoked as one line, this executes in a single shell where
# ordinary shell semantics apply.
set -uo pipefail

PACKAGE=org.mindanchor

./gradlew connectedDebugAndroidTest --stacktrace
TEST_STATUS=$?

# From here on nothing may change the verdict. The tests decide whether
# this job passes; the camera never does.
set +e

mkdir -p shots

# Do not guess where the PNGs landed — external storage is not mounted on
# every AVD, so the test logs each absolute path it actually wrote.
adb logcat -d -s MINDANCHOR_SHOT:I > shot-log.txt 2>/dev/null
grep -oE '/[^ ]+\.png' shot-log.txt | sort -u > paths.txt
echo "screenshot paths reported by the tests: $(wc -l < paths.txt)"

while read -r p; do
  [ -n "$p" ] || continue
  base=$(basename "$p")
  adb pull "$p" "shots/$base" >/dev/null 2>&1 \
    || adb exec-out run-as "$PACKAGE" cat "$p" > "shots/$base" 2>/dev/null
done < paths.txt

for f in shots/*.png; do
  [ -e "$f" ] || continue
  name=$(basename "$f" .png)
  echo "SHOT_BEGIN ${name} bytes=$(wc -c < "$f")"
  base64 -w0 "$f" | fold -w 3000 | sed "s|^|SHOT_DATA ${name} |"
  echo "SHOT_END ${name}"
done

exit $TEST_STATUS
