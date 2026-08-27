#!/bin/bash
# Helper: dump the current view and print whether TARGET is visible.
adb shell uiautomator dump /sdcard/window_dump.xml > /dev/null
adb pull /sdcard/window_dump.xml /tmp/ma-tmp.xml > /dev/null
if grep -q "AnchorCore" /tmp/ma-tmp.xml; then
  echo "FOUND AnchorCore in the view"
  exit 0
else
  echo "NOT FOUND in current view, need more swipes"
  exit 1
fi
