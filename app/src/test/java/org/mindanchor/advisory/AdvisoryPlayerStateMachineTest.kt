package org.mindanchor.advisory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Program 3 Task 4 — the foreground player is a pure function of elapsed
 * time, and only the exact registered maximum is a completion.
 */
class AdvisoryPlayerStateMachineTest {

    @Test
    fun `only exact maximum duration completes`() {
        val running = AdvisoryPlayerStateMachine.start(startedElapsedRealtime = 10_000L, maximumMillis = 300_000L)
        assertNull(AdvisoryPlayerStateMachine.maximumEvent(running, nowElapsedRealtime = 309_999L))
        assertEquals(
            EpisodeEventType.COMPLETED_MAX_DURATION,
            AdvisoryPlayerStateMachine.maximumEvent(running, nowElapsedRealtime = 310_000L),
        )
    }

    @Test
    fun `background back discomfort process recovery and kill switch are never completion`() {
        listOf(
            EpisodeEventType.INTERRUPTED_APP_BACKGROUND,
            EpisodeEventType.STOPPED_BY_USER,
            EpisodeEventType.STOPPED_DISCOMFORT_REPORTED,
            EpisodeEventType.INTERRUPTED_PROCESS_RECOVERY,
            EpisodeEventType.STOPPED_KILL_SWITCH,
        ).forEach { assertFalse(AdvisoryPlayerStateMachine.isCompletion(it)) }
    }

    @Test
    fun `only completed max duration is a completion`() {
        EpisodeEventType.entries.forEach { type ->
            assertEquals(
                type == EpisodeEventType.COMPLETED_MAX_DURATION,
                AdvisoryPlayerStateMachine.isCompletion(type),
            )
        }
    }

    @Test
    fun `elapsed time never exceeds the registered maximum or goes negative`() {
        val running = AdvisoryPlayerStateMachine.start(startedElapsedRealtime = 10_000L, maximumMillis = 300_000L)
        assertEquals(0L, AdvisoryPlayerStateMachine.elapsed(running, nowElapsedRealtime = 5_000L))
        assertEquals(150_000L, AdvisoryPlayerStateMachine.elapsed(running, nowElapsedRealtime = 160_000L))
        assertEquals(300_000L, AdvisoryPlayerStateMachine.elapsed(running, nowElapsedRealtime = 1_000_000L))
    }

    @Test
    fun `the process session registry tracks only episodes this process registered`() {
        val episodeId = "episode-under-test"
        assertFalse(AdvisoryProcessSessionRegistry.contains(episodeId))
        AdvisoryProcessSessionRegistry.register(episodeId)
        assertTrue(AdvisoryProcessSessionRegistry.contains(episodeId))
        AdvisoryProcessSessionRegistry.unregister(episodeId)
        assertFalse(AdvisoryProcessSessionRegistry.contains(episodeId))
    }

    @Test
    fun `episode transitions require the exact eligibility attested then started order`() {
        assertTrue(EpisodeTransitions.mayAppend(emptyList(), EpisodeEventType.ELIGIBILITY_ATTESTED))
        assertFalse(EpisodeTransitions.mayAppend(emptyList(), EpisodeEventType.STARTED))
        assertTrue(
            EpisodeTransitions.mayAppend(
                listOf(EpisodeEventType.ELIGIBILITY_ATTESTED),
                EpisodeEventType.STARTED,
            ),
        )
        assertFalse(
            EpisodeTransitions.mayAppend(
                listOf(EpisodeEventType.ELIGIBILITY_ATTESTED, EpisodeEventType.STARTED),
                EpisodeEventType.ELIGIBILITY_ATTESTED,
            ),
        )
    }

    @Test
    fun `exactly one terminal event may ever be appended`() {
        val running = listOf(EpisodeEventType.ELIGIBILITY_ATTESTED, EpisodeEventType.STARTED)
        assertTrue(EpisodeTransitions.mayAppend(running, EpisodeEventType.STOPPED_BY_USER))
        assertTrue(EpisodeTransitions.mayAppend(running, EpisodeEventType.COMPLETED_MAX_DURATION))
        val alreadyStopped = running + EpisodeEventType.STOPPED_BY_USER
        EpisodeEventType.entries.filter { it in EpisodeTransitions.terminal }.forEach { second ->
            assertFalse(
                "a second terminal event must be refused: $second",
                EpisodeTransitions.mayAppend(alreadyStopped, second),
            )
        }
    }

    @Test
    fun `dismissal requires an empty stream and outcome events require completion first`() {
        assertTrue(EpisodeTransitions.mayAppend(emptyList(), EpisodeEventType.DISMISSED))
        assertFalse(
            EpisodeTransitions.mayAppend(listOf(EpisodeEventType.DISMISSED), EpisodeEventType.DISMISSED),
        )
        assertFalse(
            EpisodeTransitions.mayAppend(
                listOf(
                    EpisodeEventType.ELIGIBILITY_ATTESTED, EpisodeEventType.STARTED, EpisodeEventType.STOPPED_BY_USER,
                ),
                EpisodeEventType.OUTCOME_WINDOW_OPENED,
            ),
        )
        val completed = listOf(
            EpisodeEventType.ELIGIBILITY_ATTESTED,
            EpisodeEventType.STARTED,
            EpisodeEventType.COMPLETED_MAX_DURATION,
        )
        assertTrue(EpisodeTransitions.mayAppend(completed, EpisodeEventType.OUTCOME_WINDOW_OPENED))
        val windowOpened = completed + EpisodeEventType.OUTCOME_WINDOW_OPENED
        assertTrue(EpisodeTransitions.mayAppend(windowOpened, EpisodeEventType.OUTCOME_WINDOW_CLOSED_MISSING))
        assertFalse(
            EpisodeTransitions.mayAppend(
                windowOpened + EpisodeEventType.OUTCOME_WINDOW_CLOSED_MISSING,
                EpisodeEventType.OUTCOME_WINDOW_CLOSED_MISSING,
            ),
        )
    }
}
