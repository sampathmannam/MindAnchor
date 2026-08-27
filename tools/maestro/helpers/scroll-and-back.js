// Maestro helper: scroll the Measuring page so the
// AnchorCore section is in the viewport. Uses the host
// adb directly because Maestro UI Automator2 on this
// device (Android 17) does not dispatch scroll gestures
// to the LazyColumn. The script runs inside Maestro's
// GraalJS sandbox: no Node globals (process), no Java
// runtime (java.lang) — only what the Maestro script
// runtime exposes. The working approach is to use
// `maestro.shell(...)` (Maestro's own subprocess helper)
// which runs a host shell command.

const dev = "ZD2232FCR5";
const forward = 20;
const back = 5;

for (let i = 0; i < forward; i++) {
  maestro.shell(`adb -s ${dev} shell input swipe 600 2400 600 300 500`);
  maestro.shell(`sleep 0.5`);
}
for (let i = 0; i < back; i++) {
  maestro.shell(`adb -s ${dev} shell input swipe 600 800 600 2400 500`);
  maestro.shell(`sleep 0.5`);
}
