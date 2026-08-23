package org.mindanchor.letters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mindanchor.data.CheckInPrefs
import org.mindanchor.data.NotesPrefs
import org.mindanchor.llm.LetterContext
import org.mindanchor.llm.LetterError
import org.mindanchor.llm.LlmClient
import org.mindanchor.llm.LlmClientFactory
import org.mindanchor.llm.LlmPrefs
import java.time.LocalDate

/**
 * The state of the LLM-driven letter surface. [Idle] is
 * the inbox; [Writing] is the calm "writing your letter"
 * screen; [Reader] is the success state (a letter is
 * shown); [Error] is the failure state (with the
 * [LetterError] for the UI to render).
 */
sealed class LetterWriteState {
    object Idle : LetterWriteState()
    object Writing : LetterWriteState()
    data class Reader(val letter: Letter, val previous: Letter?) : LetterWriteState()
    data class Error(val error: LetterError) : LetterWriteState()
}

/**
 * Drives the LLM letter generation. The [state] flow is
 * what the UI subscribes to; [generateToday] / [regenerate]
 * / [cancel] / [acknowledgeError] are the user actions.
 *
 * The primary constructor is the production wiring: it
 * derives the [LlmClient] from [LlmPrefs] via
 * [LlmClientFactory]. The internal secondary constructor
 * accepts an explicit [LlmClient] for testability — used
 * by [LetterWriteStateMachineTest] to inject a
 * [org.mindanchor.llm.LlmClient] stub without making a
 * real network call. `internal` so the test (same module)
 * can call it, but the rest of the app and any third-party
 * callers cannot.
 */
class LetterViewModel(
    private val llmPrefs: LlmPrefs,
    private val notesPrefs: NotesPrefs,
    private val checkInPrefs: CheckInPrefs,
    private val letterStore: LetterStore,
    private val letterLog: LetterGenerationLog,
) : ViewModel() {

    /**
     * Test seam: when non-null, [runGeneration] uses this
     * [LlmClient] instead of [LlmClientFactory.create]. Set
     * by the internal secondary constructor; null in
     * production.
     */
    private var clientOverride: LlmClient? = null

    /**
     * Test-only constructor that injects an explicit
     * [LlmClient] (a fake in [LetterWriteStateMachineTest]).
     * Delegates to the primary constructor and then sets
     * [clientOverride] so [runGeneration] uses the fake
     * instead of calling [LlmClientFactory.create]. The
     * production code never calls this — it always uses
     * the 5-arg primary.
     */
    internal constructor(
        llmPrefs: LlmPrefs,
        notesPrefs: NotesPrefs,
        checkInPrefs: CheckInPrefs,
        letterStore: LetterStore,
        letterLog: LetterGenerationLog,
        client: LlmClient,
    ) : this(llmPrefs, notesPrefs, checkInPrefs, letterStore, letterLog) {
        this.clientOverride = client
    }

    private val _state = MutableStateFlow<LetterWriteState>(LetterWriteState.Idle)
    val state: StateFlow<LetterWriteState> = _state.asStateFlow()

    private var currentJob: Job? = null
    private var cancelled: Boolean = false

    /**
     * Transition `Idle -> Writing -> Reader/Error` for today's
     * date. No-op while a generation is already in flight
     * (the `Writing` re-entry guard) — calling again would
     * race the in-flight coroutine.
     */
    fun generateToday() {
        if (_state.value is LetterWriteState.Writing) return // already in flight
        currentJob = viewModelScope.launch {
            runGeneration(today = LocalDate.now(), isRegenerate = false)
        }
    }

    /**
     * Delete today's existing letter, then transition
     * `Reader -> Writing -> Reader/Error` with a fresh
     * generation. No-op while in flight.
     */
    fun regenerate() {
        if (_state.value is LetterWriteState.Writing) return
        currentJob = viewModelScope.launch {
            // Delete today's letter first (so the inbox
            // reverts to "no letter yet for today" while
            // the new one is being written).
            letterStore.delete(LocalDate.now())
            runGeneration(today = LocalDate.now(), isRegenerate = true)
        }
    }

    /**
     * Abort the in-flight generation. Sets the [cancelled]
     * flag (so any soft-checked suspension point bails
     * early) and hard-cancels the [currentJob] (so any
     * uncancelled suspension point throws). State returns
     * to `Idle`.
     */
    fun cancel() {
        cancelled = true
        currentJob?.cancel()
        currentJob = null
        _state.value = LetterWriteState.Idle
    }

    /**
     * Dismiss the `Error` state. Returns to `Idle` so the
     * user can tap `Write today's letter` again.
     */
    fun acknowledgeError() {
        _state.value = LetterWriteState.Idle
    }

    internal suspend fun runGeneration(today: LocalDate, isRegenerate: Boolean) {
        cancelled = false
        _state.value = LetterWriteState.Writing

        val apiKey = llmPrefs.apiKey.first()
        if (apiKey.isBlank()) {
            val error = LetterError.NoApiKey()
            letterLog.append(logEntry(today, error, 0L))
            _state.value = LetterWriteState.Error(error)
            return
        }

        val provider = llmPrefs.provider.first()
        val model = llmPrefs.model.first()
        val client = clientOverride ?: LlmClientFactory.create(provider, apiKey, model)

        // NotesState / CheckInState carry the list under their
        // own field name (NotesState.notes, CheckInState.checkIns),
        // not `.list` as the brief sketch suggested.
        val notes = notesPrefs.notes.first().notes
        val checkIns = checkInPrefs.checkIns.first().checkIns
        val request = LetterContext.build(today, notes, checkIns)

        if (cancelled) return // the cancel() may have raced with this

        val result = client.complete(request)
        if (cancelled) return // the LLM call completed after cancel() — drop the result
        result.onSuccess { response ->
            val previous = letterStore.letters.first().firstOrNull { it.date == today }
            val letter = Letter(
                date = today,
                body = response.content,
                provider = provider.name.lowercase(),
                model = model,
                promptTokens = response.promptTokens,
                completionTokens = response.completionTokens,
                durationMs = response.durationMs,
            )
            if (cancelled) return@onSuccess // save+log write would race the cancel()
            letterStore.save(letter)
            letterLog.append(
                LetterLogEntry(
                    date = today,
                    provider = letter.provider!!,
                    model = letter.model!!,
                    promptTokens = letter.promptTokens,
                    completionTokens = letter.completionTokens,
                    durationMs = letter.durationMs!!,
                    errorClass = null,
                    errorMessage = null,
                    timestampMillis = System.currentTimeMillis(),
                ),
            )
            _state.value = LetterWriteState.Reader(letter = letter, previous = previous)
        }.onFailure { throwable ->
            val error = throwable as? LetterError ?: LetterError.Unknown()
            letterLog.append(logEntry(today, error, 0L))
            _state.value = LetterWriteState.Error(error)
        }
    }

    private fun logEntry(date: LocalDate, error: LetterError, durationMs: Long) = LetterLogEntry(
        date = date,
        provider = "groq",
        model = "unknown", // we may not know the model if the key was missing
        promptTokens = null,
        completionTokens = null,
        durationMs = durationMs,
        errorClass = error::class.simpleName,
        errorMessage = error.userMessage,
        timestampMillis = System.currentTimeMillis(),
    )
}
