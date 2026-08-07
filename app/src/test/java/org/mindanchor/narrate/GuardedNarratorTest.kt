package org.mindanchor.narrate

// NOTE(ci): first use of a coroutine builder in a unit test here.
// testImplementation extends implementation, and the main source set
// already compiles against kotlinx-coroutines, so runBlocking should
// resolve — but it is unverified in this environment. Check here first if
// this file fails to compile.
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

/**
 * The guard is only a safeguard if it cannot be skipped.
 *
 * These tests stand in for the engine that does not exist yet. Each one
 * is a fake model that returns something specific, and what matters is
 * what reaches the caller — because the day a real engine lands, this is
 * the path it will take, and nobody will be watching it at three in the
 * morning.
 */
class GuardedNarratorTest {

    /** A model that says whatever it is told to say. */
    private class FakeEngine(private val output: String?) : GuardedNarrator() {
        var sawSystem: String? = null
        var sawPrompt: String? = null
        override suspend fun generate(system: String, prompt: String): String? {
            sawSystem = system
            sawPrompt = prompt
            return output
        }
    }

    private fun report() = Report(
        day = "2026-08-07",
        sections = listOf(
            ReportSection(
                observation = Observation(Signal.HRV, Direction.BELOW, today = 32.0, usual = 48.0),
                passages = listOf(
                    Passage(
                        "hrv",
                        "Shaffer & Ginsberg 2017",
                        "RMSSD is a time-domain measure of the variation between consecutive heartbeats.",
                    ),
                ),
            ),
        ),
        notYetKnown = emptyList(),
    )

    private val good =
        "Heart-rate variability describes how much the time between your heartbeats " +
            "changes from one beat to the next. It is usually measured over a few minutes. " +
            "Yours was lower today than it usually is."

    @Test
    fun `an ordinary paragraph reaches the caller`() = runBlocking {
        val narration = FakeEngine(good).narrate(report())
        assertNotNull(narration)
        assertEquals(good, narration!!.text)
    }

    @Test
    fun `a model that names a condition is stopped, not passed through`() = runBlocking {
        // The failure this whole class exists to make impossible.
        val engine = FakeEngine("$good This is a common sign of depression and worth watching.")
        assertNull(engine.narrate(report()))
    }

    @Test
    fun `a model that invents a citation is stopped`() = runBlocking {
        assertNull(FakeEngine("$good Shaffer et al. found the same in a larger sample.").narrate(report()))
    }

    @Test
    fun `a looping model is stopped`() = runBlocking {
        val looped = "Your heart rate variability was lower than usual today. " +
            "It was lower than usual today. It was lower than usual today. " +
            "It was lower than usual today. It was lower than usual today."
        assertNull(FakeEngine(looped).narrate(report()))
    }

    @Test
    fun `an engine that cannot run costs a paragraph and nothing else`() = runBlocking {
        assertNull(FakeEngine(null).narrate(report()))
    }

    @Test
    fun `an engine that throws is not allowed to take the night down with it`() = runBlocking {
        val throwing = object : GuardedNarrator() {
            override suspend fun generate(system: String, prompt: String): String =
                error("the engine fell over")
        }
        assertNull(throwing.narrate(report()))
    }

    @Test
    fun `the engine is handed the prompt this app built, not one of its own`() = runBlocking {
        val engine = FakeEngine(good)
        engine.narrate(report())
        assertEquals(Prompting.SYSTEM, engine.sawSystem)
        assertTrue("the passage must reach the model", "RMSSD" in engine.sawPrompt.orEmpty())
        assertTrue("so must the count", "32.0" in engine.sawPrompt.orEmpty())
        // And the source must not: a model shown an author-year pair
        // will reuse it, and an invented citation makes an unchecked
        // claim look checked.
        assertTrue("Shaffer" !in engine.sawPrompt.orEmpty())
    }

    @Test
    fun `a quiet night never wakes the engine at all`() = runBlocking {
        val engine = FakeEngine(good)
        val quiet = Report(day = "2026-08-07", sections = emptyList(), notYetKnown = emptyList())
        assertNull(engine.narrate(quiet))
        assertNull("nothing to say is not worth a generation", engine.sawPrompt)
    }

    @Test
    fun `the build that ships generates nothing, honestly`() = runBlocking {
        // NoEngineNarrator must not fabricate, simulate from a template,
        // or claim to have tried.
        assertNull(NoEngineNarrator.narrate(report()))
    }
}
