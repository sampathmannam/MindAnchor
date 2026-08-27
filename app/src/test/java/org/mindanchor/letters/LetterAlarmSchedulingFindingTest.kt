package org.mindanchor.letters

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.72.x: regression test for the "toggle letters,
 * nothing happens at 8 AM" bug.
 *
 * The bug: [SettingsViewModel.setLettersEnabled] and
 * [SettingsViewModel.setLettersTime] only wrote to the
 * [org.mindanchor.letters.LetterStore] flag. The actual
 * [LetterScheduler.ensureScheduled] was only ever called
 * from [org.mindanchor.HomeActivity.onCreate], so a user
 * who opened settings, flipped the toggle ON, and never
 * restarted the launcher never got an alarm scheduled.
 *
 * The fix: both setters call
 * `LetterScheduler.ensureScheduled(applicationContext)`
 * after writing. This finding-test pins that wiring by
 * reading the source.
 *
 * If a future contributor removes the
 * `LetterScheduler.ensureScheduled` call from either
 * setter, the toggle stops arming the alarm and the user
 * gets no letter at their chosen time. This test fails
 * first.
 */
class LetterAlarmSchedulingFindingTest {

    private val settersViewModel = java.io.File(
        "../app/src/main/java/org/mindanchor/settings/SettingsViewModel.kt"
    )

    @Test
    fun `setLettersEnabled calls LetterScheduler_ensureScheduled after writing the flag`() {
        assertTrue(
            "Settings source must exist",
            settersViewModel.exists(),
        )
        val src = settersViewModel.readText()
        // Find the setLettersEnabled function. The body
        // must contain the LetterScheduler call after the
        // flag write.
        val fnStart = src.indexOf("fun setLettersEnabled(")
        assertTrue("setLettersEnabled function not found", fnStart >= 0)
        // Read up to the matching closing brace (one
        // level deep — the function body has no nested
        // classes, so a simple brace-counting pass is OK).
        val fnBody = readFunctionBody(src, fnStart)
        assertTrue(
            "setLettersEnabled must call LetterScheduler.ensureScheduled after the flag write",
            fnBody.contains("LetterScheduler.ensureScheduled"),
        )
    }

    @Test
    fun `setLettersTime calls LetterScheduler_ensureScheduled after writing the flag`() {
        val fnStart = settersViewModel.readText().indexOf("fun setLettersTime(")
        assertTrue("setLettersTime function not found", fnStart >= 0)
        val fnBody = readFunctionBody(settersViewModel.readText(), fnStart)
        assertTrue(
            "setLettersTime must call LetterScheduler.ensureScheduled after the flag write",
            fnBody.contains("LetterScheduler.ensureScheduled"),
        )
    }

    /**
     * Read from [start] (the index of `fun ...`) to the
     * matching close brace. Brace depth starts at 0 at
     * the function header. This is a quick-and-dirty
     * source scan — the body never contains nested
     * function declarations in this codebase, so a
     * simple counter is enough.
     */
    private fun readFunctionBody(src: String, start: Int): String {
        var depth = 0
        var i = start
        while (i < src.length) {
            when (src[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return src.substring(start, i + 1)
                }
            }
            i++
        }
        return src.substring(start)
    }
}
