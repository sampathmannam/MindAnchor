// Maestro helper: scroll the Measuring page so the
// AnchorCore section is in the viewport. Uses the host
// adb directly because Maestro UI Automator2 on this
// device (Android 17) does not dispatch scroll gestures
// to the LazyColumn.

const dev = process.env.DEVICE || process.env.ANDROID_SERIAL || "ZD2232FCR5";
const forward = parseInt(process.env.FORWARD || "20", 10);
const back = parseInt(process.env.BACK || "5", 10);

for (let i = 0; i < forward; i++) {
  java.lang.Runtime.getRuntime().exec(["bash", "-c", "adb -s " + dev + " shell input swipe 600 2400 600 300 500"]);
  java.lang.Thread.sleep(500);
}
for (let i = 0; i < back; i++) {
  java.lang.Runtime.getRuntime().exec(["bash", "-c", "adb -s " + dev + " shell input swipe 600 800 600 2400 500"]);
  java.lang.Thread.sleep(500);
}
