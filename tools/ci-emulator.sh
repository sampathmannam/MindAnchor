#!/usr/bin/env bash
#
# Runs the instrumented tests on a booted emulator, then prints the app's
# screenshots into the job log.
#
# Two things about this were learned the hard way:
#
# 1. reactivecircus/android-emulator-runner executes the `script:` input one
#    LINE AT A TIME, each in its own `sh -c`. Variables do not survive
#    between lines. An inline version of this looked fine and was broken end
#    to end — the exit-status capture and the "never fail the job" guard were
#    both no-ops, and a job failed seconds after BUILD SUCCESSFUL because an
#    adb command ran with an empty variable. Hence: one file, one line, one
#    shell.
#
# 2. Pulling the PNGs off the device does not work. Android 11+ blocks the
#    shell user from /sdcard/Android/data/<pkg>, so `adb pull` fails, and
#    `run-as` answers "unknown package" on these AOSP images. An earlier
#    version redirected that error text straight into the .png files and
#    produced five 40-byte "screenshots" that were really an error message.
#    So the images come out through logcat instead, which is the one channel
#    that was working all along.
set -uo pipefail

./gradlew connectedDebugAndroidTest --stacktrace
TEST_STATUS=$?

# Nothing below may change the verdict. The tests decide whether this job
# passes; the camera never does.
set +e

adb logcat -d -s MINDANCHOR_SHOT:I > shot-log.txt 2>/dev/null
echo "screenshot log lines: $(wc -l < shot-log.txt)"

# Re-emit the markers with the logcat preamble stripped, so the decoder on
# the other end sees clean lines.
sed -n 's/.*MINDANCHOR_SHOT *: *//p' shot-log.txt \
  | sed -e 's/^BEGIN /SHOT_BEGIN /' \
        -e 's/^DATA /SHOT_DATA /' \
        -e 's/^END /SHOT_END /'

exit $TEST_STATUS
