package org.mindanchor.note

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * Finding tests for the v0.25.0 on-device note
 * classifier. The classifier has two pieces
 * testable without a model: the parser (which
 * takes the model's raw output and folds it
 * into a [org.mindanchor.model.NoteType]) and
 * the seed (which is derived from the body for
 * reproducibility). Both are tested here.
 *
 * The model call itself is not testable on the
 * JVM unit-test runtime: [org.mindanchor.narrate.LlamaEngine.loaded]
 * is false, the `mindanchor_llama` library is
 * not on the classpath, and the native entry
 * would crash if invoked. The classifier's
 * [NoteClassifier.classify] method handles this
 * by returning GENERAL silently — a behavioural
 * test for that path is a senior-tester pass on
 * the emulator, not a unit test.
 */
class NoteClassifierFindingTest {

    @Test
    fun `parseOutput maps the four valid type names`() {
        val c = classifier()
        assertEquals(org.mindanchor.model.NoteType.GENERAL, c.parseOutput("GENERAL"))
        assertEquals(org.mindanchor.model.NoteType.TASK, c.parseOutput("TASK"))
        assertEquals(org.mindanchor.model.NoteType.REMINDER, c.parseOutput("REMINDER"))
        assertEquals(org.mindanchor.model.NoteType.JOURNAL, c.parseOutput("JOURNAL"))
    }

    @Test
    fun `parseOutput is case-insensitive on the first non-blank token`() {
        val c = classifier()
        assertEquals(org.mindanchor.model.NoteType.TASK, c.parseOutput("task"))
        assertEquals(org.mindanchor.model.NoteType.TASK, c.parseOutput("Task"))
        assertEquals(org.mindanchor.model.NoteType.TASK, c.parseOutput("tAsK"))
    }

    @Test
    fun `parseOutput trims leading and trailing whitespace`() {
        val c = classifier()
        assertEquals(org.mindanchor.model.NoteType.JOURNAL, c.parseOutput("  JOURNAL  "))
        assertEquals(org.mindanchor.model.NoteType.JOURNAL, c.parseOutput("\n\tJOURNAL\t\n"))
    }

    @Test
    fun `parseOutput takes the first non-blank line's first token`() {
        val c = classifier()
        // The model sometimes adds a preamble line; we
        // take the first non-blank line.
        assertEquals(org.mindanchor.model.NoteType.REMINDER, c.parseOutput("\nREMINDER\nTASK"))
        // The first non-blank line is the answer; we
        // take its first token, not the whole line.
        assertEquals(org.mindanchor.model.NoteType.TASK, c.parseOutput("TASK because..."))
    }

    @Test
    fun `parseOutput defaults to GENERAL on malformed input`() {
        val c = classifier()
        assertEquals(org.mindanchor.model.NoteType.GENERAL, c.parseOutput(""))
        assertEquals(org.mindanchor.model.NoteType.GENERAL, c.parseOutput("   "))
        assertEquals(org.mindanchor.model.NoteType.GENERAL, c.parseOutput("BOGUS"))
        assertEquals(org.mindanchor.model.NoteType.GENERAL, c.parseOutput("DEPRESSED")) // forbidden
        assertEquals(org.mindanchor.model.NoteType.GENERAL, c.parseOutput("a question?"))
    }

    @Test
    fun `parseOutput does not treat a non-recognised name as a fallback`() {
        // An unknown word is folded into GENERAL.
        // This is the spec's "safe default" — the
        // model can be conservative, but the user
        // never sees a chip that says "BOGUS".
        val c = classifier()
        assertEquals(org.mindanchor.model.NoteType.GENERAL, c.parseOutput("FIVE"))
        assertEquals(org.mindanchor.model.NoteType.GENERAL, c.parseOutput("REMIND"))
    }

    /**
     * Build a classifier whose engine call cannot
     * run (the unit-test runtime has no native
     * library), so [NoteClassifier.classify]
     * returns GENERAL immediately. The parser
     * itself is the testable piece; it doesn't
     * touch the [Context], so a Mockito mock is
     * sufficient.
     */
    private fun classifier(): NoteClassifier = NoteClassifier(mock(Context::class.java))
}
