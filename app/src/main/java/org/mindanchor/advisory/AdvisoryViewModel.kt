package org.mindanchor.advisory

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.ZoneId
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.mindanchor.data.db.AdvisoryOpportunityEntity
import org.mindanchor.research.EvidenceProtocol

/**
 * Program 3 Task 5 — everything the advisory surfaces render, and
 * nothing else. Neither the launcher's search/drawer state nor any
 * other feature's state lives here.
 *
 * There is no state for "the person answered yes to X": [Evidence]'s
 * only mutable local fact is whether the screen is open, and [Player]
 * exists only while this process itself is running the episode.
 */
sealed interface AdvisoryUiState {
    data object Hidden : AdvisoryUiState

    data class Card(val opportunity: AdvisoryOpportunityEntity) : AdvisoryUiState

    /**
     * [startBlockedReason] is null exactly when [startEnabled] is true.
     * It is a mechanical local-control fact (delivery off, cooldown,
     * an episode active elsewhere) — never a reinterpretation of the
     * source.
     */
    data class Evidence(
        val opportunity: AdvisoryOpportunityEntity,
        val protocol: EvidenceProtocol,
        val startEnabled: Boolean,
        val startBlockedReason: AdvisoryIneligibleReason?,
    ) : AdvisoryUiState

    data class Player(
        val opportunity: AdvisoryOpportunityEntity,
        val protocol: EvidenceProtocol,
        val episodeId: String,
        val elapsedMillis: Long,
    ) : AdvisoryUiState
}

/** What is running right now, kept only in this process's memory. */
private data class RunningPlayer(
    val episode: RunningAdvisoryEpisode,
    val opportunity: AdvisoryOpportunityEntity,
    val protocol: EvidenceProtocol,
)

/**
 * Owns exactly the advisory surfaces: a Home card, its evidence screen,
 * and the foreground player. [repository] is the single source of
 * durable truth for the card and evidence states; a running player is
 * necessarily local, in-memory, per-process state — the repository's
 * read model stops reporting an opportunity the moment it is started,
 * which is also why a genuinely orphaned (process-death) started
 * episode is recovered at the storage layer
 * ([AdvisoryEpisodeRepositoryTest]'s process-recovery coverage), not by
 * this ViewModel reconstructing playback it has no reliable clock
 * origin for.
 */
class AdvisoryViewModel internal constructor(
    private val repository: AdvisoryRepository,
    private val reconciler: AdvisoryOutcomeReconciler,
    private val wallClock: () -> Long,
    private val elapsedClock: () -> Long,
    private val zoneId: () -> ZoneId,
) : ViewModel() {

    private val evidenceOpened = MutableStateFlow(false)
    private val runningPlayer = MutableStateFlow<RunningPlayer?>(null)
    private val elapsedMillis = MutableStateFlow(0L)
    private var tickerJob: Job? = null

    val uiState: StateFlow<AdvisoryUiState> = combine(
        repository.observe(),
        evidenceOpened,
        runningPlayer,
        elapsedMillis,
    ) { readModel, opened, player, elapsed ->
        if (player != null) {
            AdvisoryUiState.Player(player.opportunity, player.protocol, player.episode.episodeId, elapsed)
        } else {
            toCardOrEvidence(readModel, opened)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_SUBSCRIPTION_TIMEOUT_MILLIS), AdvisoryUiState.Hidden)

    private fun toCardOrEvidence(readModel: AdvisoryReadModel, opened: Boolean): AdvisoryUiState = when (readModel) {
        is AdvisoryReadModel.Hidden -> AdvisoryUiState.Hidden
        is AdvisoryReadModel.ActiveEpisode -> AdvisoryUiState.Hidden
        is AdvisoryReadModel.Opportunity -> if (opened) {
            AdvisoryUiState.Evidence(
                opportunity = readModel.row,
                protocol = readModel.protocol,
                startEnabled = readModel.startAvailable,
                startBlockedReason = readModel.startBlockedReason,
            )
        } else {
            AdvisoryUiState.Card(readModel.row)
        }
    }

    /** Closes due outcome windows, then materializes today's opportunity if one is due. */
    fun onResume() {
        viewModelScope.launch {
            val now = wallClock()
            reconciler.reconcile(now, zoneId())
            repository.refreshOpportunity(now, zoneId())
        }
    }

    fun openEvidence() {
        evidenceOpened.value = true
    }

    fun dismiss() {
        val opportunityId = currentOpportunityId() ?: return
        viewModelScope.launch { repository.dismiss(opportunityId, wallClock(), zoneId()) }
        evidenceOpened.value = false
    }

    fun start() {
        val state = uiState.value as? AdvisoryUiState.Evidence ?: return
        viewModelScope.launch {
            val result = repository.start(state.opportunity.id, wallClock(), zoneId())
            if (result is AdvisoryStartResult.Started) {
                val episode = RunningAdvisoryEpisode(
                    episodeId = result.episodeId,
                    startedElapsedRealtime = elapsedClock(),
                    maximumMillis = state.protocol.maxDurationSeconds * MILLIS_PER_SECOND,
                )
                AdvisoryProcessSessionRegistry.register(result.episodeId)
                runningPlayer.value = RunningPlayer(episode, state.opportunity, state.protocol)
                evidenceOpened.value = false
                startTicker(episode)
            }
        }
    }

    fun stop() = stopWith(EpisodeEventType.STOPPED_BY_USER)

    fun reportDiscomfort() = stopWith(EpisodeEventType.STOPPED_DISCOMFORT_REPORTED)

    private fun stopWith(kind: EpisodeEventType) {
        val player = runningPlayer.value ?: return
        val delivered = AdvisoryPlayerStateMachine.elapsed(player.episode, elapsedClock())
        viewModelScope.launch { repository.stop(player.episode.episodeId, kind, wallClock(), delivered) }
        clearRunningPlayer()
    }

    /**
     * Synchronous at the ViewModel boundary: by the time this returns,
     * exactly one terminal write has been dispatched. `ON_STOP` cannot
     * wait for a suspend function to finish, so the write itself is
     * launched fire-and-forget in [viewModelScope] rather than awaited.
     */
    fun onBackground() {
        val player = runningPlayer.value ?: return
        val now = wallClock()
        val elapsed = AdvisoryPlayerStateMachine.elapsed(player.episode, elapsedClock())
        val isCompletion = AdvisoryPlayerStateMachine.maximumEvent(player.episode, elapsedClock()) != null
        viewModelScope.launch {
            if (isCompletion) {
                repository.completeMaximumDuration(player.episode.episodeId, now, elapsed, completedCyclesFor(elapsed))
            } else {
                repository.stop(player.episode.episodeId, EpisodeEventType.INTERRUPTED_APP_BACKGROUND, now, elapsed)
            }
        }
        clearRunningPlayer()
    }

    /**
     * Consumes Back by stopping an active episode or stepping Evidence
     * to Card; otherwise lets the caller navigate away.
     */
    fun onBack(): Boolean {
        if (runningPlayer.value != null) {
            stop()
            return true
        }
        if (evidenceOpened.value) {
            evidenceOpened.value = false
            return true
        }
        return false
    }

    private fun currentOpportunityId(): String? = when (val state = uiState.value) {
        is AdvisoryUiState.Card -> state.opportunity.id
        is AdvisoryUiState.Evidence -> state.opportunity.id
        else -> null
    }

    private fun completedCyclesFor(deliveredMillis: Long): Int = (deliveredMillis / CYCLE_MILLIS).toInt()

    private fun startTicker(episode: RunningAdvisoryEpisode) {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                elapsedMillis.value = AdvisoryPlayerStateMachine.elapsed(episode, elapsedClock())
                delay(TICK_MILLIS)
            }
        }
    }

    private fun clearRunningPlayer() {
        tickerJob?.cancel()
        tickerJob = null
        runningPlayer.value?.let { AdvisoryProcessSessionRegistry.unregister(it.episode.episodeId) }
        runningPlayer.value = null
        elapsedMillis.value = 0L
    }

    override fun onCleared() {
        tickerJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val STATE_SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
        private const val TICK_MILLIS = 200L
        private const val MILLIS_PER_SECOND = 1_000L
        private const val CYCLE_MILLIS = 9_000L

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                AdvisoryViewModel(
                    repository = RoomAdvisoryRepository.build(appContext),
                    reconciler = RoomAdvisoryOutcomeReconciler.build(appContext),
                    wallClock = System::currentTimeMillis,
                    elapsedClock = SystemClock::elapsedRealtime,
                    zoneId = ZoneId::systemDefault,
                )
            }
        }
    }
}
