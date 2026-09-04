package org.mindanchor.advisory

import java.util.concurrent.ConcurrentHashMap

/** One running delivery, tracked only in memory while foregrounded. */
data class RunningAdvisoryEpisode(
    val episodeId: String,
    val startedElapsedRealtime: Long,
    val maximumMillis: Long,
)

/**
 * Program 3 Task 4 — the foreground-only clock a delivery runs against.
 *
 * `SystemClock.elapsedRealtime()` is the caller's job to supply, not
 * this object's: elapsed time is meaningful only while the process is
 * actually foregrounded and running, and this stays a pure function of
 * whatever instant the caller asks about.
 */
object AdvisoryPlayerStateMachine {

    fun start(startedElapsedRealtime: Long, maximumMillis: Long) = RunningAdvisoryEpisode(
        episodeId = "",
        startedElapsedRealtime = startedElapsedRealtime,
        maximumMillis = maximumMillis,
    )

    fun elapsed(state: RunningAdvisoryEpisode, nowElapsedRealtime: Long): Long =
        (nowElapsedRealtime - state.startedElapsedRealtime).coerceIn(0L, state.maximumMillis)

    /** Only the exact registered maximum is a completion; anything short of it is null. */
    fun maximumEvent(state: RunningAdvisoryEpisode, nowElapsedRealtime: Long): EpisodeEventType? =
        if (elapsed(state, nowElapsedRealtime) == state.maximumMillis) {
            EpisodeEventType.COMPLETED_MAX_DURATION
        } else {
            null
        }

    fun isCompletion(type: EpisodeEventType): Boolean = type == EpisodeEventType.COMPLETED_MAX_DURATION
}

/**
 * Which episode IDs this process itself started, so a foreground resume
 * can tell "my own process is still running this" apart from "some
 * earlier process started this and never got to close it."
 *
 * Deliberately process-lifetime, in-memory only: an Activity recreation
 * within the same process (rotation, a configuration change) keeps an
 * episode registered, while a real process death — which is exactly
 * when recovery must run — clears it for free.
 */
object AdvisoryProcessSessionRegistry {
    private val activeEpisodeIds = ConcurrentHashMap.newKeySet<String>()

    fun register(episodeId: String) {
        activeEpisodeIds += episodeId
    }

    fun unregister(episodeId: String) {
        activeEpisodeIds -= episodeId
    }

    fun contains(episodeId: String): Boolean = episodeId in activeEpisodeIds
}
