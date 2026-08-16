@file:Suppress("MaxLineLength")
package org.mindanchor.launcher

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

import org.mindanchor.testing.TestFileUtil.fileAt
/**
 * v0.26.5: 4th "I'm up late tonight" option on the 2am shell.
 *
 * Pre-v0.26.5, the 2am shell's only paths out were the 3 main
 * options (sleep / ground / talk) and "wait until 5 AM". The
 * `okAtNight` flag in BpdProfile already existed and was wired
 * through DataStore → NowWhatHeuristic → isTwoAmWindow, and the
 * Settings → PAUSES → BPD profile checkbox was already there to
 * toggle it. What was missing was a discoverable entry point ON
 * the shell itself — a user stuck at 1 AM with the shell covering
 * home had no way to know that toggling a checkbox in Settings
 * would unblock them.
 *
 * v0.26.5 adds a 4th TextButton "I'm up late tonight" to the
 * shell. Tapping it sets `okAtNight = true` in BpdProfile via the
 * existing DataStore path; the next composition reads the new
 * value through `collectAsStateWithLifecycle`, `isTwoAmWindow`
 * recomputes to false, and the shell disappears. Persistent: the
 * user reverts via Settings → PAUSES → BPD profile → uncheck
 * "I'm OK at night".
 *
 * The FindingTest pins:
 *   1. NowWhatShell has 4 callbacks (sleep, ground, talk, stayUp)
 *      — adding a 5th, dropping one, or renaming flips this.
 *   2. NowWhatShell renders the new label `R.string.now_what_stay_up`.
 *   3. NowWhatShell wires the 4th TextButton's onClick to onStayUp
 *      (not to one of the 3 existing callbacks — a future
 *      refactor that wires stayUp to ground by mistake flips
 *      this red).
 *   4. HomeScreen passes onStayUp that calls
 *      `bpdProfilePrefs.update(... okAtNight = true)`.
 *   5. The label is defined in values/strings.xml and
 *      values-ta/strings.xml (Tamil may be a placeholder pending
 *      translation).
 *   6. The 4th option is visually subordinate (TextButton, NOT a
 *      NowWhatRow Surface) — keeps the calm "pick one" framing
 *      of the 3 main options intact.
 */
class NowWhatStayUpFindingTest {

    private val nowWhatShell: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/launcher/NowWhatShell.kt",
        ).readText()

    private val homeScreen: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/launcher/HomeScreen.kt",
        ).readText()

    private val strings: String
        get() = fileAt(
            "app/src/main/res/values/strings.xml",
        ).readText()

    private val stringsTa: String
        get() = fileAt(
            "app/src/main/res/values-ta/strings.xml",
        ).readText()

    @Test
    fun `NowWhatShell declares the 4th onStayUp callback`() {
        // The signature must include all 4 callbacks in one
        // function declaration. Pre-v0.26.5 there were 3
        // (sleep, ground, talk); adding onStayUp is the v0.26.5
        // change. A future refactor that renames or drops one
        // flips this.
        val signaturePattern = Regex(
            """fun\s+NowWhatShell\s*\([\s\S]*?onWantSleep[\s\S]*?onWantGround[\s\S]*?onWantTalk[\s\S]*?onStayUp[\s\S]*?\)""",
        )
        assertTrue(
            "NowWhatShell must declare 4 callbacks: onWantSleep, onWantGround, onWantTalk, onStayUp",
            signaturePattern.containsMatchIn(nowWhatShell),
        )
    }

    @Test
    fun `NowWhatShell renders the stay-up label`() {
        // v0.26.5: a stringResource(R.string.now_what_stay_up)
        // call site exists in the shell. Without this, the
        // button has no text.
        assertTrue(
            "NowWhatShell must call stringResource(R.string.now_what_stay_up) — the 4th button label",
            nowWhatShell.contains("stringResource(R.string.now_what_stay_up)"),
        )
    }

    @Test
    fun `NowWhatShell wires the 4th TextButton onClick to onStayUp`() {
        // The 4th button must be a TextButton (not a NowWhatRow
        // Surface — that would compete with the 3 main options
        // and read as a 4th "what do I need right now" choice
        // rather than a meta-toggle), and its onClick must call
        // `onStayUp` directly.
        val textButtonPattern = Regex(
            """TextButton\s*\([\s\S]*?onClick\s*=\s*onStayUp[\s\S]*?\)""",
        )
        assertTrue(
            "NowWhatShell must have a TextButton with onClick = onStayUp — the 4th option",
            textButtonPattern.containsMatchIn(nowWhatShell),
        )
        // The 4th option must NOT be a NowWhatRow (the 3 main
        // options use the Surface-based NowWhatRow). Mixing the
        // 4th into NowWhatRow would visually promote it to a
        // primary action. Use a literal-prefix check rather than
        // a multi-call regex (a non-greedy `[\s\S]*?` between
        // the `NowWhatRow(` and `now_what_stay_up` would span
        // the file and falsely match the third `NowWhatRow(talk)`
        // call followed by the TextButton's stay_up label).
        assertTrue(
            "NowWhatShell must NOT render the 4th option as a NowWhatRow (would visually promote it to a primary action)",
            !nowWhatShell.contains("NowWhatRow(stringResource(R.string.now_what_stay_up"),
        )
    }

    @Test
    fun `HomeScreen passes onStayUp that toggles BpdProfile okAtNight`() {
        // The onStayUp callback must be wired to write
        // okAtNight = true back to BpdProfile (via the existing
        // DataStore path). A future refactor that, say, sets
        // okAtNight = false or calls a different field flips
        // this.
        val onStayUpPattern = Regex(
            """onStayUp\s*=\s*\{[\s\S]*?bpdProfilePrefs\.update\([\s\S]*?copy\(okAtNight\s*=\s*true\)[\s\S]*?\}""",
        )
        assertTrue(
            "HomeScreen must pass onStayUp = { ... bpdProfilePrefs.update(profile.copy(okAtNight = true)) } " +
                "— the v0.26.5 4th-option wiring",
            onStayUpPattern.containsMatchIn(homeScreen),
        )
    }

    @Test
    fun `HomeScreen onStayUp uses a coroutine scope (DataStore update is suspend)`() {
        // BpdProfilePrefs.update is a `suspend fun`. The
        // callback must launch in a coroutine scope — calling
        // it directly from a non-suspend onClick would not
        // compile. The expected shape is `bpdProfileScope.launch { ... }`
        // where bpdProfileScope comes from rememberCoroutineScope.
        val scopePattern = Regex(
            """bpdProfileScope\.launch\s*\{""",
        )
        assertTrue(
            "HomeScreen must call bpdProfileScope.launch { ... } for the suspend DataStore write",
            scopePattern.containsMatchIn(homeScreen),
        )
        val scopeDeclarationPattern = Regex(
            """val\s+bpdProfileScope\s*=\s*rememberCoroutineScope\(\)""",
        )
        assertTrue(
            "HomeScreen must declare `val bpdProfileScope = rememberCoroutineScope()` near the BpdProfilePrefs setup",
            scopeDeclarationPattern.containsMatchIn(homeScreen),
        )
    }

    @Test
    fun `strings xml defines now_what_stay_up in both locales`() {
        // Both English and Tamil files must declare the new key.
        // Tamil may still be a placeholder English copy (per the
        // v0.25.18 note) — a translator is a v0.26.5+ follow-up.
        assertTrue(
            "values/strings.xml must define <string name=\"now_what_stay_up\">",
            strings.contains("name=\"now_what_stay_up\""),
        )
        assertNotNull(stringsTa)
        assertTrue(
            "values-ta/strings.xml must define <string name=\"now_what_stay_up\"> " +
                "(value may be English placeholder pending translation)",
            stringsTa.contains("name=\"now_what_stay_up\""),
        )
    }
}
