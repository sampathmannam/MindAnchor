# Maestro E2E setup

Two flow files committed under `flows/`:

- `smoke_app_launch.yaml` — boots the app, dismisses
  16 KB page-size dialog + Android task-pinning overlay,
  skips onboarding (2x `continue` + `begin`), asserts
  the time-stable bottom-bar labels (`search`, `settings`)
  are visible. **Status: passes** on the connected Motorola
  signature.
- `anchorcore_section.yaml` — walks
  Settings → Measuring → AnchorCore section and asserts:
  - master subtitle visible
  - 3 hook rows appear after master ON
  - 3 hook rows disappear after master OFF
  - override-revoke row stays hidden (no Hook C accept)

  **Status: does not pass end-to-end on this device.**
  The 40+ swipes to reach the AnchorCore section at the
  bottom of the Measuring group do not move the page from
  the top. The same coords on `adb shell input swipe`
  (manual, 200–500 ms) DO move the page, so this is a
  Maestro + Android 17 / UI Automator2 quirk, not a test
  selector problem. The scrolling section needs to be
  reworked when this is debugged (a likely fix: switch to
  `scrollUntilVisible: "AnchorCore"` or use the
  `input swipe` directly via a `runScript` step).

## Why two `continue` taps in onboarding

The MindAnchor onboarding is two pages: "What fits?" (the
first "continue") and "Your plan" (the second "continue" +
"begin"). Either may show depending on prior state; the
two-tap pattern works from a clean install (the path the
test starts in).

## Run

```bash
JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home \
  ~/.maestro/bin/maestro --udid ZD2232FCR5 \
    test tools/maestro/flows/smoke_app_launch.yaml

JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home \
  ~/.maestro/bin/maestro --udid ZD2232FCR5 \
    test tools/maestro/flows/anchorcore_section.yaml
```

## Open follow-ups

- Fix the scroll (a single line of Maestro, see above) so
  the AnchorCore flow can reach its assertions.
- CI hook: a `.github/workflows/maestro.yml` step that
  boots an Android emulator, runs the smoke test, and
  fails the build on regression.
- Tag the runs: `--tag anchorcore` lets the CI step pull
  this flow specifically out of a larger suite.
- Add a Hook A flow (LLM letter generation) and a Hook B
  flow (friction gate) once data fixtures exist.
