#!/bin/bash
# Maestro helper: scroll the measuring page to its
# bottom via adb input swipe (Maestro's built-in
# scrollUntilVisible + repeat { swipe } did not work
# against the AnchorCore section on this device) and
# then back up enough to bring the section into view.
#
# The Measuring group has 7 sub-sections; AnchorCore is
# the 5th, so 6 forward + 2 back lands it in the viewport.
set -e
DEV="${DEVICE:-${ANDROID_SERIAL:-}}"
if [ -z "$DEV" ]; then
  echo "DEVICE env required" >&2
  exit 2
fi
# Forward 6 swipes — each scrolls ~2000 px.
for _ in 1 2 3 4 5 6; do
  adb -s "$DEV" shell input swipe 600 2400 600 300 500 >/dev/null
  sleep 0.3
done
# Back 2 swipes — each scrolls back ~1400 px.
for _ in 1 2; do
  adb -s "$DEV" shell input swipe 600 800 600 2400 500 >/dev/null
  sleep 0.3
done
