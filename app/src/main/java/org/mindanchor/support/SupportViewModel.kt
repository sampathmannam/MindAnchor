package org.mindanchor.support

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.data.db.CrisisContact
import org.mindanchor.data.db.SafetyDao
import org.mindanchor.data.db.SafetyPlan

internal enum class SafetyPlanUiError { SaveFailed }

internal sealed interface SafetyPlanUiState {
    val persisted: SafetyPlan

    data class Viewing(override val persisted: SafetyPlan) : SafetyPlanUiState

    data class Editing(
        override val persisted: SafetyPlan,
        val draft: SafetyPlan,
        val error: SafetyPlanUiError? = null,
    ) : SafetyPlanUiState

    data class Saving(
        override val persisted: SafetyPlan,
        val command: SaveSafetyPlan,
        val closeRequested: Boolean = false,
        val isSlow: Boolean = false,
    ) : SafetyPlanUiState
}

internal sealed interface SupportEvent {
    data object Edit : SupportEvent
    data class DraftChanged(val draft: SafetyPlan) : SupportEvent
    data object Done : SupportEvent
    data object Back : SupportEvent
}

internal sealed interface SupportEffect {
    data object Close : SupportEffect
}

internal val SafetyPlanUiState.visiblePlan: SafetyPlan
    get() = when (this) {
        is SafetyPlanUiState.Viewing -> persisted
        is SafetyPlanUiState.Editing -> draft
        is SafetyPlanUiState.Saving -> {
            if (persisted.updatedAt > command.draft.updatedAt) persisted else command.draft
        }
    }

class SupportViewModel internal constructor(
    application: Application,
    private val store: SafetyPlanStore,
    private val dao: SafetyDao,
    private val slowThresholdMillis: Long,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application = application,
        store = RoomSafetyPlanStore(AnchorDatabase.get(application).safety()),
        dao = AnchorDatabase.get(application).safety(),
        slowThresholdMillis = SLOW_THRESHOLD_MILLIS,
    )

    private val _uiState = MutableStateFlow<SafetyPlanUiState>(
        SafetyPlanUiState.Viewing(SafetyPlan(updatedAt = UNLOADED_UPDATED_AT)),
    )
    internal val uiState = _uiState.asStateFlow()

    private val effectChannel = Channel<SupportEffect>(Channel.BUFFERED)
    internal val effects = effectChannel.receiveAsFlow()

    private var lastOperationId = 0L
    private var slowJob: Job? = null

    val contacts = dao.contacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            store.plans.collect(::acceptPublishedPlan)
        }
    }

    internal fun onEvent(event: SupportEvent) {
        when (event) {
            SupportEvent.Edit -> startEditing()
            is SupportEvent.DraftChanged -> changeDraft(event.draft)
            SupportEvent.Done -> startSave()
            SupportEvent.Back -> requestClose()
        }
    }

    private fun startEditing() {
        val state = _uiState.value as? SafetyPlanUiState.Viewing ?: return
        if (state.persisted.updatedAt == UNLOADED_UPDATED_AT) return
        _uiState.value = SafetyPlanUiState.Editing(state.persisted, state.persisted)
    }

    private fun changeDraft(draft: SafetyPlan) {
        val state = _uiState.value as? SafetyPlanUiState.Editing ?: return
        _uiState.value = state.copy(draft = draft, error = null)
    }

    private fun startSave() {
        val state = _uiState.value as? SafetyPlanUiState.Editing ?: return
        val command = SaveSafetyPlan(Math.addExact(lastOperationId, 1L), state.draft)
        lastOperationId = command.operationId
        _uiState.value = SafetyPlanUiState.Saving(state.persisted, command)
        slowJob?.cancel()
        slowJob = viewModelScope.launch {
            delay(slowThresholdMillis)
            val current = _uiState.value as? SafetyPlanUiState.Saving ?: return@launch
            if (current.command.operationId == command.operationId) {
                _uiState.value = current.copy(isSlow = true)
            }
        }
        viewModelScope.launch {
            acceptSaveResult(store.save(command))
        }
    }

    private fun requestClose() {
        when (val state = _uiState.value) {
            is SafetyPlanUiState.Viewing -> effectChannel.trySend(SupportEffect.Close)
            is SafetyPlanUiState.Editing -> {
                _uiState.value = SafetyPlanUiState.Viewing(state.persisted)
                effectChannel.trySend(SupportEffect.Close)
            }
            is SafetyPlanUiState.Saving -> _uiState.value = state.copy(closeRequested = true)
        }
    }

    private fun acceptPublishedPlan(candidate: SafetyPlan) {
        val state = _uiState.value
        if (candidate.updatedAt <= state.persisted.updatedAt) return
        _uiState.value = when (state) {
            is SafetyPlanUiState.Viewing -> state.copy(persisted = candidate)
            is SafetyPlanUiState.Editing -> state.copy(persisted = candidate)
            is SafetyPlanUiState.Saving -> state.copy(persisted = candidate)
        }
    }

    internal fun acceptSaveResult(result: SafetyPlanSaveResult) {
        val saving = _uiState.value as? SafetyPlanUiState.Saving ?: return
        val resultOperationId = when (result) {
            is SafetyPlanSaveResult.Committed -> result.operationId
            is SafetyPlanSaveResult.Failed -> result.operationId
        }
        if (resultOperationId != saving.command.operationId) return

        slowJob?.cancel()
        slowJob = null
        when (result) {
            is SafetyPlanSaveResult.Committed -> {
                val newest = if (result.stored.updatedAt > saving.persisted.updatedAt) {
                    result.stored
                } else {
                    saving.persisted
                }
                _uiState.value = SafetyPlanUiState.Viewing(newest)
                if (saving.closeRequested) effectChannel.trySend(SupportEffect.Close)
            }
            is SafetyPlanSaveResult.Failed -> {
                _uiState.value = SafetyPlanUiState.Editing(
                    persisted = saving.persisted,
                    draft = saving.command.draft,
                    error = SafetyPlanUiError.SaveFailed,
                )
            }
        }
    }

    /**
     * A crisis contact exists to be called, so the number is what makes it
     * one. The old guard only rejected a contact that was blank in both
     * fields, which let a name with no number through — it then sat at the
     * top of the crisis card looking like a way to reach someone and did
     * nothing when tapped.
     */
    fun addContact(name: String, phone: String, isProfessional: Boolean) {
        if (phone.isBlank()) return
        viewModelScope.launch {
            dao.addContact(
                CrisisContact(
                    name = name.trim(),
                    phone = phone.trim(),
                    isProfessional = isProfessional,
                ),
            )
        }
    }

    fun removeContact(contact: CrisisContact) {
        viewModelScope.launch { dao.removeContact(contact) }
    }

    internal companion object {
        const val SLOW_THRESHOLD_MILLIS = 3_000L
        private const val UNLOADED_UPDATED_AT = Long.MIN_VALUE
    }
}
