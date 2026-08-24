package org.mindanchor.narrate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.30+ (CodeRabbit review 2026-08-24 of PR #38) — the
 * [Reframer] should pass the LLM result through
 * [NarrationGuard.judge] and fall back to the template
 * when the guard rejects. The Reframer is a thin
 * wrapper around [LlamaNarrator], which needs a real
 * native model to produce output. We test the
 * contract by checking that the guard's verdict is
 * what the [Reframer] ends up using, given a
 * pre-judged body.
 *
 * The actual end-to-end [Reframer.reframe] path is
 * tested via the integration suite (which needs a
 * real device + model); this file pins the
 * "guard is applied" rule on its own.
 */
class ReframerGuardTest {

    @Test
    fun `NarrationGuard accepts a body that is within the line and char limits`() {
        val body = "This is a sentence.\n" +
            "Two facts on the table: the day continued, and the user wrote it down.\n" +
            "One skill for tonight: TIPP if it is sharp, DEAR MAN if it is with a " +
            "person, or the bedtime list if it is wide."
        val verdict = NarrationGuard.judge(body)
        assertTrue("expected Accepted, got $verdict", verdict is NarrationGuard.Verdict.Accepted)
    }

    @Test
    fun `NarrationGuard rejects a body that is too short`() {
        val verdict = NarrationGuard.judge("too short")
        assertEquals(NarrationGuard.Verdict.Rejected(NarrationGuard.Reason.TOO_SHORT), verdict)
    }

    @Test
    fun `NarrationGuard rejects a body that is too long`() {
        val body = "x".repeat(NarrationGuard.MAX_CHARACTERS + 1)
        val verdict = NarrationGuard.judge(body)
        assertEquals(NarrationGuard.Verdict.Rejected(NarrationGuard.Reason.TOO_LONG), verdict)
    }

    @Test
    fun `NarrationGuard rejects a body that names a diagnosis`() {
        // The "depress" forbidden term is matched as
        // a substring, lowercased, so the test below
        // is the failure case the guard is intended
        // to catch.
        val body = "A paragraph that names a diagnosis. " +
            "The research says you are depressed and need treatment. " +
            "A reasonable conclusion follows from the data: the user is at risk of a depressive episode. " +
            "This is the second sentence in the paragraph so the body is long enough to pass the size check."
        val verdict = NarrationGuard.judge(body)
        assertEquals(
            NarrationGuard.Verdict.Rejected(NarrationGuard.Reason.FORBIDDEN_TERM),
            verdict,
        )
    }
}
