package org.mindanchor.narrate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.corpus.Passage
import org.mindanchor.report.Direction
import org.mindanchor.report.Observation
import org.mindanchor.report.Report
import org.mindanchor.report.ReportSection
import org.mindanchor.report.Signal

class PromptingTest {

    private fun section(
        signal: Signal = Signal.HRV,
        direction: Direction = Direction.BELOW,
        passages: List<Passage> = listOf(
            Passage("hrv", "Shaffer & Ginsberg 2017", "RMSSD is a time-domain measure of heart rate variability."),
        ),
    ) = ReportSection(
        observation = Observation(signal, direction, today = 32.0, usual = 48.0),
        passages = passages,
    )

    private fun report(vararg sections: ReportSection) =
        Report(day = "2026-08-06", sections = sections.toList(), notYetKnown = emptyList())

    @Test
    fun `a quiet night is not worth waking a model for`() {
        assertNull(Prompting.build(report()))
    }

    @Test
    fun `the prompt carries the counts and the passages`() {
        val prompt = Prompting.build(report(section()))
        assertNotNull(prompt)
        assertTrue("the count is the fact the paragraph is about", "32.0" in prompt!!)
        assertTrue("the person's own usual has to be in there too", "48.0" in prompt)
        assertTrue("the model can only work from what it is shown", "RMSSD" in prompt)
    }

    @Test
    fun `the prompt never hands the model a source to repeat`() {
        // Citations are rendered by the app from the real passages. A model
        // shown an author-year pair will reuse it, and one that was never
        // shown one has nothing to reuse.
        val everything = Prompting.build(report(section())).orEmpty() + Prompting.SYSTEM
        assertFalse("the passage's author must not reach the model", "Shaffer" in everything)
        assertFalse("nor its year", "2017" in everything)
    }

    @Test
    fun `the instruction forbids saying what a change means`() {
        val system = Prompting.SYSTEM.lowercase()
        assertTrue("never say what a change means" in system)
        assertTrue("never name a study" in system)
    }

    @Test
    fun `a passage the paragraph could not legally quote is not used to build it`() {
        // Handing the model vocabulary that NarrationGuard rejects sets it
        // up to fail every night. The passage is not lost: the report
        // still shows it verbatim with its source.
        val clinical = Passage("meta", "JMIR 2025", "Only one study could distinguish relapse from recovery.")
        val plain = Passage("hrv", "Shaffer 2017", "RMSSD is a time-domain measure of beat-to-beat variation.")
        val prompt = Prompting.build(report(section(passages = listOf(clinical, plain))))
        assertNotNull(prompt)
        assertFalse("relapse" in prompt!!)
        assertTrue("RMSSD" in prompt)
    }

    @Test
    fun `nothing usable to write from is nothing to write`() {
        val clinical = Passage("meta", "JMIR 2025", "Only one study could distinguish relapse from recovery.")
        assertNull(Prompting.build(report(section(passages = listOf(clinical)))))
    }

    @Test
    fun `the same passage retrieved for two signals appears once`() {
        val shared = Passage("hrv", "Shaffer 2017", "RMSSD is a time-domain measure of beat-to-beat variation.")
        val prompt = Prompting.build(
            report(
                section(Signal.HRV, passages = listOf(shared)),
                section(Signal.RESTING_HEART_RATE, passages = listOf(shared)),
            ),
        )
        assertNotNull(prompt)
        assertEquals(1, prompt!!.split("RMSSD is a time-domain").size - 1)
    }

    @Test
    fun `every passage the shipped corpus can put in front of a model is one it may echo`() {
        // The two lists are shared deliberately; this fails if they ever
        // stop agreeing about what a paragraph may contain.
        val safe = "RMSSD is a time-domain measure of heart rate variability."
        assertTrue(Prompting.usable(safe))
        NarrationGuard.FORBIDDEN.forEach { term ->
            assertFalse("'$term' must not survive the filter", Prompting.usable("A passage about $term here."))
        }
    }
}

class NarrationGuardTest {

    private val good =
        "Heart-rate variability describes how much the time between your heartbeats changes " +
            "from one beat to the next. It is usually measured over a few minutes. Yours was " +
            "lower today than it usually is."

    @Test
    fun `an ordinary paragraph is accepted unchanged`() {
        val verdict = NarrationGuard.judge("  $good  ")
        assertTrue(verdict is NarrationGuard.Verdict.Accepted)
        assertEquals(good, (verdict as NarrationGuard.Verdict.Accepted).text)
    }

    @Test
    fun `a fragment is not a paragraph`() {
        assertEquals(
            NarrationGuard.Reason.TOO_SHORT,
            (NarrationGuard.judge("Your HRV was low.") as NarrationGuard.Verdict.Rejected).reason,
        )
    }

    @Test
    fun `a model that has stopped answering and started running on is cut off`() {
        val long = "Heart-rate variability is a measure of beat spacing. ".repeat(40)
        assertEquals(
            NarrationGuard.Reason.TOO_LONG,
            (NarrationGuard.judge(long) as NarrationGuard.Verdict.Rejected).reason,
        )
    }

    @Test
    fun `a diagnosis is refused however it is phrased`() {
        val phrasings = listOf(
            "$good This is a common sign of depression in people your age and worth watching.",
            "$good You may be experiencing symptoms that are worth discussing with someone.",
            "$good This pattern is often seen before a relapse, so take care of yourself now.",
            "$good People with anxiety frequently show exactly this shape over several days.",
        )
        phrasings.forEach { text ->
            assertEquals(
                "must refuse: $text",
                NarrationGuard.Reason.FORBIDDEN_TERM,
                (NarrationGuard.judge(text) as NarrationGuard.Verdict.Rejected).reason,
            )
        }
    }

    @Test
    fun `a careful sentence is refused too, and that is the intended trade`() {
        // "The research does not say whether this indicates depression" is
        // a model being careful, and it is still rejected. A list narrow
        // enough to let it through is a list with gaps, and the cost of a
        // gap is far higher than the cost of a lost paragraph.
        val careful = "$good The research does not say whether this indicates depression."
        assertTrue(NarrationGuard.judge(careful) is NarrationGuard.Verdict.Rejected)
    }

    @Test
    fun `an invented citation is refused`() {
        val cited = listOf(
            "$good Shaffer et al. found the same thing in a larger sample.",
            "$good This was shown in a study by researchers working on wearables.",
            "$good According to the literature, this is well established.",
            "$good The finding (2017) has been replicated many times since.",
        )
        cited.forEach { text ->
            assertEquals(
                "must refuse: $text",
                NarrationGuard.Reason.CITED_A_SOURCE,
                (NarrationGuard.judge(text) as NarrationGuard.Verdict.Rejected).reason,
            )
        }
    }

    @Test
    fun `a looping model is caught`() {
        // The characteristic failure of a small heavily quantised model:
        // it stops generating and starts repeating.
        val looped = "Your heart rate variability was lower than usual today. " +
            "It was lower than usual today. It was lower than usual today. " +
            "It was lower than usual today. It was lower than usual today."
        assertEquals(
            NarrationGuard.Reason.LOOPED,
            (NarrationGuard.judge(looped) as NarrationGuard.Verdict.Rejected).reason,
        )
    }

    @Test
    fun `an ordinary paragraph that repeats a phrase twice is not a loop`() {
        val emphatic = "Sleep regularity describes how consistent your sleep timing is. " +
            "Sleep regularity describes how consistent your sleep timing is, night to night, " +
            "and it is measured separately from how long you slept."
        assertTrue(NarrationGuard.judge(emphatic) is NarrationGuard.Verdict.Accepted)
    }
}

class ModelSlotTest {

    private val gb = 1024L * 1024 * 1024

    @Test
    fun `a four-bit three-billion model runs on the eight-gigabyte phone this has to work on`() {
        assertEquals(
            ModelSlot.Fit.TIGHT,
            ModelSlot.fit(modelBytes = (2.4 * gb).toLong(), totalMemBytes = 8 * gb, isLowRamDevice = false),
        )
        assertTrue(ModelSlot.runnable(ModelSlot.fit((2.4 * gb).toLong(), 8 * gb, false)))
    }

    @Test
    fun `the same model has room to spare on twelve gigabytes`() {
        assertEquals(
            ModelSlot.Fit.FITS,
            ModelSlot.fit((2.4 * gb).toLong(), 12 * gb, isLowRamDevice = false),
        )
    }

    @Test
    fun `a model too large for the phone is refused rather than tried`() {
        // Trying and being killed is indistinguishable, from the outside,
        // from a quiet night with nothing to report.
        assertEquals(
            ModelSlot.Fit.TOO_LARGE,
            ModelSlot.fit((7 * gb), 12 * gb, isLowRamDevice = false),
        )
        assertFalse(ModelSlot.runnable(ModelSlot.fit(7 * gb, 12 * gb, false)))
    }

    @Test
    fun `a low-memory phone is taken at Android's word, at any size`() {
        listOf(200L * 1024 * 1024, gb, 4 * gb).forEach { size ->
            assertEquals(
                ModelSlot.Fit.UNSUPPORTED,
                ModelSlot.fit(size, 8 * gb, isLowRamDevice = true),
            )
        }
    }

    @Test
    fun `a file that is not there, or a phone that will not say, is not run`() {
        assertEquals(ModelSlot.Fit.TOO_LARGE, ModelSlot.fit(0, 8 * gb, false))
        assertEquals(ModelSlot.Fit.TOO_LARGE, ModelSlot.fit(gb, 0, false))
        assertEquals(ModelSlot.Fit.TOO_LARGE, ModelSlot.fit(-1, 8 * gb, false))
    }

    @Test
    fun `the phone with least room gets the smallest context`() {
        assertTrue(ModelSlot.contextTokens(ModelSlot.Fit.TIGHT) < ModelSlot.contextTokens(ModelSlot.Fit.FITS))
        assertEquals(0, ModelSlot.contextTokens(ModelSlot.Fit.TOO_LARGE))
        assertEquals(0, ModelSlot.contextTokens(ModelSlot.Fit.UNSUPPORTED))
    }

    @Test
    fun `fit is monotonic in model size`() {
        // A larger model can never be a better fit than a smaller one.
        val order = listOf(ModelSlot.Fit.FITS, ModelSlot.Fit.TIGHT, ModelSlot.Fit.TOO_LARGE)
        var worst = 0
        for (bytes in generateSequence(256L * 1024 * 1024) { it + 256L * 1024 * 1024 }.take(30)) {
            val rank = order.indexOf(ModelSlot.fit(bytes, 8 * gb, false))
            assertTrue("fit improved as the model grew, at $bytes bytes", rank >= worst)
            worst = rank
        }
    }
}
